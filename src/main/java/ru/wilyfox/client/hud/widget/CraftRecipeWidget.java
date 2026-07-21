package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.recipe.CraftRecipeTracker;

import java.util.List;

public class CraftRecipeWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int COLUMN_GAP = 6;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 4;
    private static final int EMPTY_WIDTH = 132;
    private static final int EMPTY_HEIGHT = 28;

    public CraftRecipeWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().craftRecipe.active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        CraftRecipeTracker tracker = CraftRecipeTracker.getInstance();
        List<String> recipeLines = tracker.getRecipeLines();
        boolean compact = ConfigManager.get().craftRecipe.compact;

        if (!tracker.hasRecipe()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int lineHeight = mc.font.lineHeight + LINE_GAP;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPanel(context, getUnscaledWidth(), getUnscaledHeight());

        int y = PADDING_Y;
        if (compact) {
            context.drawString(mc.font, tracker.getTitle(), PADDING_X, y, WidgetTheme.TITLE);
            y += lineHeight + 2;
        } else {
            ItemStack icon = tracker.getIcon();
            int iconY = y + Math.max(0, (mc.font.lineHeight - ICON_SIZE) / 2);
            context.renderItem(icon, PADDING_X, iconY);

            int textX = PADDING_X + ICON_SIZE + ICON_TEXT_GAP;
            context.drawString(mc.font, tracker.getTitle(), textX, y, WidgetTheme.TITLE);
            y += lineHeight;

            String craftTimeLine = tracker.getCraftTimeLine();
            if (!craftTimeLine.isBlank()) {
                context.drawString(mc.font, craftTimeLine, textX, y, WidgetTheme.TEXT_SECONDARY);
                y += lineHeight;
            }

            y += 2;
        }

        context.drawString(mc.font, "Ingredients:", PADDING_X, y, WidgetTheme.TEXT_SECONDARY);
        y += lineHeight;

        for (String line : recipeLines) {
            context.drawString(mc.font, line, PADDING_X, y, WidgetTheme.TEXT_SOFT);
            y += lineHeight;
        }

        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().craftRecipe.active && (CraftRecipeTracker.getInstance().hasRecipe() || isEditorPreview());
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth() * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight() * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Craft Recipe";
    }

    private int getUnscaledWidth() {
        if (!CraftRecipeTracker.getInstance().hasRecipe()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        CraftRecipeTracker tracker = CraftRecipeTracker.getInstance();
        boolean compact = ConfigManager.get().craftRecipe.compact;

        int maxWidth = mc.font.width(tracker.getTitle());
        if (!compact) {
            maxWidth = Math.max(maxWidth, mc.font.width(tracker.getCraftTimeLine()));
            maxWidth += ICON_SIZE + ICON_TEXT_GAP;
        }
        maxWidth = Math.max(maxWidth, mc.font.width("Ingredients:"));

        for (String line : tracker.getRecipeLines()) {
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        if (!CraftRecipeTracker.getInstance().hasRecipe()) {
            return EMPTY_HEIGHT;
        }

        Minecraft mc = Minecraft.getInstance();
        int lineHeight = mc.font.lineHeight + LINE_GAP;
        int totalLines = 2 + CraftRecipeTracker.getInstance().getRecipeLines().size();
        if (!ConfigManager.get().craftRecipe.compact) {
            totalLines += CraftRecipeTracker.getInstance().getCraftTimeLine().isBlank() ? 1 : 2;
        }
        return totalLines * lineHeight + PADDING_Y * 2 + 2;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.drawString(mc.font, "Craft Recipe", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No recipe selected", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

