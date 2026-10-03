package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import ru.wilyfox.client.ability.AbilityCooldownStore;
import ru.wilyfox.client.ability.AbilityCooldownStore.Entry;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.utils.Formatting;

import java.util.List;

public class AbilityCooldownWidget extends AbstractWidget {
    private static final int PADDING_X = 6;
    private static final int PADDING_Y = 5;
    private static final int ROW_GAP = 3;
    private static final int EMPTY_WIDTH = 116;
    private static final int EMPTY_HEIGHT = 28;

    private final AbilityCooldownStore store;

    // Per-frame cache: render()/getWidth()/getHeight() each call getActiveEntries() (which rebuilds a
    // list) every frame; compute once per HUD frame, keyed on HudFrameClock.
    private long cachedFrameId = Long.MIN_VALUE;
    private List<Entry> cachedEntries;

    public AbilityCooldownWidget(int x, int y, HudLayer layer, AbilityCooldownStore store) {
        super(x, y, layer);
        this.store = store;
    }

    private List<Entry> entries() {
        long frame = HudFrameClock.current();
        if (frame != cachedFrameId || cachedEntries == null) {
            cachedEntries = store.getActiveEntries();
            cachedFrameId = frame;
        }
        return cachedEntries;
    }

    @Override
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!isVisible()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        List<Entry> entries = entries();

        if (entries.isEmpty()) {
            if (!isEditorPreview()) {
                return;
            }

            renderPlaceholder(context, mc);
            return;
        }

        int width = getUnscaledWidth(entries);
        int height = getUnscaledHeight(entries.size());
        int rowHeight = mc.font.lineHeight;

        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPanel(context, width, height);

        int y = PADDING_Y;
        if (WidgetUtils.showWidgetTitles()) {
            context.text(mc.font, "Ability Cooldowns", PADDING_X, y, WidgetTheme.TITLE);
            y += mc.font.lineHeight + 3;
        }

        for (Entry entry : entries) {
            String remaining = formatSeconds(entry.remainingMillis());
            int timeWidth = mc.font.width(remaining);
            int rightX = width - PADDING_X;

            context.text(mc.font, entry.name(), PADDING_X, y, WidgetTheme.TEXT_SOFT);
            context.text(mc.font, remaining, rightX - timeWidth, y, WidgetTheme.TEXT_SECONDARY);

            y += rowHeight + ROW_GAP;
        }

        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        return Math.round(getUnscaledWidth(entries()) * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(getUnscaledHeight(entries().size()) * getScale());
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().abilityCooldown.active && (store.hasActiveEntries() || isEditorPreview());
    }

    @Override
    public String getDisplayName() {
        return "Ability Cooldowns";
    }

    private int getUnscaledWidth(List<Entry> entries) {
        if (entries.isEmpty()) {
            return EMPTY_WIDTH;
        }

        Minecraft mc = Minecraft.getInstance();
        int maxWidth = WidgetUtils.showWidgetTitles() ? mc.font.width("Ability Cooldowns") : 0;

        for (Entry entry : entries) {
            String line = entry.name() + " " + formatSeconds(entry.remainingMillis());
            maxWidth = Math.max(maxWidth, mc.font.width(line));
        }

        return maxWidth + PADDING_X * 2;
    }

    private int getUnscaledHeight(int count) {
        if (count <= 0) {
            return EMPTY_HEIGHT;
        }

        int rowHeight = Minecraft.getInstance().font.lineHeight;
        int titleBlock = WidgetUtils.showWidgetTitles() ? Minecraft.getInstance().font.lineHeight + 3 : 0;
        return PADDING_Y * 2 + titleBlock + count * rowHeight + Math.max(0, count - 1) * ROW_GAP;
    }

    private String formatSeconds(long remainingMillis) {
        return Formatting.formatMillis(System.currentTimeMillis() + remainingMillis);
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().gui.screen() instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphicsExtractor context, Minecraft mc) {
        context.pose().pushMatrix();
        context.pose().translate(startX, startY);
        context.pose().scale(scale, scale);

        HudSurface.drawPlaceholderPanel(context, EMPTY_WIDTH, EMPTY_HEIGHT);
        context.text(mc.font, "Ability Cooldowns", PADDING_X, 6, WidgetTheme.TITLE);
        context.text(mc.font, "No active abilities", PADDING_X, 15, WidgetTheme.TEXT_MUTED);

        context.pose().popMatrix();
    }
}
