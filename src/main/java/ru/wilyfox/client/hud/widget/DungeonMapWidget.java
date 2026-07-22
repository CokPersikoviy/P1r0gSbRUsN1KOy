package ru.wilyfox.client.hud.widget;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import ru.wilyfox.client.dungeon.DungeonMapTracker;
import ru.wilyfox.client.clan.ClanSiegeMapRenderer;
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

        if (DiamondWorldProtocolClient.isSiegeLocation()) {
            HudSurface.drawPanel(context, OUTER_SIZE, OUTER_SIZE);
            int tileCount = ClanSiegeMapRenderer.render(
                    context,
                    mc,
                    MAP_DRAW_OFFSET,
                    MAP_DRAW_OFFSET,
                    MAP_SIZE,
                    0.7F * ConfigManager.get().dungeonMap.siegeZoomPercent / 100.0F,
                    ConfigManager.get().dungeonMap.rotateSiegeMap
            );
            if (tileCount == 0) {
                renderPlaceholder(context, mc);
            }
            context.pose().popPose();
            return;
        }

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

        HudSurface.drawPanel(context, OUTER_SIZE, OUTER_SIZE);

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
        Minecraft minecraft = Minecraft.getInstance();
        return ConfigManager.get().dungeonMap.active
                && (canRenderLiveMap(minecraft) || ClanSiegeMapRenderer.canRender(minecraft) || isEditorPreview());
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
                && DiamondWorldProtocolClient.isDungeonLocation()
                && DungeonMapTracker.getInstance().hasMapId();
    }

    private boolean isEditorPreview() {
        return Minecraft.getInstance().screen instanceof HudEditingScreen;
    }

    private void renderPlaceholder(GuiGraphics context, Minecraft mc) {
        HudSurface.drawPlaceholderPanel(context, OUTER_SIZE, OUTER_SIZE);

        int gridColor = WidgetTheme.GRID_LINE;
        for (int offset = 18; offset < OUTER_SIZE - 18; offset += 18) {
            context.fill(offset, 12, offset + 1, OUTER_SIZE - 12, gridColor);
            context.fill(12, offset, OUTER_SIZE - 12, offset + 1, gridColor);
        }

        context.drawCenteredString(mc.font, "Dungeon / Siege", OUTER_SIZE / 2, OUTER_SIZE / 2 - 6, WidgetTheme.TITLE);
        context.drawCenteredString(mc.font, "Waiting for map", OUTER_SIZE / 2, OUTER_SIZE / 2 + 6, WidgetTheme.TEXT_MUTED);
    }
}
