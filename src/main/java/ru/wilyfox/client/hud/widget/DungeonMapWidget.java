package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import ru.wilyfox.client.dungeon.DungeonMapTracker;
import ru.wilyfox.client.hud.HudEditingScreen;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

public final class DungeonMapWidget extends AbstractWidget {
    private static final int OUTER_SIZE = 132;
    private static final int MAP_SIZE = 128;
    private static final int MAP_DRAW_OFFSET = 2;
    private static final float MAP_UV_OFFSET = 1.0F;
    private static final int MAP_UV_SIZE = 126;

    public DungeonMapWidget(int x, int y, HudLayer layer) {
        super(x, y, layer);
    }

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        if (!ConfigManager.get().dungeonMap.active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        context.pose().pushPose();
        context.pose().translate(startX, startY, 0);
        context.pose().scale(scale, scale, 1.0F);

        if (!canRenderLiveMap(mc)) {
            renderPlaceholder(context, mc);
            context.pose().popPose();
            return;
        }

        MapId mapId = DungeonMapTracker.getInstance().getMapId();
        MapItemSavedData mapData = mc.level.getMapData(mapId);
        if (mapData == null) {
            renderPlaceholder(context, mc);
            context.pose().popPose();
            return;
        }

        ResourceLocation texture = mc.getMapTextureManager().prepareMapTexture(mapId, mapData);

        context.fill(0, 0, OUTER_SIZE, OUTER_SIZE, WidgetTheme.WIDGET_PANEL_BG);
        context.fill(0, 0, OUTER_SIZE, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.fill(0, OUTER_SIZE - 1, OUTER_SIZE, OUTER_SIZE, WidgetTheme.WIDGET_ACCENT_LINE);
        context.fill(0, 0, 1, OUTER_SIZE, WidgetTheme.WIDGET_ACCENT_LINE);
        context.fill(OUTER_SIZE - 1, 0, OUTER_SIZE, OUTER_SIZE, WidgetTheme.WIDGET_ACCENT_LINE);

        context.blit(
                RenderType::guiTextured,
                texture,
                MAP_DRAW_OFFSET,
                MAP_DRAW_OFFSET,
                MAP_UV_OFFSET,
                MAP_UV_OFFSET,
                MAP_SIZE,
                MAP_SIZE,
                128,
                128
        );

        context.pose().popPose();
    }

    @Override
    public boolean isVisible() {
        return ConfigManager.get().dungeonMap.active && (canRenderLiveMap(Minecraft.getInstance()) || isEditorPreview());
    }

    @Override
    public int getWidth() {
        return Math.round(OUTER_SIZE * getScale());
    }

    @Override
    public int getHeight() {
        return Math.round(OUTER_SIZE * getScale());
    }

    @Override
    public String getDisplayName() {
        return "Dungeon / Siege Map";
    }

    private boolean canRenderLiveMap(Minecraft mc) {
        return mc.level != null
                && DiamondWorldProtocolClient.isDungeonOrSiegeLocation()
                && DungeonMapTracker.getInstance().hasMapId();
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        context.fill(0, 0, OUTER_SIZE, OUTER_SIZE, WidgetTheme.WIDGET_PANEL_BG_SOFT);
        context.fill(0, 0, OUTER_SIZE, 1, WidgetTheme.WIDGET_ACCENT_LINE);
        context.fill(0, OUTER_SIZE - 1, OUTER_SIZE, OUTER_SIZE, WidgetTheme.WIDGET_ACCENT_LINE);
        context.fill(0, 0, 1, OUTER_SIZE, WidgetTheme.WIDGET_ACCENT_LINE);
        context.fill(OUTER_SIZE - 1, 0, OUTER_SIZE, OUTER_SIZE, WidgetTheme.WIDGET_ACCENT_LINE);

        int gridColor = WidgetTheme.GRID_LINE;
        for (int offset = 18; offset < OUTER_SIZE - 18; offset += 18) {
            context.fill(offset, 12, offset + 1, OUTER_SIZE - 12, gridColor);
            context.fill(12, offset, OUTER_SIZE - 12, offset + 1, gridColor);
        }

        context.drawCenteredString(mc.font, "Dungeon / Siege", OUTER_SIZE / 2, OUTER_SIZE / 2 - 6, WidgetTheme.TITLE);
        context.drawCenteredString(mc.font, "Waiting for map", OUTER_SIZE / 2, OUTER_SIZE / 2 + 6, WidgetTheme.TEXT_MUTED);
    }
}

