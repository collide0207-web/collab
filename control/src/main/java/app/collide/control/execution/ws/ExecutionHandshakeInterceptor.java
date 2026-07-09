package app.collide.control.execution.ws;

import app.collide.control.auth.AuthPrincipal;
import app.collide.control.auth.JwtService;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Verifies the caller before the WebSocket handshake completes. A browser's native
 * WebSocket API can't set an Authorization header, so — same tradeoff the Node sync server
 * makes for its own WS endpoint — the JWT travels as a {@code ?token=} query parameter
 * instead. This path is `permitAll()`-ed in {@code SecurityConfig} precisely so this
 * interceptor, not the servlet-filter JWT auth, is what gates it.
 */
@Component
public class ExecutionHandshakeInterceptor implements HandshakeInterceptor {

    static final String PRINCIPAL_ATTRIBUTE = "principal";

    private final JwtService jwt;

    public ExecutionHandshakeInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = tokenFrom(request.getURI());
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            AuthPrincipal principal = jwt.verify(token);
            attributes.put(PRINCIPAL_ATTRIBUTE, principal);
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }

    private static String tokenFrom(URI uri) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            if (eq > 0 && "token".equals(param.substring(0, eq))) {
                return param.substring(eq + 1);
            }
        }
        return null;
    }
}
