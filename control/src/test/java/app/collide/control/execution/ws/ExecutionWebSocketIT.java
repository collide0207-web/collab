package app.collide.control.execution.ws;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.collide.control.support.PostgresIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Exercises the real handshake + socket handler + publisher against a running server — no
 * mocks. Uses the submitting user's own execution and a distinct `?token=` (matching the
 * Node sync server's own approach, since a browser WebSocket can't set an Authorization
 * header) rather than MockMvc, which never opens a real socket.
 *
 * Known limitation being exercised implicitly here: a subscriber only receives events
 * published after it connects (no backlog/replay), so the submitted program has a short
 * artificial delay to guarantee the WS client is subscribed before it finishes — a client
 * that connects late should fall back to polling GET /result instead of waiting on the socket.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExecutionWebSocketIT extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void streamsLiveOutputAndAFinalResultEvent() throws Exception {
        String accessToken = signUpAndGetAccessToken();
        String executionId = submitExecution(accessToken);

        CompletableFuture<Void> gotResult = new CompletableFuture<>();
        CopyOnWriteArrayList<String> frames = new CopyOnWriteArrayList<>();
        WebSocket ws = http.newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://localhost:" + port + "/ws/execution/" + executionId + "?token=" + accessToken),
                        new WebSocket.Listener() {
                            private final StringBuilder acc = new StringBuilder();

                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                acc.append(data);
                                if (last) {
                                    String frame = acc.toString();
                                    acc.setLength(0);
                                    frames.add(frame);
                                    if (frame.contains("\"type\":\"result\"")) {
                                        gotResult.complete(null);
                                    }
                                }
                                webSocket.request(1);
                                return null;
                            }
                        })
                .get(5, TimeUnit.SECONDS);
        ws.request(1);

        gotResult.get(10, TimeUnit.SECONDS);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");

        String resultFrame = frames.stream().filter(f -> f.contains("\"type\":\"result\"")).findFirst().orElseThrow();
        assertTrue(resultFrame.contains("COMPLETED"), resultFrame);
        assertTrue(resultFrame.contains("hello from ws"), resultFrame);
    }

    private String signUpAndGetAccessToken() throws Exception {
        String email = "ws-" + System.nanoTime() + "@example.com";
        String body = """
                {"email":"%s","username":"ws%d","name":"WS Tester","password":"Str0ng!Pass"}"""
                .formatted(email, System.nanoTime());

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/signup"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return extract(response.body(), "accessToken");
    }

    private String submitExecution(String accessToken) throws Exception {
        String source = "setTimeout(() => console.log('hello from ws'), 500);";
        String body = "{\"language\":\"javascript\",\"sourceCode\":" + jsonQuote(source) + "}";

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/execute"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + accessToken)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return extract(response.body(), "executionId");
    }

    private static String jsonQuote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String extract(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("field not found in response: " + field + " -- " + json);
        }
        return matcher.group(1);
    }
}
