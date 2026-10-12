package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2f;
import ru.wilyfox.client.chat.ChatMessageCopyExtractor;
import ru.wilyfox.client.chat.ChatMessageSanitizer;
import ru.wilyfox.client.chat.ChatTabManager;
import ru.wilyfox.client.chat.ChatWidgetView;
import ru.wilyfox.client.chat.ChatInputMode;
import ru.wilyfox.client.chat.ChatTab;
import ru.wilyfox.bridge.ChatInputHost;
import ru.wilyfox.client.hud.config.ChatWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.internal.WidgetRenderScope;
import ru.wilyfox.utils.MouseUtils;

/** One independent channel viewport over the original native messages. */
public final class ChatWidget extends AbstractWidget {
    private static final int PAD = 6, LINE_HEIGHT = 10, TITLE_HEIGHT = 16;
    private final WidgetCatalog catalog;
    private final ChatWidgetView view;
    private long presentationTime;
    private float scrollShift;
    private String inputDraft = "";

    public ChatWidget(WidgetCatalog catalog, int x, int y) {
        this(catalog.key(), catalog, x, y);
    }
    public ChatWidget(String key, WidgetCatalog catalog, int x, int y) {
        super(x, y, HudLayer.CONTENT);
        if (catalog.chatChannel() == null) throw new IllegalArgumentException("Not a chat widget");
        this.catalog = catalog;
        setConfigKey(key);
        view = new ChatWidgetView(ChatTabManager.getInstance(), catalog.chatChannel());
        view.window(key);
    }
    public ChatWidgetConfig settings() { return ConfigManager.get().chatWidgets.get(getConfigKey()); }
    public ChatWidgetView view() { view.channel(settings().channel); view.filter(settings().textFilter, settings().regexFilter); return view; }
    private int contentTop() { return PAD + (settings().showTitle ? TITLE_HEIGHT : 0); }
    public boolean hasInputBar() { return settings() != null && settings().channel == ChatTab.FH && settings().showInput; }
    public String inputDraft() { return inputDraft; }
    public void inputDraft(String value) { inputDraft = value == null ? "" : value; }
    private int inputHeight() { return hasInputBar() ? 18 : 0; }
    private int unscaledHeight() { return contentTop() + settings().rows * LINE_HEIGHT + PAD + inputHeight(); }
    private float textScale() { return ConfigManager.get().render.fixedChatWidgetFont ? 1.0f : scale; }
    private int viewportWidth() { return ConfigManager.get().render.fixedChatWidgetFont ? getWidth() : settings().width; }
    private int viewportHeight() { return ConfigManager.get().render.fixedChatWidgetFont ? getHeight() : unscaledHeight(); }
    private int viewportRows() { return Math.max(0, (viewportHeight() - contentTop() - PAD - inputHeight()) / LINE_HEIGHT); }
    private int contentBottom() { return viewportHeight() - PAD - inputHeight(); }
    private int inputTop() { return contentBottom() + 4; }
    public boolean isInputBar(double x, double y) {
        return hasInputBar() && isHovered(x, y) && localX(x) >= PAD && localX(x) < viewportWidth() - PAD
                && localY(y) >= inputTop() && localY(y) < viewportHeight() - PAD;
    }
    /** Native editing uses the same text scale as the viewport, including the fixed-font option. */
    public float inputScale() { return textScale(); }
    public int inputX() { return Math.round(startX + (PAD + ChatInputMode.FH.width(Minecraft.getInstance().font) + 3) * textScale()); }
    public int inputY() { return Math.round(startY + (inputTop() + 2) * textScale()); }
    public int inputWidth() { return Math.max(1, (int) Math.floor(((viewportWidth() - PAD) * textScale() + startX - inputX()) / textScale())); }
    private java.util.List<ChatWidgetView.Line> visibleLines() {
        int rows = viewportRows();
        if (rows == 0) { scrollShift = 0; return java.util.List.of(); }
        var frame = view().presentation(Minecraft.getInstance().font, viewportWidth() - PAD * 2 - 2, rows, presentationTime);
        scrollShift = frame.shift() * LINE_HEIGHT;
        return frame.lines();
    }
    public void scroll(double amount, boolean fine) {
        int rows = viewportRows();
        if (rows == 0 || !Double.isFinite(amount) || amount == 0) return;
        view().lines(Minecraft.getInstance().font, viewportWidth() - PAD * 2 - 2, rows);
        // Half a window per wheel notch; Shift moves by one row. Preserve trackpad fractions.
        view.smoothScroll(amount * (fine ? 1 : Math.max(1, rows / 2.0)), System.nanoTime());
        view.lines(Minecraft.getInstance().font, viewportWidth() - PAD * 2 - 2, rows);
    }
    @Override public int getWidth() { return Math.round(settings().width * scale); }
    @Override public int getHeight() { return Math.round(unscaledHeight() * scale); }
    @Override public boolean isVisible() {
        return settings() != null && !settings().deleted && settings().detached && isInLayout() && visibilityAlpha() > 0;
    }
    public float visibilityAlpha() {
        var settings = settings();
        if (settings == null) return 0;
        var client = Minecraft.getInstance();
        var renderer = ru.wilyfox.client.Client.getInstance().getHudRenderer();
        boolean open = client.gui.screen() instanceof ChatScreen || renderer.isEditing() || renderer.isSettingsOpen()
                || client.gui.screen() instanceof ru.wilyfox.client.chat.ChatTabSettingsScreen;
        if (settings.visibility == ru.wilyfox.client.hud.config.ChatWidgetVisibility.ALWAYS || open) return 1;
        long activity = view().latestActivityNanos();
        return settings.visibility.alpha(false, activity == 0 ? -1 : Math.max(0, System.nanoTime() - activity));
    }
    @Override public String getDisplayName() { return "Chat " + settings().title; }
    public boolean isHeader(double x, double y) { return isHovered(x, y) && localY(y) < contentTop(); }

