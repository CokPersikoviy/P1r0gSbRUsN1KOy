package ru.wilyfox.client.clan;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwClanSiegePosition;

public final class ClanSiegeMapRenderer {
    private static final int TILE_SIZE = 128;
    private static final int COMPOSITE_CENTER = ClanSiegeMap.GRID_SIZE * TILE_SIZE / 2;

    private ClanSiegeMapRenderer() {
    }

    public static boolean canRender(Minecraft minecraft) {
        return minecraft.level != null
                && DiamondWorldProtocolClient.isSiegeLocation()
                && DiamondWorldProtocolClient.getClanSiegePosition().isAvailable();
    }

    public static int render(GuiGraphics graphics, Minecraft minecraft, int left, int top, int size,
                             float zoom, boolean rotate) {
        if (!canRender(minecraft)) {
            return 0;
        }

        DwClanSiegePosition position = DiamondWorldProtocolClient.getClanSiegePosition();
        int renderedTiles = 0;
        graphics.enableScissor(left, top, left + size, top + size);
        graphics.pose().pushPose();
        graphics.pose().translate(left + size / 2.0F, top + size / 2.0F, 0.0F);
        if (rotate && minecraft.player != null) {
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0F - minecraft.player.getYRot()));
        }
        graphics.pose().scale(zoom, zoom, 1.0F);
        graphics.pose().translate(COMPOSITE_CENTER - position.x(), COMPOSITE_CENTER - position.y(), 0.0F);

        for (int index = 0; index < ClanSiegeMap.TILE_COUNT; index++) {
            MapId mapId = new MapId(ClanSiegeMap.FIRST_MAP_ID + index);
            MapItemSavedData mapData = minecraft.level.getMapData(mapId);
            if (mapData == null) {
                continue;
            }

            ResourceLocation texture = minecraft.getMapTextureManager().prepareMapTexture(mapId, mapData);
            int tileX = index % ClanSiegeMap.GRID_SIZE;
            int tileY = index / ClanSiegeMap.GRID_SIZE;
            graphics.blit(
                    RenderType::guiTextured,
                    texture,
                    tileX * TILE_SIZE - COMPOSITE_CENTER,
                    tileY * TILE_SIZE - COMPOSITE_CENTER,
                    0.0F,
                    0.0F,
                    TILE_SIZE,
                    TILE_SIZE,
                    TILE_SIZE,
                    TILE_SIZE
            );
            renderedTiles++;
        }

        graphics.pose().popPose();
        graphics.disableScissor();
        return renderedTiles;
    }
}
