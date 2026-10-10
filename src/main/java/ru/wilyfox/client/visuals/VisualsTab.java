package ru.wilyfox.client.visuals;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RunesBagConfig;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.profiler.ModProfiler;

public final class VisualsTab {
    private static List<PlayerInfo> snapshot = List.of(), shown = List.of();
    private static PlayerInfo hovered;
    private static int page, pages = 1, slot, bottom;
    private static long refreshed;
    private static double scroll;
    private static Object connection;
    private VisualsTab() {}
    public static boolean interactive() { return Minecraft.getInstance().gui.screen() instanceof VisualsTabScreen; }
    public static void tick(Minecraft mc) {
        if (!ConfigManager.get().visuals.tab.enabled || mc.player == null || connection != mc.getConnection()
                || !interactive() && (mc.gui.screen() != null || !mc.options.keyPlayerList.isDown())) {
            snapshot = shown = List.of(); hovered = null; page = 0; pages = 1; refreshed = 0; bottom = slot = 0;
            connection = mc.getConnection(); scroll = 0;
            if (!ConfigManager.get().visuals.tab.enabled && interactive()) mc.gui.setScreen(null);
        }
    }
    public static List<PlayerInfo> page(Supplier<List<PlayerInfo>> source) {
        long now = System.nanoTime();
        if (refreshed == 0 || now - refreshed >= 250_000_000) {
            try (var profile = ModProfiler.getInstance().scope("visuals/tabSnapshot")) { snapshot = List.copyOf(source.get()); }
            refreshed = now;
        }
        var c = ConfigManager.get().visuals.tab;
        int capacity = c.rows * c.columns;
        pages = Math.max(1, (snapshot.size() + capacity - 1) / capacity); page = Math.clamp(page, 0, pages - 1);
        shown = snapshot.subList(page * capacity, Math.min(snapshot.size(), (page + 1) * capacity));
        slot = bottom = 0; hovered = null; return shown;
    }
    public static int slot(int x0, int y0, int x1, int y1, int color) {
        int index = slot++; bottom = Math.max(bottom, y1);
        if (!interactive() || index >= shown.size()) return color;
        var mc = Minecraft.getInstance(); double x = mc.mouseHandler.getScaledXPos(mc.getWindow()), y = mc.mouseHandler.getScaledYPos(mc.getWindow());
        if (x >= x0 && x < x1 && y >= y0 && y < y1 + 1) { hovered = shown.get(index); return ConfigManager.get().visuals.tab.highlight; }
        return color;
    }
    public static void area(int y1) { bottom = Math.max(bottom, y1); }
    public static int bottom() { return bottom; }
    public static PlayerInfo hovered() { return hovered; }
    public static int width(int vanilla, int columns) {
        int selected = ConfigManager.get().visuals.tab.columnWidth;
        return selected <= 0 ? vanilla : Math.max(1, Math.min(selected, (Minecraft.getInstance().getWindow().getGuiScaledWidth() - 10 - (columns - 1) * 5) / Math.max(1, columns)));
    }
    public static void finish(GuiGraphicsExtractor g, int width, int y) {
        var c = ConfigManager.get().visuals.tab;
        if (!c.enabled || !c.pageHint) return;
        String hint = (pages > 1 ? (page + 1) + " / " + pages + " | " : "")
                + (interactive() ? "Scroll / Left / Right - Click player - Esc" : (c.scrollPaging && pages > 1 ? "Scroll: page | " : "")
                    + (c.interactive && c.cursorKey >= 0 ? "Cursor: " + modifiersName(c.cursorModifiers) + keyName(c.cursorKey) : ""));
        if (hint.isBlank()) return;
        g.centeredText(Minecraft.getInstance().font, hint, width / 2, y, WidgetTheme.TEXT_SOFT);
    }
    private static String modifiersName(int value) {
        return ((value & 2) != 0 ? "Ctrl + " : "") + ((value & 1) != 0 ? "Shift + " : "") + ((value & 4) != 0 ? "Alt + " : "");
    }
    private static String keyName(int code) {
        return RunesBagConfig.isMouseCode(code) ? "Mouse " + (RunesBagConfig.mouseButton(code) + 1)
                : com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
    }
    public static void turn(int delta) { int old = page; page = Math.clamp(page + delta, 0, pages - 1); if (page != old) UiSounds.hover(); }
    public static void scrollBy(double amount) {
        scroll += amount;
        if (Math.abs(scroll) >= 1) { turn(-(int) Math.signum(scroll)); scroll = 0; }
    }
    public static boolean scroll(double amount) {
        var mc = Minecraft.getInstance(); var c = ConfigManager.get().visuals.tab;
        if (!c.enabled || !c.scrollPaging || mc.gui.screen() != null || !mc.options.keyPlayerList.isDown() || mc.player == null) return false;
        scrollBy(amount); return true;
    }
    public static boolean press(boolean mouse, int code) {
        var mc = Minecraft.getInstance(); var c = ConfigManager.get().visuals.tab;
        if (!c.enabled || !c.interactive || mc.gui.screen() != null || mc.player == null || !mc.options.keyPlayerList.isDown()) return false;
        if (!VisualsCamera.modifiers(c.cursorModifiers)) return false;
        if (c.cursorKey != (mouse ? RunesBagConfig.MOUSE_CODE_OFFSET + code : code)) return false;
        mc.gui.setScreen(new VisualsTabScreen()); return true;
    }
}
