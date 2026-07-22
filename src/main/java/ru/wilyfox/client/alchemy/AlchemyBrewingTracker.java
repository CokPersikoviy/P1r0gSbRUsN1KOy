package ru.wilyfox.client.alchemy;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.sounds.SoundEvents;
import ru.wilyfox.bridge.BossHealthOverlayAccessor;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.PopUpManager;
import ru.wilyfox.client.popup.PopUpRequest;
import ru.wilyfox.client.popup.PopUpSeverity;
import ru.wilyfox.client.popup.PopUpSource;
import ru.wilyfox.client.recipe.PotionRecipeTracker;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AlchemyBrewingTracker {
    private static final Pattern BREWING_TIME = Pattern.compile("\u0412\u0440\u0435\u043c\u044f: ([.\\d]+)\u0441");
    private static final Set<Integer> announcedTimings = new HashSet<>();

    private static boolean registered;
    private static boolean brewingActive;
    private static int ticks;
    private static long recipeRevision = -1L;
    private static double lastTime = Double.NaN;

    private AlchemyBrewingTracker() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(AlchemyBrewingTracker::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (++ticks % 10 != 0 || minecraft.player == null || minecraft.gui == null) {
            return;
        }

        PotionRecipeTracker tracker = PotionRecipeTracker.getInstance();
        if (tracker.getRevision() != recipeRevision) {
            recipeRevision = tracker.getRevision();
            announcedTimings.clear();
            brewingActive = false;
            lastTime = Double.NaN;
        }

        Double time = findBrewingTime(minecraft);
        if (time == null) {
            brewingActive = false;
            lastTime = Double.NaN;
            return;
        }

        if (!brewingActive || (!Double.isNaN(lastTime) && time > lastTime + 0.5)) {
            announcedTimings.clear();
            brewingActive = true;
        }
        lastTime = time;

        if (!ConfigManager.get().alchemy.recipeActionAlerts || !tracker.hasRecipe()) {
            return;
        }

        double delaySeconds = ConfigManager.get().alchemy.recipeActionLeadMillis / 1_000.0;
        for (PotionRecipeTracker.RecipeAction action : tracker.getActions()) {
            if (announcedTimings.contains(action.timingSeconds())) {
                continue;
            }
            if (time >= action.timingSeconds() - delaySeconds && time <= action.timingSeconds()) {
                announce(minecraft, action);
                announcedTimings.add(action.timingSeconds());
                break;
            }
        }
    }

    private static Double findBrewingTime(Minecraft minecraft) {
        if (!(minecraft.gui.getBossOverlay() instanceof BossHealthOverlayAccessor accessor)) {
            return null;
        }

        for (LerpingBossEvent event : accessor.froghelper$getEvents()) {
            Matcher matcher = BREWING_TIME.matcher(event.getName().getString().trim());
            if (!matcher.find()) {
                continue;
            }
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void announce(Minecraft minecraft, PotionRecipeTracker.RecipeAction action) {
        PopUpManager.getInstance().publish(PopUpRequest.of(
                PopUpSource.ALCHEMY_ACTION,
                "\u0410\u043b\u0445\u0438\u043c\u0438\u044f",
                action.message(),
                PopUpSeverity.WARNING
        ));

        if (ConfigManager.get().alchemy.recipeActionSound) {
            for (int i = 0; i < 5; i++) {
                minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
    }

    private static void reset() {
        announcedTimings.clear();
        brewingActive = false;
        ticks = 0;
        recipeRevision = -1L;
        lastTime = Double.NaN;
    }
}
