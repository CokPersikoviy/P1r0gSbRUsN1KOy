package ru.wilyfox.client.chat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.audio.*;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.widget.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Explicit selection; reading a foreign config never changes the player's own tabs. */
public final class ChatPlusImportScreen extends ru.wilyfox.client.hud.menu.FhDialogScreen {
    private static final ChatTab[] CHANNELS = {ChatTab.ALL, ChatTab.GLOBAL, ChatTab.TRADE, ChatTab.LOCAL, ChatTab.CLAN, ChatTab.PRIVATE};
    private final Screen parent;
    private final boolean wasEditing, wasSettings;
    private List<ChatPlusTabImport.Tab> tabs = List.of();
    private List<Path> sources = List.of();
    private final Set<String> selected = new HashSet<>();
    private final Map<String, ChatTab> channels = new HashMap<>();
    private String path = "";
    private Component status = Component.empty();
    private EditBox input;
    private Button importButton, channelButton, sourceButton, loadButton;
    private int left, panelWidth, listWidth, listBottom, scroll, detailScroll, preview, generation;
    private boolean discovered, busy, closed;

    public ChatPlusImportScreen(Screen parent) {
        super(text("title")); this.parent = parent;
        var renderer = Client.getInstance().getHudRenderer();
        wasEditing = renderer.isEditing(); wasSettings = renderer.isSettingsOpen();
    }
    private static Component text(String key, Object... args) { return Component.translatable("froghelper.chat.import." + key, args); }
    @Override protected void init() {
        super.init(); closed = false;
        panelWidth = Math.min(640, width - 20); left = (width - panelWidth) / 2;
        listWidth = panelWidth * 44 / 100; listBottom = height - 72;
        input = input(left + 8, 34, panelWidth - 16, text("path"));
        input.setMaxLength(2048); input.setValue(path); input.setHint(text("path")); input.setResponder(value -> path = value);
        loadButton = button(left + 8, 58, 72, text("load"), false, this::loadPath);
        sourceButton = button(left + 84, 58, panelWidth - 92, text("source"), false, this::nextSource);
        button(left + 8, height - 66, listWidth / 2 - 10, text("select_all"), false, this::selectAll);
        button(left + listWidth / 2 + 2, height - 66, listWidth / 2 - 10, text("select_none"), false, () -> { selected.clear(); updateButtons(); });
        channelButton = button(left + listWidth + 8, 90, panelWidth - listWidth - 16, text("channel", "ALL"), false, () -> cycleChannel(1));
        int rightWidth = panelWidth - listWidth - 16, half = (rightWidth - 8) / 2;
        importButton = button(left + listWidth + 8, height - 66, half, text("import"), true, this::apply);
        button(left + listWidth + 16 + half, height - 66, rightWidth - half - 8, Component.translatable("gui.back"), false, this::onClose);
        setInitialFocus(input); updateButtons();
        if (!discovered) { discovered = true; discover(); }
    }
    private void discover() {
        int request = ++generation; busy = true; status = text("discovering");
        var client = minecraft;
        CompletableFuture.supplyAsync(() -> {
            try { return ChatPlusTabImport.discover(FabricLoader.getInstance().getConfigDir()); }
            catch (Exception ignored) { return List.<Path>of(); }
        }).thenAccept(files -> client.execute(() -> {
            if (closed || request != generation) return;
            sources = files; busy = false;
            if (files.isEmpty()) status = text("not_found");
            else { path = files.getFirst().toString(); input.setValue(path); loadPath(); }
            updateButtons();
        }));
    }
    private void nextSource() {
        if (sources.isEmpty() || busy) return;
        int index = -1;
        try { index = sources.indexOf(Path.of(path)); } catch (InvalidPathException ignored) {}
        path = sources.get((index + 1) % sources.size()).toString(); input.setValue(path); loadPath();
    }
    private void loadPath() {
        final Path file;
        try { file = Path.of(path.strip().replaceAll("^\"|\"$", "")); }
        catch (InvalidPathException invalid) { status = text("failed", "Invalid file path"); return; }
        int request = ++generation; busy = true; status = text("loading"); updateButtons();
        var client = minecraft;
        CompletableFuture.supplyAsync(() -> {
            try { return ChatPlusTabImport.read(file); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).whenComplete((found, failure) -> client.execute(() -> {
            if (closed || request != generation) return;
            busy = false; selected.clear(); channels.clear(); scroll = detailScroll = preview = 0;
            tabs = failure == null ? found : List.of();
            status = failure == null ? text("loaded", tabs.size()) : text("failed", failure.getCause().getMessage());
            updateButtons();
        }));
    }
    private ChatTab channel(ChatPlusTabImport.Tab tab) { return channels.getOrDefault(tab.id(), ChatTab.ALL); }
    private boolean duplicate(ChatPlusTabImport.Tab tab) { return ChatPlusTabImport.duplicate(ConfigManager.get(), tab, channel(tab)); }
    private void toggle(ChatPlusTabImport.Tab tab) {
        if (busy || !tab.compatible() || duplicate(tab)) return;
        if (!selected.remove(tab.id())) {
            for (var other : tabs) if (other.group().equals(tab.group())) selected.remove(other.id());
            selected.add(tab.id());
        }
        UiSounds.click(); updateButtons();
    }
    private void selectAll() {
        if (busy) return;
        selected.clear(); var groups = new HashSet<String>();
        for (var tab : tabs) if (tab.compatible() && !duplicate(tab) && groups.add(tab.group())) selected.add(tab.id());
        UiSounds.click(); updateButtons();
    }
    private void cycleChannel(int direction) {
        if (tabs.isEmpty()) return;
        var tab = tabs.get(preview); int current = Arrays.asList(CHANNELS).indexOf(channel(tab));
        channels.put(tab.id(), CHANNELS[Math.floorMod(current + direction, CHANNELS.length)]);
        if (duplicate(tab)) selected.remove(tab.id());
        UiSounds.click(); updateButtons();
    }
    private int available() {
        return ChatWidgetConfig.MAX_CUSTOM_TABS - (int) ConfigManager.get().chatWidgets.keySet().stream().filter(WidgetCatalog::isCustomChatKey).count();
    }
    private void updateButtons() {
        if (importButton == null) return;
        importButton.active = !busy && !selected.isEmpty() && selected.size() <= available();
        loadButton.active = !busy; sourceButton.active = !busy && sources.size() > 1;
        channelButton.active = !busy && !tabs.isEmpty() && tabs.get(preview).compatible();
        if (!tabs.isEmpty()) channelButton.setMessage(text("channel", channel(tabs.get(preview)).getTitle()));
    }
    private void apply() {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/import/apply")) {
            if (busy || selected.isEmpty()) return;
            try {
                var choices = tabs.stream().filter(tab -> selected.contains(tab.id())).map(tab -> new ChatPlusTabImport.Selection(tab, channel(tab))).toList();
                var result = ConfigManager.importChatPlusTabs(choices);
                Client.getInstance().getHudRenderer().refreshConfiguration();
                ChatTabManager.getInstance().clearLineCache(); ChatTabManager.getInstance().rebuildVanillaChat();
                selected.clear(); status = text("done", result.keys().size(), result.duplicates()); UiSounds.click();
            } catch (Exception failure) { status = text("failed", failure.getMessage()); }
            updateButtons();
        }
    }
    @Override public void onFilesDrop(List<Path> files) {
        if (!files.isEmpty() && !busy) { path = files.getFirst().toString(); input.setValue(path); loadPath(); }
    }
    private int rows() { return Math.max(1, (listBottom - 86) / 27); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && channelButton.active && channelButton.isMouseOver(event.x(), event.y())) {
            cycleChannel(-1); return true;
        }
        if (event.button() == 0 && event.x() >= left + 8 && event.x() < left + listWidth - 4
                && event.y() >= 86 && event.y() < 86 + rows() * 27) {
            int index = scroll + (int) ((event.y() - 86) / 27);
            if (index < tabs.size()) { if (preview != index) detailScroll = 0; preview = index; if (event.x() < left + 32) toggle(tabs.get(index)); updateButtons(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= left && x < left + listWidth && y >= 86 && y < listBottom) {
            int before = scroll; scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, tabs.size() - rows()));
            if (scroll != before) UiSounds.scroll(); return true;
        }
        if (!tabs.isEmpty() && x >= left + listWidth && x < left + panelWidth && y >= 114 && y < listBottom) {
            int before = detailScroll;
            detailScroll = Math.clamp(detailScroll - 3 * (int)Math.signum(sy), 0, Math.max(0, detailLines().size() - detailRows()));
            if (before != detailScroll) UiSounds.scroll(); return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }
    private static String previewText(String source) { return source.length() <= 512 ? source : source.substring(0, 512) + "…"; }
    private int detailRows() { return Math.max(1, (listBottom - 120) / (font.lineHeight + 1)); }
    private List<net.minecraft.util.FormattedCharSequence> detailLines() {
        var tab = tabs.get(preview); var lines = new ArrayList<Component>();
        if (tab.error() != null) lines.add(Component.literal(tab.error()));
        if (!duplicate(tab) && ConfigManager.get().chatWidgets.values().stream().anyMatch(existing -> !existing.deleted && existing.title.equals(tab.name())))
            lines.add(text("same_name"));
        lines.add(text("regex", previewText(tab.pattern()))); lines.add(text("skip", tab.skipOthers() ? "ON" : "OFF"));
        if (!tab.server().isEmpty()) lines.add(text("server", previewText(tab.server())));
        for (var warning : tab.warnings()) lines.add(Component.literal(warning));
        return lines.stream().flatMap(line -> font.split(line, Math.max(20, panelWidth - listWidth - 22)).stream()).toList();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {}
    @Override protected void extractDialogContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        dialog(g, left, 8, panelWidth, height - 16);
        HudSurface.fillRounded(g, left + listWidth + 4, 86, panelWidth - listWidth - 8, listBottom - 86, 4, WidgetTheme.PANEL_BG_SOFT);
        if (tabs.isEmpty()) {
            var lines = font.split(text("empty"), Math.max(20, listWidth - 28));
            for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), left + 14, 94 + i * (font.lineHeight + 2), WidgetTheme.TEXT_SECONDARY);
        }
        for (int row = 0; row < rows() && row + scroll < tabs.size(); row++) {
            int index = row + scroll, y = 86 + row * 27; var tab = tabs.get(index);
            boolean exists = duplicate(tab), enabled = tab.compatible() && !exists;
            HudSurface.fillRounded(g, left + 8, y, listWidth - 12, 25, 3, index == preview ? WidgetTheme.PANEL_BG : WidgetTheme.PANEL_BG_SOFT);
            if (index == preview) g.fill(left + 8, y + 4, left + 10, y + 21, WidgetTheme.ACCENT_LINE);
            boolean checked = selected.contains(tab.id());
            HudSurface.fillRounded(g, left + 15, y + 5, 12, 12, 2, checked ? WidgetTheme.ACCENT_LINE : WidgetTheme.BAR_BG);
            if (checked) g.text(font, "✓", left + 18, y + 6, WidgetTheme.TEXT_PRIMARY);
            g.text(font, font.plainSubstrByWidth(tab.name(), listWidth - 48), left + 34, y + 4, enabled ? WidgetTheme.TEXT_PRIMARY : WidgetTheme.TEXT_SECONDARY);
            String hint = !tab.compatible() ? text("incompatible").getString() : exists ? text("duplicate").getString()
                    : text("window", tab.window(), tab.server().isEmpty() ? "*" : tab.server()).getString();
            g.text(font, font.plainSubstrByWidth(hint, listWidth - 24), left + 12, y + 15, WidgetTheme.TEXT_SECONDARY);
        }
        if (tabs.size() > rows()) {
            int track = rows() * 27, thumb = Math.max(8, track * rows() / tabs.size());
            int y = 86 + (track - thumb) * scroll / Math.max(1, tabs.size() - rows());
            g.fill(left + listWidth - 6, y, left + listWidth - 4, y + thumb, WidgetTheme.ACCENT_LINE);
        }
        if (!tabs.isEmpty()) {
            int x = left + listWidth + 8, w = panelWidth - listWidth - 16;
            var lines = detailLines(); detailScroll = Math.clamp(detailScroll, 0, Math.max(0, lines.size() - detailRows()));
            for (int row = 0; row < detailRows() && row + detailScroll < lines.size(); row++) {
                g.text(font, lines.get(row + detailScroll), x, 116 + row * (font.lineHeight + 1), WidgetTheme.TEXT_SOFT);
            }
            if (lines.size() > detailRows()) {
                int track = detailRows() * (font.lineHeight + 1), thumb = Math.max(8, track * detailRows() / lines.size());
                int y = 116 + (track - thumb) * detailScroll / Math.max(1, lines.size() - detailRows());
                g.fill(x + w - 2, y, x + w, y + thumb, WidgetTheme.ACCENT_LINE);
            }
        }
        g.text(font, text("slots", selected.size(), available()), left + 10, height - 40, selected.size() > available() ? 0xFFFF7777 : WidgetTheme.TEXT_SECONDARY);
        String message = font.plainSubstrByWidth(status.getString(), panelWidth - 20);
        g.text(font, message, left + 10, height - 26, WidgetTheme.TEXT_SOFT);
        if (my >= height - 28 && my < height - 12)
            g.setTooltipForNextFrame(font, font.split(status, Math.max(20, Math.min(320, width - 24))).stream()
                    .limit(Math.max(1, (height - 24) / font.lineHeight)).toList(), mx, my);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { closed = true; generation++; super.removed(); }
    @Override public void onClose() {
        minecraft.gui.setScreen(parent);
        var renderer = Client.getInstance().getHudRenderer(); renderer.setEditing(wasEditing); renderer.setSettings(wasSettings);
    }
}
