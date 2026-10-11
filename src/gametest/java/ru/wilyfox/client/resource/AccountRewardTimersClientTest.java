package ru.wilyfox.client.resource;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import ru.wilyfox.client.Client;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.widget.RouletteTimerWidget;
import ru.wilyfox.client.hud.widget.DailyCreditsTimerWidget;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import ru.wilyfox.client.popup.PopUpManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class AccountRewardTimersClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        var tracker = AccountRewardTimerTracker.INSTANCE;
        var store = (ResourceTimerStore) field(tracker, AccountRewardTimerTracker.class, "store");
        var credits = (ResourceTimerStore) field(tracker, AccountRewardTimerTracker.class, "creditsStore");
        var renderer = Client.getInstance().getHudRenderer();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var listener = client.getConnection();
                Object server = field(listener, ClientCommonPacketListenerImpl.class, "serverData");
                Path path = (Path) field(tracker, AccountRewardTimerTracker.class, "path");
                Path creditsPath = (Path) field(tracker, AccountRewardTimerTracker.class, "creditsPath");
                ArmorStand stand = new ArmorStand(client.level, client.player.getX() + 2, client.player.getY(), client.player.getZ());
                stand.setId(2_910_000);
                try {
                    Path directory = Files.createTempDirectory("fh-roulette-test");
                    Path backup = directory.resolve("backup.json"), temp = directory.resolve("timers.json");
                    ResourceTimerStore.save(backup, store.snapshot());
                    Path creditsBackup = directory.resolve("credits-backup.json"), creditsTemp = directory.resolve("credits.json");
                    ResourceTimerStore.save(creditsBackup, credits.snapshot());
                    try {
                        Files.writeString(temp, "[]"); store.load(temp);
                        Files.writeString(creditsTemp, "[]"); credits.load(creditsTemp);
                        write(tracker, AccountRewardTimerTracker.class, "path", temp);
                        write(tracker, AccountRewardTimerTracker.class, "creditsPath", creditsTemp);
                        write(listener, ClientCommonPacketListenerImpl.class, "serverData", new ServerData("DW", "play.diamondworld.ru", ServerData.Type.OTHER));
                        ConfigManager.get().resourceTimers.notifications = true;
                        ConfigManager.get().resourceTimers.reminderMinutes = 10;
                        ConfigManager.get().render.usefulItemsHighlight = false;
                        ConfigManager.get().render.unclutterWidgets = false;
                        ConfigManager.get().mainLayout.widgets.clear(); ConfigManager.layoutChanged(); renderer.refreshLayout();
                        stand.setCustomName(Component.literal("До следующего прокрута: 22 ч. 27 мин."));
                        client.level.addEntity(stand); tick(tracker, client); flush(tracker);
                        String owner = client.player.getGameProfile().name();
                        var entry = tracker.current();
                        check(entry != null && Math.abs(entry.endsAt() - System.currentTimeMillis() - 80_820_000) < 2000, "Actual hologram was not captured with highlights disabled");
                        var loaded = new ResourceTimerStore(); loaded.load(temp);
                        check(loaded.entry(owner).endsAt() == entry.endsAt(), "Timer was not saved off the render thread");
                        long deadline = entry.endsAt(); tick(tracker, client);
                        check(tracker.current().endsAt() == deadline, "Unchanged hologram moved the deadline");
                        var widget = renderer.getWidgets().stream().filter(w -> w instanceof RouletteTimerWidget).findFirst().orElseThrow();
                        check(!widget.isVisible(), "Timer auto-enabled itself in Main");
                        renderer.addWidget(widget);
                        check(widget.isVisible(), "Added timer is hidden");
                        var state = new GuiRenderState(); widget.render(new GuiGraphicsExtractor(client, state, 0, 0), DeltaTracker.ZERO);
                        int[] texts = {0}; state.forEachText(text -> texts[0]++);
                        check(texts[0] == 2 && widget.getWidth() > 30 && widget.getHeight() > 15, "Widget did not render title and countdown");
                        renderer.setEditing(true); check(renderer.openWidgetSettings(widget), "Roulette lacks per-widget settings");
                        renderer.setSettings(false); renderer.setEditing(false); renderer.removeWidget(widget);
                        stand.setCustomName(Component.literal("До следующего прокрута: 0 мин.")); tick(tracker, client); flush(tracker);
                        check(tracker.current().lastNoticeAt() > 0, "Expired timer failed to notify without its widget");
                        long notice = tracker.current().lastNoticeAt();
                        client.level.removeEntity(stand.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        tick(tracker, client);
                        check(tracker.current().lastNoticeAt() == notice, "Unloading or rechecking spammed another notice");
                        loaded.load(temp); check(loaded.entry(owner).lastNoticeAt() == notice, "Notice deduplication was not persisted");
                        var next = new ArmorStand(client.level, client.player.getX() + 2, client.player.getY(), client.player.getZ());
                        next.setId(2_910_001); next.setCustomName(Component.literal("До следующего прокрута: 24 ч. 0 мин.")); client.level.addEntity(next);
                        tick(tracker, client); flush(tracker);
                        check(store.snapshot().size() == 1 && tracker.current().lastNoticeAt() == 0, "Respawn/new roll duplicated the account timer or kept the old notice");
                        client.level.removeEntity(next.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        // Actual server-authored item lore, without hovering or building a tooltip.
                        var contents = new SimpleContainer(27);
                        var menu = ChestMenu.threeRows(7, client.player.getInventory(), contents);
                        var item = new ItemStack(Items.CLOCK);
                        item.set(DataComponents.LORE, new ItemLore(java.util.List.of(
                                Component.literal("Провести 1 час на режимах"), Component.literal("§eНовые кредиты через: §f1д"))));
                        contents.setItem(13, item);
                        client.gui.setScreen(new ContainerScreen(menu, client.player.getInventory(), Component.literal("Ежедневная награда")));
                        ConfigManager.get().dailyCreditsTimer.notifications = true;
                        tick(tracker, client); flush(tracker);
                        long dayDeadline = tracker.currentCredits().endsAt();
                        check(Math.abs(dayDeadline - System.currentTimeMillis() - 86_400_000) < 2000, "Compact 1д item timer was not captured");
                        item.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal("Новые кредиты через: 23 ч. 11 мин."))));
                        tick(tracker, client); flush(tracker);
                        long precise = tracker.currentCredits().endsAt();
                        check(precise < dayDeadline && Math.abs(precise - System.currentTimeMillis() - 83_460_000) < 2000, "Minute-only item timer did not refine the deadline");
                        client.gui.setScreen(null); tick(tracker, client);
                        check(tracker.currentCredits().endsAt() == precise && tracker.current().endsAt() != precise, "Closing menu lost credits or overwrote roulette");
                        loaded.load(creditsTemp); check(loaded.entry(owner).endsAt() == precise, "Item deadline was not persisted");
                        var creditsWidget = renderer.getWidgets().stream().filter(w -> w instanceof DailyCreditsTimerWidget).findFirst().orElseThrow();
                        renderer.addWidget(creditsWidget); check(creditsWidget.isVisible(), "Daily credits widget is hidden after menu closes");
                        renderer.setEditing(true); check(renderer.openWidgetSettings(creditsWidget), "Daily credits lacks its own settings");
                        renderer.setEditing(false); renderer.setSettings(false); renderer.removeWidget(creditsWidget);
                        credits.observe(owner, new ResourceCountdown(0, 1000), System.currentTimeMillis());
                        ConfigManager.get().dailyCreditsTimer.notifications = false; tick(tracker, client);
                        check(credits.entry(owner).lastNoticeAt() == 0, "Disabled credits reminders still fired");
                        ConfigManager.get().dailyCreditsTimer.notifications = true; tick(tracker, client); flush(tracker);
                        check(credits.entry(owner).lastNoticeAt() > 0, "Credits reminder requires an open menu or enabled widget");
                        check(tracker.current().lastNoticeAt() == 0, "Credits notification affected roulette");
                        long creditsNotice = credits.entry(owner).lastNoticeAt(); tick(tracker, client);
                        check(credits.entry(owner).lastNoticeAt() == creditsNotice, "Credits reminder repeated immediately");
                        client.gui.setScreen(new ContainerScreen(menu, client.player.getInventory(), Component.literal("Ежедневная награда")));
                        item.set(DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal("Новые кредиты через: 1д"))));
                        tick(tracker, client); flush(tracker);
                        check(credits.entry(owner).lastNoticeAt() == 0 && credits.entry(owner).endsAt() > System.currentTimeMillis(), "New 1д reward cycle failed to replace Ready");
                        client.gui.setScreen(null);
                    } finally { flush(tracker); store.load(backup); credits.load(creditsBackup); }
                } catch (Exception error) { throw new AssertionError(error); }
                finally {
                    client.level.removeEntity(stand.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    write(listener, ClientCommonPacketListenerImpl.class, "serverData", server);
                    write(tracker, AccountRewardTimerTracker.class, "path", path);
                    write(tracker, AccountRewardTimerTracker.class, "creditsPath", creditsPath);
                    client.gui.setScreen(null);
                    PopUpManager.getInstance().removeSources("roulette.ready");
                    PopUpManager.getInstance().removeSources("daily_credits.ready");
                }
            });
        } finally {
            context.runOnClient(client -> { ConfigManager.restoreSnapshot(original); renderer.setSettings(false); renderer.setEditing(false); renderer.refreshLayout(); });
        }
    }
    private static void tick(AccountRewardTimerTracker tracker, Minecraft client) {
        write(tracker, AccountRewardTimerTracker.class, "nextCheckNanos", 0L);
        try { var method = AccountRewardTimerTracker.class.getDeclaredMethod("tick", Minecraft.class); method.setAccessible(true); method.invoke(tracker, client); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void flush(AccountRewardTimerTracker tracker) throws Exception {
        ((ExecutorService) field(tracker, AccountRewardTimerTracker.class, "writer")).submit(() -> {}).get(5, TimeUnit.SECONDS);
    }
    private static Object field(Object object, Class<?> type, String name) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void write(Object object, Class<?> type, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
