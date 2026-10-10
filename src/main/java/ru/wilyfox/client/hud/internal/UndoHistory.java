package ru.wilyfox.client.hud.internal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Menu-local history. Snapshots must be independent of the mutable state they describe. */
public final class UndoHistory<T> {
    private final int capacity;
    private final Deque<T> previous = new ArrayDeque<>();
    private final java.util.function.BiPredicate<T, T> sameState;
    private T pending;

    public UndoHistory(int capacity) {
        this(capacity, Objects::equals);
    }

    public UndoHistory(int capacity, java.util.function.BiPredicate<T, T> sameState) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.sameState = Objects.requireNonNull(sameState);
    }

    public boolean isPending() { return pending != null; }

    public void begin(T state) {
        if (pending == null) pending = Objects.requireNonNull(state);
    }

    public void finish(T state) {
        if (pending == null) return;
        if (!sameState.test(pending, state)) {
            if (previous.size() == capacity) previous.removeFirst();
            previous.addLast(pending);
        }
        pending = null;
    }

    public T undo() {
        if (pending != null) throw new IllegalStateException("Finish the gesture before undoing it");
        return previous.pollLast();
    }

    public void clear() {
        pending = null;
        previous.clear();
    }
}
