package com.stayscale.core.service;

import com.stayscale.core.dto.HoldResponse;
import com.stayscale.core.exception.HoldExpiredException;
import com.stayscale.core.exception.InventoryConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Layer 1 Defense: Ordered Redis Distributed Locks + 10-Minute Checkout Holds.
 *
 * <p>How double-holds and leaked hold counters are prevented:
 * <ol>
 *   <li>Short locks ({@code SET NX PX 5000}) are acquired in strict ascending {@code stayDate} order
 *       so concurrent requests for overlapping dates can never deadlock.</li>
 *   <li>Active holds per {@code (roomTypeId, stayDate)} are indexed in a Redis Sorted Set (ZSET)
 *       scored by {@code expiresAtEpochMs}. Before checking capacity, {@code ZREMRANGEBYSCORE -inf now}
 *       purges any expired 10-minute holds immediately—preventing leaked hold counts.</li>
 *   <li>Short locks are released in {@code finally} using an atomic Lua compare-and-delete script
 *       so a slow thread can never delete another request's lock.</li>
 * </ol>
 */
@Service
public class RedisHoldService {

    private static final String LOCK_PREFIX = "stayscale:lock:room:";
    private static final String HOLD_PREFIX = "stayscale:hold:";
    private static final String HOLD_INDEX_PREFIX = "stayscale:hold_idx:";
    private static final Duration LOCK_TTL = Duration.ofMillis(5000);
    private static final int LOCK_MAX_ATTEMPTS = 12;

    // Atomic Lua compare-and-delete ensures only the lock owner token can unlock.
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long holdTtlSeconds;

