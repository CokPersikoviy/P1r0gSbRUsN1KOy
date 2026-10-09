package ru.wilyfox.client.chat;

import ru.wilyfox.client.audio.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.widget.WidgetTheme;

import java.util.List;

public final class ChatTabOverlay {
    private static final ChatTabOverlay INSTANCE = new ChatTabOverlay();

    private static final int START_X = 4;
    private static final int TAB_HEIGHT = 13;
    private static final int TAB_GAP = 2;
    private static final int BOTTOM_OFFSET = 18;
    private static final int EMOJI_BUTTON_WIDTH = 13;
    private static final int EMOJI_GRID_COLUMNS = 8;
    private static final int EMOJI_CELL_SIZE = 13;
    private static final int EMOJI_PANEL_PADDING = 4;
    private static final int EMOJI_PANEL_OFFSET = 5;

    private boolean emojiMenuOpen = false;

    private ChatTabOverlay() {
    }

    public static ChatTabOverlay getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        int x = START_X;
        int y = screenHeight - BOTTOM_OFFSET - TAB_HEIGHT;

        for (ChatTab tab : ChatTab.values()) {
            String title = tab.getTitle();
            int tabWidth = Math.max(20, minecraft.font.width(title) + 10);

            boolean hovered = mouseX >= x && mouseX <= x + tabWidth
                    && mouseY >= y && mouseY <= y + TAB_HEIGHT;

            boolean active = ChatTabManager.getInstance().getActiveTab() == tab;

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

            int textX = x + (tabWidth - minecraft.font.width(title)) / 2;
            int textY = y + (TAB_HEIGHT - minecraft.font.lineHeight) / 2;

            graphics.text(
                    minecraft.font,
                    title,
                    textX,
                    textY,
                    textColor
            );

            x += tabWidth + TAB_GAP;
        }

        int emojiButtonX = getEmojiButtonX(minecraft);
        renderEmojiButton(graphics, minecraft, emojiButtonX, y, mouseX, mouseY, screenHeight);

        if (emojiMenuOpen) {
            renderEmojiMenu(graphics, minecraft, emojiButtonX, y, mouseX, mouseY, screenWidth, screenHeight);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();

        int x = START_X;
        int y = screenHeight - BOTTOM_OFFSET - TAB_HEIGHT;

        for (ChatTab tab : ChatTab.values()) {
            int tabWidth = Math.max(20, minecraft.font.width(tab.getTitle()) + 10);

            if (mouseX >= x && mouseX <= x + tabWidth
                    && mouseY >= y && mouseY <= y + TAB_HEIGHT) {
                if (ChatTabManager.getInstance().getActiveTab() != tab) UiSounds.click();
                ChatTabManager.getInstance().setActiveTab(tab);
                return true;
            }

            x += tabWidth + TAB_GAP;
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
        int y = screenHeight - BOTTOM_OFFSET - TAB_HEIGHT;
        return mouseX >= buttonX && mouseX <= buttonX + EMOJI_BUTTON_WIDTH
                && mouseY >= y && mouseY <= y + TAB_HEIGHT;
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
        int buttonY = screenHeight - BOTTOM_OFFSET - TAB_HEIGHT;
        int chatRight = getChatRightX(Minecraft.getInstance());
        int panelX = Math.min(screenWidth - panelWidth - 4, chatRight + EMOJI_PANEL_OFFSET);
        int panelY = buttonY - 4 - panelHeight;
        return new int[]{panelX, panelY, panelWidth, panelHeight};
    }

    private int getEmojiButtonX(Minecraft minecraft) {
        return Math.max(START_X, getChatRightX(minecraft) - EMOJI_BUTTON_WIDTH);
    }

    private int getChatRightX(Minecraft minecraft) {
        if (minecraft.gui == null) {
            return START_X + 320;
        }

        return START_X + net.minecraft.client.gui.components.ChatComponent.getWidth(minecraft.options.chatWidth().get());
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
