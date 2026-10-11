package ru.wilyfox.client.chat;

import ru.wilyfox.client.hud.config.HudConfig;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import java.util.*;

/** Ordered tab rules over the shared archive; only Skip Others rules can stop later tabs. */
public final class ChatMessageRouting {
    private final Map<String, Rule> rules = new HashMap<>();
    private final List<Rule> stops = new ArrayList<>();

    public ChatMessageRouting(HudConfig config) {
        int index = 0;
        for (String key : config.chatTabOrder) {
            var tab = config.chatWidgets.get(key);
            if (tab == null || tab.deleted) continue;
            var rule = new Rule(index++, tab.channel, tab.textFilter.strip().toLowerCase(Locale.ROOT));
            rules.put(key, rule);
            if (tab.skipOthers && !key.equals(WidgetCatalog.CHAT_ALL.key()) && !key.equals(WidgetCatalog.CHAT_FH.key())) stops.add(rule);
        }
    }

    public boolean hasStops() { return !stops.isEmpty(); }

    public boolean accepts(String key, ChatTab channel, String normalizedText) {
        Rule destination = rules.get(key);
        if (destination == null || !destination.matches(channel, normalizedText)) return false;
        for (Rule stop : stops) {
            // ALL is a catch-all view, so exclusive messages bypass it even though it is pinned first.
            if (key.equals(WidgetCatalog.CHAT_ALL.key())) {
                if (stop.matches(channel, normalizedText)) return false;
                continue;
            }
            if (stop.index >= destination.index) break;
            if (stop.matches(channel, normalizedText)) return false;
        }
        return true;
    }

    private record Rule(int index, ChatTab channel, String filter) {
        boolean matches(ChatTab incoming, String text) {
            return (channel == incoming || channel == ChatTab.ALL && incoming != ChatTab.FH)
                    && (filter.isEmpty() || text.contains(filter));
        }
    }
}
