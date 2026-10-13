package ru.wilyfox.client.utility;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.ClickerConfig;
import ru.wilyfox.client.keybinds.KeyBinds;
import java.lang.reflect.Field;

/** Exercise the clicker's real tick and vanilla key handling while the window is unfocused. */
public final class ClickerFocusClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot = context.computeOnClient(client -> ConfigManager.snapshot());
        boolean pauseOption = context.computeOnClient(client -> client.options.pauseOnLostFocus);
        boolean focus = context.computeOnClient(client -> client.getWindow().isFocused());
        var activation = (ClickerActivation)get(Clicker.class, null, "activation");
        Object previous = get(ClickerActivation.class, activation, "previous"), toggled = get(ClickerActivation.class, activation, "toggled");
        Object enabled = get(Clicker.class, null, "autoAttackEnabled"), last = get(Clicker.class, null, "lastAttackTime");
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.gui.setScreen(null); KeyMapping.releaseAll(); activation.reset();
                var config = ConfigManager.get().clicker; config.activation = ClickerConfig.Activation.TOGGLE; config.useItem=false;
                set(client, "pause", false); set(client.getWindow(), "focused", true); client.options.pauseOnLostFocus=false;
                press(); Clicker.tick(client);
                check(client.options.keyAttack.consumeClick(), "Focused Toggle did not enqueue attack");
                check(!client.options.keyAttack.consumeClick(), "One tick queued more than one attack");
                KeyMapping.releaseAll(); set(client.getWindow(), "focused", false);
                ready(); Clicker.tick(client);
                check(!client.isWindowActive() && (boolean)get(Clicker.class,null,"autoAttackEnabled"), "Unfocused Toggle deactivated with pause disabled");
                client.hitResult = BlockHitResult.miss(new Vec3(0,80,0),Direction.DOWN,new BlockPos(0,80,0));
                client.player.swinging=false; set(client,"missTime",0); handleKeys(client);
                check(client.player.swinging && !client.options.keyAttack.consumeClick(), "Vanilla did not consume background attack");
                config.useItem=true; ready(); Clicker.tick(client); set(client,"rightClickDelay",0); handleKeys(client);
                check((int)get(Minecraft.class,client,"rightClickDelay")>0 && !client.options.keyUse.consumeClick(), "Vanilla did not consume background use-item click");
                client.options.pauseOnLostFocus=true; ready(); Clicker.tick(client);
                check(!client.options.keyUse.consumeClick() && !(boolean)get(Clicker.class,null,"autoAttackEnabled"), "Pause-on-focus-loss option ignored");
                set(client.getWindow(),"focused",true); ready(); Clicker.tick(client);
                check(client.options.keyUse.consumeClick(), "Focus restoration lost Toggle state");
                client.gui.setScreen(new ChatScreen("",false)); ready(); Clicker.tick(client);
                check(!client.options.keyUse.consumeClick(), "Clicker acted through chat"); client.gui.setScreen(null);
                set(client,"pause",true); ready(); Clicker.tick(client);
                check(!client.options.keyUse.consumeClick(), "Clicker acted while game paused"); set(client,"pause",false);
                config.activation=ClickerConfig.Activation.HOLD; KeyBinds.AUTO_ATTACK.setDown(true); ready(); Clicker.tick(client);
                check(client.options.keyUse.consumeClick(), "HOLD stopped working while held");
                KeyMapping.releaseAll(); set(client.getWindow(),"focused",false); client.options.pauseOnLostFocus=false;
                ready(); Clicker.tick(client); check(!client.options.keyUse.consumeClick(), "Released HOLD remained latched after focus loss");
                config.activation=ClickerConfig.Activation.TOGGLE; set(client.getWindow(),"focused",true); press(); ready(); Clicker.tick(client);
                check(client.options.keyUse.consumeClick(), "Toggle did not reactivate after mode change");
                var player=client.player; client.player=null; Clicker.tick(client); client.player=player; ready(); Clicker.tick(client);
                check(!client.options.keyUse.consumeClick(), "Disconnect left Toggle running");
                System.out.println("Clicker: unfocused attack/use, F3+P, menus, pause, HOLD release, focus restoration and disconnect passed");
            });
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null); KeyMapping.releaseAll(); client.options.pauseOnLostFocus=pauseOption;
                set(client.getWindow(),"focused",focus); ConfigManager.restoreSnapshot(snapshot);
                set(ClickerActivation.class,activation,"previous",previous); set(ClickerActivation.class,activation,"toggled",toggled);
                set(Clicker.class,null,"autoAttackEnabled",enabled); set(Clicker.class,null,"lastAttackTime",last);
            });
        }
    }
    private static void ready(){set(Clicker.class,null,"lastAttackTime",0L);}
    private static void press(){KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(KeyBinds.AUTO_ATTACK.saveString()));}
    private static void handleKeys(Minecraft client){try{var method=Minecraft.class.getDeclaredMethod("handleKeybinds");method.setAccessible(true);method.invoke(client);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static Object get(Class<?> type,Object object,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void set(Object object,String name,Object value){set(object.getClass(),object,name,value);}
    private static void set(Class<?> type,Object object,String name,Object value){try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
