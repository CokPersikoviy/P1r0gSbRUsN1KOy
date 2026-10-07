package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;

/** Test-only injection at the DW packet boundary, without production test hooks. */
public final class SocialProtocolFixture {
    private SocialProtocolFixture() {}

    private static ProtocolState state() {
        try {
            Field field = DiamondWorldProtocolClient.class.getDeclaredField("STATE");
            field.setAccessible(true);
            return (ProtocolState) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    public static void token(String token) {
        var packet = Unpooled.buffer();
        try {
            DwProtocolCodec.writeString(packet, "token");
            packet.writeByte((token == null ? 0 : 1) ^ 103);
            if (token != null) DwProtocolCodec.writeString(packet, token);
            byte[] bytes = new byte[packet.readableBytes()];
            packet.readBytes(bytes);
            new ProtocolRouter().route(state(), bytes);
        } finally {
            packet.release();
        }
    }

    public static void location(String server, int mirror, String location) {
        ProtocolPayloadHandlers.applyServerInfo(state(), CurrentServerInfo.fromProtocol(server, mirror));
        state().currentGameLocation = new DwGameLocation(location);
    }

    public static void bossTypes(java.util.Map<String, DwBossType> types) {
        ProtocolPayloadHandlers.applyBossTypes(state(), new DwBossTypesPacket(types));
    }

    public static void quests(java.util.List<DwQuest> quests) {
        payload("questssetup", packet -> {
            DwProtocolCodec.writeVarInt(packet, quests.size());
            for (DwQuest quest : quests) {
                DwProtocolCodec.writeString(packet, quest.id());
                DwProtocolCodec.writeVarInt(packet, quest.dimension().ordinal());
                DwProtocolCodec.writeVarInt(packet, quest.category().ordinal());
                DwProtocolCodec.writeString(packet, quest.name());
                DwProtocolCodec.writeString(packet, quest.description());
                DwProtocolCodec.writeVarInt(packet, quest.progress());
                DwProtocolCodec.writeVarInt(packet, quest.required());
            }
        });
    }

    public static void questUpdate(String id, int progress) {
        payload("questupdate", packet -> {
            DwProtocolCodec.writeString(packet, id);
            DwProtocolCodec.writeVarInt(packet, progress);
        });
    }

    public static void hourlyQuests(DwHourlyQuestType type, DwHourlyQuestProgress progress) {
        state().hourlyQuestTypes.put(type.id(), type);
        state().hourlyQuestProgress.put(progress.id(), progress);
    }

    public static void clearQuests() {
        state().quests.clear();
        state().hourlyQuestTypes.clear();
        state().hourlyQuestProgress.clear();
    }

    private static void payload(String channel, java.util.function.Consumer<io.netty.buffer.ByteBuf> writer) {
        var packet = Unpooled.buffer();
        try {
            DwProtocolCodec.writeString(packet, channel);
            writer.accept(packet);
            byte[] bytes = new byte[packet.readableBytes()];
            packet.readBytes(bytes);
            new ProtocolRouter().route(state(), bytes);
        } finally {
            packet.release();
        }
    }

    public static void clear() {
        state().gameToken = null;
        state().currentServerInfo = CurrentServerInfo.unknown();
        state().serverDisplay = new ServerDisplayState();
        state().currentGameLocation = null;
    }
}
