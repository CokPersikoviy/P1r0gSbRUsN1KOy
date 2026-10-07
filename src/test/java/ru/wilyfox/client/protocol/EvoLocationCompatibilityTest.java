package ru.wilyfox.client.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStreamReader;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Reads actual static ID dictionaries and translations; no reference mod/network initialization. */
@EnabledIfSystemProperty(named = "froghelper.evoReference", matches = ".+")
class EvoLocationCompatibilityTest {
    @Test void allFourLocationDictionariesHaveExactlyTheReferenceIds() throws Exception {
        List<Map<String, String>> reference = dictionaries();
        assertEquals(4, reference.size());
        for (String[] group : groups()) {
            var actualField = DwGameLocation.class.getDeclaredField(group[0]);
            actualField.setAccessible(true);
            Map<?, ?> actual = (Map<?, ?>) actualField.get(null);
            Set<String> expectedIds = dictionary(reference, group[1]).keySet().stream()
                    .map(id -> id.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            assertEquals(expectedIds, actual.keySet(), group[0]);
        }
    }

    @Test void all48LocationNamesMatchRussianReferenceTranslations() throws Exception {
        List<Map<String, String>> reference = dictionaries();
        JsonObject language;
        try (var zip = new ZipFile(referencePath().toFile());
             var reader = new InputStreamReader(zip.getInputStream(zip.getEntry("assets/evo-plus/lang/ru_ru.json")), StandardCharsets.UTF_8)) {
            language = JsonParser.parseReader(reader).getAsJsonObject();
        }
        int checked = 0;
        for (String[] group : groups()) {
            for (var entry : dictionary(reference, group[1]).entrySet()) {
                String id = group[2] + entry.getKey();
                String expected = language.get(entry.getValue()).getAsString();
                if (!group[2].isEmpty()) expected = language.get("evo-plus.location.dungeon").getAsString().formatted(expected);
                assertEquals(expected, new DwGameLocation(id).displayName(), id);
                checked++;
            }
        }
        assertEquals(48, checked);
    }

    @Test void fishingClassificationUsesExactlyThe11ReferenceSpots() throws Exception {
        Map<String, String> fishing = dictionary(dictionaries(), "bay");
        var idsField = DwGameLocation.class.getDeclaredField("FISHING_SPOT_IDS");
        idsField.setAccessible(true);
        assertEquals(fishing.keySet(), idsField.get(null));
        for (String id : fishing.keySet()) assertTrue(new DwGameLocation(id).isFishing(), id);
        for (String id : List.of("fish_1_overworld", "fish_2_overworld", "fish_nether", "fish_end", "shaft_17", "market")) {
            assertFalse(new DwGameLocation(id).isFishing(), id);
        }
    }

    private static String[][] groups() {
        return new String[][]{
                {"LOCATION_NAMES", "spawn_overworld", ""},
                {"FISHING_SPOT_NAMES", "bay", ""},
                {"DUNGEON_NAMES", "pyramid", "dungeon_"},
                {"PROCEDURAL_DUNGEON_NAMES", "camp", "procedural_dungeon_"}
        };
    }

    private static Path referencePath() { return Path.of(System.getProperty("froghelper.evoReference")); }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> dictionaries() throws Exception {
        try (var loader = new URLClassLoader(new java.net.URL[]{referencePath().toUri().toURL()}, EvoLocationCompatibilityTest.class.getClassLoader())) {
            Class<?> facade = loader.loadClass("slaDk1yPERSIkov1Ys0k$631554143.sladKIyp3RsikoviyS0K$992051481");
            List<Map<String, String>> result = new ArrayList<>();
            for (var field : facade.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                result.add((Map<String, String>) field.get(null));
            }
            return result;
        }
    }

    private static Map<String, String> dictionary(List<Map<String, String>> dictionaries, String key) {
        return dictionaries.stream().filter(map -> map.containsKey(key)).findFirst().orElseThrow();
    }
}
