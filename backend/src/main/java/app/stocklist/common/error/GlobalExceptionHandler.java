package app.stocklist.common.error;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error leaves the API as RFC 7807 problem+json with a stable "code" the app can switch on. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setProperty("code", code);
        return pd;
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail api(ApiException e) {
        ProblemDetail pd = problem(e.status(), e.code(), e.getMessage());
        if (e instanceof app.stocklist.common.sync.VersionConflictException c) {
            pd.setProperty("currentVersion", c.currentVersion());
            pd.setProperty("current", c.current());
        }
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(),
                        "message", f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request could not be read");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail denied(Exception e) {
        return problem(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "You do not have permission for this action");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException e) {
        log.warn("Constraint violation: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "CONFLICT", "This change conflicts with existing data");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail stale(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "STALE_VERSION", "Someone else changed this record. Reload and try again.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail noResource(NoResourceFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong");
    }
}
