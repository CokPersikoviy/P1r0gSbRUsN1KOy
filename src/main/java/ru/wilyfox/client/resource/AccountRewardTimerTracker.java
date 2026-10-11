package ru.wilyfox.client.resource;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import ru.wilyfox.client.hud.config.ResourceTimersConfig;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.*;
import ru.wilyfox.client.profiler.ModProfiler;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static ru.wilyfox.FrogHelper.LOGGER;

/** Event-indexed loaded stands, sampled once a second; independent of highlights and HUD visibility. */
public final class AccountRewardTimerTracker {
    public static final AccountRewardTimerTracker INSTANCE = new AccountRewardTimerTracker();
    private final ResourceTimerStore store = new ResourceTimerStore();
    private final ResourceTimerStore creditsStore = new ResourceTimerStore();
    private final Map<ArmorStand, String> stands = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FrogHelper-reward-timer-save"); thread.setDaemon(true); return thread;
    });
    private Path path;
    private Path creditsPath;
    private Object creditsMenu;
    private String creditsText;
    private long nextCheckNanos;
    private boolean registered;
    private AccountRewardTimerTracker() {}
    public void register() {
        if (registered) return;
        registered = true;
        path = FabricLoader.getInstance().getConfigDir().resolve("froghelper-roulette-timers.json");
        try { store.load(path); } catch (Exception error) { LOGGER.warn("Cannot load roulette timer", error); }
        creditsPath = FabricLoader.getInstance().getConfigDir().resolve("froghelper-daily-credits-timers.json");
        try { creditsStore.load(creditsPath); } catch (Exception error) { LOGGER.warn("Cannot load daily credits timer", error); }
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ArmorStand stand) stands.put(stand, "");
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof ArmorStand stand) stands.remove(stand);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            stands.clear(); creditsMenu = null; creditsText = null; nextCheckNanos = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            writer.shutdown();
            try { if (!writer.awaitTermination(2, TimeUnit.SECONDS)) LOGGER.warn("Reward timer save still pending at shutdown"); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
    }
    private static boolean onDw(Minecraft client) {
        var server = client.getCurrentServer();
        return client.player != null && client.level != null && client.getConnection() != null && server != null
                && server.ip.toLowerCase(java.util.Locale.ROOT).contains("diamondworld");
    }
    public ResourceTimerStore.Entry current() {
        return current(store);
    }
    public ResourceTimerStore.Entry currentCredits() {
        return current(creditsStore);
    }
    private ResourceTimerStore.Entry current(ResourceTimerStore selected) {
        var client = Minecraft.getInstance();
        return onDw(client) ? selected.entry(client.player.getGameProfile().name()) : null;
    }
    private void tick(Minecraft client) {
        long monotonic = System.nanoTime();
        if (monotonic < nextCheckNanos || !onDw(client)) return;
        nextCheckNanos = monotonic + TimeUnit.SECONDS.toNanos(1);
        try (var profile = ModProfiler.getInstance().scope("rewardTimers/tick")) {
            long now = System.currentTimeMillis();
            String owner = client.player.getGameProfile().name();
            boolean dirty = false;
            var iterator = stands.entrySet().iterator();
            while (iterator.hasNext()) {
                var observed = iterator.next(); var stand = observed.getKey();
                if (stand.isRemoved() || stand.level() != client.level) { iterator.remove(); continue; }
                if (stand.distanceToSqr(client.player) > 64 * 64 || !stand.hasCustomName()) continue;
                String text = stand.getCustomName().getString();
                if (text.equals(observed.getValue())) continue;
                observed.setValue(text);
                ResourceCountdown countdown = ResourceCountdown.parse(text);
                if (countdown != null) dirty |= store.observe(owner, countdown, now);
            }
            boolean creditsDirty = observeCredits(client, owner, now);
            dirty |= remind(client, store, owner, now, ConfigManager.get().resourceTimers, "roulette.ready", "Roulette ready",
                    "Рулетка доступна: можно сделать следующий прокрут.");
            creditsDirty |= remind(client, creditsStore, owner, now, ConfigManager.get().dailyCreditsTimer, "daily_credits.ready", "Daily credits ready",
                    "Таймер ежедневных кредитов истёк. Проверьте меню ежедневной награды.");
            if (dirty) save(store, path);
            if (creditsDirty) save(creditsStore, creditsPath);
        }
    }
    private boolean observeCredits(Minecraft client, String owner, long now) {
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            creditsMenu = null; creditsText = null; return false;
        }
        var menu = screen.getMenu();
        if (creditsMenu != menu) { creditsMenu = menu; creditsText = null; }
        // Read only server-authored lore; building tooltips invokes other mods and allocates far more.
        for (var slot : menu.slots) {
            var lore = slot.getItem().get(DataComponents.LORE);
            if (lore == null) continue;
            for (var line : lore.lines()) {
                String text = line.getString();
                ResourceCountdown countdown = ResourceCountdown.parseCredits(text);
                if (countdown == null) continue;
                if (text.equals(creditsText)) return false;
                creditsText = text;
                return creditsStore.observe(owner, countdown, now);
            }
        }
        // Allow a new countdown even if the item disappears briefly after claiming a reward.
        creditsText = null;
        return false;
    }
    private boolean remind(Minecraft client, ResourceTimerStore selected, String owner, long now, ResourceTimersConfig config,
                           String source, String title, String message) {
        if (!config.notifications || selected.reminders(owner, now, config.reminderMinutes).isEmpty()) return false;
        client.gui.hud.getChat().addClientSystemMessage(Component.literal("[FH] " + message).withColor(0xFFAA00));
        PopUpManager.getInstance().publish(PopUpRequest.of(source, title, message, PopUpSeverity.SUCCESS));
        return true;
    }
    private void save(ResourceTimerStore selected, Path file) {
        var snapshot = selected.snapshot();
        writer.execute(() -> {
            try { ResourceTimerStore.save(file, snapshot); }
            catch (Exception error) { LOGGER.warn("Cannot save account reward timer", error); }
        });
    }
}
