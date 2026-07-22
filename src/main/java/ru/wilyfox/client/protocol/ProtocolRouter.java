package ru.wilyfox.client.protocol;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;
import ru.wilyfox.client.profiler.ModProfiler;

final class ProtocolRouter {
    void route(ProtocolState state, byte[] data) {
        try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("protocol/route")) {
        ProtocolEnvelope envelope = tryReadEnvelope(data);
        int length = data.length;

        if (envelope != null) {
            state.diagnostics.onPayloadReceived(envelope.typeId());
            info(LOGGER, "DW protocol: received dw:evoplus payload, bytes={}, typeId={}", length, envelope.typeId());
            ProtocolGraphTelemetry.getInstance().onPayloadReceived(envelope.typeId(), envelope.body().length);
            ProtocolDebugLogger.logPayloadSampleIfNeeded(state, envelope.typeId(), envelope.body());
        } else {
            info(LOGGER, "DW protocol: received dw:evoplus payload, bytes={}, typeId=<unreadable>", length);
            state.diagnostics.onUnreadableEnvelope(length);
            ProtocolDebugLogger.logUnknownPayload("unreadable typeId", data);
            return;
        }

        String typeId = envelope.typeId();
        byte[] body = envelope.body();

        switch (typeId) {
            case "bosstimers" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleBossTimers(state, body), state);
            case "bosstypes" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleBossTypes(state, body), state);
            case "activerunes" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleActiveRunes(state, body), state);
            case "serverinfo" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleServerInfo(state, body), state);
            case "pettypes" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handlePetTypes(state, body), state);
            case "potiontypes" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handlePotionTypes(state, body), state);
            case "sellers" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleSellers(state, body), state);
            case "combo" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleCombo(state, body), state);
            case "comboblocks" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleComboBlocks(state, body), state);
            case "potiontimers" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handlePotionTimers(state, body), state);
            case "statisticinfo" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleStatisticInfo(state, body), state);
            case "fishingpots" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleFishingSpots(state, body), state);
            case "spotnibbles" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleSpotNibbles(state, body), state);
            case "hourlyquestypes" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleHourlyQuestTypes(state, body), state);
            case "hourlyquestinfo" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleHourlyQuestInfo(state, body), state);
            case "stafftypes" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleStaffTypes(state, body), state);
            case "stafftimers" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleStaffTimers(state, body), state);
            case "abilitytypes" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleAbilityTypes(state, body), state);
            case "abilitytimers" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleAbilityTimers(state, body), state);
            case "bossdamage" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleBossDamage(state, body), state);
            case "bosscollect" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleBossCollect(state, body), state);
            case "levelinfo" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleLevelInfo(state, body), state);
            case "harpooncd" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleHarpoonCooldown(state, body), state);
            case "marketcd" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleNamedCooldown("marketcd", "Market", body), state);
            case "gourmetcd" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleGourmetCooldown(state, body), state);
            case "potioncd" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handlePotionCooldowns(state, body), state);
            case "token" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleToken(body), state);
            case "boosters" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleBoosters(state, body), state);
            case "gameevent" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleGameEvent(state, body), state);
            case "claninfo" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleClanInfo(state, body), state);
            case "siegepos" -> dispatch(typeId, body, () -> ProtocolPayloadHandlers.handleClanSiegePosition(state, body), state);
            default -> {
                state.diagnostics.onUnknownType(typeId, body.length);
                ProtocolDebugLogger.logUnknownPayloadBody("unknown typeId=" + typeId, typeId, body);
            }
        }
        }
    }

    private void dispatch(String subchannel, byte[] data, PayloadHandler handler, ProtocolState state) {
        try {
            boolean success;
            try (ModProfiler.Scope ignored = ModProfiler.getInstance().scope("protocol/handle/" + subchannel)) {
                success = handler.handle();
            }
            ProtocolGraphTelemetry.getInstance().onRouteHandled(subchannel, success, data.length);
            if (!success) {
                state.diagnostics.onDecodeFailure(subchannel, data.length, null);
                ProtocolDebugLogger.logDecodeFailure(subchannel, data, null);
            }
        } catch (Exception exception) {
            ProtocolGraphTelemetry.getInstance().onRouteHandled(subchannel, false, data.length);
            state.diagnostics.onDecodeFailure(subchannel, data.length, exception);
            ProtocolDebugLogger.logDecodeFailure(subchannel, data, exception);
            throw exception;
        }
    }

    private ProtocolEnvelope tryReadEnvelope(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

        try {
            String typeId = DwProtocolCodec.readString(buf);
            byte[] body = new byte[buf.readableBytes()];
            buf.readBytes(body);
            return new ProtocolEnvelope(typeId, body);
        } catch (Exception ignored) {
            return null;
        } finally {
            buf.release();
        }
    }

    private record ProtocolEnvelope(String typeId, byte[] body) {
    }

    @FunctionalInterface
    private interface PayloadHandler {
        boolean handle();
    }
}
