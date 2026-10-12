package ru.wilyfox.client.chat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.ChatWidgetConfig;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ChatTextFilterTest {
    @Test void followsChatPlusWholeMessageSemanticsAndKeepsPatternCase() {
        assertTrue(ChatTextFilter.compile("", true).matches("any\nmessage"));
        assertFalse(ChatTextFilter.compile("dragon", true).matches("Fox: dragon"));
        assertTrue(ChatTextFilter.compile(".*dragon.*", true).matches("Fox: dragon"));
        assertFalse(ChatTextFilter.compile(".*Dragon.*", true).matches("Fox: dragon"));
        assertTrue(ChatTextFilter.compile("(?i).*Dragon.*", true).matches("Fox: DRAGON"));
        assertTrue(ChatTextFilter.compile("(?s).*", true).matches("line\nline"));
        assertTrue(ChatTextFilter.compile("\\S+ \\d+", true).matches("Fox 123"));
        assertFalse(ChatTextFilter.compile("\\S+ \\d+", true).matches(" Fox 123"));
        assertTrue(ChatTextFilter.compile(" Fox ", true).matches(" Fox "));
        assertFalse(ChatTextFilter.compile(" Fox ", true).matches("Fox"));
    }
    @Test void supportsAlternationNegationLookaroundsAndBackreferences() {
        var filter = ChatTextFilter.compile("(?iu)^(?!.*:).*(заш[её]л|выш[её]л).*", true);
        assertTrue(filter.matches("Fox зашел на сервер"));
        assertTrue(filter.matches("FOX ВЫШЁЛ с сервера"));
        assertFalse(filter.matches("Fox: зашел в шахту"));
        assertFalse(filter.matches("Fox получил награду"));
        assertTrue(ChatTextFilter.compile("(Fox) \\1", true).matches("Fox Fox"));
        assertTrue(ChatTextFilter.compile(".*(?<=Fox )hello", true).matches("Fox hello"));
    }
    @Test void legacyLiteralFiltersKeepSymbolsAndCaseInsensitiveSubstring() {
        var literal = ChatTextFilter.compile(" [C] DRAGON + ? ", false);
        assertTrue(literal.matches("Fox: [c] dragon + ? yes"));
        assertFalse(literal.matches("Fox: c dragon"));
        var gson = new Gson();
        var old = gson.fromJson("{\"channel\":\"CLAN\",\"textFilter\":\"[C]\"}", ChatWidgetConfig.class);
        assertFalse(old.regexFilter);
        var fresh = new ChatWidgetConfig(ChatTab.CLAN);
        assertTrue(fresh.regexFilter);
        assertTrue(gson.fromJson(gson.toJson(fresh), ChatWidgetConfig.class).regexFilter);
    }
    @Test void invalidAndPathologicalPatternsDoNotCrashOrHang() {
        var invalid = ChatTextFilter.compile("[", true);
        assertNotNull(invalid.error()); assertFalse(invalid.matches("["));
        assertNull(ChatTextFilter.compile("[", false).error());
        assertTrue(ChatTextFilter.compile("[", false).matches("["));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            var expensive = ChatTextFilter.compile("(a+)+$", true);
            assertFalse(expensive.matches("a".repeat(5000) + "!"));
            assertFalse(expensive.matches("a".repeat(5000) + "!"));
            assertTrue(expensive.matches("aaa"));
            assertFalse(ChatTextFilter.compile("(a|aa)+$", true).matches("a".repeat(5000) + "!"));
        });
    }
}
