package ru.wilyfox.client.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordRpcServiceTest {
    @Test
    void activityTextIncludesItsEllipsisWithinTheDiscordLimit() {
        assertEquals("x".repeat(125) + "...", DiscordRpcService.limit("x".repeat(200), 128));
    }

    @Test
    void leavesShortTextUnchangedAndHandlesMissingText() {
        assertEquals("Mining", DiscordRpcService.limit("Mining", 128));
        assertEquals("", DiscordRpcService.limit(null, 128));
        assertEquals("ab", DiscordRpcService.limit("abcd", 2));
    }
}
