package ru.wilyfox.client.chat;

import net.minecraft.client.Minecraft;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.hud.config.ChatWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.hud.widget.ChatWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tabs and HUD windows are two presentations of the same persisted identity. */
public final class ChatDock {
    private static String pressed;
    private static ChatWidget dragging;
    private static double pressX, pressY, offsetX, offsetY;
    private static int originalX, originalY;
    private static boolean originallyDocked;
    private static String focusedWindow;
    private static boolean moving;
    private static ru.wilyfox.client.hud.indicators.ScreenAnchor originalAnchor;
    private static String originalSnap;
    private static ru.wilyfox.client.hud.widget.WidgetCorner originalOwnCorner, originalTargetCorner;
    private static final java.util.Map<ChatTab, String> KEYS = new java.util.EnumMap<>(ChatTab.class);
    private static List<String> cachedTabs = List.of();
    private static Object cachedConfig;
    private static long cachedRevision = -1;
    private static List<String> cachedOrder;
    private static List<String> previewOrder;
    private static double pointerX;
    static { for (var entry : WidgetCatalog.values()) if (entry.chatChannel() != null) KEYS.put(entry.chatChannel(), entry.key()); }
    private ChatDock() {}

    public static String key(ChatTab tab) {
        return KEYS.get(tab);
    }
    public static List<String> tabs() {
        var order = previewOrder == null ? ConfigManager.get().chatTabOrder : previewOrder;
        if (cachedConfig == ConfigManager.get() && cachedRevision == ConfigManager.getLayoutRevision() && cachedOrder == order) return cachedTabs;
        var result = new ArrayList<String>();
        for (String key : order) {
            var settings = settings(key);
            if (settings != null && !settings.deleted && !settings.detached) result.add(key);
        }
        cachedConfig = ConfigManager.get(); cachedRevision = ConfigManager.getLayoutRevision(); cachedOrder = order;
        return cachedTabs = List.copyOf(result);
    }
    public static ChatWidgetConfig settings(String key) { return ConfigManager.get().chatWidgets.get(key); }
    public static String selectedKey() {
        var store = ChatTabManager.getInstance();
        return store.activeWindow() == null ? key(store.getActiveTab()) : store.activeWindow();
    }
    public static ChatWidget widget(String key) {
        return (ChatWidget) Client.getInstance().getHudRenderer().getWidgets().stream()
                .filter(w -> w instanceof ChatWidget chat && key.equals(chat.getConfigKey())).findFirst().orElseThrow();
    }
    public static void select(String key) { focusedWindow = null; ChatTabManager.getInstance().selectWindow(key); }
    public static void ensureDockSelection() {
        var selected = settings(selectedKey());
        if (selected != null && !selected.deleted && !selected.detached) return;
        var tabs = tabs();
        if (tabs.isEmpty()) ChatTabManager.getInstance().showEmptyDock();
        else select(tabs.getFirst());
    }
    public static void focusWindow(String key) { focusedWindow = key; }
    public static ChatTab outgoingChannel() {
        var config = focusedWindow == null ? null : settings(focusedWindow);
        if (config != null && !config.deleted && config.detached && ConfigManager.isWidgetInCurrentLayout(focusedWindow)) return config.channel;
        focusedWindow = null;
        return ChatTabManager.getInstance().getActiveTab();
    }
    public static void cycle(boolean backwards) {
        var tabs = tabs();
        if (tabs.isEmpty()) return;
        int index = tabs.indexOf(selectedKey());
        select(tabs.get(Math.floorMod(index + (backwards ? -1 : 1), tabs.size())));
    }
    public static String create() {
        if (ConfigManager.get().chatWidgets.keySet().stream().filter(WidgetCatalog::isCustomChatKey).count() >= 12) return null;
        String key = "ChatWidgetCustom_" + UUID.randomUUID();
        var config = new ChatWidgetConfig(ChatTab.ALL);
        config.title = "Chat " + (ConfigManager.get().chatWidgets.size() - ChatTab.values().length + 1);
        ConfigManager.get().chatWidgets.put(key, config);
        ConfigManager.get().chatTabOrder.add(key);
        Client.getInstance().getHudRenderer().registerWidget(new ChatWidget(key, WidgetCatalog.CHAT_ALL, 200, 30));
        ConfigManager.layoutChanged(); ConfigManager.save();
        select(key);
        return key;
    }
    public static boolean canEdit(String key) {
        var settings = settings(key);
        return settings != null && !settings.deleted && !isPinned(key);
    }
    public static boolean isPinned(String key) { return key(ChatTab.ALL).equals(key) || key(ChatTab.FH).equals(key); }
    public static void delete(String key) {
        if (!canEdit(key)) return;
        cancelDrag();
        boolean selected = selectedKey().equals(key);
        dock(key);
        var renderer = Client.getInstance().getHudRenderer();
        if (WidgetCatalog.isCustomChatKey(key)) {
            renderer.unregisterWidget(widget(key));
            ConfigManager.get().chatWidgets.remove(key);
        } else settings(key).deleted = true;
        ConfigManager.get().widgetLayouts.remove(key); ConfigManager.get().widgetLocations.remove(key);
        for (var layout : ConfigManager.get().locationLayouts.values()) layout.placements.remove(key);
        ConfigManager.get().chatTabOrder.remove(key);
        if (key.equals(focusedWindow)) focusedWindow = null;
        ConfigManager.layoutChanged(); renderer.refreshLayout();
        if (selected) ensureDockSelection();
        ChatTabManager.getInstance().forgetWindow(key);
        ConfigManager.save();
    }
    public static void detach(String key, int x, int y) {
        if (settings(key) == null || settings(key).deleted || isPinned(key)) return;
        int scroll = ChatTabManager.getInstance().windowScroll(key);
        settings(key).detached = true;
        ConfigManager.get().mainLayout.widgets.add(key);
        ConfigManager.layoutChanged();
        var renderer = Client.getInstance().getHudRenderer(); renderer.refreshLayout();
        var widget = widget(key);
        widget.setScreenAnchor(null); widget.clearWidgetSnap(); widget.setStartX(x); widget.setStartY(y);
        ConfigManager.captureWidgetLayout(widget);
        widget.view().restoreScroll(scroll);
        if (selectedKey().equals(key)) {
            var tabs = tabs();
            if (!tabs.isEmpty()) select(tabs.getFirst());
            else ChatTabManager.getInstance().showEmptyDock();
        }
    }
    public static void dock(String key) {
        ChatTabManager.getInstance().restoreWindowScroll(key, widget(key).view().scroll());
        settings(key).detached = false;
        ConfigManager.get().mainLayout.widgets.remove(key);
        for (var layout : ConfigManager.get().locationLayouts.values()) layout.widgets.remove(key);
        ConfigManager.layoutChanged(); Client.getInstance().getHudRenderer().refreshLayout();
    }
    public static void pressTab(String key, double x, double y) {
        cancelDrag(); if (!selectedKey().equals(key)) UiSounds.click();
        select(key); if (isPinned(key)) return;
        pressed = key; pressX = pointerX = x; pressY = y; originallyDocked = true;
        previewOrder = List.copyOf(ConfigManager.get().chatTabOrder);
    }
    public static void pressWindow(ChatWidget widget, double x, double y) {
        cancelDrag(); pressed = widget.getConfigKey(); dragging = widget; originallyDocked = false;
        originalX = widget.getStartX(); originalY = widget.getStartY();
        originalAnchor = widget.getScreenAnchor(); originalSnap = widget.getSnapTargetKey();
        originalOwnCorner = widget.getSnapOwnCorner(); originalTargetCorner = widget.getSnapTargetCorner();
        offsetX = x - originalX; offsetY = y - originalY; pressX = x; pressY = y;
    }
    public static boolean drag(double x, double y) {
        if (pressed == null) return false;
        pointerX = x;
        if (!moving && Math.hypot(x - pressX, y - pressY) < 6) return true;
        if (dragging == null) {
            if (ChatTabOverlay.getInstance().isDockRow(y)) {
                moving = true;
                ChatTabOverlay.getInstance().updateTabDrag(pressed, x);
                return true;
            }
            detach(pressed, (int) x - 8, (int) y - 8);
            dragging = widget(pressed); offsetX = offsetY = 8;
            UiSounds.openClose();
        }
        if (!moving) { dragging.setScreenAnchor(null); dragging.clearWidgetSnap(); }
        moving = true;
        var window = Minecraft.getInstance().getWindow();
        dragging.setStartX(Math.clamp((int) (x - offsetX), 0, Math.max(0, window.getGuiScaledWidth() - dragging.getWidth())));
        dragging.setStartY(Math.clamp((int) (y - offsetY), 0, Math.max(0, window.getGuiScaledHeight() - dragging.getHeight())));
        return true;
    }
    public static boolean release(double x, double y) {
        if (pressed == null) return false;
        if (moving && dragging == null) drag(x, y);
        boolean save = false;
        if (dragging != null && moving) {
            if (ChatTabOverlay.getInstance().isDockTarget(x, y)) {
                dock(pressed);
                ChatTabOverlay.getInstance().updateTabDrag(pressed, x);
                select(pressed); UiSounds.openClose();
            } else ConfigManager.captureWidgetLayout(dragging);
            save = true;
        }
        if (moving && previewOrder != null && !previewOrder.equals(ConfigManager.get().chatTabOrder)) {
            ConfigManager.get().chatTabOrder = new ArrayList<>(previewOrder);
            ConfigManager.layoutChanged(); save = true;
        }
        if (save) ConfigManager.save();
        clearGesture();
        return true;
    }
    /** Preview only: dragging never writes the config or rebuilds the HUD on every mouse move. */
    static void moveBefore(String key, String before) {
        if (isPinned(key)) return;
        var source = previewOrder == null ? ConfigManager.get().chatTabOrder : previewOrder;
        int from = source.indexOf(key);
        if (key.equals(before) || from < 0) return;
        int to = before == null ? source.size() : source.indexOf(before);
        if (to < 0) to = source.size();
        if (to > from) to--;
        to = Math.max(2, to);
        if (from == to) return;
        var order = new ArrayList<>(source);
        order.remove(from); order.add(to, key);
        previewOrder = List.copyOf(order);
    }
    public static boolean isDragging() { return moving && pressed != null; }
    public static String draggedTabKey() { return moving && dragging == null ? pressed : null; }
    public static double dragPointerX() { return pointerX; }
    private static void clearGesture() {
        pressed = null; dragging = null; moving = false; previewOrder = null;
        ChatTabOverlay.getInstance().endTabDrag();
    }
    public static void cancelDrag() {
        if (dragging != null) {
            if (originallyDocked) { dock(pressed); select(pressed); }
            else {
                dragging.setStartX(originalX); dragging.setStartY(originalY); dragging.setScreenAnchor(originalAnchor);
                dragging.setWidgetSnap(originalSnap, originalOwnCorner, originalTargetCorner);
            }
        }
        clearGesture();
    }
    public static void edit(String key) {
        if (!canEdit(key)) return;
        var client = Minecraft.getInstance();
        client.gui.setScreen(new ChatTabSettingsScreen(client.gui.screen(), widget(key)));
    }
}
