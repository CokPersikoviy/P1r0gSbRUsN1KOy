package ru.wilyfox.client.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import ru.wilyfox.client.booster.BoosterStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void bossTimersUsesFixedMaskedLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVarInt(payload, 1);
        writeString(payload, "MYTHIC_BOSS");
        writeLong(payload, 128_000L);

        DwBossTimersPacket packet = DwBossTimersDecoder.decode(payload.toByteArray());

        assertEquals(128_000L, packet.timers().get("MYTHIC_BOSS"));
    }

    @Test
    void simpleCooldownUsesFixedMaskedLong() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeLong(payload, 128L);

        assertEquals(128L, DwCooldownValueDecoder.decode(payload.toByteArray()).remainingMillis());
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
