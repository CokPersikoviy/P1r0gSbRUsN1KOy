package ru.wilyfox.client.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Executes only offline packet data classes and their buffer codecs from EvoPlus 3.3.2. */
@EnabledIfSystemProperty(named = "froghelper.evoReference", matches = ".+")
class EvoPacketSchemaCompatibilityTest {
    private static final Map<String, String> PACKET_CLASSES = Map.ofEntries(
            Map.entry("abilitytimers", "SLaDK1yPersIK0v1ySoK$946209364.sLAdkiYperS1KoV1YS0k$859560684"),
            Map.entry("abilitytypes", "slAdKIYPErSIkOV1YsOK$527333077.SlADkIYP3Rs1kOv1ysOK$892606469"),
            Map.entry("activerunes", "slaDkIyp3Rs1K0v1ySOk$765186776.slAdKIYp3RSIk0V1ySoK$194427473"),
            Map.entry("boosters", "sLAdkIyp3rS1KoVIys0K$505468041.SLadK1yPeRS1KoVIysoK$71526498"),
            Map.entry("bosscollect", "sLadk1yp3RS1KOViySOk$303378640.sLaDK1YpErS1K0ViYSoK$330526401"),
            Map.entry("bossdamage", "Sladk1Yp3rsIK0v1ysok$125296240.sladkIypERs1K0V1ySOK$619980633"),
            Map.entry("bosstimers", "sLADK1Yp3Rs1KoV1YS0k$50064812.slaDk1YPERSIk0vIYS0k$614362458"),
            Map.entry("bosstypes", "SLADKIyPeRs1k0v1YSoK$265028536.sLaDKIYP3rs1kOviYs0k$479862288"),
            Map.entry("claninfo", "sLaDK1Yp3RS1kOVIYs0k$911034535.SlAdKiyp3RS1k0ViysOK$999205485"),
            Map.entry("combo", "sladkIyPERSik0VIys0K$412872136.sladKIYPERs1K0VIYSok$29143822"),
            Map.entry("comboblocks", "slaDKIYPeRsIKOviys0K$355952904.slADk1YpERsIK0v1ys0k$513930078"),
            Map.entry("dungeonpos", "SladKIYP3RS1kOVIYs0k$876849549.SLaDkIyp3rs1K0ViysOk$49673840"),
            Map.entry("fishingpots", "SlADK1yperS1k0V1ySok$729210366.SLAdkiyp3Rs1Kov1YS0K$686223949"),
            Map.entry("gameevent", "sladkIyPERSik0VIys0K$412872136.sLadKIyp3Rs1K0VIYsok$730360102"),
            Map.entry("gourmetcd", "sLaDK1Yp3RS1kOVIYs0k$911034535.SLAdk1Yp3rS1koV1Ys0K$754777846"),
            Map.entry("harpooncd", "sLadk1yp3RS1KOViySOk$303378640.slAdkiYP3RS1k0V1YS0k$338621879"),
            Map.entry("hourlyquestinfo", "SladkIYPers1KoV1YS0k$647036676.SLaDKIYpErS1kOVIyS0k$160917385"),
            Map.entry("hourlyquestypes", "sLaDk1YpErsik0v1yS0k$242510409.SlaDK1yP3RSiK0v1yS0k$877380337"),
            Map.entry("levelinfo", "SladKiyP3RSIK0V1ysok$296426760.sLADKIyPeRsik0ViysOK$719736482"),
            Map.entry("marketcd", "sLaDk1YpErsik0v1yS0k$242510409.SlADK1YPErS1koV1yS0k$24767104"),
            Map.entry("pettypes", "SLaDK1yPersIK0v1ySoK$946209364.sLAdK1YP3Rs1kOv1ys0K$952332234"),
            Map.entry("potioncd", "sLaDk1YP3RS1kov1Ys0K$531995795.SlAdkiyPerS1KoVIYsoK$191824479"),
            Map.entry("potiontimers", "sLAdK1yPerSikoV1ySOk$494930154.Sladk1yP3Rs1k0VIYS0K$560741457"),
            Map.entry("potiontypes", "slAdKIYPErSIkOV1YsOK$527333077.SLaDk1yp3rS1K0Viys0K$609893631"),
            Map.entry("questssetup", "SLaDK1yPersIK0v1ySoK$946209364.sLAdkIYPERS1kovIysok$271676083"),
            Map.entry("questupdate", "SLADk1YP3Rs1kOV1YS0K$672406753.SlADk1YP3rSIkoVIys0K$258743520"),
            Map.entry("sellers", "sLADK1Yp3Rs1KoV1YS0k$50064812.SLaDkIyp3rSIK0vIYS0k$845629424"),
            Map.entry("serverinfo", "SladK1yp3rsIKOVIys0k$5617715.sLADk1YP3rS1k0v1YS0K$303044185"),
            Map.entry("siegepos", "SLaDK1yPersIK0v1ySoK$946209364.SlADk1Yp3RSIK0viySoK$590465448"),
            Map.entry("spotnibbles", "slaDk1yPERSIkov1Ys0k$631554143.slaDKiyPerS1KOVIYsOk$182735231"),
            Map.entry("stafftimers", "sladk1Yp3rSIkoV1Ysok$921356689.sLADk1YPERs1kOv1YS0K$761455404"),
            Map.entry("stafftypes", "SladkIYPers1KoV1YS0k$647036676.SladKIyP3rSIkoVIYsok$632160339"),
            Map.entry("statisticinfo", "sLADK1Yp3Rs1KoV1YS0k$50064812.SLaDKIyP3rSiKov1yS0K$862282309"),
            Map.entry("token", "sLaDK1Yp3RS1kOVIYs0k$911034535.SlADkIyperSIk0ViYs0K$516895041")
    );

