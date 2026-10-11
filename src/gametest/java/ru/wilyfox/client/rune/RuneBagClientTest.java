package ru.wilyfox.client.rune;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.ActiveRunesWidget;

import java.util.List;

public final class RuneBagClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                ConfigManager.get().render.nativeRenderer = false;
                ConfigManager.get().render.lightweightHud = false;
                ConfigManager.get().render.widgetChrome = ru.wilyfox.client.hud.config.WidgetChrome.FROST;
                var contents = new SimpleContainer(54);
                var menu = ChestMenu.sixRows(1, client.player.getInventory(), contents);
                contents.setItem(0, paper("Сет рун №1", "Используется"));
                var names = List.of("Ветер II", "Железо II", "Валькирия II", "Добытчик II");
                int[] slots = {11, 13, 15, 27};
                for (int i = 0; i < slots.length; i++) contents.setItem(slots[i], paper(names.get(i), "Сила: +5%"));
                String locked = "§cРазблокировать слот для шахтёрской руны";
                contents.setItem(35, paper(locked, "Сила: +999%", "Пассивная способность"));
                check(RuneSetEffectOverlay.isRuneBagLoaded(menu), "Bag selector was not loaded");
                check(RuneSetEffectOverlay.collectActiveRuneNames(menu).equals(names), "Locked mining slot appeared as an equipped rune");
                check(RuneSetEffectOverlay.collect(menu).lines().equals(List.of("Сила: +20%")), "Slot control polluted set stats or abilities");
                var store = new ActiveRunesStore(); store.replace(RuneSetEffectOverlay.collectActiveRuneNames(menu));
                var widget = new ActiveRunesWidget(20, 20, HudLayer.CONTENT, store);
                check(widget.getWidth() < client.font.width(locked), "Slot instruction still inflated the rune widget");

                contents.setItem(35, paper("Шахтёр III", "Дроп: 1 к 10"));
                var equipped = RuneSetEffectOverlay.collectActiveRuneNames(menu);
                check(equipped.size() == 5 && equipped.getLast().equals("Шахтёр III"), "Real mining rune was removed");
                check(RuneSetEffectOverlay.collect(menu).lines().contains("Дроп: 1 к 10"), "Real mining rune effect was removed");
                contents.setItem(35, paper("Empty slot", "Сила: +999%"));
                check(RuneSetEffectOverlay.collectActiveRuneNames(menu).equals(names), "Empty slot appeared as a rune");
                for (int slot : slots) contents.setItem(slot, ItemStack.EMPTY);
                check(RuneSetEffectOverlay.collectActiveRuneNames(menu).isEmpty(), "Removing all runes retained a slot placeholder");
                store.replace(RuneSetEffectOverlay.collectActiveRuneNames(menu));
                check(store.isEmpty(), "Empty bag did not clear active runes");
                for (int i = 0; i < slots.length; i++) contents.setItem(slots[i], paper(names.get(i), "Сила: +5%"));
                ConfigManager.get().runesBag.setEffectOverlayScalePercent = 100;
                client.gui.setScreen(new ContainerScreen(menu, client.player.getInventory(), Component.literal("\uE962")));
            });
            for (int scale : new int[]{100, 150, 50, 300}) {
                context.runOnClient(client -> ConfigManager.get().runesBag.setEffectOverlayScalePercent = scale);
                context.waitTicks(3);
                context.takeScreenshot("rune-set-scale-" + scale);
            }
            context.runOnClient(client -> {
                var fish = new ItemStack(Items.COD, 4);
                fish.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Pet experience: 125"))));
                client.player.getInventory().setItem(9, fish);
                ConfigManager.get().fishing.showPetExperienceOverlay = true;
                client.gui.setScreen(new InventoryScreen(client.player));
                check(PetExperienceOverlay.collect(client.player.inventoryMenu).lines().getFirst().equals("Всего: 500"),
                        "Scaled experience plate lost its inventory data");
            });
            for (int scale : new int[]{100, 150, 50, 300}) {
                context.runOnClient(client -> ConfigManager.get().fishing.petExperienceOverlayScalePercent = scale);
                context.waitTicks(3);
                context.takeScreenshot("fish-experience-scale-" + scale);
            }
            context.setScreen(() -> null);
            context.waitTicks(3);
            context.takeScreenshot("menu-backdrop-world");
            context.setScreen(ru.wilyfox.client.quickaccess.QuickAccessScreen::new);
            context.waitTicks(3);
            context.takeScreenshot("quick-access-clear-surroundings");
        } finally {
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                ConfigManager.restoreSnapshot(original);
            });
        }
    }
    private static ItemStack paper(String name, String... lore) {
        var item = new ItemStack(Items.PAPER);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        item.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(lore).map(Component::literal)
                .map(component -> (Component) component).toList()));
        return item;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
