package com.stayscale.core.exception;

import com.stayscale.core.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Translates backend exceptions into standardized RFC 7807 Problem Details JSON
 * ({@code type}, {@code title}, {@code status}, {@code detail}, plus {@code traceId})
 * so the frontend always gets consistent, human-readable error payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidSearchException.class)
    public ProblemDetail handleInvalidSearch(InvalidSearchException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.BAD_REQUEST, "Invalid Search Parameters", ex.getMessage(), req);
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ProblemDetail handleMissingIdempotencyKey(MissingIdempotencyKeyException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.BAD_REQUEST, "Missing Idempotency-Key Header", ex.getMessage(), req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(InventoryConflictException.class)
    public ProblemDetail handleInventoryConflict(InventoryConflictException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.CONFLICT, "Inventory Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ProblemDetail handleHoldExpired(HoldExpiredException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.GONE, "Checkout Hold Expired", ex.getMessage(), req);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency Key Payload Mismatch", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Invalid value for parameter '" + ex.getName() + "'";
        return buildProblem(HttpStatus.BAD_REQUEST, "Invalid Parameter Type", msg, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildProblem(HttpStatus.BAD_REQUEST, "Validation Failed", details, req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Unexpected server error occurred. Check server logs with traceId.", req);
    }

    private ProblemDetail buildProblem(HttpStatus status, String title, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://stayscale.dev/errors/" + status.value()));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("timestamp", Instant.now().toString());
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_KEY);
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
        }
        return pd;
    }
}
