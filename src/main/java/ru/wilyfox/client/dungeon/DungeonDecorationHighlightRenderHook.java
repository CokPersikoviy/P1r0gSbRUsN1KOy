package ru.wilyfox.client.dungeon;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.profiler.ModProfiler;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;

import java.util.ArrayList;
import java.util.List;

public final class DungeonDecorationHighlightRenderHook {
    private static final GizmoStyle STYLE = GizmoStyle.stroke(0xFFFFCF24, 2.0F);
    private static final List<AABB> BOXES = new ArrayList<>();
    private static ClientLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;
    private static String cachedLocation;
    private static boolean registered;
    private static final double BOX_SIZE = 1.0D;

    private DungeonDecorationHighlightRenderHook() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        LevelRenderEvents.BEFORE_GIZMOS.register(DungeonDecorationHighlightRenderHook::onAfterEntities);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearCache());
    }

    private static void onAfterEntities(LevelRenderContext context) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("render/DungeonDecorationHighlightRenderHook")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null
                    || !ConfigManager.get().render.dungeonDecorationHighlight
                    || !DiamondWorldProtocolClient.isDungeonLocation()) {
                clearCache();
                return;
            }

            long tick = mc.level.getGameTime();
            String location = DiamondWorldProtocolClient.getCurrentGameLocation();
            if (cachedLevel != mc.level || cachedTick != tick || !java.util.Objects.equals(cachedLocation, location)) {
                BOXES.clear();
                cachedLevel = mc.level;
                cachedTick = tick;
                cachedLocation = location;
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof Display.ItemDisplay itemDisplay && !entity.isRemoved() && shouldHighlight(itemDisplay)) {
                        Vec3 position = itemDisplay.position();
                        BOXES.add(AABB.ofSize(new Vec3(position.x, position.y + BOX_SIZE * 0.5D, position.z),
                                BOX_SIZE, BOX_SIZE, BOX_SIZE));
                    }
                }
            }

            if (BOXES.isEmpty()) return;
            try (var collection = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                for (AABB box : BOXES) {
                    Gizmos.cuboid(box, STYLE);
                }
            }
        }
    }

    private static void clearCache() {
        BOXES.clear();
        cachedLevel = null;
        cachedTick = Long.MIN_VALUE;
        cachedLocation = null;
    }

    private static boolean shouldHighlight(Display.ItemDisplay itemDisplay) {
        ItemStack stack = itemDisplay.getSlot(0).get();
        if (stack.isEmpty()) {
            return false;
        }

        CustomModelData customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (customModelData == null) {
            return false;
        }

        Float firstValue = customModelData.getFloat(0);
        if (firstValue == null) {
            return false;
        }

        int cmd = (int) (float) firstValue;
        return (cmd >= 10271 && cmd <= 10282) || (cmd >= 10311 && cmd <= 10327);
    }

}
