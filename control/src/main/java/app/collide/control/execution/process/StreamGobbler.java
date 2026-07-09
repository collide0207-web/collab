package app.collide.control.execution.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Drains one stream on its own thread, capturing up to {@code maxBytes} of it and forwarding
 * each retained chunk to {@code onChunk} as it arrives (for live streaming). Draining never
 * stops early even after the cap is hit — the child process's pipe buffer must keep emptying
 * or a chatty program would deadlock against a full pipe instead of finishing or being killed
 * on timeout; chunks simply stop being retained/forwarded once the cap is reached, bounding
 * memory the same way for the live view as for the final aggregated text.
 */
final class StreamGobbler implements Runnable {

    private final InputStream in;
    private final long maxBytes;
    private final Consumer<String> onChunk;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private volatile boolean truncated = false;

    StreamGobbler(InputStream in, long maxBytes, Consumer<String> onChunk) {
        this.in = in;
        this.maxBytes = maxBytes;
        this.onChunk = onChunk;
    }

    @Override
    public void run() {
        byte[] chunk = new byte[8192];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                if (buffer.size() < maxBytes) {
                    int room = (int) Math.min(read, maxBytes - buffer.size());
                    buffer.write(chunk, 0, room);
                    onChunk.accept(new String(chunk, 0, room, StandardCharsets.UTF_8));
                    if (room < read) {
                        truncated = true;
                    }
                } else {
                    truncated = true;
                }
            }
        } catch (IOException ignored) {
            // Expected once the process is killed and its pipe is torn down mid-read.
        }
    }

    String text() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    boolean truncated() {
        return truncated;
    }
}
