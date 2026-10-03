package ru.wilyfox.utils;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.protocol.DwBossType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BossNameTest {
    @Test
    void russianHologramIgnoresCaseFormattingAndCombiningMarks() {
        assertEquals("Крысиный Король", BossName.getBossName("§cКрысиный Король"));
        assertEquals("Бессмертный Легион", BossName.getBossName("Бессмертный легион"));
        assertEquals("Бессмертный Легион", BossName.getBossName("§cКОМАНДИР ЛЕГИОНА ×2"));
        assertEquals("Кригер", BossName.getBossName("§cКРИГЕР х3"));
        assertNull(BossName.getBossName(null));
    }

    @Test
    void englishHologramResolvesARealServerRegistryId() {
        var type = new DwBossType("RatKing", "Крысиный Король", "", 200, 0, 0, false);
        assertEquals("Крысиный Король", BossName.resolveRegistryName("§c✦ RAT KING ✦", List.of(type)));
        assertEquals("Крысиный Король", BossName.resolveRegistryName("§cRAT KING x2", List.of(type)));
        assertNull(BossName.resolveRegistryName("UNKNOWN BOSS", List.of(type)));
    }

    @Test
    void legionCommanderAliasUsesTheReferenceRegistryKey() {
        var type = new DwBossType("ImmortalLegion", "Бессмертный Легион", "", 400, 0, 0, false);
        assertEquals("Бессмертный Легион", BossName.resolveRegistryName("LEGION COMMANDER", List.of(type)));
        assertEquals("Бессмертный Легион", BossName.resolveRegistryName("КОМАНДИР ЛЕГИОНА Х3", List.of(type)));
    }
}
