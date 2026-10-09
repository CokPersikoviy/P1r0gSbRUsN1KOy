package ru.wilyfox.client.audio;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.popup.*;

import java.util.ArrayList;
import java.util.List;

public final class UiSoundsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        double uiVolume = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.UI).get());
        double masterVolume = context.computeOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).get());
        boolean alchemySound = ConfigManager.get().alchemy.recipeActionSound;
        boolean privateMessages = ConfigManager.get().popUps.privateMessageEvent;
        var played = new ArrayList<String>();
        SoundEventListener listener = (instance, event, distance) -> {
            if (instance.getIdentifier().getNamespace().equals("froghelper")) {
                expect(instance.getSource() == SoundSource.UI, "A mod sample bypassed Minecraft UI volume");
                played.add(instance.getIdentifier().toString());
            }
        };
        context.runOnClient(client -> client.getSoundManager().addListener(listener));
        try {
            context.runOnClient(client -> {
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(0.0);
                var manager = client.getSoundManager();
                for (UiSound sound : UiSound.values()) {
                    expect(manager.getSoundEvent(Identifier.parse(sound.eventId())) != null, "Missing event: " + sound);
                    var resource = client.getResourceManager().getResource(Identifier.parse(sound.resourceId())).orElseThrow();
                    try (var stream = resource.open()) {
                        expect(new String(stream.readNBytes(4), java.nio.charset.StandardCharsets.US_ASCII).equals("OggS"),
                                "Missing or invalid OGG sample: " + sound);
                    } catch (java.io.IOException failure) { throw new AssertionError(failure); }
                    var instance = UiSounds.createInstance(sound);
                    expect(instance.getSource() == SoundSource.UI, "Incorrect volume category: " + sound);
                    expect(manager.play(instance) != SoundEngine.PlayResult.STARTED, "Muted UI started audibly: " + sound);
                }
                expect(client.options.getFinalSoundSourceVolume(SoundSource.UI) == 0, "UI mute was not applied");
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(.5);
                expect(client.options.getFinalSoundSourceVolume(SoundSource.UI) > 0, "UI volume did not restore");
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                expect(client.options.getFinalSoundSourceVolume(SoundSource.UI) == 0, "Master mute did not include UI sounds");
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(1.0);
                expect(manager.play(UiSounds.createInstance(UiSound.CLICK)) == SoundEngine.PlayResult.STARTED,
                        "Unmuted UI sample failed to play");

                played.clear();
                ConfigManager.get().alchemy.recipeActionSound = false;
                UiSounds.notification(PopUpRequest.of(PopUpSource.ALCHEMY_ACTION, "Alchemy", "", PopUpSeverity.WARNING));
                expect(played.isEmpty(), "Disabled alchemy sound still played");
                ConfigManager.get().alchemy.recipeActionSound = true;
                UiSounds.notification(PopUpRequest.of(PopUpSource.ALCHEMY_ACTION, "Alchemy", "", PopUpSeverity.WARNING));
                expect(played.equals(List.of(UiSound.WARNING.eventId())), "Alchemy did not use one custom warning");
                played.clear();
                ConfigManager.get().popUps.privateMessageEvent = true;
                PopUpManager.getInstance().publish(PopUpRequest.of(PopUpSource.PRIVATE_MESSAGE, "Private", "", PopUpSeverity.INFO));
                for (int i = 0; i < 10; i++) PopUpManager.getInstance().getVisibleNotifications(3);
                expect(played.equals(List.of(UiSound.PRIVATE_MESSAGE.eventId())), "Popup rendering duplicated its sound");
                played.clear();
            });
            var screen = context.computeOnClient(client -> new SoundScreen());
            context.setScreen(() -> screen);
            context.runOnClient(client -> {
                client.resizeGui();
                client.resizeGui();
                expect(played.equals(List.of(UiSound.OPEN_CLOSE.eventId())), "Resize duplicated the opening cue");
            });
            context.setScreen(() -> null);
            context.runOnClient(client -> expect(played.equals(List.of(UiSound.OPEN_CLOSE.eventId(), UiSound.OPEN_CLOSE.eventId())),
                    "Screen close did not play exactly once"));
            System.out.println("UI audio: all 11 OGG events loaded; UI/master mute, alchemy toggle and screen lifecycle verified");
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                client.getSoundManager().removeListener(listener);
                client.options.getSoundSourceOptionInstance(SoundSource.UI).set(uiVolume);
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(masterVolume);
                ConfigManager.get().alchemy.recipeActionSound = alchemySound;
                ConfigManager.get().popUps.privateMessageEvent = privateMessages;
                PopUpManager.getInstance().removeSources(PopUpSource.PRIVATE_MESSAGE);
            });
        }
    }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class SoundScreen extends UiSoundScreen {
        SoundScreen() { super(Component.literal("UI audio test")); }
    }
}
