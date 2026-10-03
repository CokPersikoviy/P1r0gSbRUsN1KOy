package ru.wilyfox.client.hud.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudSettingsSectionsTest {
    @Test
    void staticSectionsPopulateEveryNonDynamicCategory() {
        Map<SettingsCategory, List<SettingsComponent>> sections = new EnumMap<>(SettingsCategory.class);
        for (SettingsCategory category : SettingsCategory.values()) {
            sections.put(category, new ArrayList<>());
        }
        AtomicBoolean autoMessagesBuilt = new AtomicBoolean();

        HudSettingsFeatureSections.populate(sections, () -> autoMessagesBuilt.set(true));
        HudSettingsCoreSections.populate(sections);

        assertTrue(autoMessagesBuilt.get());
        for (SettingsCategory category : SettingsCategory.values()) {
            if (category == SettingsCategory.AUTO_MESSAGES) {
                continue;
            }
            assertFalse(sections.get(category).isEmpty(), () -> "Missing settings section: " + category);
        }
    }
}
