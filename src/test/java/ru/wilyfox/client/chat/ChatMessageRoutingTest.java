package ru.wilyfox.client.chat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatMessageRoutingTest {
    private static final String ALL = WidgetCatalog.CHAT_ALL.key(), FH = WidgetCatalog.CHAT_FH.key();
    private HudConfig config() {
        var c = new HudConfig();
        tab(c, ALL, ChatTab.ALL, false, ""); tab(c, FH, ChatTab.FH, false, "");
        tab(c, "before", ChatTab.CLAN, false, "");
        tab(c, "exclusive", ChatTab.CLAN, true, " Dragon ");
        tab(c, "after", ChatTab.ALL, false, "");
        return c;
    }
    private void tab(HudConfig c, String key, ChatTab channel, boolean skip, String filter) {
        var tab = new ChatWidgetConfig(channel); tab.skipOthers = skip; tab.textFilter = filter; tab.regexFilter = false;
        c.chatWidgets.put(key, tab); c.chatTabOrder.add(key);
    }
    @Test void matchingExclusiveTabBypassesAllAndLaterTabsButKeepsEarlierRecipients() {
        var routing = new ChatMessageRouting(config());
        assertFalse(routing.accepts(ALL, ChatTab.CLAN, "c fox: dragon"));
        assertTrue(routing.accepts("before", ChatTab.CLAN, "c fox: dragon"));
        assertTrue(routing.accepts("exclusive", ChatTab.CLAN, "c fox: dragon"));
        assertFalse(routing.accepts("after", ChatTab.CLAN, "c fox: dragon"));
        assertFalse(routing.accepts(FH, ChatTab.CLAN, "c fox: dragon"));
    }
    @Test void bothChannelAndPersistentFilterMustMatch() {
        var routing = new ChatMessageRouting(config());
        assertTrue(routing.accepts(ALL, ChatTab.CLAN, "c fox: unrelated"));
        assertTrue(routing.accepts("after", ChatTab.CLAN, "c fox: unrelated"));
        assertTrue(routing.accepts(ALL, ChatTab.TRADE, "t fox: dragon"));
        assertTrue(routing.accepts("after", ChatTab.TRADE, "t fox: dragon"));
        assertFalse(routing.accepts("exclusive", ChatTab.TRADE, "t fox: dragon"));
    }
    @Test void disabledOrDeletedExclusiveTabDoesNotBlockOtherViews() {
        var c = config(); c.chatWidgets.get("exclusive").skipOthers = false;
        var routing = new ChatMessageRouting(c);
        assertFalse(routing.hasStops());
        assertTrue(routing.accepts(ALL, ChatTab.CLAN, "dragon"));
        assertTrue(routing.accepts("after", ChatTab.CLAN, "dragon"));
        c.chatWidgets.get("exclusive").skipOthers = true; c.chatWidgets.get("exclusive").deleted = true;
        routing = new ChatMessageRouting(c);
        assertFalse(routing.hasStops());
        assertFalse(routing.accepts("exclusive", ChatTab.CLAN, "dragon"));
        assertTrue(routing.accepts(ALL, ChatTab.CLAN, "dragon"));
    }
    @Test void reorderingChangesPriorityAndFirstMatchingExclusiveTabWins() {
        var c = config(); c.chatWidgets.get("after").skipOthers = true;
        var routing = new ChatMessageRouting(c);
        assertFalse(routing.accepts("after", ChatTab.CLAN, "dragon"));
        c.chatTabOrder = List.of(ALL, FH, "before", "after", "exclusive");
        routing = new ChatMessageRouting(c);
        assertTrue(routing.accepts("after", ChatTab.CLAN, "dragon"));
        assertFalse(routing.accepts("exclusive", ChatTab.CLAN, "dragon"));
        assertFalse(routing.accepts(ALL, ChatTab.CLAN, "dragon"));
    }
    @Test void backendArchiveStaysSeparateAndPinnedTabsNeverBlock() {
        var c = config();
        c.chatWidgets.get(ALL).skipOthers = true; c.chatWidgets.get(FH).skipOthers = true;
        tab(c, "backend", ChatTab.FH, true, ""); tab(c, "backendLater", ChatTab.FH, false, "");
        var routing = new ChatMessageRouting(c);
        assertTrue(routing.accepts(FH, ChatTab.FH, "fox: dragon"));
        assertTrue(routing.accepts("backend", ChatTab.FH, "fox: dragon"));
        assertFalse(routing.accepts("backendLater", ChatTab.FH, "fox: dragon"));
        assertFalse(routing.accepts(ALL, ChatTab.FH, "fox: dragon"));
        assertTrue(routing.accepts("before", ChatTab.CLAN, "dragon"));
    }
    @Test void oldConfigsDefaultOffAndPreferenceSurvivesSerialization() {
        var gson = new Gson();
        assertFalse(gson.fromJson("{\"channel\":\"CLAN\"}", ChatWidgetConfig.class).skipOthers);
        var c = HudConfigCodec.decode(gson, gson.toJsonTree(new HudConfig()));
        var owner = c.chatWidgets.get(WidgetCatalog.CHAT_CLAN.key()); owner.skipOthers = true;
        c.chatWidgets.get(ALL).skipOthers = true; c.chatWidgets.get(FH).skipOthers = true;
        c = HudConfigCodec.decode(gson, gson.toJsonTree(c));
        assertTrue(c.chatWidgets.get(WidgetCatalog.CHAT_CLAN.key()).skipOthers);
        assertFalse(c.chatWidgets.get(ALL).skipOthers); assertFalse(c.chatWidgets.get(FH).skipOthers);
    }
    @Test void regexRoutingUsesFullOriginalTextAndSharesExclusiveRuleWithAll() {
        var c = config(); var tab = c.chatWidgets.get("exclusive"); tab.regexFilter = true;
        tab.textFilter = "(?iu)^(?!.*:).*(заш[её]л|выш[её]л).*";
        var routing = new ChatMessageRouting(c);
        assertTrue(routing.accepts("exclusive", ChatTab.CLAN, "Fox ЗАШЁЛ на сервер"));
        assertFalse(routing.accepts(ALL, ChatTab.CLAN, "Fox ЗАШЁЛ на сервер"));
        assertFalse(routing.accepts("after", ChatTab.CLAN, "Fox ЗАШЁЛ на сервер"));
        assertTrue(routing.accepts("before", ChatTab.CLAN, "Fox ЗАШЁЛ на сервер"));
        assertFalse(routing.accepts("exclusive", ChatTab.CLAN, "Fox: я вышел"));
        assertTrue(routing.accepts(ALL, ChatTab.CLAN, "Fox: я вышел"));
        assertFalse(routing.accepts("exclusive", ChatTab.TRADE, "Fox вышел"));
        tab.textFilter = "(?-i).*Dragon.*";
        routing = new ChatMessageRouting(c);
        assertTrue(routing.accepts("exclusive", ChatTab.CLAN, "Fox Dragon"));
        assertFalse(routing.accepts("exclusive", ChatTab.CLAN, "Fox dragon"));
        tab.textFilter = "[";
        routing = new ChatMessageRouting(c);
        assertTrue(routing.accepts(ALL, ChatTab.CLAN, "anything"));
        assertFalse(routing.accepts("exclusive", ChatTab.CLAN, "anything"));
    }
}
