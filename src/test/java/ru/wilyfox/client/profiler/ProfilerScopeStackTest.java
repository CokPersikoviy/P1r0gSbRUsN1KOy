package ru.wilyfox.client.profiler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfilerScopeStackTest {
    @Test
    void closesNestedScopesInReverseOrder() {
        ProfilerScopeStack scopes = new ProfilerScopeStack();
        List<String> closed = new ArrayList<>();

        scopes.push(() -> closed.add("outer"));
        scopes.push(() -> closed.add("inner"));
        scopes.closeLatest();
        scopes.closeLatest();

        assertEquals(List.of("inner", "outer"), closed);
    }

    @Test
    void ignoresUnmatchedReturnInjection() {
        ProfilerScopeStack scopes = new ProfilerScopeStack();
        scopes.closeLatest();
        scopes.closeLatest();
    }
}
