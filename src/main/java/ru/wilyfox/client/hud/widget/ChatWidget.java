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
import ru.wilyfox.client.hud.config.ChatWidgetConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import ru.wilyfox.client.hud.config.WidgetChrome;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.utils.MouseUtils;

/** One independent channel viewport over the original native messages. */
public final class ChatWidget extends AbstractWidget {
    private static final int PAD = 6, LINE_HEIGHT = 10, TITLE_HEIGHT = 16;
    private final WidgetCatalog catalog;
    private final ChatWidgetView view;

    public ChatWidget(WidgetCatalog catalog, int x, int y) {
        this(catalog.key(), catalog, x, y);
    }
    public ChatWidget(String key, WidgetCatalog catalog, int x, int y) {
        super(x, y, HudLayer.CONTENT);
        if (catalog.chatChannel() == null) throw new IllegalArgumentException("Not a chat widget");
        this.catalog = catalog;
        setConfigKey(key);
        view = new ChatWidgetView(ChatTabManager.getInstance(), catalog.chatChannel());
    }
    public ChatWidgetConfig settings() { return ConfigManager.get().chatWidgets.get(getConfigKey()); }
    public ChatWidgetView view() { view.channel(settings().channel); view.filter(settings().textFilter); return view; }
    private int contentTop() { return PAD + (settings().showTitle ? TITLE_HEIGHT : 0); }
    private int unscaledHeight() { return contentTop() + settings().rows * LINE_HEIGHT + PAD; }
    private float textScale() { return ConfigManager.get().render.fixedChatWidgetFont ? 1.0f : scale; }
    private int viewportWidth() { return ConfigManager.get().render.fixedChatWidgetFont ? getWidth() : settings().width; }
    private int viewportHeight() { return ConfigManager.get().render.fixedChatWidgetFont ? getHeight() : unscaledHeight(); }
    private int viewportRows() { return Math.max(0, (viewportHeight() - contentTop() - PAD) / LINE_HEIGHT); }
    private int contentBottom() { return viewportHeight() - PAD; }
    private java.util.List<ChatWidgetView.Line> visibleLines() {
        int rows = viewportRows();
        return rows == 0 ? java.util.List.of() : view().lines(Minecraft.getInstance().font, viewportWidth() - PAD * 2 - 2, rows);
    }
    @Override public int getWidth() { return Math.round(settings().width * scale); }
    @Override public int getHeight() { return Math.round(unscaledHeight() * scale); }
    @Override public boolean isVisible() { return settings() != null && !settings().deleted && settings().detached && isInLayout(); }
    @Override public String getDisplayName() { return "Chat " + settings().title; }
    public boolean isHeader(double x, double y) { return isHovered(x, y) && localY(y) < contentTop(); }

    @Override public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!isVisible()) return;
        var client = Minecraft.getInstance();
        var lines = visibleLines();
        int width = viewportWidth(), height = viewportHeight();
        boolean interactive = client.gui.screen() instanceof ChatScreen;
        boolean hovered = interactive && isHovered(MouseUtils.getMouseX(), MouseUtils.getMouseY());
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(startX, startY).scale(textScale(), textScale());
            if (HudSurface.chrome() == WidgetChrome.BARE) {
                int alpha = Math.round(client.options.textBackgroundOpacity().get().floatValue() * 0.5f * 255);
                graphics.fill(0, 0, width, height, alpha << 24);
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
                var renderer = graphics.textRenderer(interactive && hovered
                        ? GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR : GuiGraphicsExtractor.HoveredTextEffects.NONE);
                var parameters = renderer.defaultParameters().withPose(new Matrix3x2f(graphics.pose()));
                int bottom = contentBottom();
                for (int i = 0; i < lines.size(); i++) {
                    var line = lines.get(i);
                    int y = bottom - (i + 1) * LINE_HEIGHT;
                    renderer.accept(TextAlignment.LEFT, PAD + 2, y, parameters, line.text());
                    var tag = line.message().tag();
                    if (tag != null) {
                        graphics.fill(PAD - 1, y, PAD, y + 8, 0xFF000000 | tag.indicatorColor());
                        if (hovered && localX(MouseUtils.getMouseX()) < PAD + 2
                                && localY(MouseUtils.getMouseY()) >= y && localY(MouseUtils.getMouseY()) < y + LINE_HEIGHT
                                && tag.text() != null) {
                            graphics.setTooltipForNextFrame(client.font, tag.text(), (int) MouseUtils.getMouseX(), (int) MouseUtils.getMouseY());
                        }
                    }
                }
            }
            if (viewportRows() > 0 && (view.scroll() > 0 || view.hasOlder())) {
                graphics.fill(width - 3, contentTop(), width - 2, contentBottom(), WidgetTheme.PANEL_BG_SOFT);
                int thumbY = view.scroll() == 0 ? contentBottom() - 8 : contentTop();
                graphics.fill(width - 3, thumbY, width - 2, thumbY + 8, WidgetTheme.TEXT_MUTED);
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
        int index = (int) ((contentBottom() - localY - 1) / LINE_HEIGHT);
        return index >= 0 && index < lines.size() ? lines.get(index) : null;
    }
    public Style styleAt(double x, double y) {
        var line = lineAt(x, y);
        if (line == null) return null;
        int index = (int) ((contentBottom() - localY(y) - 1) / LINE_HEIGHT);
        var finder = new ClickableStyleFinder(Minecraft.getInstance().font, (int) x, (int) y).includeInsertions(true);
        var parameters = finder.defaultParameters().withPose(new Matrix3x2f().translate(startX, startY).scale(textScale()));
        finder.accept(TextAlignment.LEFT, PAD + 2, contentBottom() - (index + 1) * LINE_HEIGHT,
                parameters, line.text());
        return finder.result();
    }
    public boolean copyAt(double x, double y) {
        var line = lineAt(x, y);
        if (line == null) return false;
        String text = ChatMessageCopyExtractor.selectCopiedText(
                ChatMessageSanitizer.forLogic(line.message().content().getString()), ConfigManager.get().render.fullMessageCopy);
        if (text.isBlank()) return false;
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        ru.wilyfox.client.popup.PopUpManager.getInstance().notifyChatCopied();
        return true;
    }
}
