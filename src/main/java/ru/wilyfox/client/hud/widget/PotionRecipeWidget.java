package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.recipe.PotionRecipeTracker;

import java.util.List;

public class PotionRecipeWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int EMPTY_WIDTH = 118;
    private static final int EMPTY_HEIGHT = 28;

    public PotionRecipeWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().potionRecipe.active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        PotionRecipeTracker tracker = PotionRecipeTracker.getInstance();
        List<String> recipeLines = tracker.getRecipeLines();

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
        context.drawString(mc.font, tracker.getTitle(), PADDING_X, y, WidgetTheme.TITLE);
        y += lineHeight + 2;

        context.drawString(mc.font, "Recipe:", PADDING_X, y, WidgetTheme.TEXT_SECONDARY);
        y += lineHeight;

        for (String line : recipeLines) {
            context.drawString(mc.font, line, PADDING_X, y, WidgetTheme.TEXT_SOFT);
            y += lineHeight;
        }

        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().potionRecipe.active && (PotionRecipeTracker.getInstance().hasRecipe() || isEditorPreview());
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
        return "Potion Recipe";
    }

    private int getUnscaledWidth() {
        if (!PotionRecipeTracker.getInstance().hasRecipe()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        PotionRecipeTracker tracker = PotionRecipeTracker.getInstance();

        int maxWidth = mc.font.width(tracker.getTitle());
        maxWidth = Math.max(maxWidth, mc.font.width("Recipe:"));

        for (String line : tracker.getRecipeLines()) {
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        if (!PotionRecipeTracker.getInstance().hasRecipe()) {
            return EMPTY_HEIGHT;
        }

        Minecraft mc = Minecraft.getInstance();
        int lineHeight = mc.font.lineHeight + LINE_GAP;
        int totalLines = 2 + PotionRecipeTracker.getInstance().getRecipeLines().size();
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
        context.drawString(mc.font, "Potion Recipe", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No recipe selected", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }
}