    @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!isVisible()) return;
        var client = Minecraft.getInstance();
        presentationTime = System.nanoTime();
        var lines = visibleLines();
        int width = viewportWidth(), height = viewportHeight();
        boolean interactive = client.gui.screen() instanceof ChatScreen;
        boolean hovered = interactive && isHovered(MouseUtils.getMouseX(), MouseUtils.getMouseY());
        graphics.pose().pushMatrix();
        float opacity = visibilityAlpha();
        try (var fade = opacity < 1 ? WidgetRenderScope.fade(opacity) : null) {
            graphics.pose().translate(startX, startY).scale(textScale(), textScale());
            if (HudSurface.chrome() == WidgetChrome.BARE) {
                int alpha = Math.round(client.options.textBackgroundOpacity().get().floatValue() * 0.5f * 255);
                try (var background = ru.wilyfox.client.hud.internal.WidgetRenderScope.background()) {
                    if (ru.wilyfox.client.hud.internal.WidgetRenderScope.alpha() > 0) graphics.fill(0, 0, width, height, alpha << 24);
                }
            } else {
                HudSurface.drawPanel(graphics, width, height);
            }
            graphics.enableScissor(0, 0, width, height);
            if (settings().showTitle) {
                String title = settings().title + (view.scroll() > 0 ? " ↑" : "");
                if (!view.search().isEmpty()) title += " · " + view.search();
                graphics.text(client.font, client.font.plainSubstrByWidth(title, width - PAD * 2),
                        PAD, PAD, hovered ? WidgetTheme.TEXT_ACCENT : WidgetTheme.TITLE);
            }
            if (lines.isEmpty()) {
                graphics.text(client.font, Component.translatable("froghelper.chat.widget_empty"), PAD,
                        contentTop(), WidgetTheme.TEXT_MUTED);
            } else {
                graphics.enableScissor(PAD - 1, contentTop(), width - PAD, contentBottom());
                graphics.pose().pushMatrix();
                graphics.pose().translate(0, scrollShift);
                var renderer = graphics.textRenderer(interactive && hovered
                        ? GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR : GuiGraphicsExtractor.HoveredTextEffects.NONE);
                var parameters = renderer.defaultParameters().withPose(new Matrix3x2f(graphics.pose()));
                int bottom = contentBottom();
                for (int i = 0; i < lines.size(); i++) {
                    var line = lines.get(i);
                    int y = bottom - (i + 1) * LINE_HEIGHT;
                    if (interactive && ru.wilyfox.client.chat.ChatMessageSelection.getInstance().isSelected(line.message()))
                        graphics.fill(PAD, y - 1, width - PAD, y + 9, WidgetTheme.withAlpha(WidgetTheme.ACCENT_LINE, 70));
                    renderer.accept(TextAlignment.LEFT, PAD + 2, y, parameters, line.text());
                    var tag = line.message().tag();
                    if (tag != null) {
                        graphics.fill(PAD - 1, y, PAD, y + 8, 0xFF000000 | tag.indicatorColor());
                        if (hovered && localX(MouseUtils.getMouseX()) < PAD + 2
                                && localY(MouseUtils.getMouseY()) >= y + scrollShift
                                && localY(MouseUtils.getMouseY()) < y + scrollShift + LINE_HEIGHT
                                && tag.text() != null) {
                            graphics.setTooltipForNextFrame(client.font, tag.text(), (int) MouseUtils.getMouseX(), (int) MouseUtils.getMouseY());
                        }
                    }
                }
                graphics.pose().popMatrix();
                graphics.disableScissor();
            }
            if (viewportRows() > 0 && (view.scroll() > 0 || view.hasOlder())) {
                graphics.fill(width - 3, contentTop(), width - 2, contentBottom(), WidgetTheme.PANEL_BG_SOFT);
                int thumbY = view.scroll() == 0 ? contentBottom() - 8 : contentTop();
                graphics.fill(width - 3, thumbY, width - 2, thumbY + 8, WidgetTheme.TEXT_MUTED);
            }
            if (hasInputBar()) {
                graphics.fill(PAD, inputTop(), width - PAD, height - PAD, WidgetTheme.PANEL_BG_SOFT);
                ChatInputMode.FH.render(graphics, client.font, PAD, inputTop());
                boolean editingInput = client.gui.screen() instanceof ChatInputHost host
                        && getConfigKey().equals(host.froghelper$inlineInputKey());
                if (!editingInput) graphics.text(client.font,
                        client.font.plainSubstrByWidth(inputDraft.isEmpty() ? Component.translatable("froghelper.chat.fh_input_hint").getString() : inputDraft,
                                Math.max(0, width - PAD * 2 - ChatInputMode.FH.width(client.font) - 4)),
                        PAD + ChatInputMode.FH.width(client.font) + 4, inputTop() + 2, WidgetTheme.TEXT_MUTED);
            }
        } finally {
            graphics.disableScissor();
            graphics.pose().popMatrix();
        }
    }
    private double localX(double x) { return (x - startX) / textScale(); }
    private double localY(double y) { return (y - startY) / textScale(); }
    public ChatWidgetView.Line lineAt(double x, double y) {
        if (!isHovered(x, y)) return null;
        double localY = localY(y), localX = localX(x);
        if (localX < PAD || localX >= viewportWidth() - PAD || localY < contentBottom() - viewportRows() * LINE_HEIGHT
                || localY >= contentBottom()) return null;
        var lines = visibleLines();
        int index = lineIndex(localY);
        return index >= 0 && index < lines.size() ? lines.get(index) : null;
    }
    public Style styleAt(double x, double y) {
        var line = lineAt(x, y);
        if (line == null) return null;
        int index = lineIndex(localY(y));
        var finder = new ClickableStyleFinder(Minecraft.getInstance().font, (int) x, (int) y).includeInsertions(true);
        var parameters = finder.defaultParameters().withPose(new Matrix3x2f().translate(startX, startY).scale(textScale()).translate(0, scrollShift));
        finder.accept(TextAlignment.LEFT, PAD + 2, contentBottom() - (index + 1) * LINE_HEIGHT,
                parameters, line.text());
        return finder.result();
    }
    private int lineIndex(double localY) { return (int) Math.floor((contentBottom() + scrollShift - localY - .001) / LINE_HEIGHT); }
    public boolean copyAt(double x, double y) {
        var line = lineAt(x, y);
        if (line == null) return false;
        String text = ChatMessageCopyExtractor.selectCopiedText(
                ChatMessageSanitizer.forLogic(line.message().content().getString()), ConfigManager.get().render.fullMessageCopy);
        return ChatMessageCopyExtractor.copyText(text);
    }
}
