package app.collide.control.execution.ws;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Single-node session registry + fan-out. See {@link ExecutionPublisher} for the seam this is. */
@Component
public class InProcessExecutionPublisher implements ExecutionPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessExecutionPublisher.class);

    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> sessionsByExecution = new ConcurrentHashMap<>();

    public void subscribe(UUID executionId, WebSocketSession session) {
        sessionsByExecution.computeIfAbsent(executionId, id -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unsubscribe(UUID executionId, WebSocketSession session) {
        sessionsByExecution.computeIfPresent(executionId, (id, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @Override
    public void publish(UUID executionId, ExecutionEvent event) {
        Set<WebSocketSession> sessions = sessionsByExecution.get(executionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(event.toJson());
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.debug("dropping execution event — session {} is no longer writable", session.getId());
            }
        }
    }
}
