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
    private boolean rebuilding, registered, restoringView;
    private String search = "";
    private long revision, archiveRevision, lineCacheRevision, countRevision = -1;
    private int matchCount, cachedLines, refreshScroll;
    private boolean refreshNewMessages;
    private ChatMessageRouting routing;
    private Object routingConfig, nativeRoutingConfig;
    private long routingRevision = -1, nativeRoutingRevision = -1;
    private boolean nativeHadRoutingStops;
    private final ChatReadReceipts<GuiMessage> readReceipts = new ChatReadReceipts<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private long unreadRevision = -1, unreadRoutingRevision = -1;
    private Object unreadConfig;

    private ChatMessageRouting routing() {
        if (routingConfig != ConfigManager.get() || routingRevision != ConfigManager.getLayoutRevision()) {
            routing = new ChatMessageRouting(ConfigManager.get());
            readReceipts.retainWindows(ConfigManager.get().chatWidgets.keySet());
            routingConfig = ConfigManager.get(); routingRevision = ConfigManager.getLayoutRevision();
            // Remember rules used between ticks too, so disabling them refreshes native display lines.
            if (routing.hasStops()) nativeHadRoutingStops = true;
        }
        return routing;
    }
    public boolean routesTo(String key, ChatMessageEntry entry) {
        return key == null || routing().accepts(key, entry.tab(), entry.searchText());
    }

    private ChatTabManager() {
        for (ChatTab tab : ChatTab.values()) {
            messagesByTab.put(tab, new ArrayDeque<>());
            states.put(tab, new TabState());
        }
    }

    public static ChatTabManager getInstance() { return INSTANCE; }
    public ChatTab getActiveTab() { return activeTab; }
    public String activeWindow() { return activeWindow; }
    public void forgetWindow(String key) { windowStates.remove(key); readReceipts.forgetWindow(key); revision++; }
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
    public synchronized int unread(ChatTab tab) {
        if (registered) return unread(ChatDock.key(tab));
        return (int) messagesByTab.get(tab).stream().filter(entry -> entry.receivedNanos() != 0
                && readReceipts.unread(tab.name(), entry.message())).limit(9999).count();
    }
    public synchronized int unread(String key) {
        var config = ConfigManager.get();
        long layout = ConfigManager.getLayoutRevision();
        if (unreadRevision != revision || unreadRoutingRevision != layout || unreadConfig != config) {
            var rules = routing();
            unreadCounts.clear();
            for (var window : config.chatWidgets.entrySet()) {
                if (window.getValue().deleted) continue;
                int count = 0;
                for (var entry : messagesByTab.get(window.getValue().channel)) {
                    if (entry.receivedNanos() != 0 && rules.accepts(window.getKey(), entry.tab(), entry.searchText())
                            && readReceipts.unread(window.getKey(), entry.message())) count = Math.min(9999, count + 1);
                }
                unreadCounts.put(window.getKey(), count);
            }
            unreadRevision = revision; unreadRoutingRevision = layout; unreadConfig = config;
        }
        return unreadCounts.getOrDefault(key, 0);
    }
    public String search() { return search; }

    public void register() {
        if (registered) return;
        registered = true;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (nativeRoutingConfig == ConfigManager.get() && nativeRoutingRevision == ConfigManager.getLayoutRevision()) return;
            nativeRoutingConfig = ConfigManager.get(); nativeRoutingRevision = ConfigManager.getLayoutRevision();
            boolean stops = routing().hasStops();
            if (activeWindow != null) selectWindow(activeWindow);
            if (stops || nativeHadRoutingStops) {
                revision++; rebuildVanillaChat();
            }
            nativeHadRoutingStops = stops;
        });
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

    public synchronized void captureIncoming(Component component) {
        if (component != null) notifyWidgetMessage(capture(new GuiMessage(0, component.copy(), null,
                GuiMessageSource.SYSTEM_CLIENT, null), historyLimit(), false));
    }
    synchronized void captureIncoming(Component component, int limit) {
        if (component != null) capture(new GuiMessage(0, component.copy(), null, GuiMessageSource.SYSTEM_CLIENT, null), limit, false);
    }
    public synchronized void captureIncoming(GuiMessage message, boolean chatOpen) {
        notifyWidgetMessage(capture(message, historyLimit(), chatOpen));
    }
    synchronized void captureIncoming(GuiMessage message, int limit, boolean chatOpen) { capture(message, limit, chatOpen); }
    private ChatMessageEntry capture(GuiMessage message, int limit, boolean chatOpen) {
        if (message == null || rebuilding || entries.containsKey(message)) return null;
        var entry = entry(message, true);
        entries.put(message, entry);
        messagesByTab.get(ChatTab.ALL).addLast(entry);
        if (entry.tab() != ChatTab.ALL) messagesByTab.get(entry.tab()).addLast(entry);
        boolean seen = chatOpen && isVisible(entry) && (!registered
                || ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).froghelper$getScroll() == 0);
        if (seen) readInActiveView(entry);
        trimToLimit(limit);
        revision++;
        archiveRevision++;
        return entry;
    }
    /** Notify on arrival, never while rebuilding, loading history or rendering a window. */
    private void notifyWidgetMessage(ChatMessageEntry entry) {
        if (entry == null) return;
        for (var window : ConfigManager.get().chatWidgets.entrySet()) {
            var config = window.getValue();
            if (!config.messageSound || config.deleted || !config.detached
                    || !ConfigManager.isWidgetInCurrentLayout(window.getKey())) continue;
            // ALL contains server chat; FH has its own separate backend archive.
            if (config.channel != entry.tab() && !(config.channel == ChatTab.ALL && entry.tab() != ChatTab.FH)) continue;
            String filter = config.textFilter.strip().toLowerCase(Locale.ROOT);
            if (!filter.isEmpty() && !entry.searchText().contains(filter)) continue;
            if (!routesTo(window.getKey(), entry)) continue;
            ru.wilyfox.client.audio.UiSounds.toggle();
            return; // One message may match several windows: play only once.
        }
    }
    private static ChatMessageEntry entry(GuiMessage message, boolean live) {
        String text = ChatTimestampFormatter.stripTimestampPrefix(
                Formatting.stripMinecraftFormatting(ChatMessageSanitizer.forLogic(message.content().getString()))
                        .replace('\u00a0', ' ')).strip();
        return new ChatMessageEntry(message, Instant.now(), ChatPrefixRouter.resolve(text), text.toLowerCase(Locale.ROOT), live ? System.nanoTime() : 0);
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
                revision++; rebuildVanillaChat(); markActiveRead();
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
    public synchronized void markActiveRead() {
        if (restoringView) return;
        if (registered && ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).froghelper$getScroll() > 0) return;
        boolean changed = false;
        for (var entry : messagesByTab.get(activeTab)) if (isVisible(entry)) changed |= readInActiveView(entry);
        if (changed) revision++;
    }
    private boolean readInActiveView(ChatMessageEntry entry) {
        if (entry.receivedNanos() == 0) return false;
        String key = registered ? activeWindow == null ? ChatDock.key(activeTab) : activeWindow : activeTab.name();
        boolean all = registered ? key.equals(ChatDock.key(ChatTab.ALL)) : activeTab == ChatTab.ALL;
        return readReceipts.read(key, entry.message(), all);
    }
    public void showEmptyDock() {
        setActiveTab(ChatTab.ALL);
        emptyDock = true; revision++; rebuildVanillaChat();
    }
    public void setSearch(String value) {
        if (!updateSearch(value)) return;
        rebuildVanillaChat();
        markActiveRead();
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
        for (var state : states.values()) { state.scroll = 0; state.newMessages = false; }
        readReceipts.clear(); unreadCounts.clear();
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
        restoringView = true;
        try (var ignored = ModProfiler.getInstance().scope("chat/viewRefresh")) {
            var chat = client.gui.hud.getChat();
            var state = viewState();
            chat.resetChatScroll();
            chat.setVisibleMessageFilter(visibleFilter);
            chat.scrollChat(state.scroll);
            ((ChatComponentAccessor) chat).froghelper$setNewMessageSinceScroll(state.newMessages && state.scroll > 0);
        } finally {
            restoringView = false;
        }
    }
    public boolean shouldDisplayInActiveTab(Component component) {
        if (emptyDock) return false;
        String text = ChatTimestampFormatter.stripTimestampPrefix(Formatting.stripMinecraftFormatting(
                ChatMessageSanitizer.forLogic(component.getString())));
        return (activeTab == ChatTab.ALL || ChatPrefixRouter.resolve(text) == activeTab)
                && (search.isEmpty() || text.toLowerCase(Locale.ROOT).contains(search))
                && (windowFilter().isEmpty() || text.toLowerCase(Locale.ROOT).contains(windowFilter()))
                && (!registered || routing().accepts(activeWindow == null ? ChatDock.key(activeTab) : activeWindow,
                        ChatPrefixRouter.resolve(text), text.toLowerCase(Locale.ROOT)));
    }
    public boolean isVisible(GuiMessage message) {
        var known = entries.get(message);
        return known != null ? isVisible(known) : shouldDisplayInActiveTab(message.content());
    }
    private boolean isVisible(ChatMessageEntry entry) {
        return !emptyDock && (activeTab == ChatTab.ALL && entry.tab() != ChatTab.FH || entry.tab() == activeTab) && (search.isEmpty() || entry.searchText().contains(search))
                && (windowFilter().isEmpty() || entry.searchText().contains(windowFilter()))
                && (!registered || routesTo(activeWindow == null ? ChatDock.key(activeTab) : activeWindow, entry));
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
            if (entry == null) { entry = entry(message, false); changed = true; }
            entries.put(message, entry);
            messagesByTab.get(ChatTab.ALL).addLast(entry);
            if (entry.tab() != ChatTab.ALL) messagesByTab.get(entry.tab()).addLast(entry);
        }
        previous.keySet().forEach(message -> { forgetWrapped(message); readReceipts.forget(message); });
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
        for (var entry : backendMessages.values()) { entries.remove(entry.message()); forgetWrapped(entry.message()); readReceipts.forget(entry.message()); }
        backendMessages.clear(); messagesByTab.get(ChatTab.FH).clear();
        revision++; archiveRevision++; backendResetRevision++;
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
            if (ConfigManager.get().render.chatTimestamps)
                content.append(ChatTimestampFormatter.createTimestampPrefix(Instant.ofEpochMilli(message.sentAt())));
            content.append(Component.literal(message.name()).withStyle(net.minecraft.ChatFormatting.GREEN))
                    .append(Component.literal(": ").withStyle(net.minecraft.ChatFormatting.GRAY)).append(Component.literal(message.text()));
            var gui = new GuiMessage(client.gui.hud.getGuiTicks(), content, null, GuiMessageSource.SYSTEM_CLIENT, null);
            var entry = new ChatMessageEntry(gui, Instant.ofEpochMilli(message.sentAt()), ChatTab.FH,
                    (message.name() + ": " + message.text()).toLowerCase(Locale.ROOT), live ? System.nanoTime() : 0);
            backendMessages.put(message.id(), entry); entries.put(gui, entry); changed = true;
            if (live) {
                notifyWidgetMessage(entry);
                if (client.player == null || !message.name().equalsIgnoreCase(client.player.getGameProfile().name())) {
                    ru.wilyfox.client.popup.PopUpManager.getInstance().publish(ru.wilyfox.client.popup.PopUpRequest.of(
                            ru.wilyfox.client.popup.PopUpSource.FH_MESSAGE, "FH: " + message.name(), message.text(),
                            ru.wilyfox.client.popup.PopUpSeverity.INFO));
                }
            }
            if (live && chat.isChatFocused() && scroll == 0 && isVisible(entry)) readInActiveView(entry);
            if (live && activeTab == ChatTab.FH && scroll > 0 && isVisible(entry)) {
                int width = (int) (ChatComponent.getWidth(client.options.chatWidth().get()) / client.options.chatScale().get());
                addedLines += wrappedLines(gui, client.font, width).size();
            }
        }
        if (!changed) return;
        while (backendMessages.size() > 5000) {
            var removed = backendMessages.pollFirstEntry().getValue(); entries.remove(removed.message()); forgetWrapped(removed.message()); readReceipts.forget(removed.message());
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
        revision++; archiveRevision++;
    }
    private void trimToLimit(int limit) {
        var all = messagesByTab.get(ChatTab.ALL);
        while (all.size() > Math.max(0, limit)) {
            var removed = all.removeFirst();
            entries.remove(removed.message());
            readReceipts.forget(removed.message());
            if (removed.tab() != ChatTab.ALL) messagesByTab.get(removed.tab()).removeFirst();
            forgetWrapped(removed.message());
        }
    }
    static int totalHistoryLimit(int extra) { return 100 + Math.max(0, Math.min(10000, extra)); }
    private static int historyLimit() { return totalHistoryLimit(ConfigManager.get().render.extraChatHistoryLines); }
    private static int reconnectLimit() { return Math.min(300, historyLimit()); }
    private static final class TabState { int scroll; boolean newMessages; }
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
