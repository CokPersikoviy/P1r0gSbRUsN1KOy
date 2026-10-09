package ru.wilyfox.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.PopUpRequest;
import ru.wilyfox.client.popup.PopUpSource;

import java.util.EnumMap;

public final class UiSounds {
    private static final UiSoundLimiter SCROLL_LIMIT = new UiSoundLimiter(40);
    private static final EnumMap<UiSound, SoundEvent> EVENTS = new EnumMap<>(UiSound.class);
    private static final EnumMap<UiSound, UiSoundLimiter> ALERT_LIMITS = new EnumMap<>(UiSound.class);
    private static SimpleSoundInstance lastScroll;
    private static SimpleSoundInstance lastInteraction, lastNotification;
    private static boolean pendingScroll;

    private UiSounds() {}

    public static void click() { play(UiSound.CLICK); }
    public static void toggle() { play(UiSound.TOGGLE); }
    public static void openClose() { play(UiSound.OPEN_CLOSE); }

    public static void hover() {
        if (SCROLL_LIMIT.allow(System.nanoTime())) playNow(UiSound.SCROLL);
    }

    public static void notification(PopUpRequest request) {
        if (PopUpSource.ALCHEMY_ACTION.equals(request.source()) && !ConfigManager.get().alchemy.recipeActionSound) return;
        play(UiSound.forNotification(request.source(), request.severity()));
    }

    public static void play(UiSound sound) {
        var client = Minecraft.getInstance();
        if (client == null) return;
        if (!client.isSameThread()) {
            client.execute(() -> play(sound));
            return;
        }
        if (isNotification(sound) && !ALERT_LIMITS.computeIfAbsent(sound, ignored -> new UiSoundLimiter(4)).allow(System.nanoTime())) return;
        playNow(sound);
    }

    public static SimpleSoundInstance createInstance(UiSound sound) {
        var event = EVENTS.computeIfAbsent(sound,
                key -> SoundEvent.createVariableRangeEvent(Identifier.parse(key.eventId())));
        // forUI uses SoundSource.UI: Minecraft applies both its UI and master volume sliders.
        return SimpleSoundInstance.forUI(event, 1.0f, .55f);
    }

    private static boolean isNotification(UiSound sound) {
        return sound != UiSound.SCROLL && sound != UiSound.CLICK && sound != UiSound.TOGGLE && sound != UiSound.OPEN_CLOSE;
    }

    private static void playNow(UiSound sound) {
        var client = Minecraft.getInstance();
        if (client == null) return;
        var manager = client.getSoundManager();
        var instance = createInstance(sound);
        // Separate ticks, interaction feedback and alerts, with at most one voice in each group.
        if (sound == UiSound.SCROLL) {
            if (lastScroll != null) manager.stop(lastScroll);
            lastScroll = instance;
        } else if (isNotification(sound)) {
            if (lastNotification != null) manager.stop(lastNotification);
            lastNotification = instance;
        } else {
            if (lastInteraction != null) manager.stop(lastInteraction);
            lastInteraction = instance;
        }
        manager.play(instance);
    }

    public static void scroll() {
        pendingScroll = true;
        update();
    }

    public static void cancelScroll() { pendingScroll = false; }

    public static void update() {
        if (!pendingScroll) return;
        if (!SCROLL_LIMIT.allow(System.nanoTime())) return;
        pendingScroll = false;
        playNow(UiSound.SCROLL);
    }
}