    @TestFactory
    Stream<DynamicTest> packetSchemasMatchTheReal332ReadersAndWriters() {
        Map<String, Object[]> fixtures = new LinkedHashMap<>();
        fixtures.put("abilitytimers", fields(v(1), "WIND", 45_000L));
        fixtures.put("abilitytypes", fields(v(1), "WIND", "Wind"));
        fixtures.put("activerunes", fields(v(1), "FROG"));
        fixtures.put("boosters", fields(v(1), v(2), v(1), 45_000L, 2.5D));
        fixtures.put("bosscollect", fields(v(1), "MYTHIC_BOSS", v(1), "Loot"));
        fixtures.put("bossdamage", fields("MYTHIC_BOSS", 123_456));
        fixtures.put("bosstimers", fields(v(1), "MYTHIC_BOSS", 45_000L));
        fixtures.put("bosstypes", fields(v(1), "MYTHIC_BOSS", "Mythic Boss", "STONE", v(35), v(2), v(99), true));
        fixtures.put("claninfo", fields(v(1), "name", "\"Frogs\""));
        fixtures.put("combo", fields(2.5D, 3.5D, 12, 5_000));
        fixtures.put("comboblocks", fields(123));
        fixtures.put("dungeonpos", fields(64, 128));
        fixtures.put("fishingpots", fields(v(1), "bay", "Bay"));
        fixtures.put("gameevent", fields(v(1)));
        fixtures.put("gourmetcd", fields(45_000L));
        fixtures.put("harpooncd", fields(45_000L));
        fixtures.put("hourlyquestinfo", fields(v(1), 7, 12, 45_000L));
        fixtures.put("hourlyquestypes", fields(v(1), 7, "OVERWORLD", "Quest", "Lore", 20));
        fixtures.put("levelinfo", fields(v(42), 12_000D, 300, false, 14_000D, 1_000));
        fixtures.put("marketcd", fields(45_000L));
        fixtures.put("pettypes", fields(v(1), "WOLF", "Wolf", "LEGENDARY", "STONE", v(3)));
        fixtures.put("potioncd", fields(v(1), 3, 45_000L));
        fixtures.put("potiontimers", fields(v(1), 3, 100, 45_000L));
        fixtures.put("potiontypes", fields(v(1), "Potion", v(3)));
        fixtures.put("questssetup", fields(v(1), "quest-id", v(0), v(1), "Quest", "Lore", v(12), v(5_000)));
        fixtures.put("questupdate", fields("quest-id", v(13)));
        fixtures.put("sellers", fields(v(1), "seller-id", "Seller", 45_000L));
        fixtures.put("serverinfo", fields("PRISONEVO1", 2));
        fixtures.put("siegepos", fields(64, 128));
        fixtures.put("spotnibbles", fields(v(1), "bay", 0.5D));
        fixtures.put("stafftimers", fields(v(1), 3, 45_000L));
        fixtures.put("stafftypes", fields(v(1), "Staff", v(3)));
        fixtures.put("statisticinfo", fields(v(1), "blocks", "1234"));
        fixtures.put("token", fields(true, "header.claims.signature"));
        assertEquals(PACKET_CLASSES.keySet(), fixtures.keySet());
        Stream<DynamicTest> channels = fixtures.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey(), () -> assertReferenceRoundTrip(entry.getKey(), encode(entry.getValue()))));
        return Stream.concat(channels, Stream.of(
                DynamicTest.dynamicTest("levelinfo max-level branch", () -> assertReferenceRoundTrip(
                        "levelinfo", encode(v(43), 15_000D, 900, true))),
                DynamicTest.dynamicTest("nullable token", () -> assertReferenceRoundTrip("token", encode(false)))));
    }

    private void assertReferenceRoundTrip(String channel, byte[] payload) throws Exception {
        try (var loader = new URLClassLoader(new java.net.URL[]{
                Path.of(System.getProperty("froghelper.evoReference")).toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> packetClass = loader.loadClass(PACKET_CLASSES.get(channel));
            Object packet = packetClass.getConstructor().newInstance();
            ByteBuf input = Unpooled.wrappedBuffer(payload);
            ByteBuf output = Unpooled.buffer();
            try {
                packetClass.getMethod("sLADK1YPeRS1K0V1ys0K$915226922", ByteBuf.class).invoke(packet, input);
                assertEquals(0, input.readableBytes(), channel + " left unread input bytes");
                packetClass.getMethod("sLadkIyp3Rs1kOV1yS0K$904734075", ByteBuf.class).invoke(packet, output);
                byte[] actual = new byte[output.readableBytes()];
                output.readBytes(actual);
                assertArrayEquals(payload, actual, channel);
            } finally {
                input.release();
                output.release();
            }
        }
    }

    private static byte[] encode(Object... fields) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            for (Object field : fields) {
                switch (field) {
                    case VarInt value -> DwProtocolCodec.writeVarInt(buffer, value.value());
                    case String value -> DwProtocolCodec.writeString(buffer, value);
                    case Integer value -> buffer.writeInt(value ^ 0x67676767);
                    case Long value -> buffer.writeLong(value ^ 0x6767676767676767L);
                    case Double value -> buffer.writeLong(Double.doubleToRawLongBits(value) ^ 0x6767676767676767L);
                    case Boolean value -> buffer.writeByte((value ? 1 : 0) ^ 103);
                    default -> throw new IllegalArgumentException("Unsupported fixture field: " + field);
                }
            }
            byte[] payload = new byte[buffer.readableBytes()];
            buffer.readBytes(payload);
            return payload;
        } finally {
            buffer.release();
        }
    }

    private static Object[] fields(Object... fields) {
        return fields;
    }

    private static VarInt v(int value) {
        return new VarInt(value);
    }

    private record VarInt(int value) {
    }
}
