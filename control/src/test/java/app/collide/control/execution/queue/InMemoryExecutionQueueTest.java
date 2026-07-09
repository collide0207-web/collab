package app.collide.control.execution.queue;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.collide.control.common.ApiException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryExecutionQueueTest {

    @Test
    void runsSubmittedTasks() throws InterruptedException {
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(2);
        CountDownLatch ran = new CountDownLatch(1);

        queue.submit(ran::countDown);

        assertTrue(ran.await(2, TimeUnit.SECONDS), "submitted task should have run");
    }

    @Test
    void rejectsWorkOnceThePoolAndItsBackingQueueAreSaturated() throws InterruptedException {
        // pool size 1, backing queue capacity 1*4=4 -> 1 running + 4 queued = 5 accepted, the 6th rejects.
        InMemoryExecutionQueue queue = new InMemoryExecutionQueue(1);
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);

        queue.submit(() -> {
            workerStarted.countDown();
            await(blockWorker);
        });
        assertTrue(workerStarted.await(2, TimeUnit.SECONDS));

        for (int i = 0; i < 4; i++) {
            queue.submit(() -> await(blockWorker));
        }

        assertThrows(ApiException.class, () -> queue.submit(() -> {}));

        blockWorker.countDown();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
