package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ChatTabManager {
    private static final ChatTabManager INSTANCE = new ChatTabManager();

    private final List<ChatMessageEntry> allMessages = new ArrayList<>();
    private final Map<ChatTab, List<ChatMessageEntry>> messagesByTab = new EnumMap<>(ChatTab.class);

    private ChatTab activeTab = ChatTab.ALL;
    private boolean rebuilding = false;

    private ChatTabManager() {
        for (ChatTab tab : ChatTab.values()) {
            messagesByTab.put(tab, new ArrayList<>());
        }
    }

    public static ChatTabManager getInstance() {
        return INSTANCE;
    }

    public ChatTab getActiveTab() {
        return activeTab;
    }

    public boolean isRebuilding() {
        return rebuilding;
    }

    public void captureIncoming(Component component) {
        if (component == null || rebuilding) {
            return;
        }

        ChatMessageEntry entry = new ChatMessageEntry(component.copy(), Instant.now());

        allMessages.add(entry);
        messagesByTab.get(ChatTab.ALL).add(entry);

        ChatTab resolved = ChatPrefixRouter.resolve(component);
        if (resolved != ChatTab.ALL) {
            messagesByTab.get(resolved).add(entry);
        }
    }

    public void setActiveTab(ChatTab tab) {
        if (tab == null || tab == activeTab) {
            return;
        }

        activeTab = tab;
        rebuildVanillaChat();
    }

    public List<ChatMessageEntry> getMessages(ChatTab tab) {
        if (tab == ChatTab.ALL) {
            return List.copyOf(allMessages);
        }

        return List.copyOf(messagesByTab.getOrDefault(tab, List.of()));
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

    public void clearAll() {
        allMessages.clear();

        for (ChatTab tab : ChatTab.values()) {
            messagesByTab.get(tab).clear();
        }
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
}
