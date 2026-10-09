package ru.wilyfox.client.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PopUpManagerTest {
    @Test
    void soundIsPublishedOnceAndNeverReplayedByRenderingOrMeasurement() {
        var sounds = new java.util.ArrayList<PopUpRequest>();
        var manager = new PopUpManager(() -> 1_000L, sounds::add);
        var request = PopUpRequest.of("test.find", "Find", "", PopUpSeverity.INFO);
        manager.publish(request);
        for (int i = 0; i < 20; i++) manager.getVisibleNotifications(1);
        manager.diagnosticNotificationCount();
        assertEquals(java.util.List.of(request), sounds);
    }

    @Test
    void disabledNotificationDoesNotProduceSound() {
        var config = ru.wilyfox.client.hud.config.ConfigManager.get().popUps;
        boolean previous = config.privateMessageEvent;
        var sounds = new java.util.ArrayList<PopUpRequest>();
        var manager = new PopUpManager(() -> 1_000L, sounds::add);
        try {
            config.privateMessageEvent = false;
            manager.publish(PopUpRequest.of(PopUpSource.PRIVATE_MESSAGE, "Private", "", PopUpSeverity.INFO));
            assertEquals(0, sounds.size());
            assertEquals(0, manager.diagnosticNotificationCount());
        } finally { config.privateMessageEvent = previous; }
    }

    @Test
    void notificationsWaitingForASlotDoNotExpireBeforeBeingDisplayed() {
        var time = new java.util.concurrent.atomic.AtomicLong(1_000L);
        var manager = new PopUpManager(time::get);
        for (int i = 0; i < 5; i++) {
            manager.publish(new PopUpRequest("test.find", "Find " + i, "", PopUpSeverity.SUCCESS, 0, 100, 0));
        }
        assertEquals(java.util.List.of("Find 0", "Find 1", "Find 2"),
                manager.getVisibleNotifications(3).stream().map(PopUpNotification::title).toList());
        time.addAndGet(101L);
        var next = manager.getVisibleNotifications(3);
        assertEquals(java.util.List.of("Find 3", "Find 4"), next.stream().map(PopUpNotification::title).toList());
        assertEquals(time.get(), next.getFirst().createdAtMs());
        time.addAndGet(101L);
        assertEquals(0, manager.diagnosticNotificationCount());
    }

    @Test
    void worldTransitionRemovesBothVisibleAndPendingFinds() {
        var manager = new PopUpManager(() -> 1_000L);
        for (int i = 0; i < 4; i++) {
            manager.publish(new PopUpRequest("test.find", "Find", "", PopUpSeverity.INFO, 0, 100, 0));
        }
        manager.getVisibleNotifications(1);
        manager.removeSources("test.find");
        assertEquals(0, manager.diagnosticNotificationCount());
    }

    @Test
    void stripsMinecraftFormattingFromDisplayedText() {
        assertEquals(
                "Посох силы is ready",
                PopUpManager.sanitizeText("\u00A7eПосох силы \u00A7ris ready", "")
        );
        assertEquals(
                "Посох силы is ready",
                PopUpManager.sanitizeText("&eПосох силы &ris ready", "")
        );
    }

    @Test
    void normalizesWhitespaceButPreservesMessagePunctuation() {
        assertEquals(
                "Potion (95%) expired: take another!",
                PopUpManager.sanitizeText(" Potion\u00A0(95%)\nexpired: take another! ", "")
        );
    }

    @Test
    void usesFallbackWhenFormattingWasTheOnlyContent() {
        assertEquals("Notification", PopUpManager.sanitizeText("\u00A7e\u00A7l", "Notification"));
    }
}
