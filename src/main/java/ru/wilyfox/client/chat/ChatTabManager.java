package ru.wilyfox.client.chat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ru.wilyfox.bridge.ChatComponentAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.utils.Formatting;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

public final class ChatTabManager {
    private static final java.time.format.DateTimeFormatter BACKEND_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault());
    private static final ChatTabManager INSTANCE = new ChatTabManager();
    private final Map<ChatTab, Deque<ChatMessageEntry>> messagesByTab = new EnumMap<>(ChatTab.class);
    private final Map<ChatTab, TabState> states = new EnumMap<>(ChatTab.class);
    private final Map<GuiMessage, ChatMessageEntry> entries = new IdentityHashMap<>();
    private final NavigableMap<Long, ChatMessageEntry> backendMessages = new TreeMap<>();
    private long backendResetRevision;
    private final LinkedHashMap<WrapKey, List<FormattedCharSequence>> wrapped = new LinkedHashMap<>(16, .75f, true);
    private final Predicate<GuiMessage> visibleFilter = this::isVisible;
    private ChatTab activeTab = ChatTab.ALL;
    private String activeWindow;
    private String activeWindowFilter = "";
    private boolean emptyDock;
    private final Map<String, TabState> windowStates = new HashMap<>();
    private boolean rebuilding, registered;
    private String search = "";
    private long revision, archiveRevision, lineCacheRevision, countRevision = -1;
    private int matchCount, cachedLines, refreshScroll;
    private boolean refreshNewMessages;

    private ChatTabManager() {
        for (ChatTab tab : ChatTab.values()) {
            messagesByTab.put(tab, new ArrayDeque<>());
            states.put(tab, new TabState());
        }
    }

    public static ChatTabManager getInstance() { return INSTANCE; }
    public ChatTab getActiveTab() { return activeTab; }
    public String activeWindow() { return activeWindow; }
    public void forgetWindow(String key) { windowStates.remove(key); }
    public int windowScroll(String key) {
        if (Objects.equals(key, activeWindow)) return ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).froghelper$getScroll();
        var state = windowStates.get(key); return state == null ? 0 : state.scroll;
    }
    public void restoreWindowScroll(String key, int scroll) {
        windowStates.computeIfAbsent(key, ignored -> new TabState()).scroll = scroll;
    }
    private TabState viewState() { return activeWindow == null ? states.get(activeTab) : windowStates.computeIfAbsent(activeWindow, ignored -> new TabState()); }
    private String windowFilter() {
        return activeWindowFilter;
    }
    public boolean isRebuilding() { return rebuilding; }
    public Predicate<GuiMessage> visibleFilter() { return visibleFilter; }
    public int unread(ChatTab tab) { return states.get(tab).unread; }
    public String search() { return search; }

    public void register() {
        if (registered) return;
        registered = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ChatDock.ensureDockSelection());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            trimForReconnect(historyLimit());
            search = "";
            clearLineCache();
            for (var state : states.values()) { state.scroll = 0; state.newMessages = false; }
            if (client.gui != null) {
                var chat = client.gui.hud.getChat();
                var nativeMessages = ((ChatComponentAccessor) chat).froghelper$getAllMessages();
                while (nativeMessages.size() > reconnectLimit()) nativeMessages.removeLast();
                rebuildVanillaChat();
            }
        });
    }

    public synchronized void captureIncoming(Component component) { captureIncoming(component, historyLimit()); }
    synchronized void captureIncoming(Component component, int limit) {
        if (component != null) capture(new GuiMessage(0, component.copy(), null, GuiMessageSource.SYSTEM_CLIENT, null), limit, false);
    }
    public synchronized void captureIncoming(GuiMessage message, boolean chatOpen) {
        capture(message, historyLimit(), chatOpen);
    }
    synchronized void captureIncoming(GuiMessage message, int limit, boolean chatOpen) { capture(message, limit, chatOpen); }
    private void capture(GuiMessage message, int limit, boolean chatOpen) {
        if (message == null || rebuilding || entries.containsKey(message)) return;
        var entry = entry(message);
        entries.put(message, entry);
        messagesByTab.get(ChatTab.ALL).addLast(entry);
        if (entry.tab() != ChatTab.ALL) messagesByTab.get(entry.tab()).addLast(entry);
        boolean seen = chatOpen && isVisible(entry);
        if (!seen) states.get(ChatTab.ALL).unread = increment(states.get(ChatTab.ALL).unread);
        if (entry.tab() != ChatTab.ALL && !seen) states.get(entry.tab()).unread = increment(states.get(entry.tab()).unread);
        trimToLimit(limit);
        revision++;
        archiveRevision++;
    }
    private static int increment(int value) { return Math.min(9999, value + 1); }
    private static ChatMessageEntry entry(GuiMessage message) {
        String text = ChatTimestampFormatter.stripTimestampPrefix(
                Formatting.stripMinecraftFormatting(ChatMessageSanitizer.forLogic(message.content().getString()))
                        .replace('\u00a0', ' ')).strip();
        return new ChatMessageEntry(message, Instant.now(), ChatPrefixRouter.resolve(text), text.toLowerCase(Locale.ROOT));
    }

    public void setActiveTab(ChatTab tab) {
        select(tab, null);
    }
    public void selectWindow(String key) {
        var config = ConfigManager.get().chatWidgets.get(key);
        if (config != null && !config.deleted) {
            String filter = config.textFilter.strip().toLowerCase(Locale.ROOT);
            boolean changed = !filter.equals(activeWindowFilter);
            activeWindowFilter = filter;
            if (changed && Objects.equals(key, activeWindow) && config.channel == activeTab) {
                revision++; rebuildVanillaChat();
            } else select(config.channel, key);
        }
    }
    private void select(ChatTab tab, String key) {
        if (tab == null || !emptyDock && tab == activeTab && Objects.equals(key, activeWindow)) return;
        emptyDock = false;
        var chat = Minecraft.getInstance().gui.hud.getChat();
        var accessor = (ChatComponentAccessor) chat;
        var old = viewState();
        old.scroll = accessor.froghelper$getScroll();
        old.newMessages = accessor.froghelper$getNewMessageSinceScroll();
        activeTab = tab;
        activeWindow = key;
        if (key == null) activeWindowFilter = "";
        revision++;
        rebuildVanillaChat();
        markActiveRead();
    }
    public void markActiveRead() {
        if (!search.isEmpty() || !windowFilter().isEmpty()) return;
        if (activeTab == ChatTab.ALL) states.forEach((tab, state) -> { if (tab != ChatTab.FH) state.unread = 0; });
        else states.get(activeTab).unread = 0;
        revision++;
    }
    public void showEmptyDock() {
        setActiveTab(ChatTab.ALL);
        emptyDock = true; revision++; rebuildVanillaChat();
    }
    public void setSearch(String value) {
        if (!updateSearch(value)) return;
        rebuildVanillaChat();
        if (search.isEmpty()) markActiveRead();
    }
    boolean updateSearch(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals(search)) return false;
        search = normalized;
        viewState().scroll = 0;
        viewState().newMessages = false;
        revision++;
        return true;
    }
    public synchronized int matchingMessages() {
        if (countRevision != revision) {
            try (var ignored = ModProfiler.getInstance().scope("chat/search")) {
                matchCount = (int) messagesByTab.get(activeTab).stream().filter(this::isVisible).count();
                countRevision = revision;
            }
        }
        return matchCount;
    }
    public synchronized List<ChatMessageEntry> getMessages(ChatTab tab) { return List.copyOf(messagesByTab.get(tab)); }
    public long archiveRevision() { return archiveRevision; }
    public long uiRevision() { return revision; }
    public long lineCacheRevision() { return lineCacheRevision; }
    /** Stop as soon as the caller has enough visible lines; no per-frame history copies. */
    public synchronized void visitNewest(ChatTab tab, Predicate<ChatMessageEntry> visitor) {
        var iterator = messagesByTab.get(tab).descendingIterator();
        while (iterator.hasNext() && visitor.test(iterator.next())) { }
    }
    public ChatTab getNextTab() { return ChatTab.values()[(activeTab.ordinal() + 1) % ChatTab.values().length]; }
    public ChatTab getPreviousTab() { return ChatTab.values()[(activeTab.ordinal() + ChatTab.values().length - 1) % ChatTab.values().length]; }

    public synchronized void clearAll() {
        backendMessages.clear(); backendResetRevision++;
        messagesByTab.values().forEach(Deque::clear);
        entries.clear();
        for (var state : states.values()) { state.unread = 0; state.scroll = 0; state.newMessages = false; }
        windowStates.clear();
        clearLineCache();
        revision++;
        archiveRevision++;
    }
    public synchronized ArchiveSnapshot archiveSnapshot() {
        return new ArchiveSnapshot(entries.size(), messagesByTab.values().stream().mapToInt(Deque::size).sum(), historyLimit(), reconnectLimit());
    }
    public void rebuildVanillaChat() {
        var client = Minecraft.getInstance();
        if (client.gui == null) return;
        try (var ignored = ModProfiler.getInstance().scope("chat/viewRefresh")) {
            var chat = client.gui.hud.getChat();
            var state = viewState();
            chat.resetChatScroll();
            chat.setVisibleMessageFilter(visibleFilter);
            chat.scrollChat(state.scroll);
            ((ChatComponentAccessor) chat).froghelper$setNewMessageSinceScroll(state.newMessages && state.scroll > 0);
        }
    }
    public boolean shouldDisplayInActiveTab(Component component) {
        if (emptyDock) return false;
        String text = ChatTimestampFormatter.stripTimestampPrefix(Formatting.stripMinecraftFormatting(
                ChatMessageSanitizer.forLogic(component.getString())));
        return (activeTab == ChatTab.ALL || ChatPrefixRouter.resolve(text) == activeTab)
                && (search.isEmpty() || text.toLowerCase(Locale.ROOT).contains(search))
                && (windowFilter().isEmpty() || text.toLowerCase(Locale.ROOT).contains(windowFilter()));
    }
    public boolean isVisible(GuiMessage message) {
        var known = entries.get(message);
        return known != null ? isVisible(known) : shouldDisplayInActiveTab(message.content());
    }
    private boolean isVisible(ChatMessageEntry entry) {
        return !emptyDock && (activeTab == ChatTab.ALL && entry.tab() != ChatTab.FH || entry.tab() == activeTab) && (search.isEmpty() || entry.searchText().contains(search))
                && (windowFilter().isEmpty() || entry.searchText().contains(windowFilter()));
    }

    /** Native deletion/restoration can replace message objects; never resurrect removed messages. */
    public synchronized void beginNativeRefresh(ChatComponent chat) {
        rebuilding = true;
        var accessor = (ChatComponentAccessor) chat;
        refreshScroll = accessor.froghelper$getScroll();
        refreshNewMessages = accessor.froghelper$getNewMessageSinceScroll();
        chat.resetChatScroll();
        var previous = new IdentityHashMap<>(entries);
        boolean changed = false;
        entries.clear();
        messagesByTab.forEach((tab, messages) -> { if (tab != ChatTab.FH) messages.clear(); });
        backendMessages.values().forEach(entry -> { entries.put(entry.message(), entry); previous.remove(entry.message()); });
        for (GuiMessage message : accessor.froghelper$getAllMessages().reversed()) {
            var entry = previous.remove(message);
            if (entry == null) { entry = entry(message); changed = true; }
            entries.put(message, entry);
            messagesByTab.get(ChatTab.ALL).addLast(entry);
            if (entry.tab() != ChatTab.ALL) messagesByTab.get(entry.tab()).addLast(entry);
        }
        previous.keySet().forEach(this::forgetWrapped);
        if (changed || !previous.isEmpty()) archiveRevision++;
        revision++;
    }
    public void endNativeRefresh(ChatComponent chat) {
        if (activeTab == ChatTab.FH && !emptyDock) {
            var accessor = (ChatComponentAccessor) chat;
            for (var entry : messagesByTab.get(ChatTab.FH)) if (isVisible(entry)) accessor.froghelper$addDisplayMessage(entry.message());
        }
        rebuilding = false;
        chat.scrollChat(refreshScroll);
        ((ChatComponentAccessor) chat).froghelper$setNewMessageSinceScroll(refreshNewMessages && refreshScroll > 0);
    }
    public List<FormattedCharSequence> wrappedLines(GuiMessage message, Font font, int width) {
        var key = new WrapKey(message, font, width);
        var known = wrapped.get(key);
        if (known != null) return known;
        List<FormattedCharSequence> lines;
        try (var ignored = ModProfiler.getInstance().scope("chat/lineWrap")) { lines = message.splitLines(font, width); }
        int budget = Math.clamp(historyLimit() * 4, 400, 4096);
        if (lines.size() <= budget) {
            while (cachedLines + lines.size() > budget && !wrapped.isEmpty()) {
                var iterator = wrapped.entrySet().iterator();
                cachedLines -= iterator.next().getValue().size();
                iterator.remove();
            }
            wrapped.put(key, lines);
            cachedLines += lines.size();
        }
        return lines;
    }
    private void forgetWrapped(GuiMessage message) {
        var iterator = wrapped.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().message == message) { cachedLines -= entry.getValue().size(); iterator.remove(); }
        }
    }
    public void clearLineCache() { wrapped.clear(); cachedLines = 0; lineCacheRevision++; }
    public long backendResetRevision() { return backendResetRevision; }
    public synchronized void clearBackendMessages() {
        for (var entry : backendMessages.values()) { entries.remove(entry.message()); forgetWrapped(entry.message()); }
        backendMessages.clear(); messagesByTab.get(ChatTab.FH).clear();
        states.get(ChatTab.FH).unread = 0; revision++; archiveRevision++; backendResetRevision++;
        if (activeTab == ChatTab.FH) rebuildVanillaChat();
    }
    public synchronized void acceptBackendMessages(List<BackendChatMessage> messages, boolean live) {
        for (var message : messages) message.validate();
        var client = Minecraft.getInstance();
        var chat = client.gui.hud.getChat();
        var accessor = (ChatComponentAccessor) chat;
        int scroll = accessor.froghelper$getScroll(), addedLines = 0;
        boolean changed = false;
        for (var message : messages) {
            if (backendMessages.containsKey(message.id())) continue;
            var content = Component.empty();
            if (ConfigManager.get().render.chatTimestamps) content.append(Component.literal("[" + BACKEND_TIME.format(Instant.ofEpochMilli(message.sentAt()))
                    + "] ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            content.append(Component.literal(message.name()).withStyle(net.minecraft.ChatFormatting.GREEN))
                    .append(Component.literal(": ").withStyle(net.minecraft.ChatFormatting.GRAY)).append(Component.literal(message.text()));
            var gui = new GuiMessage(client.gui.hud.getGuiTicks(), content, null, GuiMessageSource.SYSTEM_CLIENT, null);
            var entry = new ChatMessageEntry(gui, Instant.ofEpochMilli(message.sentAt()), ChatTab.FH,
                    (message.name() + ": " + message.text()).toLowerCase(Locale.ROOT));
            backendMessages.put(message.id(), entry); entries.put(gui, entry); changed = true;
            if (live && (!chat.isChatFocused() || activeTab != ChatTab.FH)) states.get(ChatTab.FH).unread = increment(states.get(ChatTab.FH).unread);
            if (live && activeTab == ChatTab.FH && scroll > 0 && isVisible(entry)) {
                int width = (int) (ChatComponent.getWidth(client.options.chatWidth().get()) / client.options.chatScale().get());
                addedLines += wrappedLines(gui, client.font, width).size();
            }
        }
        if (!changed) return;
        while (backendMessages.size() > 5000) {
            var removed = backendMessages.pollFirstEntry().getValue(); entries.remove(removed.message()); forgetWrapped(removed.message());
        }
        messagesByTab.get(ChatTab.FH).clear(); messagesByTab.get(ChatTab.FH).addAll(backendMessages.values());
        revision++; archiveRevision++;
        if (activeTab == ChatTab.FH) {
            viewState().scroll = scroll + addedLines; viewState().newMessages = addedLines > 0 || accessor.froghelper$getNewMessageSinceScroll();
            rebuildVanillaChat();
        }
    }
    synchronized void trimForReconnect(int configuredLimit) {
        trimToLimit(Math.min(300, Math.max(0, configuredLimit)));
        archiveRevision++;
    }
    private void trimToLimit(int limit) {
        var all = messagesByTab.get(ChatTab.ALL);
        while (all.size() > Math.max(0, limit)) {
            var removed = all.removeFirst();
            entries.remove(removed.message());
            if (removed.tab() != ChatTab.ALL) messagesByTab.get(removed.tab()).removeFirst();
            forgetWrapped(removed.message());
        }
    }
    static int totalHistoryLimit(int extra) { return 100 + Math.max(0, Math.min(10000, extra)); }
    private static int historyLimit() { return totalHistoryLimit(ConfigManager.get().render.extraChatHistoryLines); }
    private static int reconnectLimit() { return Math.min(300, historyLimit()); }
    private static final class TabState { int scroll, unread; boolean newMessages; }
    private static final class WrapKey {
        final GuiMessage message;
        final Font font;
        final int width;
        WrapKey(GuiMessage message, Font font, int width) { this.message = message; this.font = font; this.width = width; }
        @Override public boolean equals(Object other) {
            return other instanceof WrapKey key && message == key.message && font == key.font && width == key.width;
        }
        @Override public int hashCode() { return 31 * (31 * System.identityHashCode(message) + System.identityHashCode(font)) + width; }
    }
    public record ArchiveSnapshot(int uniqueMessages, int tabReferences, int historyLimit, int reconnectLimit) {}
}
