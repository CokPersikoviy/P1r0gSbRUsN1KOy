package ru.wilyfox.client.performance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.joml.Matrix4f;
import ru.wilyfox.client.hud.config.ConfigManager;

public final class EntityVisibilityClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean old = context.computeOnClient(c -> ConfigManager.get().render.hideFirstPersonCosmetics);
        var camera = context.computeOnClient(c -> c.options.getCameraType());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var frustum = new Frustum(new Matrix4f(), new Matrix4f()) {
                    @Override public boolean isVisible(net.minecraft.world.phys.AABB box) { return true; }
                };
                var stand = new ArmorStand(EntityTypes.ARMOR_STAND, client.level);
                stand.setPos(client.player.position());
                var renderer = client.getEntityRenderDispatcher().getRenderer(stand);
                var head = Items.STONE.getDefaultInstance(); var tag = new CompoundTag();
                tag.putString("accessory_owner", client.player.getGameProfile().name());
                head.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); stand.setItemSlot(EquipmentSlot.HEAD, head);
                ConfigManager.get().render.hideFirstPersonCosmetics = true; client.options.setCameraType(CameraType.FIRST_PERSON);
                check(!renderer.shouldRender(stand, frustum, stand.getX(), stand.getY(), stand.getZ()), "Own cosmetic visible in first person");
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                check(renderer.shouldRender(stand, frustum, stand.getX(), stand.getY(), stand.getZ()), "Own cosmetic hidden in third person");
                client.options.setCameraType(CameraType.FIRST_PERSON); ConfigManager.get().render.hideFirstPersonCosmetics = false;
                check(renderer.shouldRender(stand, frustum, stand.getX(), stand.getY(), stand.getZ()), "Disabled option still hid cosmetics");
                ConfigManager.get().render.hideFirstPersonCosmetics = true;
                stand.setItemSlot(EquipmentSlot.HEAD, Items.STONE.getDefaultInstance());
                check(renderer.shouldRender(stand, frustum, stand.getX(), stand.getY(), stand.getZ()), "Unrelated stand was hidden");
                check(!renderer.shouldRender(stand, frustum, stand.getX() + 10000, stand.getY(), stand.getZ()), "Vanilla distance rejection was overwritten");
                var display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, client.level); display.setPos(client.player.position());
                var displayRenderer = client.getEntityRenderDispatcher().getRenderer(display);
                for (int i = 0; i < 40000; i++) displayRenderer.shouldRender(display, frustum, display.getX(), display.getY(), display.getZ());
                var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                long id = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(id);
                for (int i = 0; i < 30000; i++) displayRenderer.shouldRender(display, frustum, display.getX(), display.getY(), display.getZ());
                long bytes = bean.getThreadAllocatedBytes(id) - before;
                check(bytes < 64000, "Visibility callback still allocates per entity: " + bytes);
                System.out.println("FH visibility: cosmetics/camera/options/vanilla rejection passed; 30000 Item Display visibility calls=" + bytes + " bytes");
            });
        } finally { context.runOnClient(c -> { ConfigManager.get().render.hideFirstPersonCosmetics = old; c.options.setCameraType(camera); }); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
