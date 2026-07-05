package app.collide.control.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Per-request client metadata for device tracking and audit. Extracted from the
 * request in the controller so services stay free of the servlet API. Honours
 * X-Forwarded-For (first hop) when behind a trusted reverse proxy / load balancer.
 */
public record ClientInfo(String device, String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest req) {
        return new ClientInfo(header(req, "X-Device"), clientIp(req), header(req, "User-Agent"));
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim(); // left-most = original client
        }
        return req.getRemoteAddr();
    }

    private static String header(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        if (v == null) return null;
        return v.length() > 512 ? v.substring(0, 512) : v; // bound stored length
    }
}
