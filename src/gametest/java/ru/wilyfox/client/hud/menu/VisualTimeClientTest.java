package ru.wilyfox.client.hud.menu;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import ru.wilyfox.client.hud.config.ConfigManager;

import java.nio.file.Files;
import java.util.ArrayList;

public final class VisualTimeClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var original = context.computeOnClient(client -> ConfigManager.snapshot());
        try (var world = context.worldBuilder().create()) {
            var number = context.computeOnClient(client -> {
                var config = ConfigManager.get().visuals;
                config.timeEnabled = true;
                config.time = 6000;
                var items = new ArrayList<SettingsComponent>();
                HudSettingsVisualsSection.populate(items);
                var field = (DragNumberSettingsComponent) items.stream()
                        .filter(item -> item.getLabel().equals("Time (ticks)")).findFirst().orElseThrow();
                field.setSize(200, 22);
                expect(field.mouseClicked(180, 10, 0), "Time field did not capture the mouse");
                field.mouseDragged(660, 10, 0, 480, 0);
                expect(config.time == 18000, "A practical drag could not change noon to midnight");
                expect(Math.floorMod(client.level.getOverworldClockTime(), 24000L) == 18000,
                        "Time field did not update the actual client clock");
                field.mouseReleased(660, 10, 0);
                return field;
            });
            context.getInput().holdShift();
            context.runOnClient(client -> {
                number.mouseClicked(180, 10, 0);
                number.mouseDragged(184, 10, 0, 4, 0);
                expect(ConfigManager.get().visuals.time == 18000, "Shift did not slow time adjustment");
                number.mouseDragged(220, 10, 0, 36, 0);
                number.mouseReleased(220, 10, 0);
                expect(ConfigManager.get().visuals.time == 18100, "Fine time adjustment lost its step");
            });
            context.getInput().releaseShift();
            context.runOnClient(client -> {
                number.mouseClicked(180, 10, 0);
                number.mouseDragged(-820, 10, 0, -1000, 0);
                expect(ConfigManager.get().visuals.time == 0, "Time could not reach dawn");
                number.mouseDragged(180, 10, 0, 1000, 0);
                number.mouseReleased(180, 10, 0);
                expect(ConfigManager.get().visuals.time == 23999, "Time did not clamp at the last tick");
                expect(Math.floorMod(client.level.getOverworldClockTime(), 24000L) == 23999,
                        "Client clock ignored the upper time limit");
                try {
                    var saved = JsonParser.parseString(Files.readString(
                            FabricLoader.getInstance().getConfigDir().resolve("froghelper.json")));
                    expect(saved.getAsJsonObject().getAsJsonObject("visuals").get("time").getAsInt() == 23999,
                            "Time setting was not saved on release");
                } catch (java.io.IOException failure) { throw new AssertionError(failure); }
            });
        } finally {
            context.getInput().releaseShift();
            context.runOnClient(client -> { ConfigManager.restoreSnapshot(original); ConfigManager.save(); });
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
