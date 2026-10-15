package ru.wilyfox.client.utility;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.keybinds.KeyBinds;

public final class ClickerHoldClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot=context.computeOnClient(client->ConfigManager.snapshot());
        boolean pause=context.computeOnClient(client->client.options.pauseOnLostFocus);
        boolean focus=context.computeOnClient(client->client.getWindow().isFocused());
        String modeKey = context.computeOnClient(client -> KeyBinds.CLICKER_MODE.saveString());
        try(var world=context.worldBuilder().create()) {
            context.runOnClient(client->{
                client.gui.setScreen(null);KeyMapping.releaseAll();activation().reset();
                client.options.pauseOnLostFocus=false;set(client,"pause",false);set(client.getWindow(),"focused",true);
                KeyBinds.CLICKER_MODE.setKey(InputConstants.getKey("key.keyboard.f10")); KeyMapping.resetMapping();
                check(java.util.Arrays.asList(client.options.keyMappings).contains(KeyBinds.CLICKER_MODE), "Mode bind is missing from native Controls");
                check(KeyBinds.CLICKER_MODE.getDefaultKey().getValue() == -1, "Mode bind unexpectedly takes an existing key by default");
                var config=ConfigManager.get().clicker;config.activation=ClickerConfig.Activation.TOGGLE;config.actionMode=ClickerConfig.ActionMode.CLICK;config.useItem=false;
                switchMode(); Clicker.tick(client);
                check(config.actionMode == ClickerConfig.ActionMode.HOLD && !client.options.keyAttack.isDown(), "Mode bind activated the clicker instead of only selecting HOLD");
                checkSavedMode(ClickerConfig.ActionMode.HOLD);
                press();Clicker.tick(client);check(client.options.keyAttack.isDown(),"Hold did not press attack");
                check(client.options.keyAttack.consumeClick(),"Initial held press did not start the action");
                switchMode(); Clicker.tick(client);
                check(config.actionMode == ClickerConfig.ActionMode.CLICK && !client.options.keyAttack.isDown()
                        && client.options.keyAttack.consumeClick(), "Mode bind left attack held or stopped the active clicker");
                check(config.activation == ClickerConfig.Activation.TOGGLE, "Action mode bind changed the activation method");
                checkSavedMode(ClickerConfig.ActionMode.CLICK);
                switchMode(); Clicker.tick(client);
                check(client.options.keyAttack.isDown() && client.options.keyAttack.consumeClick(), "Returning to HOLD did not resume the held action");
                for(int i=0;i<5;i++)Clicker.tick(client);
                check(client.options.keyAttack.isDown()&&!client.options.keyAttack.consumeClick(),"Hold generated repeated clicks");
                config.useItem=true;Clicker.tick(client);
                check(!client.options.keyAttack.isDown()&&client.options.keyUse.isDown(),"Switching buttons left attack held");
                client.player.getAbilities().instabuild=true;client.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BOW));
                set(client,"rightClickDelay",0);handleKeys(client);
                check(client.player.isUsingItem(),"Vanilla did not start charging the bow");
                for(int i=0;i<5;i++){Clicker.tick(client);handleKeys(client);}
                check(client.player.isUsingItem()&&!client.options.keyUse.consumeClick(),"Bow was not continuously held");
                press();Clicker.tick(client);handleKeys(client);
                check(!client.options.keyUse.isDown()&&!client.player.isUsingItem(),"Disabling hold did not release bow");
                press();Clicker.tick(client);client.gui.setScreen(new ChatScreen("",false));switchMode();Clicker.tick(client);
                check(config.actionMode == ClickerConfig.ActionMode.HOLD, "Chat input changed clicker mode");
                check(!client.options.keyUse.isDown(),"Menu kept the action held");client.gui.setScreen(null);
                Clicker.tick(client);check(config.actionMode == ClickerConfig.ActionMode.HOLD, "A key pressed in chat leaked into gameplay");
                set(client,"pause",true);switchMode();Clicker.tick(client);check(!client.options.keyUse.isDown(),"Pause kept button held");
                check(config.actionMode == ClickerConfig.ActionMode.HOLD, "Paused mode bind changed configuration");set(client,"pause",false);
                Clicker.tick(client);set(client.getWindow(),"focused",false);Clicker.tick(client);
                check(client.options.keyUse.isDown(),"Background hold failed with pause disabled");
                client.options.pauseOnLostFocus=true;Clicker.tick(client);check(!client.options.keyUse.isDown(),"Focus-loss pause did not release button");
                set(client.getWindow(),"focused",true);Clicker.tick(client);config.actionMode=ClickerConfig.ActionMode.CLICK;Clicker.tick(client);
                check(!client.options.keyUse.isDown(),"Switch to repeated clicks left hold latched");
                config.actionMode=ClickerConfig.ActionMode.HOLD;config.activation=ClickerConfig.Activation.HOLD;KeyBinds.AUTO_ATTACK.setDown(true);Clicker.tick(client);
                check(client.options.keyUse.isDown(),"Hold activation and held action cannot be combined");KeyBinds.AUTO_ATTACK.setDown(false);Clicker.tick(client);
                check(!client.options.keyUse.isDown(),"Releasing activation bind did not release action");
                KeyBinds.AUTO_ATTACK.setDown(true);Clicker.tick(client);var player=client.player;client.player=null;Clicker.tick(client);client.player=player;KeyBinds.AUTO_ATTACK.setDown(false);
                check(!client.options.keyUse.isDown(),"Disconnect left the button held");
                System.out.println("Clicker mode bind: native registration, inactive selection, live HOLD/CLICK transitions, persistence and chat/pause guards passed");
            });
        }finally{context.runOnClient(client->{client.gui.setScreen(null);activation().reset();Clicker.tick(client);KeyMapping.releaseAll();KeyBinds.CLICKER_MODE.setKey(InputConstants.getKey(modeKey));KeyMapping.resetMapping();ConfigManager.restoreSnapshot(snapshot);client.options.pauseOnLostFocus=pause;set(client.getWindow(),"focused",focus);});}
    }
    private static ClickerActivation activation(){try{var f=Clicker.class.getDeclaredField("activation");f.setAccessible(true);return (ClickerActivation)f.get(null);}catch(Exception e){throw new AssertionError(e);}}
    private static void press(){KeyMapping.click(InputConstants.getKey(KeyBinds.AUTO_ATTACK.saveString()));}
    private static void switchMode(){KeyMapping.click(InputConstants.getKey(KeyBinds.CLICKER_MODE.saveString()));}
    private static void checkSavedMode(ClickerConfig.ActionMode expected) {
        try {
            var file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("froghelper.json");
            var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
            check(saved.getAsJsonObject("clicker").get("actionMode").getAsString().equals(expected.name()), "Selected action mode was not saved");
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    private static void handleKeys(Minecraft client){try{var f=Minecraft.class.getDeclaredMethod("handleKeybinds");f.setAccessible(true);f.invoke(client);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object target,String field,Object value){try{var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
