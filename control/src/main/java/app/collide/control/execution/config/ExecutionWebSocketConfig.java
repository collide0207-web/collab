package app.collide.control.execution.config;

import app.collide.control.execution.ws.ExecutionHandshakeInterceptor;
import app.collide.control.execution.ws.ExecutionSocketHandler;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the live-output socket at /ws/execution/{executionId}. */
@Configuration
@EnableWebSocket
public class ExecutionWebSocketConfig implements WebSocketConfigurer {

    private final ExecutionSocketHandler handler;
    private final ExecutionHandshakeInterceptor handshakeInterceptor;
    private final List<String> corsOrigins;

    public ExecutionWebSocketConfig(
            ExecutionSocketHandler handler,
            ExecutionHandshakeInterceptor handshakeInterceptor,
            @Value("${collide.security.cors-allowed-origins}") String corsOrigins) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.corsOrigins = Arrays.stream(corsOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/execution/*")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(corsOrigins.toArray(new String[0]));
    }
}
