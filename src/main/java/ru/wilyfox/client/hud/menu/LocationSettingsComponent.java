package ru.wilyfox.client.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;
import ru.wilyfox.client.audio.UiSounds;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.LocationSelectors;
import ru.wilyfox.client.hud.widget.HudSurface;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwGameLocation;
import java.util.*;
import java.util.function.*;

/** A bounded, searchable list of server location IDs; selections are never display names. */
public final class LocationSettingsComponent extends SettingsComponent {
    private record Option(String id, String name) {}
    private final Supplier<Set<String>> getter;
    private final Consumer<Set<String>> setter;
    private final String emptyText;
    private boolean expanded, searchFocused;
    private String query = "";
    private int scroll;
    private int parentBottom = Integer.MAX_VALUE;
    private List<Option> options = List.of();
    private String optionsKey;
    private static final int LIST_Y = 73, ROW = 19, VIEWPORT = 95;

    public LocationSettingsComponent(String label, Supplier<Set<String>> getter, Consumer<Set<String>> setter, String emptyText) {
        super(0, 0, 0, 0, label);
        this.getter = getter; this.setter = setter; this.emptyText = emptyText;
    }
    @Override public int getPreferredHeight() { return expanded ? 174 : 22; }
    private String name(String id) {
        return switch (id) {
            case "#fishing" -> "All fishing locations";
            case "#boss" -> "All bosses";
            case "#shaft" -> "All mines";
            case "#dungeon" -> "All dungeons";
            case "#spawn" -> "All spawns";
            case "#siege" -> "All sieges";
            default -> DiamondWorldProtocolClient.getGameLocationDisplayName(id);
        };
    }
    private void refreshOptions() {
        String current = LocationSelectors.normalize(DiamondWorldProtocolClient.getCurrentGameLocation());
        String key = query + "|" + current + "|" + getter.get();
        if (key.equals(optionsKey)) return;
        var ids = new LinkedHashSet<String>(List.of("#fishing", "#boss", "#shaft", "#dungeon", "#spawn", "#siege"));
        if (!current.isEmpty()) ids.add(current);
        ids.addAll(getter.get());
        ids.addAll(DwGameLocation.knownIds());
        ids.addAll(DiamondWorldProtocolClient.getFishingLocationIds());
        String normalized = LocationSelectors.normalize(query);
        options = ids.stream().map(id -> new Option(id, name(id)))
                .filter(option -> normalized.isEmpty() || option.id.contains(normalized)
                        || option.name.toLowerCase(Locale.ROOT).contains(normalized)).toList();
        optionsKey = key;
        clampScroll();
    }
    public void setViewportBottom(int bottom) { parentBottom = bottom; }
    private int viewport() { return Math.min(VIEWPORT, Math.max(ROW, parentBottom - y - LIST_Y)); }
    private void clampScroll() { scroll = Math.clamp(scroll, 0, Math.max(0, options.size() * ROW - viewport())); }
    private void update(Set<String> value) {
        setter.accept(LocationSelectors.sanitize(value));
        ConfigManager.layoutChanged(); ConfigManager.save();
        optionsKey = null; UiSounds.toggle();
    }
    private void toggle(String id) {
        var values = new LinkedHashSet<>(getter.get());
        if (!values.remove(id)) values.add(id);
        update(values);
    }
    @Override public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        HudSurface.fillRounded(graphics, x, y, width, height, 4, WidgetTheme.PANEL_BG_SOFT);
        graphics.text(font, label, x + 8, y + 7, WidgetTheme.TEXT_PRIMARY);
        String value = getter.get().isEmpty() ? emptyText : getter.get().size() + " selected";
        String summary = value + (expanded ? " <" : " >");
        graphics.text(font, summary, x + width - font.width(summary) - 8, y + 7, WidgetTheme.TEXT_ACCENT);
        if (!expanded) return;
        refreshOptions();
        clampScroll();
        HudSurface.fillRounded(graphics, x + 5, y + 27, width - 10, 21, 3, WidgetTheme.BAR_BG);
        String search = query.isEmpty() && !searchFocused ? "Search name / ID; Enter adds ID*" : query + (searchFocused ? "_" : "");
        graphics.text(font, font.plainSubstrByWidth(search, width - 22), x + 10, y + 34, WidgetTheme.TEXT_MUTED);
        graphics.text(font, "+ Current", x + 8, y + 55, WidgetTheme.TEXT_ACCENT);
        graphics.text(font, "Clear", x + width - 38, y + 55, WidgetTheme.TEXT_MUTED);
        graphics.enableScissor(x + 4, y + LIST_Y, x + width - 4, y + LIST_Y + viewport());
        int first = scroll / ROW, last = Math.min(options.size(), (scroll + viewport() + ROW - 1) / ROW);
        for (int i = first; i < last; i++) {
            var option = options.get(i);
            int rowY = y + LIST_Y + i * ROW - scroll;
            boolean selected = getter.get().contains(option.id);
            String text = (selected ? "[x] " : "[ ] ") + option.name + " (" + option.id + ")";
            if (mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW)
                graphics.fill(x + 4, rowY, x + width - 4, rowY + ROW, WidgetTheme.PANEL_BG);
            graphics.text(font, font.plainSubstrByWidth(text, width - 18), x + 8, rowY + 5,
                    selected ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TEXT_SECONDARY);
        }
        if (options.isEmpty()) graphics.text(font, "No matches. Enter adds the exact ID.", x + 8, y + LIST_Y + 5, WidgetTheme.TEXT_MUTED);
        graphics.disableScissor();
    }
    @Override public String getTooltip(int mouseX, int mouseY) {
        if (!expanded || !isHovered(mouseX, mouseY) || mouseY < y + LIST_Y) return null;
        refreshOptions();
        int index = (mouseY - y - LIST_Y + scroll) / ROW;
        if (index < 0 || index >= options.size()) return null;
        var option = options.get(index);
        return option.name + " (" + option.id + ")";
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) return false;
        if (mouseY < y + 22) { expanded = !expanded; searchFocused = false; UiSounds.click(); return true; }
        searchFocused = mouseY >= y + 27 && mouseY < y + 48;
        if (mouseY >= y + 50 && mouseY < y + 69) {
            if (mouseX >= x + width - 48) update(Set.of());
            else {
                String current = LocationSelectors.normalize(DiamondWorldProtocolClient.getCurrentGameLocation());
                if (!current.isEmpty()) { var values = new LinkedHashSet<>(getter.get()); values.add(current); update(values); }
            }
        } else if (mouseY >= y + LIST_Y && mouseY < y + LIST_Y + viewport()) {
            refreshOptions();
            int index = ((int) mouseY - y - LIST_Y + scroll) / ROW;
            if (index >= 0 && index < options.size()) toggle(options.get(index).id);
        }
        return true;
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!expanded || !isHovered(mouseX, mouseY) || mouseY < y + LIST_Y) return false;
        refreshOptions(); int previous = scroll; scroll -= (int) Math.round(amount * ROW * 2); clampScroll();
        if (scroll != previous) UiSounds.scroll(); return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (!expanded) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClickOutside(); return true; }
        if (!searchFocused) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            String id = LocationSelectors.normalize(query);
            if (!id.isEmpty()) { var values = new LinkedHashSet<>(getter.get()); values.add(id); update(values); }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
        if (key == GLFW.GLFW_KEY_DELETE) query = "";
        optionsKey = null; scroll = 0; return true;
    }
    @Override public boolean charTyped(int codePoint, int mods) {
        if (!expanded || !searchFocused) return false;
        if (!Character.isISOControl(codePoint) && query.length() < 128) query += new String(Character.toChars(codePoint));
        optionsKey = null; scroll = 0; return true;
    }
    @Override public void onClickOutside() { expanded = false; searchFocused = false; }
}