    public RedisHoldService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${stayscale.holds.ttl-seconds:600}") long holdTtlSeconds
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.holdTtlSeconds = holdTtlSeconds;
    }

    /**
     * Atomically verifies {@code dbAvailable - activeRedisHeld >= roomsRequested} across every night
     * in {@code [checkIn, checkOut)} and reserves a 10-minute checkout hold token.
     */
    public HoldResponse acquireHold(
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomsRequested,
            Map<LocalDate, Integer> dbAvailableByDate,
            HoldResponse draftHoldTemplate
    ) {
        List<LocalDate> dates = enumerateDatesSorted(checkIn, checkOut);
        String lockToken = UUID.randomUUID().toString();
        List<String> acquiredLockKeys = new ArrayList<>(dates.size());

        try {
            // 1. Acquire ordered short locks across all stay dates to protect Redis check-and-reserve.
            for (LocalDate date : dates) {
                String lockKey = LOCK_PREFIX + roomTypeId + ":" + date;
                if (!tryAcquireLock(lockKey, lockToken)) {
                    throw new InventoryConflictException(
                            "High demand on this room right now — please retry in a moment");
                }
                acquiredLockKeys.add(lockKey);
            }

            // 2. Purge expired holds and verify remaining capacity across EVERY night.
            long nowMs = System.currentTimeMillis();
            for (LocalDate date : dates) {
                String idxKey = HOLD_INDEX_PREFIX + roomTypeId + ":" + date;
                redis.opsForZSet().removeRangeByScore(idxKey, Double.NEGATIVE_INFINITY, nowMs);

                int activeHeld = sumActiveHeldRooms(idxKey);
                int dbAvail = dbAvailableByDate.getOrDefault(date, 0);
                int effectiveAvail = dbAvail - activeHeld;
                if (effectiveAvail < roomsRequested) {
                    throw new InventoryConflictException(
                            String.format("Only %d room(s) available on %s (including active checkouts)",
                                    Math.max(0, effectiveAvail), date));
                }
            }

            // 3. Create hold payload & index entries with 10-minute TTL.
            String holdId = "hold_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Instant expiresAt = Instant.now().plusSeconds(holdTtlSeconds);
            long expiresAtMs = expiresAt.toEpochMilli();

            HoldResponse finalized = new HoldResponse(
                    holdId,
                    draftHoldTemplate.userId(),
                    draftHoldTemplate.hotelId(),
                    draftHoldTemplate.hotelName(),
                    draftHoldTemplate.hotelCity(),
                    draftHoldTemplate.hotelNeighborhood(),
                    draftHoldTemplate.hotelImageUrl(),
                    draftHoldTemplate.roomTypeId(),
                    draftHoldTemplate.roomName(),
                    draftHoldTemplate.roomImageUrl(),
                    draftHoldTemplate.checkIn(),
                    draftHoldTemplate.checkOut(),
                    draftHoldTemplate.totalNights(),
                    draftHoldTemplate.rooms(),
                    draftHoldTemplate.guests(),
                    expiresAt,
                    holdTtlSeconds,
                    draftHoldTemplate.pricing()
            );

            String holdJson = objectMapper.writeValueAsString(finalized);
            redis.opsForValue().set(HOLD_PREFIX + holdId, holdJson, Duration.ofSeconds(holdTtlSeconds));

            String member = holdId + ":" + roomsRequested;
            for (LocalDate date : dates) {
                String idxKey = HOLD_INDEX_PREFIX + roomTypeId + ":" + date;
                redis.opsForZSet().add(idxKey, member, expiresAtMs);
                redis.expire(idxKey, Duration.ofSeconds(holdTtlSeconds + 60));
            }

            return finalized;
        } finally {
            // Release locks in reverse order using atomic Lua script so only our token deletes them.
            for (int i = acquiredLockKeys.size() - 1; i >= 0; i--) {
                redis.execute(UNLOCK_SCRIPT, Collections.singletonList(acquiredLockKeys.get(i)), lockToken);
            }
        }
    }

    /**
     * Loads an active checkout hold with live remaining TTL, or throws {@link HoldExpiredException} (410 Gone).
     */
    public HoldResponse getActiveHold(String holdId) {
        String key = HOLD_PREFIX + holdId;
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            throw new HoldExpiredException("Checkout hold '" + holdId + "' has expired or does not exist");
        }
        HoldResponse stored = objectMapper.readValue(json, HoldResponse.class);
        long remainingSeconds = Math.max(0L, Duration.between(Instant.now(), stored.expiresAt()).getSeconds());
        if (remainingSeconds <= 0L) {
            throw new HoldExpiredException("Checkout hold '" + holdId + "' has expired");
        }
        return new HoldResponse(
                stored.holdId(),
                stored.userId(),
                stored.hotelId(),
                stored.hotelName(),
                stored.hotelCity(),
                stored.hotelNeighborhood(),
                stored.hotelImageUrl(),
                stored.roomTypeId(),
                stored.roomName(),
                stored.roomImageUrl(),
                stored.checkIn(),
                stored.checkOut(),
                stored.totalNights(),
                stored.rooms(),
                stored.guests(),
                stored.expiresAt(),
                remainingSeconds,
                stored.pricing()
        );
    }

    /**
     * Releases a hold from Redis once PostgreSQL has permanently committed the booking.
     */
    public void consumeHold(HoldResponse hold) {
        redis.delete(HOLD_PREFIX + hold.holdId());
        String member = hold.holdId() + ":" + hold.rooms();
        for (LocalDate date : enumerateDatesSorted(hold.checkIn(), hold.checkOut())) {
            redis.opsForZSet().remove(HOLD_INDEX_PREFIX + hold.roomTypeId() + ":" + date, member);
        }
    }

    private boolean tryAcquireLock(String lockKey, String token) {
        for (int attempt = 0; attempt < LOCK_MAX_ATTEMPTS; attempt++) {
            Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            try {
                Thread.sleep(20L + (attempt * 5L));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private int sumActiveHeldRooms(String idxKey) {
        Set<String> members = redis.opsForZSet().range(idxKey, 0, -1);
        if (members == null || members.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (String member : members) {
            int colon = member.lastIndexOf(':');
            if (colon > 0 && colon < member.length() - 1) {
                try {
                    total += Integer.parseInt(member.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return total;
    }

    private List<LocalDate> enumerateDatesSorted(LocalDate checkIn, LocalDate checkOut) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            dates.add(d);
        }
        return dates;
    }
}
