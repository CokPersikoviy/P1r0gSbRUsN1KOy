package ru.wilyfox.client.discord;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscordPresenceRefreshPolicyTest {
    @Test void enteringAnotherLocationBypassesFiveSecondProgressInterval() {
        var policy = new DiscordPresenceRefreshPolicy();
        assertTrue(policy.shouldUpdate(10000, 5000, 1, 0)); policy.requested(10000, 1);
        assertFalse(policy.shouldUpdate(10100, 5000, 1, 10000));
        assertTrue(policy.shouldUpdate(10100, 5000, 2, 10000)); policy.requested(10100, 2);
        assertFalse(policy.shouldUpdate(10150, 5000, 2, 10120));
        assertTrue(policy.shouldUpdate(10200, 5000, 3, 10120));
    }
    @Test void routineUpdatesWaitForIntervalAndPendingRequestsDoNotFloodWorker() {
        var policy = new DiscordPresenceRefreshPolicy(); policy.requested(10000, 4);
        for (long now = 10001; now < 15000; now += 50) assertFalse(policy.shouldUpdate(now, 5000, 4, 0));
        assertTrue(policy.shouldUpdate(15000, 5000, 4, 0));
        assertFalse(policy.shouldUpdate(15000, 5000, 4, 10200));
        assertTrue(policy.shouldUpdate(15200, 5000, 4, 10200));
    }
    @Test void disconnectAndReenableStartFreshEvenAtSameLocation() {
        var policy = new DiscordPresenceRefreshPolicy(); policy.requested(10000, 2); policy.reset();
        assertTrue(policy.shouldUpdate(10001, 5000, 2, 10000)); assertTrue(policy.contextChanged(2));
    }
}
