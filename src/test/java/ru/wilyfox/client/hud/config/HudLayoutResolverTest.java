package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HudLayoutResolverTest {
    private static final String BOSS = "BossHudWidget", FISH = "FishingQuestsWidget";
    private HudConfig fresh() { return HudConfigSanitizer.sanitize(new HudConfig()); }
    private LocationWidgetLayoutConfig add(HudConfig config, String id, Set<String> locations, String... widgets) {
        var layout = new LocationWidgetLayoutConfig();
        layout.locationVisibility = new LinkedHashSet<>(locations); layout.widgets.addAll(List.of(widgets));
        config.locationLayouts.put(id, layout); return layout;
    }
    private WidgetLayoutConfig placement(int x, float scale) {
        var value = new WidgetLayoutConfig(); value.x = x; value.scale = scale; return value;
    }
    @Test void mainAlwaysExistsAndAdditionalEmptyLocationsAreInactive() {
        var c = fresh(); c.mainLayout.widgets.add(BOSS);
        add(c, "fish", Set.of(), FISH);
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(c, null, "bay"));
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(c, null, null));
        assertEquals(Set.of(FISH), HudLayoutResolver.widgets(c, "fish", null));
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(c, "", "bay"));
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(c, "missing", "bay"));
    }
    @Test void lastMatchingLayoutReplacesMembershipAndPreservesMainPlacement() {
        var c = fresh(); c.mainLayout.widgets.add(BOSS);
        var main = placement(12, 1f); c.widgetLayouts.put(BOSS, main);
        var first = add(c, "first", Set.of("#fishing"), BOSS, FISH);
        first.placements.put(BOSS, placement(80, 1.4f));
        var second = add(c, "second", Set.of("bay"), BOSS);
        second.placements.put(BOSS, placement(120, 2f));
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(c, null, "bay"));
        assertEquals(Set.of(BOSS, FISH), HudLayoutResolver.widgets(c, null, "azurepond"));
        assertSame(second.placements.get(BOSS), HudLayoutResolver.placement(c, BOSS, null, "bay"));
        assertSame(first.placements.get(BOSS), HudLayoutResolver.placement(c, BOSS, null, "azurepond"));
        assertSame(main, HudLayoutResolver.placement(c, BOSS, null, "spawn_overworld"));
        assertSame(main, HudLayoutResolver.placement(c, BOSS, "", "bay"));
        assertSame(first.placements.get(BOSS), HudLayoutResolver.placement(c, BOSS, "first", "spawn_overworld"));
        assertEquals(12, main.x); assertEquals(1f, main.scale);
    }
    @Test void savingTargetsOnlySelectedLayoutAndRuntimeOverrideOwner() {
        var c = fresh(); var layout = add(c, "fish", Set.of("bay"), BOSS);
        assertSame(c.widgetLayouts, HudLayoutResolver.placementStorage(c, BOSS, "", "bay"));
        assertSame(layout.placements, HudLayoutResolver.placementStorage(c, BOSS, "fish", null));
        assertSame(layout.placements, HudLayoutResolver.placementStorage(c, BOSS, null, "bay"));
        assertSame(c.widgetLayouts, HudLayoutResolver.placementStorage(c, BOSS, null, "market"));
    }
    @Test void widgetAllowAndDenyAreExclusiveAndHandleUnknownLocations() {
        var rule = new WidgetLocationConfig(); assertTrue(rule.isVisible(null));
        rule.selectVisible(Set.of(" BAY "));
        assertTrue(rule.isVisible("bay")); assertFalse(rule.isVisible("market")); assertFalse(rule.isVisible(null));
        rule.selectHidden(Set.of("#fishing"));
        assertTrue(rule.locationVisibility.isEmpty()); assertFalse(rule.isVisible("bay")); assertTrue(rule.isVisible(null));
        rule.selectVisible(Set.of("#boss"));
        assertTrue(rule.locationHidden.isEmpty()); assertTrue(rule.isVisible("boss_legion"));
        rule.selectVisible(Set.of()); assertTrue(rule.isVisible("market"));
    }
    @Test void selectorGroupsUseProtocolIdsAndExactIdsDoNotUseSubstringMatching() {
        assertTrue(LocationSelectors.matches(Set.of("#fishing"), "fish_end"));
        assertTrue(LocationSelectors.matches(Set.of("#fishing"), "crystal"));
        assertTrue(LocationSelectors.matches(Set.of("#shaft"), "shaft_125"));
        assertTrue(LocationSelectors.matches(Set.of("#shaft"), "mine"));
        assertTrue(LocationSelectors.matches(Set.of("#dungeon"), "procedural_dungeon_forest"));
        assertTrue(LocationSelectors.matches(Set.of("#spawn"), "spawn_overworld"));
        assertTrue(LocationSelectors.matches(Set.of("shaft_*"), "shaft_25"));
        assertFalse(LocationSelectors.matches(Set.of("bay"), "fake_bay"));
        assertFalse(LocationSelectors.matches(Set.of("#fishing"), "market"));
        assertFalse(LocationSelectors.matches(Set.of("*"), null));
    }
    @Test void sanitationRepairsContainersUnknownWidgetsNonFiniteValuesAndMainId() {
        var c = fresh(); c.locationLayouts.put("", new LocationWidgetLayoutConfig());
        c.locationLayouts.put("bad", null);
        var layout = add(c, "good", Set.of(" BAY "), BOSS, "DeletedWidget");
        layout.name = " "; var p = placement(50, Float.NaN);
        p.xFraction = Double.POSITIVE_INFINITY; p.yFraction = 3d; p.snapTarget = FISH;
        layout.placements.put(BOSS, p); layout.placements.put("DeletedWidget", new WidgetLayoutConfig());
        var rule = new WidgetLocationConfig(); rule.locationVisibility.add(" BAY "); rule.locationHidden.add("market");
        c.widgetLocations.put(BOSS, rule); c.widgetLocations.put("DeletedWidget", rule);
        HudConfigSanitizer.sanitize(c);
        assertEquals(Set.of("good"), c.locationLayouts.keySet()); assertEquals("Layout", layout.name);
        assertEquals(Set.of(BOSS), layout.widgets); assertEquals(Set.of("bay"), layout.locationVisibility);
        assertEquals(1f, p.scale); assertNull(p.xFraction); assertEquals(1d, p.yFraction); assertNull(p.snapTarget);
        assertEquals(Set.of(BOSS), c.widgetLocations.keySet()); assertTrue(rule.locationHidden.isEmpty());
        c.locationLayouts = null; c.widgetLocations = null; HudConfigSanitizer.sanitize(c);
        assertNotNull(c.locationLayouts); assertNotNull(c.widgetLocations);
    }
    @Test void newFieldsRoundTripWithoutChangingLegacyMainLayout() {
        var gson = HudConfigCodec.createGson(); var c = fresh(); c.mainLayout.widgets.add(BOSS);
        c.widgetLayouts.put(BOSS, placement(43, 1.2f));
        var layout = add(c, "fish", Set.of("bay"), BOSS, FISH); layout.name = "Fishing";
        layout.placements.put(BOSS, placement(100, 1.8f));
        c.widgetLocations.put(BOSS, new WidgetLocationConfig()); c.widgetLocations.get(BOSS).selectHidden(Set.of("market"));
        var loaded = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(c)));
        assertEquals(Set.of(BOSS), loaded.mainLayout.widgets);
        assertEquals(43, loaded.widgetLayouts.get(BOSS).x);
        assertEquals(100, loaded.locationLayouts.get("fish").placements.get(BOSS).x);
        assertEquals("Fishing", loaded.locationLayouts.get("fish").name);
        assertFalse(loaded.widgetLocations.get(BOSS).isVisible("market"));
    }
    @Test void fishingReplacesMigratedLegacyMainEvenWhenEmptyAndAfterReload() {
        var gson = HudConfigCodec.createGson();
        var config = HudConfigCodec.decode(gson, JsonParser.parseString("""
                {"bossWidget":{"active":true},"scoreboard":{"active":true},
                 "widgetLayouts":{"BossHudWidget":{"x":37,"y":92,"scale":1.4}}}
                """));
        var migrated = Set.copyOf(config.mainLayout.widgets);
        assertTrue(migrated.containsAll(Set.of(BOSS, "ScoreboardWidget")));
        var fishing = add(config, "fish", Set.of("#fishing"));
        assertTrue(HudLayoutResolver.widgets(config, null, "bay").isEmpty());
        assertEquals(migrated, HudLayoutResolver.widgets(config, null, "market"));
        fishing.widgets.add(BOSS); fishing.placements.put(BOSS, placement(120, 2f));
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(config, null, "bay"));
        assertEquals(120, HudLayoutResolver.placement(config, BOSS, null, "bay").x);
        assertEquals(37, HudLayoutResolver.placement(config, BOSS, null, "market").x);
        assertEquals(migrated, config.mainLayout.widgets);
        var loaded = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(config)));
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(loaded, null, "azurepond"));
        assertEquals(migrated, HudLayoutResolver.widgets(loaded, null, "market"));
        loaded.locationLayouts.get("fish").widgets.clear();
        loaded = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(loaded)));
        assertTrue(HudLayoutResolver.widgets(loaded, null, "fish_end").isEmpty());
        assertEquals(migrated, loaded.mainLayout.widgets);
    }
    @Test void inheritanceCombinesMainWithoutDuplicatesAndKeepsPlacementOwnership() {
        var config = fresh(); config.mainLayout.widgets.addAll(List.of(BOSS, "ScoreboardWidget"));
        var mainBoss = placement(12, 1f);
        var mainScoreboard = placement(30, 1.2f);
        config.widgetLayouts.put(BOSS, mainBoss); config.widgetLayouts.put("ScoreboardWidget", mainScoreboard);
        var fishing = add(config, "fish", Set.of("#fishing"), BOSS, FISH);
        fishing.placements.put(BOSS, placement(120, 2f)); fishing.inheritMainLayout = true;
        assertEquals(List.of(BOSS, "ScoreboardWidget", FISH),
                List.copyOf(HudLayoutResolver.widgets(config, null, "bay")));
        assertSame(fishing.placements.get(BOSS), HudLayoutResolver.placement(config, BOSS, null, "bay"));
        assertSame(mainScoreboard, HudLayoutResolver.placement(config, "ScoreboardWidget", null, "bay"));
        assertSame(config.widgetLayouts, HudLayoutResolver.placementStorage(config, "ScoreboardWidget", null, "bay"));
        assertSame(fishing.placements, HudLayoutResolver.placementStorage(config, BOSS, null, "bay"));
        assertEquals(Set.of(BOSS, FISH), HudLayoutResolver.widgets(config, "fish", "bay"));
        assertEquals(Set.of(BOSS, "ScoreboardWidget"), config.mainLayout.widgets);
        fishing.inheritMainLayout = false;
        assertEquals(Set.of(BOSS, FISH), HudLayoutResolver.widgets(config, null, "bay"));
        fishing.widgets.clear(); fishing.inheritMainLayout = true;
        assertEquals(config.mainLayout.widgets, HudLayoutResolver.widgets(config, null, "bay"));
        assertSame(mainBoss, HudLayoutResolver.placement(config, BOSS, null, "bay"));
    }
    @Test void inheritancePersistsAndExistingLayoutsRemainIndependentByDefault() {
        var gson = HudConfigCodec.createGson(); var config = fresh(); config.mainLayout.widgets.add(BOSS);
        var fishing = add(config, "fish", Set.of("#fishing"), FISH);
        var oldJson = JsonParser.parseString(gson.toJson(config)).getAsJsonObject();
        oldJson.getAsJsonObject("locationLayouts").getAsJsonObject("fish").remove("inheritMainLayout");
        var legacy = HudConfigCodec.decode(gson, oldJson);
        assertFalse(legacy.locationLayouts.get("fish").inheritMainLayout);
        assertEquals(Set.of(FISH), HudLayoutResolver.widgets(legacy, null, "bay"));
        fishing.inheritMainLayout = true;
        var loaded = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(config)));
        assertTrue(loaded.locationLayouts.get("fish").inheritMainLayout);
        assertEquals(Set.of(BOSS, FISH), HudLayoutResolver.widgets(loaded, null, "bay"));
        loaded.locationLayouts.get("fish").inheritMainLayout = false;
        loaded = HudConfigCodec.decode(gson, JsonParser.parseString(gson.toJson(loaded)));
        assertFalse(loaded.locationLayouts.get("fish").inheritMainLayout);
        assertEquals(Set.of(FISH), HudLayoutResolver.widgets(loaded, null, "bay"));
    }
    @Test void selectedLayoutNeverInheritsAnEarlierMatchingLayout() {
        var config = fresh(); config.mainLayout.widgets.add(BOSS);
        var main = placement(12, 1f); config.widgetLayouts.put(BOSS, main);
        var first = add(config, "group", Set.of("#fishing"), BOSS, FISH);
        first.placements.put(BOSS, placement(80, 2f));
        var last = add(config, "bay", Set.of("bay"), BOSS);
        last.inheritMainLayout = true;
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(config, null, "bay"));
        assertSame(main, HudLayoutResolver.placement(config, BOSS, null, "bay"));
        assertSame(last.placements, HudLayoutResolver.placementStorage(config, BOSS, null, "bay"));
        last.widgets.clear();
        assertEquals(Set.of(BOSS), HudLayoutResolver.widgets(config, null, "bay"));
        last.inheritMainLayout = false;
        assertTrue(HudLayoutResolver.widgets(config, null, "bay").isEmpty());
    }

}
