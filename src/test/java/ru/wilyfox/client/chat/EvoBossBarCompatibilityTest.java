package ru.wilyfox.client.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Reads the actual title pattern from the reference JAR; no mod or network initialization. */
@EnabledIfSystemProperty(named = "froghelper.evoReference", matches = ".+")
class EvoBossBarCompatibilityTest {
    @Test void referenceHealthAndNameAgreeWithOurParserForClanSuffixes() throws Exception {
        try (var loader = new URLClassLoader(new java.net.URL[]{
                Path.of(System.getProperty("froghelper.evoReference")).toUri().toURL()}, getClass().getClassLoader())) {
            var type = loader.loadClass("SlADkIYpErS1koVIYsoK$232445035.sLADKiYPErS1kOViYsOk$256905550");
            Pattern reference = null;
            for (var field : type.getDeclaredFields()) {
                if (!field.getType().getName().equals("kotlin.text.Regex")) continue;
                field.setAccessible(true);
                Object regex = field.get(null);
                reference = (Pattern) regex.getClass().getMethod("toPattern").invoke(regex);
            }
            assertNotNull(reference);
            for (String title : new String[]{"КРИГЕР 125❤ (Frogs42)", "LEGION COMMANDER 250❤ (Clan 2)",
                    "Вестник ада 100❤", "✦ KRIEGER 40❤ (Clan42)"}) {
                var expected = reference.matcher(title);
                assertTrue(expected.find(), title);
                var actual = BossMessageParser.parseBossBar(title);
                assertNotNull(actual, title);
                // Decorations are accepted by registry resolution; compare the name without the leading icon.
                assertTrue(actual.bossName().endsWith(expected.group(1).trim()), title);
                assertEquals(Double.parseDouble(expected.group(2)), actual.health(), title);
            }
        }
    }
}
