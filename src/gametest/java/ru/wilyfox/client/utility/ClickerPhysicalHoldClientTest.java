package ru.wilyfox.client.utility;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ClickerConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.keybinds.KeyBinds;

/** Compare vanilla held input with HOLD, then let an authoritative bow charge across real game ticks. */
public final class ClickerPhysicalHoldClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        var snapshot = context.computeOnClient(client -> ConfigManager.snapshot());
        boolean pause = context.computeOnClient(client -> client.options.pauseOnLostFocus);
        boolean focused = context.computeOnClient(client -> client.getWindow().isFocused());
        boolean grabbed = context.computeOnClient(client -> client.mouseHandler.isMouseGrabbed());
        boolean toggleAttack = context.computeOnClient(client -> client.options.toggleAttack().get());
        boolean toggleUse = context.computeOnClient(client -> client.options.toggleUse().get());
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode survival @a");
            context.waitTicks(3);
            BlockPos target = context.computeOnClient(client -> client.player.blockPosition().offset(2, 0, 0));
            world.getServer().runCommand("setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:obsidian");
            context.waitTicks(3);
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                KeyMapping.releaseAll();
                activation().reset();
                client.options.pauseOnLostFocus = false;
                client.options.toggleAttack().set(false);
                client.options.toggleUse().set(false);
                set(client, "pause", false);
                set(client.getWindow(), "focused", true);
                set(client.mouseHandler, "mouseGrabbed", true);
                var config = ConfigManager.get().clicker;
                config.activation = ClickerConfig.Activation.TOGGLE;
                config.actionMode = ClickerConfig.ActionMode.HOLD;
                config.useItem = false;
                Clicker.tick(client);
                client.hitResult = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
                set(client, "missTime", 0);

                // These are the same vanilla press/down states a physical mouse button supplies.
                KeyMapping.click(InputConstants.getKey(client.options.keyAttack.saveString()));
                client.options.keyAttack.setDown(true);
                float[] physical = new float[12];
                for (int tick = 0; tick < physical.length; tick++) {
                    handleKeys(client);
                    physical[tick] = (float) get(client.gameMode, "destroyProgress");
                }
                check(physical[physical.length - 1] > 0, "Vanilla mining fixture made no progress");
                client.options.keyAttack.setDown(false);
                handleKeys(client);
                check(!client.gameMode.isDestroying(), "Vanilla release did not stop mining");

                press();
                for (int tick = 0; tick < physical.length; tick++) {
                    Clicker.tick(client);
                    check((int) get(KeyMapping.class, client.options.keyAttack, "clickCount") == (tick == 0 ? 1 : 0),
                            "HOLD generated extra mouse presses on tick " + tick);
                    check(client.options.keyAttack.isDown(), "HOLD released attack between ticks");
                    handleKeys(client);
                    check(Float.compare((float) get(client.gameMode, "destroyProgress"), physical[tick]) == 0,
                            "HOLD mining differs from vanilla held input on tick " + tick);
                }
                press();
                Clicker.tick(client);
                handleKeys(client);
                check(!client.options.keyAttack.isDown() && !client.gameMode.isDestroying(), "Stopping HOLD did not release mining");
                System.out.println("HOLD mining: 12 ticks exactly match vanilla held mouse input; one initial press and no repeated clicks");
                client.options.toggleAttack().set(true);
                press();
                for (int tick = 0; tick < 6; tick++) {
                    Clicker.tick(client);
                    check(client.options.keyAttack.isDown(), "Vanilla Toggle Attack caused HOLD to alternate on tick " + tick);
                    handleKeys(client);
                }
                press();
                Clicker.tick(client);
                handleKeys(client);
                check(!client.options.keyAttack.isDown() && !client.gameMode.isDestroying(), "Vanilla Toggle Attack left HOLD latched after stopping");
                client.options.toggleAttack().set(false);
            });

            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
            world.getServer().runCommand("give @a minecraft:arrow 16");
            context.waitTicks(3);
            context.runOnClient(client -> {
                ConfigManager.get().clicker.useItem = true;
                client.options.toggleUse().set(true);
                set(client, "rightClickDelay", 0);
                press();
                Clicker.tick(client);
                handleKeys(client);
                check(client.player.isUsingItem(), "Authoritative bow fixture did not start charging");
            });
            context.waitTicks(12);
            int chargedTicks = context.computeOnClient(client -> {
                check(client.player.isUsingItem() && client.options.keyUse.isDown(), "Bow HOLD was interrupted across game ticks");
                int ticks = client.player.getTicksUsingItem();
                check(ticks >= 10, "Bow charge was restarted by repeated clicks: " + ticks);
                press();
                Clicker.tick(client);
                handleKeys(client);
                check(!client.options.keyUse.isDown() && !client.player.isUsingItem(), "Stopping HOLD did not release the charged bow");
                return ticks;
            });
            System.out.println("HOLD bow: uninterrupted charge for " + chargedTicks + " real ticks; release stops item use");
        } finally {
            context.runOnClient(client -> {
                activation().reset();
                Clicker.tick(client);
                KeyMapping.releaseAll();
                ConfigManager.restoreSnapshot(snapshot);
                client.options.pauseOnLostFocus = pause;
                client.options.toggleAttack().set(toggleAttack);
                client.options.toggleUse().set(toggleUse);
                set(client.getWindow(), "focused", focused);
                set(client.mouseHandler, "mouseGrabbed", grabbed);
            });
        }
    }

    private static ClickerActivation activation() {
        return (ClickerActivation) get(Clicker.class, null, "activation");
    }

    private static void press() {
        KeyMapping.click(InputConstants.getKey(KeyBinds.AUTO_ATTACK.saveString()));
    }

    private static void handleKeys(Minecraft client) {
        try {
            var method = Minecraft.class.getDeclaredMethod("handleKeybinds");
            method.setAccessible(true);
            method.invoke(client);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object get(Object target, String name) { return get(target.getClass(), target, name); }
    private static Object get(Class<?> type, Object target, String name) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void set(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
