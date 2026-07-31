package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossTypeCatalogTest {
    @BeforeEach
    void resetCatalog() {
        BossTypeCatalog.resetForTesting();
    }

    @AfterEach
    void restoreCatalog() {
        BossTypeCatalog.resetForTesting();
    }

    @Test
    void startsWithBuiltInBossesBeforeProtocolPacket() {
        DwBossType krieger = BossTypeCatalog.snapshot().stream()
                .filter(type -> type.level() == 15)
                .findFirst()
                .orElse(null);

        assertNotNull(krieger);
        assertEquals("Кригер", krieger.name());
        assertEquals("level:15", krieger.id());
    }

    @Test
    void bossTypesReplacesFallbackAndAddsNewBosses() {
        BossTypeCatalog.update(Map.of(
                "krieger", new DwBossType("krieger", "Кригер", "STONE", 15, 1, 0, false),
                "future", new DwBossType("future", "Future Boss", "CLOCK", 610, 2, 0, true)
        ));

        assertTrue(BossTypeCatalog.snapshot().stream().anyMatch(type -> type.id().equals("krieger")));
        assertTrue(BossTypeCatalog.snapshot().stream().anyMatch(type -> type.id().equals("future")));
        assertFalse(BossTypeCatalog.snapshot().stream().anyMatch(type -> type.id().equals("level:15")));
    }

    @Test
    void timerIdIsVisibleBeforeItsBossTypeArrives() {
        BossTypeCatalog.observe("unknown_boss", "unknown_boss", 0);

        assertTrue(BossTypeCatalog.snapshot().stream().anyMatch(type -> type.id().equals("unknown_boss")));
    }
}
