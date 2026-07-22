package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import ru.wilyfox.client.booster.BoosterStore;
import ru.wilyfox.client.boss.BossDamageStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DwProtocolDecodersTest {
    private static final int BYTE_XOR_KEY = 103;
    private static final int INT_XOR_MASK = 0x67676767;
    private static final long LONG_XOR_MASK = 0x6767676767676767L;

    @Test
    void bossTypesUsesProtocolFieldOrder() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "MYTHIC_BOSS");
        writeString(payload, "Mythic Boss");
        writeString(payload, "minecraft:diamond");
        writeVarInt(payload, 27);
        writeVarInt(payload, 5_012);
        writeVarInt(payload, 73);
        writeBoolean(payload, true);

        DwBossType boss = DwBossTypesDecoder.decode(payload.toByteArray()).types().get("MYTHIC_BOSS");

        assertEquals(27, boss.level());
        assertEquals(5_012, boss.customModelData());
        assertEquals(73, boss.capturePoints());
        assertTrue(boss.raid());
    }

    @Test
    void capturedBossesResolveWhenBossTypesArriveAfterClanInfo() throws IOException {
        ProtocolState state = new ProtocolState();
        state.clanInfo = new DwClanState("Frogs", java.util.List.of("Fox"), java.util.List.of("MYTHIC_BOSS"));

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "MYTHIC_BOSS");
        writeString(payload, "Mythic Boss");
        writeString(payload, "minecraft:diamond");
        writeVarInt(payload, 27);
        writeVarInt(payload, 5_012);
        writeVarInt(payload, 73);
        writeBoolean(payload, true);

        state.bossTypes = DwBossTypesDecoder.decode(payload.toByteArray()).types();
        state.capturedBossLevels = DwClanBossResolver.resolveLevels(state.clanInfo, state.bossTypes);

        assertEquals(java.util.Set.of(27), state.capturedBossLevels);
    }

    @Test
    void petTypesUsesProtocolFieldOrder() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "WOLF");
        writeString(payload, "Wolf");
        writeString(payload, "LEGENDARY");
        writeString(payload, "minecraft:bone");
        writeVarInt(payload, 321);

        DwPetType pet = DwPetTypesDecoder.decode(payload.toByteArray()).types().get("WOLF");

        assertEquals("Wolf", pet.name());
        assertEquals("LEGENDARY", pet.rarity());
        assertEquals("minecraft:bone", pet.material());
        assertEquals(321, pet.customModelData());
    }

    @Test
    void bossTimersUsesFixedMaskedLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "MYTHIC_BOSS");
        writeLong(payload, 128_000L);

        DwBossTimersPacket packet = DwBossTimersDecoder.decode(payload.toByteArray());

        assertEquals(128_000L, packet.timers().get("MYTHIC_BOSS"));
    }

    @Test
    void bossDamageUsesStringIdAndFixedMaskedInt() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeString(payload, "MYTHIC_BOSS");
        writeInt(payload, 123_456);

        DwBossDamagePacket packet = DwBossDamageDecoder.decode(payload.toByteArray());

        assertEquals("MYTHIC_BOSS", packet.bossId());
        assertEquals(123_456, packet.damage());
    }

    @Test
    void bossDamageHandlerIgnoresUnknownBossIds() {
        ProtocolState state = new ProtocolState();
        state.bossDamageStore = new BossDamageStore();

        assertFalse(ProtocolPayloadHandlers.applyBossDamage(
                state,
                new DwBossDamagePacket("UNKNOWN_BOSS", 500),
                1_000L
        ));
        assertFalse(state.bossDamageStore.hasActiveEntry());
    }

    @Test
    void bossDamageApplicationUsesDynamicBossMetadata() {
        ProtocolState state = new ProtocolState();
        state.bossDamageStore = new BossDamageStore();
        state.bossTypes.put("MYTHIC_BOSS", new DwBossType(
                "MYTHIC_BOSS", "Mythic Boss", "minecraft:diamond", 520, 5_012, 73, true
        ));

        assertTrue(ProtocolPayloadHandlers.applyBossDamage(
                state,
                new DwBossDamagePacket("MYTHIC_BOSS", 123_456),
                1_000L
        ));
        assertEquals("Mythic Boss", state.bossDamageStore.getCurrent().bossName());
        assertEquals(520, state.bossDamageStore.getCurrent().bossLevel());
        assertEquals(123_456L, state.bossDamageStore.getCurrent().damage());
    }

    @Test
    void extractsBossIdFromProtocolLocation() {
        assertEquals("heraldOfHell", DiamondWorldProtocolClient.bossIdFromLocation("boss_heraldOfHell"));
        assertEquals("heraldOfHell", DiamondWorldProtocolClient.bossIdFromLocation("boss:heraldOfHell"));
        assertEquals("heraldOfHell", DiamondWorldProtocolClient.bossIdFromLocation("bossheraldOfHell"));
        assertNull(DiamondWorldProtocolClient.bossIdFromLocation("spawn"));
    }

    @Test
    void resolvesCommanderLegionBossBarAlias() {
        assertTrue(DiamondWorldProtocolClient.bossNamesMatch("Бессмертный легион", "КОМАНДИР ЛЕГИОНА"));
        assertTrue(DiamondWorldProtocolClient.bossNamesMatch("Бессмертныи легион", "КОМАНДИР ЛЕГИОНА"));
    }

    @Test
    void bossCollectUsesStringToStringSetMap() {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 2);
        writeString(payload, "MYTHIC_BOSS");
        writeVarInt(payload, 2);
        writeString(payload, "first");
        writeString(payload, "second");
        writeString(payload, "EMPTY_BOSS");
        writeVarInt(payload, 0);

        DwBossCollectPacket packet = DwBossCollectDecoder.decode(payload.toByteArray());

        assertEquals(java.util.Set.of("first", "second"), packet.collectibles().get("MYTHIC_BOSS"));
        assertTrue(packet.collectibles().get("EMPTY_BOSS").isEmpty());
    }

    @Test
    void simpleCooldownUsesFixedMaskedLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLong(payload, 128L);

        assertEquals(128L, DwCooldownValueDecoder.decode(payload.toByteArray()).remainingMillis());
    }

    @Test
    void levelInfoUsesMaskedFieldOrderAndConditionalRequirements() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 42);
        writeDouble(payload, 12_345.5D);
        writeInt(payload, 900);
        writeBoolean(payload, false);
        writeDouble(payload, 50_000.25D);
        writeInt(payload, 1_000);

        DwLevelInfoPacket packet = DwLevelInfoDecoder.decode(payload.toByteArray());

        assertEquals(42, packet.level());
        assertEquals(12_345.5D, packet.money());
        assertEquals(900, packet.blocks());
        assertFalse(packet.maxLevel());
        assertEquals(50_000.25D, packet.requiredMoney());
        assertEquals(1_000, packet.requiredBlocks());

        ByteArrayOutputStream maxPayload = new ByteArrayOutputStream();
        writeVarInt(maxPayload, 43);
        writeDouble(maxPayload, 99_000.0D);
        writeInt(maxPayload, 2_000);
        writeBoolean(maxPayload, true);

        DwLevelInfoPacket maxPacket = DwLevelInfoDecoder.decode(maxPayload.toByteArray());
        assertTrue(maxPacket.maxLevel());
        assertEquals(0.0D, maxPacket.requiredMoney());
        assertEquals(0, maxPacket.requiredBlocks());
    }

    @Test
    void potionCooldownsUsesFixedIntToFixedLongMap() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 2);
        writeInt(payload, 15);
        writeLong(payload, 90_000L);
        writeInt(payload, 41);
        writeLong(payload, -2_500L);

        DwPotionCooldownsPacket packet = DwPotionCooldownsDecoder.decode(payload.toByteArray());

        assertEquals(2, packet.cooldowns().size());
        assertEquals(90_000L, packet.cooldowns().get(15));
        assertEquals(-2_500L, packet.cooldowns().get(41));
    }

    @Test
    void sellersUseProtocolIdNameAndFixedMaskedLongOrder() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 2);
        writeString(payload, "lucas_id");
        writeString(payload, "Lucas");
        writeLong(payload, 90_999L);
        writeString(payload, "liam_id");
        writeString(payload, "Liam");
        writeLong(payload, -1L);

        DwSellersPacket packet = DwSellersDecoder.decode(payload.toByteArray());

        assertEquals(2, packet.entries().size());
        assertEquals(new DwSellerEntry("lucas_id", "Lucas", 90_999L), packet.entries().getFirst());
        assertEquals(new DwSellerEntry("liam_id", "Liam", -1L), packet.entries().get(1));
    }

    @Test
    void comboUsesFixedMaskedDoublesAndInts() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeDouble(payload, 2.5D);
        writeDouble(payload, 3.0D);
        writeInt(payload, 12_345);
        writeInt(payload, 50_000);

        DwComboPacket packet = DwComboDecoder.decode(payload.toByteArray());

        assertEquals(2.5D, packet.booster());
        assertEquals(3.0D, packet.nextBooster());
        assertEquals(12_345, packet.blocks());
        assertEquals(50_000, packet.requiredBlocks());
    }

    @Test
    void comboBlocksUsesFixedMaskedInt() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeInt(payload, 12_346);

        DwComboBlocksPacket packet = DwComboBlocksDecoder.decode(payload.toByteArray());

        assertEquals(12_346, packet.blocks());
    }

    @Test
    void clanSiegePositionUsesTwoFixedMaskedInts() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeInt(payload, 137);
        writeInt(payload, -42);

        DwClanSiegePosition position = DwClanSiegePositionDecoder.decode(payload.toByteArray());

        assertEquals(137, position.x());
        assertEquals(-42, position.y());
        assertTrue(position.isAvailable());
        assertFalse(DwClanSiegePosition.UNAVAILABLE.isAvailable());
    }

    @Test
    void boostersDecodeShaftAndUseFixedMaskedLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeVarInt(payload, 2);
        writeVarInt(payload, 1);
        writeLong(payload, 120_000L);
        writeDouble(payload, 1.5D);

        DwBoostersPacket packet = DwBoostersDecoder.decode(payload.toByteArray());

        BoosterStore.ProtocolEntry entry = packet.boosters().get(BoosterStore.Kind.SHAFT).getFirst();
        assertEquals(120_000L, entry.remainingMillis());
        assertEquals(1.5D, entry.multiplier());
    }

    @Test
    void staffTypesUseModelIdAsRegistryKey() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "Посох вихря");
        writeVarInt(payload, 5_021);

        DwStaffType type = DwStaffTypesDecoder.decode(payload.toByteArray()).types().get(5_021);

        assertEquals(5_021, type.id());
        assertEquals(5_021, type.modelId());
        assertEquals("Посох вихря", type.name());
    }

    @Test
    void staffTimersUseFixedMaskedIntAndLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeInt(payload, 5_021);
        writeLong(payload, 45_000L);

        DwStaffTimersPacket packet = DwStaffTimersDecoder.decode(payload.toByteArray());

        assertEquals(45_000L, packet.timers().get(5_021));
    }

    @Test
    void abilityTypesUseStringIdAndName() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "WIND");
        writeString(payload, "\u041f\u043e\u0440\u044b\u0432 \u0432\u0435\u0442\u0440\u0430");

        DwAbilityType type = DwAbilityTypesDecoder.decode(payload.toByteArray()).types().get("WIND");

        assertEquals("WIND", type.id());
        assertEquals("\u041f\u043e\u0440\u044b\u0432 \u0432\u0435\u0442\u0440\u0430", type.name());
    }

    @Test
    void abilityTimersUseStringIdAndFixedMaskedLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "WIND");
        writeLong(payload, 45_000L);

        DwAbilityTimersPacket packet = DwAbilityTimersDecoder.decode(payload.toByteArray());

        assertEquals(45_000L, packet.timers().get("WIND"));
    }

    @Test
    void hourlyQuestTypesUseMaskedFixedIntsAndStrings() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeInt(payload, 7);
        writeString(payload, "NETHER");
        writeString(payload, "Адский улов");
        writeString(payload, "Поймайте 20 рыб");
        writeInt(payload, 20);

        DwHourlyQuestType type = DwHourlyQuestTypesDecoder.decode(payload.toByteArray()).types().get(7);

        assertEquals("NETHER", type.type());
        assertEquals("Адский улов", type.name());
        assertEquals("Поймайте 20 рыб", type.lore());
        assertEquals(20, type.needed());
    }

    @Test
    void hourlyQuestInfoUsesMaskedFixedIntsAndLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeInt(payload, 7);
        writeInt(payload, 12);
        writeLong(payload, 111_111L);

        DwHourlyQuestInfoPacket.Entry quest = DwHourlyQuestInfoDecoder.decode(payload.toByteArray()).quests().get(7);

        assertEquals(12, quest.progress());
        assertEquals(111_111L, quest.remainedMillis());
    }

    @Test
    void fishingLocationDetectionUsesEvoPlusSpotIds() {
        assertTrue(DiamondWorldProtocolClient.isFishingLocation("bay"));
        assertTrue(DiamondWorldProtocolClient.isFishingLocation("AMBERGROT"));
        assertTrue(DiamondWorldProtocolClient.isFishingLocation("crystal"));
        assertFalse(DiamondWorldProtocolClient.isFishingLocation("fish_nether"));
        assertFalse(DiamondWorldProtocolClient.isFishingLocation("fish_end"));
        assertFalse(DiamondWorldProtocolClient.isFishingLocation("spawn"));
    }

    private static void writeString(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        for (int index = 0; index < bytes.length; index++) {
            output.write((bytes[index] & 0xFF) ^ (BYTE_XOR_KEY ^ (index & 0xFF)));
        }
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & ~0x7F) != 0) {
            output.write(((value & 0x7F) | 0x80) ^ BYTE_XOR_KEY);
            value >>>= 7;
        }
        output.write(value ^ BYTE_XOR_KEY);
    }

    private static void writeBoolean(ByteArrayOutputStream output, boolean value) {
        output.write((value ? 1 : 0) ^ BYTE_XOR_KEY);
    }

    private static void writeInt(ByteArrayOutputStream output, int value) throws IOException {
        new DataOutputStream(output).writeInt(value ^ INT_XOR_MASK);
    }

    private static void writeLong(ByteArrayOutputStream output, long value) throws IOException {
        new DataOutputStream(output).writeLong(value ^ LONG_XOR_MASK);
    }

    private static void writeDouble(ByteArrayOutputStream output, double value) throws IOException {
        writeLong(output, Double.doubleToRawLongBits(value));
    }
}
