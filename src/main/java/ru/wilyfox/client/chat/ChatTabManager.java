package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ChatTabManager {
    private static final ChatTabManager INSTANCE = new ChatTabManager();

    private final Map<ChatTab, Deque<ChatMessageEntry>> messagesByTab = new EnumMap<>(ChatTab.class);

    private ChatTab activeTab = ChatTab.ALL;
    private boolean rebuilding = false;
    private boolean registered;

    private ChatTabManager() {
        for (ChatTab tab : ChatTab.values()) {
            messagesByTab.put(tab, new ArrayDeque<>());
        }
    }

    public static ChatTabManager getInstance() {
        return INSTANCE;
    }

    public void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> trimForReconnect());
    }

    public ChatTab getActiveTab() {
        return activeTab;
    }

    public boolean isRebuilding() {
        return rebuilding;
    }

    public synchronized void captureIncoming(Component component) {
        captureIncoming(component, historyLimit());
    }

    synchronized void captureIncoming(Component component, int limit) {
        if (component == null || rebuilding) {
            return;
        }

        ChatMessageEntry entry = new ChatMessageEntry(component.copy(), Instant.now());

        messagesByTab.get(ChatTab.ALL).addLast(entry);

        ChatTab resolved = ChatPrefixRouter.resolve(component);
        if (resolved != ChatTab.ALL) {
            messagesByTab.get(resolved).addLast(entry);
        }

        trimToLimit(limit);
    }

    public void setActiveTab(ChatTab tab) {
        if (tab == null || tab == activeTab) {
            return;
        }

        activeTab = tab;
        rebuildVanillaChat();
    }

    public synchronized List<ChatMessageEntry> getMessages(ChatTab tab) {
        Deque<ChatMessageEntry> messages = messagesByTab.get(tab);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    public ChatTab getNextTab() {
        ChatTab[] values = ChatTab.values();
        int next = (activeTab.ordinal() + 1) % values.length;
        return values[next];
    }

    public ChatTab getPreviousTab() {
        ChatTab[] values = ChatTab.values();
        int prev = activeTab.ordinal() - 1;
        if (prev < 0) {
            prev = values.length - 1;
        }
        return values[prev];
    }

    public synchronized void clearAll() {
        for (Deque<ChatMessageEntry> messages : messagesByTab.values()) {
            messages.clear();
        }
    }

    public synchronized ArchiveSnapshot archiveSnapshot() {
        int references = messagesByTab.values().stream().mapToInt(Deque::size).sum();
        return new ArchiveSnapshot(
                messagesByTab.get(ChatTab.ALL).size(),
                references,
                historyLimit(),
                reconnectLimit()
        );
    }

    public void rebuildVanillaChat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) {
            return;
        }

        ChatComponent chat = minecraft.gui.getChat();

        rebuilding = true;
        try {
            chat.clearMessages(false);

            for (ChatMessageEntry entry : getMessages(activeTab)) {
                ChatMessageDecorator.withTimestamp(entry.timestamp(), () -> chat.addMessage(entry.component().copy()));
            }
        } finally {
            rebuilding = false;
        }
    }

    public boolean shouldDisplayInActiveTab(Component component) {
        ChatTab active = activeTab;

        if (active == ChatTab.ALL) {
            return true;
        }

        ChatTab resolved = ChatPrefixRouter.resolve(component);
        return resolved == active;
    }

    private synchronized void trimForReconnect() {
        trimForReconnect(historyLimit());
    }

    synchronized void trimForReconnect(int configuredLimit) {
        trimToLimit(Math.min(300, Math.max(0, configuredLimit)));
    }

    private void trimToLimit(int limit) {
        int safeLimit = Math.max(0, limit);
        for (Deque<ChatMessageEntry> messages : messagesByTab.values()) {
            while (messages.size() > safeLimit) {
                messages.removeFirst();
            }
        }
    }

    private static int historyLimit() {
        return Math.max(0, ConfigManager.get().render.extraChatHistoryLines);
    }

    private static int reconnectLimit() {
        return Math.min(300, historyLimit());
    }

    public record ArchiveSnapshot(
            int uniqueMessages,
            int tabReferences,
            int historyLimit,
            int reconnectLimit
    ) {
    }
}
