package com.stayscale.core.service;

import com.stayscale.core.exception.IdempotencyConflictException;
import com.stayscale.core.exception.MissingIdempotencyKeyException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Layer 3 Defense: HTTP Idempotency Middleware backed by PostgreSQL.
 *
 * <p>Prevents duplicate holds, charges, or cancellations when clients retry after
 * mobile network drops or double-clicks:
 * <ul>
 *   <li>Identical {@code Idempotency-Key} + identical payload -> replays saved HTTP status &amp; JSON body.</li>
 *   <li>Identical {@code Idempotency-Key} + different payload -> rejects with {@code 422 Unprocessable Entity}.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private static final long RETENTION_HOURS = 24;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IdempotencyService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public String requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header is required for this endpoint");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > 160) {
            throw new MissingIdempotencyKeyException("Idempotency-Key cannot exceed 160 characters");
        }
        return trimmed;
    }

    public String hashRequest(String endpoint, Object payload) {
        String json = payload == null ? "" : objectMapper.writeValueAsString(payload);
        String canonical = endpoint + "|" + json;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public <T> Optional<ReplayResult<T>> findReplay(String key, String requestHash, Class<T> responseType) {
        String sql = """
                SELECT request_hash, http_status, response_body::text AS body_text
                FROM idempotency_keys
                WHERE idempotency_key = :key
                  AND expires_at > NOW()
                """;
        List<StoredRecord> rows = jdbc.query(sql, new MapSqlParameterSource("key", key), (rs, rowNum) ->
                new StoredRecord(
                        rs.getString("request_hash"),
                        rs.getInt("http_status"),
                        rs.getString("body_text")
                ));

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        StoredRecord record = rows.getFirst();
        if (!record.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key '" + key + "' was already used with a different request payload"
            );
        }
        T body = objectMapper.readValue(record.responseBody(), responseType);
        return Optional.of(new ReplayResult<>(record.httpStatus(), body));
    }

    /**
     * Persists the idempotency record. When invoked inside a {@code @Transactional} booking method,
     * this commits atomically alongside the booking row and inventory update.
     */
    public void store(String key, String endpoint, String requestHash, int httpStatus, Object responseBody) {
        String json = objectMapper.writeValueAsString(responseBody);
        OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                Instant.now().plus(RETENTION_HOURS, ChronoUnit.HOURS), ZoneOffset.UTC);

        String sql = """
                INSERT INTO idempotency_keys (idempotency_key, endpoint, request_hash, http_status, response_body, expires_at)
                VALUES (:key, :endpoint, :requestHash, :httpStatus, CAST(:responseBody AS jsonb), :expiresAt)
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("key", key)
                    .addValue("endpoint", endpoint)
                    .addValue("requestHash", requestHash)
                    .addValue("httpStatus", httpStatus)
                    .addValue("responseBody", json)
                    .addValue("expiresAt", expiresAt));
        } catch (DuplicateKeyException ex) {
            // Concurrent identical retry raced with us; verify hash matches rather than failing silently.
            findReplay(key, requestHash, Object.class);
        }
    }

    public record ReplayResult<T>(int httpStatus, T body) {}

    private record StoredRecord(String requestHash, int httpStatus, String responseBody) {}
}
