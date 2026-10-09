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
    @Test void overlaysPreserveIdentityAndMainPlacementAndUseLastMatch() {
        var c = fresh(); c.mainLayout.widgets.add(BOSS);
        var main = placement(12, 1f); c.widgetLayouts.put(BOSS, main);
        var first = add(c, "first", Set.of("#fishing"), BOSS, FISH);
        first.placements.put(BOSS, placement(80, 1.4f));
        var second = add(c, "second", Set.of("bay"), BOSS);
        second.placements.put(BOSS, placement(120, 2f));
        assertEquals(Set.of(BOSS, FISH), HudLayoutResolver.widgets(c, null, "bay"));
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
}
