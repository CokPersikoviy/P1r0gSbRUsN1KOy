package ru.wilyfox.client.discord;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Coalesces updates and invalidates work from a disconnected or disabled session. */
final class DiscordPresenceUpdateQueue<T> {
    private final Executor executor;
    private final Consumer<Request<T>> handler;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Request<T>> pending = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private long handledGeneration;

    DiscordPresenceUpdateQueue(Executor executor, Consumer<Request<T>> handler) {
        this.executor = executor;
        this.handler = handler;
    }

    synchronized void update(T presence) {
        pending.set(new Request<>(generation.get(), presence));
        schedule();
    }

    synchronized void stop() {
        pending.set(new Request<>(generation.incrementAndGet(), null));
        schedule();
    }

    boolean isCurrent(Request<T> request) {
        return request.generation() == generation.get();
    }

    private void schedule() {
        if (scheduled.compareAndSet(false, true)) {
            executor.execute(this::drain);
        }
    }

    private void drain() {
        try {
            Request<T> request;
            while ((request = pending.getAndSet(null)) != null) {
                if (isCurrent(request)) {
                    if (handledGeneration != request.generation()) {
                        handledGeneration = request.generation();
                        // A reconnect may replace a pending stop with its first presence. Keep the
                        // session boundary so its timestamp and connection state are still reset.
                        handler.accept(new Request<>(request.generation(), null));
                        if (!isCurrent(request) || request.presence() == null) {
                            continue;
                        }
                    }
                    handler.accept(request);
                }
            }
        } finally {
            scheduled.set(false);
            if (pending.get() != null) {
                schedule();
            }
        }
    }

    record Request<T>(long generation, T presence) {
    }
}
