package ru.wilyfox.client.chat;

import ru.wilyfox.client.audio.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.List;

public final class ChatTabOverlay {
    private static final ChatTabOverlay INSTANCE = new ChatTabOverlay();

    private static final int TAB_HEIGHT = 13;
    private static final int TAB_GAP = 2;
    private static final int EMOJI_BUTTON_WIDTH = 13;
    private static final int EMOJI_GRID_COLUMNS = 8;
    private static final int EMOJI_CELL_SIZE = 13;
    private static final int EMOJI_PANEL_PADDING = 4;

    private boolean emojiMenuOpen = false;
    private int stripScroll;
    private long widthRevision = -1, layoutRevision = -1, cacheRevision = -1;
    private Object widthConfig;
    private boolean narrowWidth;
    private int totalWidth;
    private String scrollSelection;
    private int scrollViewport = -1;
    private Object scrollConfig;
    private long dragScrollTime;
    private double dragScrollRemainder;

    private ChatTabOverlay() {
    }

    public static ChatTabOverlay getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        if (ChatDock.draggedTabKey() != null) updateTabDrag(ChatDock.draggedTabKey(), ChatDock.dragPointerX());
        synchronizeScroll();
        int x = dockLeft();
        int y = rowTop(screenHeight);
        graphics.fill(dockLeft(), y, dockRight(), y + TAB_HEIGHT, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0xEC));

        graphics.enableScissor(dockLeft(), y, stripRight(), y + TAB_HEIGHT);
        boolean floating = false;
        for (String tab : ChatDock.tabs()) {
            if (!ChatDock.isPinned(tab) && !floating) {
                graphics.disableScissor();
                graphics.enableScissor(floatingLeft(), y, stripRight(), y + TAB_HEIGHT);
                x = floatingLeft() - stripScroll; floating = true;
            }
            String title = tabTitle(tab);
            int tabWidth = Math.max(20, minecraft.font.width(title) + 10);

            boolean hovered = mouseX >= Math.max(ChatDock.isPinned(tab) ? dockLeft() : floatingLeft(), x) && mouseX < Math.min(stripRight(), x + tabWidth)
                    && mouseY >= y && mouseY < y + TAB_HEIGHT;

            boolean active = ChatDock.selectedKey().equals(tab);

            int bg;
            int textColor;
            int accentColor = WidgetTheme.ACCENT_LINE;

            if (active) {
                bg = activeTabBackground();
                textColor = WidgetTheme.TITLE;
            } else if (hovered) {
                bg = hoveredTabBackground();
                textColor = WidgetTheme.TEXT_SOFT;
            } else {
                bg = idleTabBackground();
                textColor = WidgetTheme.TEXT_SECONDARY;
            }

            graphics.fill(x, y, x + tabWidth, y + TAB_HEIGHT, bg);

            if (active) {
                graphics.fill(x, y + TAB_HEIGHT - 1, x + tabWidth, y + TAB_HEIGHT, accentColor);
            }
            if (ChatDock.isPinned(tab) && ChatTabManager.getInstance().unread(ChatDock.settings(tab).channel) > 0)
                graphics.fill(x + 3, y + 2, x + 5, y + 4, WidgetTheme.TEXT_ACCENT);
            if (tab.equals(ChatDock.draggedTabKey())) graphics.fill(x, y, x + 2, y + TAB_HEIGHT, accentColor);

            int textX = x + (tabWidth - minecraft.font.width(title)) / 2;
            int textY = y + (TAB_HEIGHT - minecraft.font.lineHeight) / 2;

            graphics.text(
                    minecraft.font,
                    title,
                    textX,
                    textY,
                    textColor
            );
            if (hovered && !ChatDock.isDragging()) graphics.setTooltipForNextFrame(minecraft.font,
                    net.minecraft.network.chat.Component.translatable(ChatDock.canEdit(tab) ? "froghelper.chat.detach_tip" : "froghelper.chat.pinned_tip"), mouseX, mouseY);

            x += tabWidth + TAB_GAP;
        }

        graphics.disableScissor();
        int viewport = stripRight() - floatingLeft();
        if (naturalWidth() > viewport) {
            int thumb = Math.max(8, viewport * viewport / naturalWidth());
            int thumbX = floatingLeft() + stripScroll * (viewport - thumb) / (naturalWidth() - viewport);
            graphics.fill(floatingLeft(), y + TAB_HEIGHT + 1, stripRight(), y + TAB_HEIGHT + 3, idleTabBackground());
            graphics.fill(thumbX, y + TAB_HEIGHT + 1, thumbX + thumb, y + TAB_HEIGHT + 3, WidgetTheme.ACCENT_LINE);
        }
        x = searchX();
        int searchWidth = searchButtonWidth(minecraft);
        boolean searchHovered = isSearchButton(mouseX, mouseY, screenHeight);
        graphics.fill(x, y, x + searchWidth, y + TAB_HEIGHT, searchHovered ? hoveredTabBackground() : idleTabBackground());
        graphics.centeredText(minecraft.font, searchLabel(), x + searchWidth / 2, y + 2, WidgetTheme.TEXT_MUTED);
        if (searchHovered) graphics.setTooltipForNextFrame(minecraft.font,
                net.minecraft.network.chat.Component.translatable("froghelper.chat.search_tip"), mouseX, mouseY);

        int addX = addButtonX();
        boolean addHovered = isAddButton(mouseX, mouseY, screenHeight);
        graphics.fill(addX, y, addX + EMOJI_BUTTON_WIDTH, y + TAB_HEIGHT, addHovered ? hoveredTabBackground() : idleTabBackground());
        graphics.centeredText(minecraft.font, "+", addX + EMOJI_BUTTON_WIDTH / 2, y + 2, WidgetTheme.TEXT_ACCENT);
        if (addHovered)
            graphics.setTooltipForNextFrame(minecraft.font, net.minecraft.network.chat.Component.translatable("froghelper.chat.new_tab"), mouseX, mouseY);
        if (ChatDock.isDragging()) graphics.fill(dockLeft(), y - 3, dockRight(), y - 1, WidgetTheme.ACCENT_LINE);
        int emojiButtonX = getEmojiButtonX(minecraft);
        renderEmojiButton(graphics, minecraft, emojiButtonX, y, mouseX, mouseY, screenHeight);

        if (emojiMenuOpen) {
            renderEmojiMenu(graphics, minecraft, emojiButtonX, y, mouseX, mouseY, screenWidth, screenHeight);
        }
        String dragged = ChatDock.draggedTabKey();
        if (dragged != null) {
            String title = tabTitle(dragged);
            int width = Math.min(stripRight() - floatingLeft(), Math.max(20, minecraft.font.width(title) + 10));
            int ghostX = Math.clamp((int) ChatDock.dragPointerX() - width / 2, floatingLeft(), Math.max(floatingLeft(), stripRight() - width));
            int ghostY = y - TAB_HEIGHT - 3;
            graphics.fill(ghostX, ghostY, ghostX + width, ghostY + TAB_HEIGHT, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0xF0));
            graphics.fill(ghostX, ghostY + TAB_HEIGHT - 1, ghostX + width, ghostY + TAB_HEIGHT, WidgetTheme.ACCENT_LINE);
            graphics.text(minecraft.font, minecraft.font.plainSubstrByWidth(title, width - 10), ghostX + 5, ghostY + 2, WidgetTheme.TEXT_ACCENT);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();

        if (isAddButton(mouseX, mouseY, screenHeight)) {
            String key = ChatDock.create();
            if (key != null) ChatDock.edit(key);
            return true;
        }

        int emojiButtonX = getEmojiButtonX(minecraft);

        if (isOverEmojiButton(mouseX, mouseY, emojiButtonX, screenHeight)) {
            emojiMenuOpen = !emojiMenuOpen;
            UiSounds.openClose();
            return true;
        }

        if (emojiMenuOpen) {
            int emojiIndex = getClickedEmojiIndex(mouseX, mouseY, emojiButtonX, this.getScreenWidth(minecraft), screenHeight);
            if (emojiIndex >= 0) {
                insertEmoji(minecraft, ServerEmojiRegistry.all().get(emojiIndex).symbol());
                UiSounds.click();
                return true;
            }

            if (!isInsideEmojiMenu(mouseX, mouseY, emojiButtonX, this.getScreenWidth(minecraft), screenHeight)) {
                emojiMenuOpen = false;
            }
        }

        return false;
    }

    private void renderEmojiButton(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y, int mouseX, int mouseY, int screenHeight) {
        boolean hovered = isOverEmojiButton(mouseX, mouseY, x, screenHeight);
        int bg = emojiMenuOpen ? activeTabBackground() : (hovered ? hoveredTabBackground() : idleTabBackground());
        int textColor = emojiMenuOpen ? WidgetTheme.TITLE : (hovered ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_SECONDARY);

        graphics.fill(x, y, x + EMOJI_BUTTON_WIDTH, y + TAB_HEIGHT, bg);
        if (emojiMenuOpen) {
            graphics.fill(x, y + TAB_HEIGHT - 1, x + EMOJI_BUTTON_WIDTH, y + TAB_HEIGHT, WidgetTheme.ACCENT_LINE);
        }

        graphics.centeredText(
                minecraft.font,
                ":)",
                x + EMOJI_BUTTON_WIDTH / 2,
                y + (TAB_HEIGHT - minecraft.font.lineHeight) / 2,
            textColor
        );
    }

    private void renderEmojiMenu(GuiGraphicsExtractor graphics, Minecraft minecraft, int buttonX, int buttonY, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        List<ServerEmojiRegistry.EmojiEntry> emojis = ServerEmojiRegistry.all();
        int rows = (emojis.size() + EMOJI_GRID_COLUMNS - 1) / EMOJI_GRID_COLUMNS;
        int panelWidth = EMOJI_PANEL_PADDING * 2 + EMOJI_GRID_COLUMNS * EMOJI_CELL_SIZE;
        int panelHeight = EMOJI_PANEL_PADDING * 2 + rows * EMOJI_CELL_SIZE;
        int[] bounds = getEmojiMenuBounds(buttonX, screenWidth, screenHeight);
        int panelX = bounds[0];
        int panelY = bounds[1];

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0xD0));
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, WidgetTheme.withAlpha(WidgetTheme.ACCENT_LINE, 0xB0));

        for (int index = 0; index < emojis.size(); index++) {
            int column = index % EMOJI_GRID_COLUMNS;
            int row = index / EMOJI_GRID_COLUMNS;
            int cellX = panelX + EMOJI_PANEL_PADDING + column * EMOJI_CELL_SIZE;
            int cellY = panelY + EMOJI_PANEL_PADDING + row * EMOJI_CELL_SIZE;
            boolean hovered = mouseX >= cellX && mouseX <= cellX + EMOJI_CELL_SIZE
                    && mouseY >= cellY && mouseY <= cellY + EMOJI_CELL_SIZE;

            graphics.fill(cellX, cellY, cellX + EMOJI_CELL_SIZE, cellY + EMOJI_CELL_SIZE, hovered ? hoveredTabBackground() : idleEmojiCellBackground());
            graphics.centeredText(
                    minecraft.font,
                    emojis.get(index).symbol(),
                    cellX + EMOJI_CELL_SIZE / 2,
                    cellY + (EMOJI_CELL_SIZE - minecraft.font.lineHeight) / 2,
                    hovered ? WidgetTheme.TITLE : WidgetTheme.TEXT_SOFT
            );
        }
    }

    private static String tabTitle(String key) {
        var config = ChatDock.settings(key);
        if (ChatDock.isPinned(key)) return config.title;
        int unread = ChatTabManager.getInstance().unread(config.channel);
        boolean narrow = Minecraft.getInstance().getWindow().getGuiScaledWidth() < 400;
        return config.title + (unread == 0 ? "" : narrow ? "·" + (unread > 9 ? "9+" : unread)
                : " [" + (unread > 99 ? "99+" : unread) + "]");
    }
    private int naturalWidth() {
        var store = ChatTabManager.getInstance();
        var config = ru.wilyfox.client.hud.config.ConfigManager.get();
        long layout = ru.wilyfox.client.hud.config.ConfigManager.getLayoutRevision();
        boolean narrow = Minecraft.getInstance().getWindow().getGuiScaledWidth() < 400;
        if (widthConfig == config && widthRevision == store.uiRevision() && layoutRevision == layout
                && narrowWidth == narrow && cacheRevision == store.lineCacheRevision()) return totalWidth;
        int width = 0;
        for (String key : ChatDock.tabs()) if (!ChatDock.isPinned(key)) width += Math.max(20, Minecraft.getInstance().font.width(tabTitle(key)) + 10) + TAB_GAP;
        widthConfig = config; widthRevision = store.uiRevision(); layoutRevision = layout;
        narrowWidth = narrow; cacheRevision = store.lineCacheRevision();
        return totalWidth = width;
    }
    // Match native ChatComponent's scaled text origin and bottom, rather than the screen edges.
    int dockLeft() { return (int) Math.ceil(4 * Minecraft.getInstance().options.chatScale().get()); }
    int dockRight() {
        var minecraft = Minecraft.getInstance();
        double scale = minecraft.options.chatScale().get();
        int chatWidth = (int) Math.ceil(net.minecraft.client.gui.components.ChatComponent.getWidth(minecraft.options.chatWidth().get()) / scale);
        return Math.min(minecraft.getWindow().getGuiScaledWidth() - 4, dockLeft() + Math.max(128, (int) Math.ceil(chatWidth * scale)));
    }
    public int rowTop(int screenHeight) {
        var minecraft = Minecraft.getInstance();
        double scale = minecraft.options.chatScale().get();
        int bottom = (int) Math.floor(Math.floor((screenHeight - 40) / scale) * scale);
        if (minecraft.gui != null && minecraft.gui.chatListener().queueSize() > 0) bottom += (int) Math.ceil(9 * scale);
        return Math.min(bottom + 1, screenHeight - 16 - TAB_HEIGHT);
    }
    private String searchLabel() {
        return dockRight() - dockLeft() < 150 ? "/" : net.minecraft.network.chat.Component.translatable("froghelper.chat.search").getString();
    }
    private int floatingLeft() {
        int x = dockLeft();
        for (String key : ChatDock.tabs()) if (ChatDock.isPinned(key)) x += Math.max(20, Minecraft.getInstance().font.width(tabTitle(key)) + 10) + TAB_GAP;
        return Math.min(x, stripRight() - 1);
    }
    private int stripRight() { return searchX() - TAB_GAP; }
    private int searchX() { return dockRight() - 2 * (EMOJI_BUTTON_WIDTH + TAB_GAP) - searchButtonWidth(Minecraft.getInstance()); }
    private void synchronizeScroll() {
        int viewport = Math.max(1, stripRight() - floatingLeft());
        stripScroll = Math.clamp(stripScroll, 0, Math.max(0, naturalWidth() - viewport));
        String selected = ChatDock.selectedKey();
        Object config = ru.wilyfox.client.hud.config.ConfigManager.get();
        if (!java.util.Objects.equals(scrollSelection, selected) || scrollViewport != viewport || scrollConfig != config) {
            int x = 0;
            for (String key : ChatDock.tabs()) {
                if (ChatDock.isPinned(key)) continue;
                int width = Math.max(20, Minecraft.getInstance().font.width(tabTitle(key)) + 10);
                if (key.equals(selected)) {
                    if (x < stripScroll) stripScroll = x;
                    else if (x + width > stripScroll + viewport) stripScroll = x + width - viewport;
                    break;
                }
                x += width + TAB_GAP;
            }
            stripScroll = Math.clamp(stripScroll, 0, Math.max(0, naturalWidth() - viewport));
            scrollSelection = selected; scrollViewport = viewport; scrollConfig = config;
        }
    }
    public String tabAt(double mouseX, double mouseY, int height) {
        int y = rowTop(height);
        synchronizeScroll();
        if (mouseY < y || mouseY >= y + TAB_HEIGHT || mouseX < dockLeft() || mouseX >= stripRight()) return null;
        int x = dockLeft();
        for (String key : ChatDock.tabs()) if (ChatDock.isPinned(key)) {
            int width = Math.max(20, Minecraft.getInstance().font.width(tabTitle(key)) + 10);
            if (mouseX >= x && mouseX < Math.min(floatingLeft(), x + width)) return key;
            x += width + TAB_GAP;
        }
        if (mouseX < floatingLeft()) return null;
        x = floatingLeft() - stripScroll;
        for (String key : ChatDock.tabs()) if (!ChatDock.isPinned(key)) {
            int width = Math.max(20, Minecraft.getInstance().font.width(tabTitle(key)) + 10);
            if (mouseX >= x && mouseX < x + width) return key;
            x += width + TAB_GAP;
        }
        return null;
    }
    public boolean scrollTabs(double x, double y, double amount) {
        if (!isDockTarget(x, y)) return false;
        synchronizeScroll();
        int old = stripScroll;
        stripScroll = Math.clamp(stripScroll - (int) (amount * 30), 0, Math.max(0, naturalWidth() - stripRight() + floatingLeft()));
        if (old != stripScroll) UiSounds.scroll();
        return true;
    }
    public boolean isDockRow(double y) {
        int top = rowTop(Minecraft.getInstance().getWindow().getGuiScaledHeight());
        return y >= top - 3 && y < top + TAB_HEIGHT + 4;
    }
    public boolean isDockTarget(double x, double y) { return x >= dockLeft() && x < dockRight() && isDockRow(y); }
    public void endTabDrag() { dragScrollTime = 0; dragScrollRemainder = 0; scrollSelection = null; }
    void updateTabDrag(String dragged, double pointerX) {
        synchronizeScroll();
        if (ChatDock.draggedTabKey() != null) {
            long now = System.nanoTime();
            double elapsed = dragScrollTime == 0 ? 0 : Math.min(.05, (now - dragScrollTime) / 1_000_000_000d);
            dragScrollTime = now;
            int direction = pointerX < floatingLeft() + 12 ? -1 : pointerX >= stripRight() - 12 ? 1 : 0;
            if (direction == 0) dragScrollRemainder = 0;
            else {
                dragScrollRemainder += direction * elapsed * 150;
                int pixels = (int) dragScrollRemainder;
                dragScrollRemainder -= pixels;
                stripScroll = Math.clamp(stripScroll + pixels, 0, Math.max(0, naturalWidth() - stripRight() + floatingLeft()));
            }
        }
        int x = floatingLeft() - stripScroll;
        for (String key : ChatDock.tabs()) {
            if (ChatDock.isPinned(key)) continue;
            int width = Math.max(20, Minecraft.getInstance().font.width(tabTitle(key)) + 10);
            if (!key.equals(dragged) && pointerX < x + width / 2d) { ChatDock.moveBefore(dragged, key); return; }
            x += width + TAB_GAP;
        }
        ChatDock.moveBefore(dragged, null);
    }

    private int searchButtonWidth(Minecraft minecraft) {
        return minecraft.font.width(searchLabel()) + 10;
    }

    public boolean isSearchButton(double mouseX, double mouseY, int screenHeight) {
        var minecraft = Minecraft.getInstance();
        int x = searchX();
        int y = rowTop(screenHeight);
        return mouseX >= x && mouseX < x + searchButtonWidth(minecraft) && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }

    private int activeTabBackground() {
        return WidgetTheme.withAlpha(WidgetTheme.PANEL_BG_SOFT, 0xB8);
    }

    private int hoveredTabBackground() {
        return WidgetTheme.withAlpha(WidgetTheme.PANEL_BG, 0xAA);
    }

    private int idleTabBackground() {
        return WidgetTheme.withAlpha(WidgetTheme.BAR_BG, 0x88);
    }

    private int idleEmojiCellBackground() {
        return WidgetTheme.withAlpha(WidgetTheme.PANEL_BG_SOFT, 0x88);
    }

    private boolean isOverEmojiButton(double mouseX, double mouseY, int buttonX, int screenHeight) {
        int y = rowTop(screenHeight);
        return mouseX >= buttonX && mouseX < buttonX + EMOJI_BUTTON_WIDTH
                && mouseY >= y && mouseY < y + TAB_HEIGHT;
    }

    private boolean isInsideEmojiMenu(double mouseX, double mouseY, int buttonX, int screenWidth, int screenHeight) {
        int[] bounds = getEmojiMenuBounds(buttonX, screenWidth, screenHeight);
        return mouseX >= bounds[0] && mouseX <= bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[3];
    }

    private int getClickedEmojiIndex(double mouseX, double mouseY, int buttonX, int screenWidth, int screenHeight) {
        int[] bounds = getEmojiMenuBounds(buttonX, screenWidth, screenHeight);
        int panelX = bounds[0];
        int panelY = bounds[1];

        int emojiCount = ServerEmojiRegistry.all().size();
        for (int index = 0; index < emojiCount; index++) {
            int column = index % EMOJI_GRID_COLUMNS;
            int row = index / EMOJI_GRID_COLUMNS;
            int cellX = panelX + EMOJI_PANEL_PADDING + column * EMOJI_CELL_SIZE;
            int cellY = panelY + EMOJI_PANEL_PADDING + row * EMOJI_CELL_SIZE;

            if (mouseX >= cellX && mouseX <= cellX + EMOJI_CELL_SIZE
                    && mouseY >= cellY && mouseY <= cellY + EMOJI_CELL_SIZE) {
                return index;
            }
        }

        return -1;
    }

    private int[] getEmojiMenuBounds(int buttonX, int screenWidth, int screenHeight) {
        int rows = (ServerEmojiRegistry.all().size() + EMOJI_GRID_COLUMNS - 1) / EMOJI_GRID_COLUMNS;
        int panelWidth = EMOJI_PANEL_PADDING * 2 + EMOJI_GRID_COLUMNS * EMOJI_CELL_SIZE;
        int panelHeight = EMOJI_PANEL_PADDING * 2 + rows * EMOJI_CELL_SIZE;
        int buttonY = rowTop(screenHeight);
        int panelX = Math.max(4, Math.min(screenWidth - panelWidth - 4, buttonX + EMOJI_BUTTON_WIDTH - panelWidth));
        int panelY = buttonY - 4 - panelHeight;
        return new int[]{panelX, panelY, panelWidth, panelHeight};
    }

    private int addButtonX() { return searchX() + searchButtonWidth(Minecraft.getInstance()) + TAB_GAP; }
    private boolean isAddButton(double x, double y, int screenHeight) {
        int top = rowTop(screenHeight);
        return x >= addButtonX() && x < addButtonX() + EMOJI_BUTTON_WIDTH && y >= top && y < top + TAB_HEIGHT;
    }
    private int getEmojiButtonX(Minecraft minecraft) {
        return addButtonX() + EMOJI_BUTTON_WIDTH + TAB_GAP;
    }

    private int getScreenWidth(Minecraft minecraft) {
        return minecraft.getWindow().getGuiScaledWidth();
    }

    private void insertEmoji(Minecraft minecraft, String emoji) {
        if (minecraft.gui.screen() == null || emoji == null || emoji.isBlank()) {
            return;
        }

        emoji.codePoints().forEach(codepoint -> minecraft.gui.screen().charTyped(new net.minecraft.client.input.CharacterEvent(codepoint)));
    }
}
