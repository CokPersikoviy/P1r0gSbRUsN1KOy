package ru.wilyfox.client.hud.config;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.wilyfox.client.chat.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatPlusTabImportTest {
    @TempDir Path directory;
    private static JsonObject profile(String name, String pattern) {
        var p = new JsonObject(); p.addProperty("name", name); p.addProperty("pattern", pattern); return p;
    }
    private static String config(JsonObject[]... groups) {
        var root = new JsonObject(); var windows = new JsonArray(); var window = new JsonObject();
        var settings = new JsonObject(); var tabs = new JsonArray();
        for (var group : groups) {
            var tab = new JsonObject(); var profiles = new JsonArray(); for (var p : group) profiles.add(p);
            tab.add("settings", profiles); tabs.add(tab);
        }
        settings.add("tabs", tabs); window.add("tabSettings", settings); windows.add(window); root.add("chatWindows", windows);
        root.addProperty("unrelatedSetting", "ignored"); return root.toString();
    }
    private static ChatPlusTabImport.Selection selection(ChatPlusTabImport.Tab tab) { return new ChatPlusTabImport.Selection(tab, ChatTab.ALL); }

    @Test void actualSerializedShapePreservesEscapesPriorityAndServerVariants() throws Exception {
        var plain = profile("Joined", "(?iu)^(?!.*:).*(заш[её]л|выш[её]л).*");
        plain.addProperty("skipOthers", true); plain.addProperty("priority", 9);
        var dw = profile("DW", "\\S+ joined"); var server = new JsonObject(); server.addProperty("pattern", ".*diamondworld.*"); dw.add("serverPattern", server);
        var tabs = ChatPlusTabImport.parse(config(new JsonObject[]{dw, plain}, new JsonObject[]{profile("All", "(?s).*")}));
        assertEquals("Joined", tabs.getFirst().name()); assertEquals(9, tabs.getFirst().priority()); assertTrue(tabs.getFirst().skipOthers());
        assertTrue(ChatTextFilter.compile(tabs.getFirst().pattern(), true).matches("Игрок зашёл"));
        assertEquals("\\S+ joined", tabs.get(1).pattern()); assertEquals(".*diamondworld.*", tabs.get(1).server());
        assertEquals(tabs.getFirst().group(), tabs.get(1).group());
        var target = HudConfigSanitizer.sanitize(new HudConfig());
        assertThrows(IllegalArgumentException.class, () -> ChatPlusTabImport.append(target, List.of(selection(tabs.getFirst()), selection(tabs.get(1)))));
        assertEquals(7, target.chatWidgets.size());
    }
    @Test void unsupportedFiltersAreVisibleButCannotBeImportedOrTruncated() throws Exception {
        var colors = profile("Colors", "§a.*"); colors.addProperty("formatted", true);
        var longRegex = profile("Long", "x".repeat(513)); var invalid = profile("Invalid", "[");
        var tabs = ChatPlusTabImport.parse(config(new JsonObject[]{colors}, new JsonObject[]{longRegex}, new JsonObject[]{invalid}));
        assertTrue(tabs.stream().noneMatch(ChatPlusTabImport.Tab::compatible)); assertEquals(513, tabs.get(1).pattern().length());
        var target = HudConfigSanitizer.sanitize(new HudConfig());
        assertThrows(IllegalArgumentException.class, () -> ChatPlusTabImport.append(target, List.of(selection(tabs.getFirst()))));
        assertEquals(7, target.chatWidgets.size());
    }
    @Test void addingAndReimportingLeaveAdaptedTabsAllFhHistoryAndLayoutsUntouched() throws Exception {
        var target = HudConfigSanitizer.sanitize(new HudConfig());
        String existingKey = "ChatWidgetCustom_aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var adapted = new ChatWidgetConfig(ChatTab.CLAN); adapted.title = "Bosses"; adapted.textFilter = "(?iu).*Legion.*"; adapted.detached = true;
        target.chatWidgets.put(existingKey, adapted); target.mainLayout.widgets.add(existingKey);
        var all = target.chatWidgets.get(WidgetCatalog.CHAT_ALL.key()); var fh = target.chatWidgets.get(WidgetCatalog.CHAT_FH.key());
        var p = profile("Bosses", ".*Цербер.*"); p.addProperty("skipOthers", true);
        var tab = ChatPlusTabImport.parse(config(new JsonObject[]{p})).getFirst();
        var result = ChatPlusTabImport.append(target, List.of(selection(tab)));
        assertEquals(1, result.keys().size()); assertSame(adapted, target.chatWidgets.get(existingKey));
        assertTrue(target.mainLayout.widgets.contains(existingKey)); assertSame(all, target.chatWidgets.get(WidgetCatalog.CHAT_ALL.key()));
        assertSame(fh, target.chatWidgets.get(WidgetCatalog.CHAT_FH.key()));
        var imported = target.chatWidgets.get(result.keys().getFirst()); assertFalse(imported.detached); assertTrue(imported.regexFilter); assertTrue(imported.skipOthers);
        var repeated = ChatPlusTabImport.append(target, List.of(selection(tab))); assertTrue(repeated.keys().isEmpty()); assertEquals(1, repeated.duplicates());
        var restored = HudConfigCodec.decode(HudConfigCodec.createGson(), HudConfigCodec.createGson().toJsonTree(target));
        assertEquals(".*Цербер.*", restored.chatWidgets.get(result.keys().getFirst()).textFilter);
        assertEquals(List.of(WidgetCatalog.CHAT_ALL.key(), WidgetCatalog.CHAT_FH.key()), restored.chatTabOrder.subList(0, 2));
    }
    @Test void capacityFailureIsAtomicAndExact512CharacterRegexSurvives() throws Exception {
        var target = HudConfigSanitizer.sanitize(new HudConfig());
        for (int i = 0; i < 19; i++) target.chatWidgets.put("ChatWidgetCustom_" + UUID.randomUUID(), new ChatWidgetConfig(ChatTab.ALL));
        var tabs = ChatPlusTabImport.parse(config(new JsonObject[]{profile("One", "^" + "a".repeat(510) + "$")}, new JsonObject[]{profile("Two", ".*two.*")}));
        assertThrows(IllegalArgumentException.class, () -> ChatPlusTabImport.append(target, tabs.stream().map(ChatPlusTabImportTest::selection).toList()));
        assertEquals(26, target.chatWidgets.size());
        var result = ChatPlusTabImport.append(target, List.of(selection(tabs.getFirst())));
        assertEquals(512, target.chatWidgets.get(result.keys().getFirst()).textFilter.length());
        assertTrue(ChatTextFilter.compile(tabs.getFirst().pattern(), true).matches("a".repeat(510)));
    }
    @Test void discoveryFindsCurrentVersionedConfigAndReadingIsBounded() throws Exception {
        var folder = Files.createDirectories(directory.resolve("chatplus"));
        var older = directory.resolve("chatplus.json"); var current = folder.resolve("chatplus-v2.7.0.json");
        Files.writeString(older, "{}"); Files.setLastModifiedTime(older, FileTime.fromMillis(1));
        Files.writeString(current, config(new JsonObject[]{profile("Test", ".*")}));
        Files.writeString(folder.resolve("chatplus-backup.json"), "{}");
        assertEquals(List.of(current, older), ChatPlusTabImport.discover(directory));
        assertEquals("Test", ChatPlusTabImport.read(current).getFirst().name());
        assertThrows(IOException.class, () -> ChatPlusTabImport.parse("[".repeat(65)));
        assertThrows(IOException.class, () -> ChatPlusTabImport.parse("{\"chatWindows\":[null]}"));
        var malformed = profile("Bad", "x"); malformed.add("pattern", new JsonArray());
        assertThrows(IOException.class, () -> ChatPlusTabImport.parse(config(new JsonObject[]{malformed})));
        assertThrows(IOException.class, () -> ChatPlusTabImport.parse("{}"));
        Files.writeString(current, " ".repeat(2 * 1024 * 1024 + 1));
        assertThrows(IOException.class, () -> ChatPlusTabImport.read(current));
    }
    @Test void chatPlusShortcutsKeepTheirMeaningInsteadOfTurningEmptyFiltersIntoMatchAll() throws Exception {
        var all = profile("All", "(.*?)"); all.addProperty("formatted", true);
        var tabs = ChatPlusTabImport.parse(config(new JsonObject[]{all}, new JsonObject[]{profile("Empty", "")}));
        assertTrue(tabs.getFirst().compatible()); assertEquals("(?s).*", tabs.getFirst().pattern());
        assertTrue(ChatTextFilter.compile(tabs.getFirst().pattern(), true).matches("line one\nline two"));
        assertEquals("^$", tabs.get(1).pattern());
        assertFalse(ChatTextFilter.compile(tabs.get(1).pattern(), true).matches("message"));
        var target = HudConfigSanitizer.sanitize(new HudConfig());
        var builtInAll = ChatPlusTabImport.parse(config(new JsonObject[]{profile("ALL", "(?s).*")})).getFirst();
        assertTrue(ChatPlusTabImport.duplicate(target, builtInAll, ChatTab.ALL));
        assertTrue(ChatPlusTabImport.append(target, List.of(selection(builtInAll))).keys().isEmpty());
    }
}
