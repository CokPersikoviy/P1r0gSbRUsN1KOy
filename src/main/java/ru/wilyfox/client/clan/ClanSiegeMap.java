package ru.wilyfox.client.clan;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.saveddata.maps.MapId;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

public final class ClanSiegeMap {
    public static final int FIRST_MAP_ID = 20_000_000;
    public static final int TILE_COUNT = 16;
    public static final int GRID_SIZE = 4;

    private ClanSiegeMap() {
    }

    public static boolean isSiegeMap(MapId mapId) {
        return mapId != null && isSiegeMapId(mapId.id());
    }

    public static boolean isSiegeMapId(int id) {
        return id >= FIRST_MAP_ID && id < FIRST_MAP_ID + TILE_COUNT;
    }

    public static boolean shouldHidePhysicalMap(Entity entity) {
        if (!ConfigManager.get().dungeonMap.hidePhysicalSiegeMap
                || !DiamondWorldProtocolClient.isSiegeLocation()
                || !(entity instanceof ItemFrame itemFrame)) {
            return false;
        }

        ItemStack stack = itemFrame.getItem();
        if (!stack.is(Items.FILLED_MAP)) {
            return false;
        }

        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        Float value = modelData != null ? modelData.getFloat(0) : null;
        return value != null && isSiegeMapId(Math.round(value));
    }
}
