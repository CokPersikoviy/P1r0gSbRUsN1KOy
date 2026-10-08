package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.pet.ActivePetInfo;
import ru.wilyfox.client.pet.ActivePetsStore;

import java.util.List;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class ActivePetsWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int LINE_GAP = 1;
    private static final int EMPTY_WIDTH = 112;
    private static final int EMPTY_HEIGHT = 28;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 4;
    private static final DecimalFormat ENERGY_FORMAT = new DecimalFormat("###", DecimalFormatSymbols.getInstance(Locale.US));

    private final ActivePetsStore store;

    public ActivePetsWidget(int x, int y, HudLayer layer, ActivePetsStore store) {
        super(x, y, layer);
        this.store = store;
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<ActivePetInfo> pets = store.getResolved();

        if (pets.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int lineStep = Math.max(ICON_SIZE, mc.font.lineHeight) + LINE_GAP;

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPanel(context, getUnscaledWidth(), getUnscaledHeight());

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.text(mc.font, "Active Pets", PADDING_X, y, WidgetTheme.TITLE);
            y += lineStep + 2;
        }

        for (ActivePetInfo pet : pets) {
            ItemStack icon = pet.icon();
            if (!icon.isEmpty()) {
                WidgetUtils.drawItemIcon(context, icon, PADDING_X, y);
            }
            context.text(
                    mc.font,
                    formatPetLine(pet),
                    PADDING_X + ICON_SIZE + ICON_TEXT_GAP,
                    y + Math.max(0, (ICON_SIZE - mc.font.lineHeight) / 2),
                    WidgetTheme.TEXT_SOFT
            );
            y += lineStep;
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
        return ConfigManager.get().activePets.active && (store.hasResolved() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Active Pets";
    }

    private int getUnscaledWidth() {
        List<ActivePetInfo> pets = store.getResolved();
        if (pets.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Active Pets") : 0;

        for (ActivePetInfo pet : pets) {
            maxWidth = Math.max(maxWidth, ICON_SIZE + ICON_TEXT_GAP + mc.font.width(formatPetLine(pet)));
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight() {
        int count = store.getResolved().size();
        if (count == 0) {
            return EMPTY_HEIGHT;
        }

        int lineStep = Math.max(ICON_SIZE, Minecraft.getInstance().font.lineHeight) + LINE_GAP;
        int titleBlock = WidgetUtils.showWidgetTitles() ? lineStep + 2 : 0;
        return PADDING_Y * 2 + 2 + titleBlock + count * lineStep;
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphicsExtractor context, Minecraft mc) {
        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.text(mc.font, "Active Pets", PADDING_X, 6, WidgetTheme.TITLE);
        context.text(mc.font, "No active pets", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popMatrix();
    }

    private String formatPetLine(ActivePetInfo pet) {
        return pet.name() + " [" + pet.level() + "] (" + formatEnergy(pet.energy()) + "\u26a1)";
    }

    private String formatEnergy(double energy) {
        return ENERGY_FORMAT.format(energy);
    }
}
