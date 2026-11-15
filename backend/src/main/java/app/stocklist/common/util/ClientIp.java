package app.stocklist.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {
    private ClientIp() {}

    /** With server.forward-headers-strategy=native, getRemoteAddr() already reflects the proxy's X-Forwarded-For. */
    public static String of(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip != null && ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
