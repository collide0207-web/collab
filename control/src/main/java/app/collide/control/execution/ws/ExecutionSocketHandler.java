package app.collide.control.execution.ws;

import app.collide.control.auth.AuthPrincipal;
import app.collide.control.execution.ExecutionRegistry;
import app.collide.control.execution.ExecutionState;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * One connection per `/ws/execution/{executionId}`. Subscribes the session to that
 * execution's live events and enforces that only the user who submitted the execution can
 * watch its output — the handshake already verified the caller's identity (see
 * {@link ExecutionHandshakeInterceptor}); this checks it against the execution's owner.
 */
@Component
public class ExecutionSocketHandler extends TextWebSocketHandler {

    private final InProcessExecutionPublisher publisher;
    private final ExecutionRegistry registry;

    public ExecutionSocketHandler(InProcessExecutionPublisher publisher, ExecutionRegistry registry) {
        this.publisher = publisher;
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID executionId = executionIdFrom(session);
        if (executionId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        AuthPrincipal principal = (AuthPrincipal) session.getAttributes().get(ExecutionHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        ExecutionState state;
        try {
            state = registry.get(executionId);
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("no such execution"));
            return;
        }
        if (principal == null || !state.userId().equals(principal.id())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        publisher.subscribe(executionId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID executionId = executionIdFrom(session);
        if (executionId != null) {
            publisher.unsubscribe(executionId, session);
        }
    }

    private static UUID executionIdFrom(WebSocketSession session) {
        String path = session.getUri().getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        try {
            return UUID.fromString(last);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
