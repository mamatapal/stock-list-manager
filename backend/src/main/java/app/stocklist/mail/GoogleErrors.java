package app.stocklist.mail;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Safe diagnostics from Google error answers. Only Google's fixed error codes are kept: the HTTP status, the
 * "status" and "reason" codes of a Gmail API error, the "error" code of a token endpoint error, and a numeric
 * Retry-After. Free text ("message", "error_description") is never used, because it can repeat parts of the request
 * such as an address. Values that do not look like a plain code are dropped.
 */
final class GoogleErrors {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Pattern SECONDS = Pattern.compile("[0-9]{1,6}");

    private GoogleErrors() {}

    /** Parses a JSON object, or returns an empty map for anything else. */
    static Map<?, ?> parse(String body) {
        if (body == null || body.isBlank()) return Map.of();
        try {
            Object value = JSON.readValue(body, Map.class);
            return value instanceof Map<?, ?> m ? m : Map.of();
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    /** "HTTP 403, status=PERMISSION_DENIED, reason=insufficientPermissions" for a Gmail API error. */
    static String gmail(int httpStatus, String body, HttpHeaders headers) {
        StringBuilder s = new StringBuilder("HTTP ").append(httpStatus);
        if (parse(body).get("error") instanceof Map<?, ?> error) {
            add(s, "status", error.get("status"));
            if (error.get("errors") instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                add(s, "reason", first.get("reason"));
            }
        }
        retryAfter(s, headers);
        return s.toString();
    }

    /** "HTTP 401, error=invalid_client" for a token endpoint error. */
    static String token(int httpStatus, Map<?, ?> json) {
        StringBuilder s = new StringBuilder("HTTP ").append(httpStatus);
        add(s, "error", json.get("error"));
        return s.toString();
    }

    private static void add(StringBuilder s, String name, Object value) {
        if (value instanceof String v && CODE.matcher(v).matches()) s.append(", ").append(name).append('=').append(v);
    }

    private static void retryAfter(StringBuilder s, HttpHeaders headers) {
        if (headers == null) return;
        headers.firstValue("Retry-After")
                .filter(v -> SECONDS.matcher(v.trim()).matches())
                .ifPresent(v -> s.append(", retry-after=").append(v.trim()).append('s'));
    }
}
