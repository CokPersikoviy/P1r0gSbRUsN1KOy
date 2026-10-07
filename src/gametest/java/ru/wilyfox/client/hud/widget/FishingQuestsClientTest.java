package ru.wilyfox.client.hud.widget;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.FishingConfig;
import ru.wilyfox.client.hud.config.FishingQuestDescriptionMode;
import ru.wilyfox.client.hud.config.FishingQuestTypeFilter;
import ru.wilyfox.client.hud.config.FishingWidgetVisibility;
import ru.wilyfox.client.hud.layer.HudLayer;
import ru.wilyfox.client.protocol.DiamondWorldProtocolClient;
import ru.wilyfox.client.protocol.DwHourlyQuestProgress;
import ru.wilyfox.client.protocol.DwHourlyQuestType;
import ru.wilyfox.client.protocol.DwQuest;
import ru.wilyfox.client.protocol.SocialProtocolFixture;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class FishingQuestsClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        AtomicReference<FishingConfig> original = new AtomicReference<>();
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                original.set(ConfigManager.get().fishing);
                var config = new FishingConfig();
                config.showFishingQuestsWidget = true;
                config.questsVisibility = FishingWidgetVisibility.FISHING_WARP;
                config.questsDescription = FishingQuestDescriptionMode.ALWAYS;
                ConfigManager.get().fishing = config;
                SocialProtocolFixture.clearQuests();
                SocialProtocolFixture.location("PRISONEVO1", 2, "bay");
                SocialProtocolFixture.quests(List.of(
                        quest("fish", DwQuest.Dimension.OVERWORLD, DwQuest.Category.FISHING, "Catch salmon", 148, 5_000),
                        quest("fish-end", DwQuest.Dimension.END, DwQuest.Category.FISHING, "Catch end fish", 8, 20),
                        quest("hunt", DwQuest.Dimension.OVERWORLD, DwQuest.Category.HUNTING, "Hunt mobs", 12, 20),
                        quest("alchemy", DwQuest.Dimension.NETHER, DwQuest.Category.ALCHEMY, "Brew potion", 4, 20)));
                var widget = new FishingQuestsWidget(20, 20, HudLayer.CONTENT);
                if (!widget.isVisible()) fail("Fishing warp hid the enabled quest widget");
                List<List<String>> views = text(widget);
                if (views.size() != 2 || !views.getFirst().contains("Catch salmon")
                        || !views.getFirst().contains("Progress: 148/5000")) fail("Fishing quest packets did not populate the HUD");
                if (views.stream().flatMap(List::stream).anyMatch(line -> line.contains("Hunt mobs") || line.contains("Brew potion"))) {
                    fail("Non-fishing quests appeared in the fishing HUD");
                }

                SocialProtocolFixture.questUpdate("fish", 5_000);
                views = text(widget);
                if (!views.getFirst().contains("Claim reward") || views.getFirst().contains("Progress: 148/5000")) {
                    fail("questupdate did not change the displayed progress");
                }
                SocialProtocolFixture.questUpdate("unknown", 10);
                if (DiamondWorldProtocolClient.getQuests().size() != 4) fail("Unknown quest update created a ghost quest");
                config.questsTypeFilter = FishingQuestTypeFilter.END;
                views = text(widget);
                if (views.size() != 1 || !views.getFirst().contains("Catch end fish")) fail("Dimension filter lost the pinned End quest");

                config.questsTypeFilter = FishingQuestTypeFilter.ALL;
                SocialProtocolFixture.hourlyQuests(new DwHourlyQuestType(7, "NETHER", "Hourly lava", "Catch lava fish", 20),
                        new DwHourlyQuestProgress(7, 12, System.currentTimeMillis() + 300_000));
                SocialProtocolFixture.hourlyQuests(new DwHourlyQuestType(8, "NORMAL", "Expired hourly", "", 20),
                        new DwHourlyQuestProgress(8, 12, System.currentTimeMillis() - 5_000));
                if (text(widget).size() != 3) fail("New quests replaced active hourly quests or revived an expired quest");

                SocialProtocolFixture.quests(List.of(quest("replacement", DwQuest.Dimension.OVERWORLD,
                        DwQuest.Category.FISHING, "New fishing quest", 3, 20)));
                views = text(widget);
                if (views.size() != 2 || !views.getFirst().contains("New fishing quest")
                        || views.stream().flatMap(List::stream).anyMatch(line -> line.equals("Catch salmon"))) {
                    fail("A new setup retained stale quests or removed hourly quests");
                }
                if (widget.getWidth() <= 0 || widget.getHeight() <= 0) fail("Fishing quest HUD has no renderable bounds");
            });
            context.waitTicks(3);
            context.takeScreenshot("fishing-quests-pinned");
            context.runOnClient(client -> {
                SocialProtocolFixture.quests(List.of());
                var widget = new FishingQuestsWidget(20, 20, HudLayer.CONTENT);
                if (text(widget).size() != 1) fail("Empty setup did not remove general quests independently of hourly quests");
                SocialProtocolFixture.clearQuests();
                if (!text(widget).isEmpty()) fail("Cleared quests remained in the HUD outside the editor");
            });
        } finally {
            context.runOnClient(client -> {
                if (original.get() != null) ConfigManager.get().fishing = original.get();
                SocialProtocolFixture.clearQuests();
                SocialProtocolFixture.clear();
            });
        }
    }

    private static DwQuest quest(String id, DwQuest.Dimension dimension, DwQuest.Category category, String name, int progress, int required) {
        return new DwQuest(id, dimension, category, name, "Quest description", progress, required);
    }

    private static List<List<String>> text(FishingQuestsWidget widget) {
        try {
            var build = FishingQuestsWidget.class.getDeclaredMethod("buildViews");
            build.setAccessible(true);
            var result = new java.util.ArrayList<List<String>>();
            for (Object view : (List<?>) build.invoke(widget)) {
                var lines = view.getClass().getDeclaredMethod("lines");
                lines.setAccessible(true);
                var texts = new java.util.ArrayList<String>();
                for (Object line : (List<?>) lines.invoke(view)) {
                    var text = line.getClass().getDeclaredMethod("text");
                    text.setAccessible(true);
                    texts.add((String) text.invoke(line));
                }
                result.add(texts);
            }
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void fail(String message) { throw new AssertionError(message); }
}
