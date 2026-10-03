package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.rune.ActiveRunesStore;
import ru.wilyfox.client.rune.RuneSetCooldownStore;
import ru.wilyfox.utils.Formatting;

import java.util.List;

public class ActiveRunesWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int BAR_HEIGHT = 2;
    private static final int EMPTY_WIDTH = 100;
    private static final int EMPTY_HEIGHT = 28;

    private final ActiveRunesStore store;

    public ActiveRunesWidget(int x, int y, HudLayer layer, ActiveRunesStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<String> runes = store.getAll();

        if (runes.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int lineStep = mc.font.lineHeight + LINE_GAP;

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPanel(context, getUnscaledWidth(), getUnscaledHeight());

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.text(mc.font, "Active Runes", PADDING_X, y, WidgetTheme.TITLE);
            y += lineStep + 2;
        }

        for (int i = 0; i < runes.size(); i++) {
            int color = i < 3 ? WidgetTheme.TEXT_SOFT : WidgetTheme.TEXT_SECONDARY;
            context.text(mc.font, Formatting.stripMinecraftFormatting(runes.get(i)), PADDING_X, y, color);
            y += lineStep;
        }

        if (RuneSetCooldownStore.isActive()) {
            int barY = getUnscaledHeight() - BAR_HEIGHT;
            HudSurface.drawBar(context, 0, barY, getUnscaledWidth(), BAR_HEIGHT, RuneSetCooldownStore.getProgress(), WidgetTheme.BAR_FILL);
        }

        context.pose().popMatrix();
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
    public boolean isVisible() {
        return ConfigManager.get().activeRunes.active && (!store.isEmpty() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Active Runes";
    }

    private int getUnscaledWidth() {
        List<String> runes = store.getAll();
        if (runes.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Active Runes") : 0;

        for (String rune : runes) {
            maxWidth = Math.max(maxWidth, mc.font.width(Formatting.stripMinecraftFormatting(rune)));
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        int count = store.getAll().size();
        if (count == 0) {
            return EMPTY_HEIGHT;
        }

        int lineStep = Minecraft.getInstance().font.lineHeight + LINE_GAP;
        int titleBlock = WidgetUtils.showWidgetTitles() ? lineStep + 2 : 0;
        return PADDING_Y * 2 + 2 + titleBlock + count * lineStep + (RuneSetCooldownStore.isActive() ? BAR_HEIGHT : 0);
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphicsExtractor context, Minecraft mc) {
        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.text(mc.font, "Active Runes", PADDING_X, 6, WidgetTheme.TITLE);
        context.text(mc.font, "No active set", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popMatrix();
    }
}

