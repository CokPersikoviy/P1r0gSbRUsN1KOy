package ru.wilyfox.client.moduser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModUserProtocolTest {
    private static final Pattern CHUNK =
            Pattern.compile("\\{fhmu:[A-Za-z0-9_-]+:([1-9]\\d*):([1-9]\\d*):([A-Za-z0-9_-]+)}");

    @Test
    void largeSyncFitsTheChatLimitIncludingTwoDigitCounters() {
        String target = "SixteenCharName12";
        String payload = "x".repeat(2_300);
        List<String> chunks = ModUserProtocol.splitPayload(target, payload);
        assertTrue(chunks.size() >= 10);
        StringBuilder assembled = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            assertTrue(("m " + target + " " + chunk).length() <= 240);
            Matcher matcher = CHUNK.matcher(chunk);
            assertTrue(matcher.matches());
            assertEquals(index + 1, Integer.parseInt(matcher.group(1)));
            assertEquals(chunks.size(), Integer.parseInt(matcher.group(2)));
            assembled.append(matcher.group(3));
        }
        assertEquals(payload, assembled.toString());
    }

    @Test
    void neverSendsMorePartsThanTheReceiverAccepts() {
        assertTrue(ModUserProtocol.splitPayload("Player", "x".repeat(8_192)).isEmpty());
    }

    @Test
    void incomingSyncKeepsOnlyPlayerNamesAndLimitsItsSize() {
        assertEquals(List.of("Player", "Other_User"),
                ModUserProtocol.parseNames(" Player , /spawn ,Other_User,ab,way_too_long_for_a_name"));
        String names = String.join(",", IntStream.range(0, 150).mapToObj(index -> "Player" + index).toList());
        assertEquals(100, ModUserProtocol.parseNames(names).size());
    }
}
