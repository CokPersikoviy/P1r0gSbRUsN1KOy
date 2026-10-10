package ru.wilyfox.client.hud.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndoHistoryTest {
    @Test void keepsOnlyFortyMostRecentActions() {
        var history = new UndoHistory<Integer>(40);
        for (int i = 0; i < 70; i++) { history.begin(i); history.finish(i + 1); }
        for (int i = 69; i >= 30; i--) assertEquals(i, history.undo());
        assertNull(history.undo());
    }

    @Test void wholeGestureKeepsItsInitialStateAndIgnoresNoOps() {
        var history = new UndoHistory<Integer>(40);
        history.begin(10); history.begin(11); history.begin(20); history.finish(50);
        history.begin(50); history.finish(50);
        assertEquals(10, history.undo());
        assertNull(history.undo());
    }

    @Test void closingMenuClearsPendingAndFinishedActions() {
        var history = new UndoHistory<Integer>(40);
        history.begin(1); history.finish(2); history.begin(2); history.clear();
        assertFalse(history.isPending());
        assertNull(history.undo());
        history.begin(7); history.finish(8);
        assertEquals(7, history.undo());
    }

    @Test void navigationMetadataDoesNotBecomeAnUndoAction() {
        record State(int value, String page) {}
        var history = new UndoHistory<State>(40, (a, b) -> a.value == b.value);
        history.begin(new State(10, "Main")); history.finish(new State(10, "Fishing"));
        assertNull(history.undo());
        history.begin(new State(10, "Fishing")); history.finish(new State(20, "Fishing"));
        assertEquals(new State(10, "Fishing"), history.undo());
    }
}
