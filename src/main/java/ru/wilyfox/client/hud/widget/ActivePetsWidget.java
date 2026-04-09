package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.pet.ActivePetInfo;
import ru.wilyfox.client.pet.ActivePetsStore;

import java.util.List;

public class ActivePetsWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int EMPTY_WIDTH = 112;
    private static final int EMPTY_HEIGHT = 28;

    private final ActivePetsStore store;

    public ActivePetsWidget(int x, int y, HudLayer layer, ActivePetsStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<ActivePetInfo> pets = store.getAll();

        if (pets.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int lineStep = mc.font.lineHeight + LINE_GAP;

        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, getUnscaledWidth(), getUnscaledHeight(), WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, getUnscaledWidth(), 1, WidgetTheme.WIDGET_ACCENT_LINE);

        int y = PADDING_Y;
        context.drawString(mc.font, "Active Pets", PADDING_X, y, WidgetTheme.TITLE);
        y += lineStep + 2;

        for (ActivePetInfo pet : pets) {
            context.drawString(mc.font, formatPetLine(pet), PADDING_X, y, WidgetTheme.TEXT_SOFT);
            y += lineStep;
        }

        context.pose().popPose();
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
        return ConfigManager.get().activePets.active && (!store.isEmpty() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Active Pets";
    }

    private int getUnscaledWidth() {
        List<ActivePetInfo> pets = store.getAll();
        if (pets.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = mc.font.width("Active Pets");

        for (ActivePetInfo pet : pets) {
            maxWidth = Math.max(maxWidth, mc.font.width(formatPetLine(pet)));
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        int count = store.getAll().size();
        if (count == 0) {
            return EMPTY_HEIGHT;
        }

        int lineStep = Minecraft.getInstance().font.lineHeight + LINE_GAP;
        return PADDING_Y * 2 + 2 + lineStep + 2 + count * lineStep;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0f);

        context.fill(0, 0, EMPTY_WIDTH, EMPTY_HEIGHT, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, EMPTY_WIDTH, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.drawString(mc.font, "Active Pets", PADDING_X, 6, WidgetTheme.TITLE);
        context.drawString(mc.font, "No active pets", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popPose();
    }

    private String formatPetLine(ActivePetInfo pet) {
        return pet.name() + " [" + pet.level() + "] (" + formatEnergy(pet.energy()) + "\u26a1)";
    }

    private String formatEnergy(double energy) {
        if (Math.floor(energy) == energy) {
            return Integer.toString((int) energy);
        }

        return String.format(java.util.Locale.US, "%.1f", energy);
    }
}

