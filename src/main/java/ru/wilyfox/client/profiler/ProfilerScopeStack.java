package ru.wilyfox.client.profiler;

import java.util.ArrayDeque;
import java.util.Deque;

/** Keeps HEAD/RETURN profiler injections balanced across reentrant calls. */
public final class ProfilerScopeStack {
    private final ThreadLocal<Deque<ModProfiler.Scope>> scopes = ThreadLocal.withInitial(ArrayDeque::new);
    private volatile boolean used;

    public void push(ModProfiler.Scope scope) {
        if (ModProfiler.isNoopScope(scope)) {
            return;
        }
        used = true;
        scopes.get().addLast(scope);
    }

    public void closeLatest() {
        if (!used) return;
        Deque<ModProfiler.Scope> threadScopes = scopes.get();
        ModProfiler.Scope scope = threadScopes.pollLast();
        if (scope != null) {
            scope.close();
        }
    }
}
