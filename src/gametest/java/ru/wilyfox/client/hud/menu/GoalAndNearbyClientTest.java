package ru.wilyfox.client.hud.menu;

import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.highlight.*;
import ru.wilyfox.client.calculator.*;
import ru.wilyfox.client.hud.*;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.hud.internal.HudFrameClock;
import ru.wilyfox.client.hud.menu.*;
import ru.wilyfox.client.hud.widget.*;
import ru.wilyfox.client.level.*;
import ru.wilyfox.client.protocol.SocialProtocolFixture;
import java.util.*;

public final class GoalAndNearbyClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot=context.computeOnClient(client->ConfigManager.snapshot());
        int scale=context.computeOnClient(client->client.options.guiScale().get());
        int width=context.computeOnClient(client->client.getWindow().getScreenWidth()),height=context.computeOnClient(client->client.getWindow().getScreenHeight());
        try(var world=context.worldBuilder().create()) {
            context.runOnClient(client->{
                client.gui.setScreen(null);var config=ConfigManager.get();config.render.usefulItemsHighlight=false;
                config.mainLayout.widgets.clear();config.mainLayout.widgets.add(WidgetCatalog.NEARBY_WORMS.key());ConfigManager.layoutChanged();
                SocialProtocolFixture.location("PRISONEVO1",1,"shaft_12");invoke("clearCache");invoke("shouldWaitForWorldContext");set("waitingForWorldContext",false);
                var worm=new ArmorStand(client.level,client.player.getX()+2,client.player.getY(),client.player.getZ());worm.setId(2900101);worm.setCustomName(Component.literal("Червь"));client.level.addEntity(worm);
                invoke("refreshCacheIfNeeded",client);
                check(count(client,NearbyMineState.Kind.WORMS).count()==1,"Counter did not reuse entity detector with highlight disabled");
                check(UsefulWorldHighlightRenderHook.diagnosticSnapshot().cachedChunks()==0,"Worm-only counter scanned blocks");
                config.mainLayout.widgets.add(WidgetCatalog.NEARBY_BARRELS.key());config.mainLayout.widgets.add(WidgetCatalog.NEARBY_CRYSTALS.key());ConfigManager.layoutChanged();
                var barrel=client.player.blockPosition().offset(2,0,2);
                var barrelState=Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.INSTRUMENT,NoteBlockInstrument.FLUTE).setValue(NoteBlock.NOTE,19);
                client.getConnection().handleBlockUpdate(new ClientboundBlockUpdatePacket(barrel,barrelState));
                var crystal=new ArmorStand(client.level,client.player.getX()+4,client.player.getY(),client.player.getZ());crystal.setId(2900102);
                var model=new ItemStack(Items.LEATHER_HORSE_ARMOR);model.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(List.of(271f),List.of(),List.of(),List.of()));crystal.setItemSlot(EquipmentSlot.HEAD,model);client.level.addEntity(crystal);set("lastEntityScanTick",Long.MIN_VALUE);
                for(int i=0;i<35;i++)invoke("refreshCacheIfNeeded",client);
                check(count(client,NearbyMineState.Kind.BARRELS).count()==1,"Counter did not detect the real DW barrel model");
                check(count(client,NearbyMineState.Kind.CRYSTALS).count()==1,"Counter did not detect the golden crystal");
                client.getConnection().handleBlockUpdate(new ClientboundBlockUpdatePacket(barrel,Blocks.AIR.defaultBlockState()));invoke("refreshCacheIfNeeded",client);
                check(count(client,NearbyMineState.Kind.BARRELS).count()==0,"Destroyed barrel remained in counter");
                client.level.removeEntity(worm.getId(),Entity.RemovalReason.DISCARDED);set("lastEntityScanTick",Long.MIN_VALUE);invoke("refreshCacheIfNeeded",client);
                check(count(client,NearbyMineState.Kind.WORMS).count()==0,"Unloaded worm remained in counter");
                client.getConnection().handleBlockUpdate(new ClientboundBlockUpdatePacket(barrel,barrelState));invoke("refreshCacheIfNeeded",client);
                var newWorm=new ArmorStand(client.level,client.player.getX()+2,client.player.getY(),client.player.getZ());newWorm.setId(2900103);newWorm.setCustomName(Component.literal("Червь"));
                client.level.addEntity(newWorm);set("lastEntityScanTick",Long.MIN_VALUE);invoke("refreshCacheIfNeeded",client);
                var renderer=Client.getInstance().getHudRenderer();renderer.refreshLayout();
                client.gui.setScreen(new LevelCalculatorScreen(null));
                var screen=client.gui.screen();var inputs=screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();inputs.getFirst().setValue("125");inputs.get(1).setValue("150");
                var pin=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().contains("Pin goal")).findFirst().orElseThrow();
                screen.mouseClicked(new MouseButtonEvent(pin.getX()+3,pin.getY()+3,new MouseButtonInfo(0,0)),false);
                check(config.levelGoal.targetLevel==150&&ConfigManager.get().mainLayout.widgets.contains(WidgetCatalog.LEVEL_GOAL.key()),"Calculator did not pin a live goal");
                var store=(LevelProgressStore)get(Client.getInstance(),"levelProgressStore");store.updateFromLevelInfo(125,12000,2500000,15000,5000000,false);
                var goal=(LevelGoalWidget)renderer.getWidgets().stream().filter(w->w instanceof LevelGoalWidget).findFirst().orElseThrow();
                HudFrameClock.advance();check(goal.getWidth()>50&&goal.getHeight()>10,"Live goal has invalid bounds");
                var controls=WidgetSettingsSections.create(WidgetCatalog.LEVEL_GOAL);var discount=controls.stream().filter(c->c.getLabel().equals("Goal discount")).findFirst().orElseThrow();discount.setPosition(0,0);discount.setSize(260,22);
                config.levelGoal.targetLevel=500;discount.mouseClicked(200,10,1);check(config.levelGoal.discount==LevelCalculator.Discount.MAX,"Dynamic goal discount cannot cycle backwards to maximum");
                config.levelGoal.targetLevel=150;config.levelGoal.discount=LevelCalculator.Discount.NONE;
                config.render.nativeRenderer=true;client.options.guiScale().set(2);client.getWindow().setWindowed(1280,720);client.resizeGui();
                client.gui.setScreen(null);renderer.setEditing(false);renderer.refreshLayout();
                int i=0;for(var w:renderer.getWidgets())if(w instanceof NearbyMineWidget||w instanceof LevelGoalWidget){var a=(ru.wilyfox.client.hud.widget.AbstractWidget)w;a.setScreenAnchor(null);a.clearWidgetSnap();a.setStartX(20+(i%2)*270);a.setStartY(30+(i/2)*100);ConfigManager.captureWidgetLayout(a);i++;}
                ConfigManager.save();
            });
            context.waitTicks(3);context.takeScreenshot("level-goal-and-nearby-counters");
            context.runOnClient(client->{
                var before=ConfigManager.snapshot();ConfigManager.restoreSnapshot(before);check(ConfigManager.get().levelGoal.targetLevel==150,"Goal did not survive config reload");
                UsefulWorldHighlightRenderHook.onPlayerTeleport();for(var kind:NearbyMineState.Kind.values())check(count(client,kind).count()==0,"Teleport retained stale mine objects");
            });
        }finally{context.runOnClient(client->{client.gui.setScreen(null);ConfigManager.restoreSnapshot(snapshot);Client.getInstance().getHudRenderer().setEditing(false);Client.getInstance().getHudRenderer().refreshConfiguration();SocialProtocolFixture.clear();invoke("clearCache");client.options.guiScale().set(scale);client.getWindow().setWindowed(width,height);client.resizeGui();});}
    }
    private static NearbyMineState.Count count(Minecraft client,NearbyMineState.Kind kind){return NearbyMineState.count(kind,new NearbyMineState.Point(client.player.getX(),client.player.getY(),client.player.getZ()),32);}
    private static Object invoke(String name,Object... args){try{var m=UsefulWorldHighlightRenderHook.class.getDeclaredMethod(name,Arrays.stream(args).map(Object::getClass).toArray(Class[]::new));m.setAccessible(true);return m.invoke(null,args);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(String name,Object value){try{var f=UsefulWorldHighlightRenderHook.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}catch(Exception e){throw new AssertionError(e);}}
    private static Object get(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
