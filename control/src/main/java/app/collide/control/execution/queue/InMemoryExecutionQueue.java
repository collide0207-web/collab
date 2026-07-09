package app.collide.control.execution.queue;

import app.collide.control.common.ApiException;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single-node bounded worker pool. Pool size caps how many executions actually run at once;
 * the bounded backing queue caps how many more can wait before new submissions are rejected
 * (surfaced to the client as 503, not an unbounded queue that would let memory grow without
 * limit under load).
 */
@Component
public class InMemoryExecutionQueue implements ExecutionQueue {

    private final ThreadPoolExecutor executor;

    public InMemoryExecutionQueue(@Value("${collide.execution.max-concurrent-executions:4}") int maxConcurrent) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread t = new Thread(runnable, "execution-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = new ThreadPoolExecutor(
                maxConcurrent,
                maxConcurrent,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(maxConcurrent * 4),
                threadFactory);
    }

    @Override
    public void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            throw ApiException.serviceUnavailable("execution queue is full, please try again shortly");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
