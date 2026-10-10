package ru.wilyfox.client.hud.menu;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.WidgetCatalog;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WidgetSettingsSectionsTest {
    @Test void allExistingSpecificControlsHaveExactlyOneWidgetOwner() {
        var expected = Map.ofEntries(
                Map.entry(WidgetCatalog.LEVEL_PROGRESS, 1), Map.entry(WidgetCatalog.ESTIMATED_TPS, 1),
                Map.entry(WidgetCatalog.CRAFT_RECIPE, 1), Map.entry(WidgetCatalog.BOOSTERS, 1),
                Map.entry(WidgetCatalog.POTION_TIMERS, 3), Map.entry(WidgetCatalog.SELLERS, 1),
                Map.entry(WidgetCatalog.COMBO, 1), Map.entry(WidgetCatalog.WAND, 1),
                Map.entry(WidgetCatalog.VISIBILITY, 1), Map.entry(WidgetCatalog.MAP, 6),
                Map.entry(WidgetCatalog.BOSS_TIMERS, 13), Map.entry(WidgetCatalog.POP_UPS, 4),
                Map.entry(WidgetCatalog.FISHING_NIBBLES, 2), Map.entry(WidgetCatalog.FISHING_QUESTS, 3));
        var labels = new HashSet<String>();
        for (var widget : WidgetCatalog.values()) {
            var controls = WidgetSettingsSections.create(widget);
            if (widget.chatChannel() != null) {
                assertEquals(Set.of("Tab name", "Text filter", "Channel", "Chat width", "Chat rows", "Show chat title"),
                        new HashSet<>(controls.stream().map(control -> control.label).toList()));
                continue;
            }
            assertEquals(expected.getOrDefault(widget, 0), controls.size(), widget.title());
            for (var control : controls) assertTrue(labels.add(control.label), "Duplicated control: " + control.label);
        }
        assertEquals(39, labels.size());
    }

    @Test void globalMenuKeepsAppearanceAndGameplayFeaturesWithoutWidgetContentDuplicates() {
        Map<SettingsCategory, List<SettingsComponent>> sections = new EnumMap<>(SettingsCategory.class);
        for (var category : SettingsCategory.values()) sections.put(category, new ArrayList<>());
        HudSettingsFeatureSections.populate(sections, () -> {});
        HudSettingsCoreSections.populate(sections);
        assertEquals(Set.of("Edit Main Layout", "UnClutter (hide widget titles)", "Background",
                        "Native Renderer (no blur, if glass lags)", "Lightweight HUD (no blur / item icons)", "BG Opacity"),
                new HashSet<>(sections.get(SettingsCategory.WIDGET).stream()
                        .filter(control -> !(control instanceof BreakLineSettingsComponent)).map(control -> control.label).toList()));
        var allLabels = sections.values().stream().flatMap(List::stream).map(control -> control.label).toList();
        var oldWidgetSections = List.of(SettingsCategory.WIDGET, SettingsCategory.FISHING, SettingsCategory.POP_UPS)
                .stream().flatMap(category -> sections.get(category).stream()).map(control -> control.label).toList();
        for (var widget : WidgetCatalog.values()) for (var control : WidgetSettingsSections.create(widget)) {
            assertFalse(oldWidgetSections.contains(control.label), "Widget control still exposed globally: " + control.label);
        }
        assertTrue(allLabels.contains("Event: Private message"));
        assertTrue(allLabels.contains("Show markers"));
    }
}
