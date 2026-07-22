package ru.wilyfox.client.protocol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProtocolGraphTelemetry {
    public static final long ACTIVE_WINDOW_MS = 12_000L;
    public static final long EDGE_PULSE_WINDOW_MS = 950L;
    private static final int MAX_EDGE_PULSES = 6;
    private static final ProtocolGraphTelemetry INSTANCE = new ProtocolGraphTelemetry();
    private static final String ROOT_NODE_ID = "root:dw:evoplus";

    private final Map<String, NodeState> nodes = new LinkedHashMap<>();
    private final Map<String, EdgeState> edges = new LinkedHashMap<>();
    private final Map<String, RouteSpec> routes = new LinkedHashMap<>();

    private ProtocolGraphTelemetry() {
        registerNode(ROOT_NODE_ID, "dw:evoplus", GraphNodeKind.ROOT);

        registerRoute("bosstimers", "DwBossTimersDecoder", "handleBossTimers", outputs(store("bossRepository"), widget("BossHudWidget")));
        registerRoute("bosstypes", "DwBossTypesDecoder", "handleBossTypes", outputs(state("bossTypes"), widget("BossHudWidget")));
        registerRoute("activerunes", "DwActiveRunesDecoder", "handleActiveRunes", outputs(store("activeRunesStore"), widget("ActiveRunesWidget")));
        registerRoute("serverinfo", "DwServerInfoDecoder", "handleServerInfo", outputs(state("currentServerInfo")));
        registerRoute("pettypes", "DwPetTypesDecoder", "handlePetTypes", outputs(state("petTypes"), widget("ActivePetsWidget")));
        registerRoute("potiontypes", "DwPotionTypesDecoder", "handlePotionTypes", outputs(store("potionStore"), widget("PotionTimersWidget")));
        registerRoute("sellers", "DwSellersDecoder", "handleSellers", outputs(store("sellerCooldownStore"), widget("SellerCooldownWidget")));
        registerRoute("combo", "DwComboDecoder", "handleCombo", outputs(store("comboProgressStore"), widget("ComboProgressWidget")));
        registerRoute("comboblocks", "DwComboBlocksDecoder", "handleComboBlocks", outputs(store("comboProgressStore"), widget("ComboProgressWidget")));
        registerRoute("potiontimers", "DwPotionTimersDecoder", "handlePotionTimers", outputs(store("potionStore")));
        registerRoute("statisticinfo", "DwStatisticInfoDecoder", "handleStatisticInfo", outputs(store("activePetsStore"), store("activeMinersStore"), state("currentGameLocation")));
        registerRoute("fishingpots", "DwFishingSpotsDecoder", "handleFishingSpots", outputs(state("fishingLocationIds"), widget("FishingNibblesWidget")));
        registerRoute("spotnibbles", "DwSpotNibblesDecoder", "handleSpotNibbles", outputs(state("fishingNibbles"), widget("FishingNibblesWidget")));
        registerRoute("hourlyquestypes", "DwHourlyQuestTypesDecoder", "handleHourlyQuestTypes", outputs(state("hourlyQuestTypes"), widget("FishingQuestsWidget")));
        registerRoute("hourlyquestinfo", "DwHourlyQuestInfoDecoder", "handleHourlyQuestInfo", outputs(state("hourlyQuestProgress"), widget("FishingQuestsWidget")));
        registerRoute("stafftypes", "DwStaffTypesDecoder", "handleStaffTypes", outputs(state("staffTypes"), widget("WandCooldownWidget")));
        registerRoute("stafftimers", "DwStaffTimersDecoder", "handleStaffTimers", outputs(store("WandCooldownTracker"), widget("WandCooldownWidget")));
        registerRoute("abilitytypes", "DwAbilityTypesDecoder", "handleAbilityTypes", outputs(state("abilityTypes"), widget("AbilityCooldownWidget")));
        registerRoute("abilitytimers", "DwAbilityTimersDecoder", "handleAbilityTimers", outputs(store("abilityCooldownStore"), widget("AbilityCooldownWidget")));
        registerRoute("bossdamage", "DwBossDamageDecoder", "handleBossDamage", outputs(store("bossDamageStore"), widget("BossDamageWidget")));
        registerRoute("bosscollect", "DwBossCollectDecoder", "handleBossCollect", outputs(state("bossCollectibles"), widget("BossHudWidget")));
        registerRoute("levelinfo", "DwLevelInfoDecoder", "handleLevelInfo", outputs(store("levelProgressStore"), widget("LevelProgressWidget")));
        registerRoute("harpooncd", "DwCooldownValueDecoder", "handleHarpoonCooldown", outputs(store("WandCooldownTracker"), widget("WandCooldownWidget")));
        registerRoute("marketcd", "DwCooldownValueDecoder", "handleNamedCooldown", outputs(state("externalCooldowns")));
        registerRoute("gourmetcd", "DwCooldownValueDecoder", "handleGourmetCooldown", outputs(store("abilityCooldownStore"), widget("AbilityCooldownWidget")));
        registerRoute("potioncd", "DwPotionCooldownsDecoder", "handlePotionCooldowns", outputs(store("potionStore"), widget("PotionTimersWidget")));
        registerRoute("token", "DwTokenDecoder", "handleToken", outputs(state("token")));
        registerRoute("boosters", "DwBoostersDecoder", "handleBoosters", outputs(store("boosterStore"), widget("BoostersWidget")));
        registerRoute("gameevent", "DwGameEventDecoder", "handleGameEvent", outputs(state("currentGameEvent")));
        registerRoute("claninfo", "DwClanInfoDecoder", "handleClanInfo", outputs(state("clanInfo")));
        registerRoute("siegepos", "DwClanSiegePositionDecoder", "handleClanSiegePosition", outputs(state("clanSiegePosition"), widget("DungeonMapWidget")));
    }

    public static ProtocolGraphTelemetry getInstance() {
        return INSTANCE;
    }

    public synchronized void onPayloadReceived(String subchannel, int bodyBytes) {
        long now = System.currentTimeMillis();
        RouteSpec route = routes.get(subchannel);

        touchNode(ROOT_NODE_ID, now, true, bodyBytes);
        if (route == null) {
            String inputNodeId = registerDynamicInputNode(subchannel);
            touchNode(inputNodeId, now, true, bodyBytes);
            touchEdge(ROOT_NODE_ID, inputNodeId, now, true);
            return;
        }

        touchNode(route.inputNodeId(), now, true, bodyBytes);
        touchEdge(ROOT_NODE_ID, route.inputNodeId(), now, true);
    }

    public synchronized void onRouteHandled(String subchannel, boolean success, int bodyBytes) {
        long now = System.currentTimeMillis();
        RouteSpec route = routes.get(subchannel);
        if (route == null) {
            return;
        }

        touchNode(route.decoderNodeId(), now, success, bodyBytes);
        touchNode(route.handlerNodeId(), now, success, bodyBytes);
        touchEdge(route.inputNodeId(), route.decoderNodeId(), now, success);
        touchEdge(route.decoderNodeId(), route.handlerNodeId(), now, success);

        if (!success) {
            return;
        }

        for (OutputSpec output : route.outputs()) {
            touchNode(output.nodeId(), now, true, bodyBytes);
            touchEdge(route.handlerNodeId(), output.nodeId(), now, true);
        }
    }

    public synchronized void reset() {
        for (NodeState node : nodes.values()) {
            node.hits = 0;
            node.errors = 0;
            node.bytes = 0L;
            node.lastActiveAt = 0L;
        }

        for (EdgeState edge : edges.values()) {
            edge.hits = 0;
            edge.errors = 0;
            edge.lastActiveAt = 0L;
            edge.pulseStartedAt.clear();
        }
    }

    public synchronized GraphSnapshot snapshot() {
        List<GraphNodeSnapshot> nodeSnapshots = nodes.values().stream()
                .map(node -> new GraphNodeSnapshot(
                        node.id,
                        node.label,
                        node.kind,
                        node.hits,
                        node.errors,
                        node.bytes,
                        node.lastActiveAt
                ))
                .toList();
        List<GraphEdgeSnapshot> edgeSnapshots = edges.values().stream()
                .map(edge -> new GraphEdgeSnapshot(
                        edge.fromNodeId,
                        edge.toNodeId,
                        edge.hits,
                        edge.errors,
                        edge.lastActiveAt,
                        List.copyOf(edge.pulseStartedAt)
                ))
                .toList();
        return new GraphSnapshot(nodeSnapshots, edgeSnapshots, System.currentTimeMillis());
    }

    private void registerRoute(String subchannel, String decoderLabel, String handlerLabel, List<OutputSpec> outputs) {
        String inputNodeId = "input:" + subchannel;
        String decoderNodeId = "decoder:" + subchannel;
        String handlerNodeId = "handler:" + subchannel;

        registerNode(inputNodeId, subchannel, GraphNodeKind.INPUT);
        registerNode(decoderNodeId, decoderLabel, GraphNodeKind.DECODER);
        registerNode(handlerNodeId, handlerLabel, GraphNodeKind.HANDLER);
        touchStaticEdge(ROOT_NODE_ID, inputNodeId);
        touchStaticEdge(inputNodeId, decoderNodeId);
        touchStaticEdge(decoderNodeId, handlerNodeId);

        for (OutputSpec output : outputs) {
            registerNode(output.nodeId(), output.label(), output.kind());
            touchStaticEdge(handlerNodeId, output.nodeId());
        }

        routes.put(subchannel, new RouteSpec(subchannel, inputNodeId, decoderNodeId, handlerNodeId, List.copyOf(outputs)));
    }

    private List<OutputSpec> outputs(OutputSpec... specs) {
        List<OutputSpec> outputs = new ArrayList<>();
        for (OutputSpec spec : specs) {
            outputs.add(spec);
        }
        return outputs;
    }

    private OutputSpec store(String label) {
        return new OutputSpec("store:" + normalizeId(label), label, GraphNodeKind.STORE);
    }

    private OutputSpec state(String label) {
        return new OutputSpec("state:" + normalizeId(label), label, GraphNodeKind.STATE);
    }

    private OutputSpec widget(String label) {
        return new OutputSpec("widget:" + normalizeId(label), label, GraphNodeKind.WIDGET);
    }

    private String registerDynamicInputNode(String subchannel) {
        String inputNodeId = "input:" + normalizeId(subchannel);
        registerNode(inputNodeId, subchannel, GraphNodeKind.INPUT);
        touchStaticEdge(ROOT_NODE_ID, inputNodeId);
        return inputNodeId;
    }

    private void registerNode(String id, String label, GraphNodeKind kind) {
        nodes.computeIfAbsent(id, ignored -> new NodeState(id, label, kind));
    }

    private void touchStaticEdge(String fromNodeId, String toNodeId) {
        edge(fromNodeId, toNodeId);
    }

    private void touchNode(String nodeId, long now, boolean success, int bodyBytes) {
        NodeState node = nodes.get(nodeId);
        if (node == null) {
            return;
        }

        node.hits++;
        node.bytes += Math.max(0, bodyBytes);
        node.lastActiveAt = now;
        if (!success) {
            node.errors++;
        }
    }

    private void touchEdge(String fromNodeId, String toNodeId, long now, boolean success) {
        EdgeState edge = edge(fromNodeId, toNodeId);
        edge.hits++;
        edge.lastActiveAt = now;
        edge.pulseStartedAt.addLast(now);
        while (edge.pulseStartedAt.size() > MAX_EDGE_PULSES) {
            edge.pulseStartedAt.removeFirst();
        }
        while (!edge.pulseStartedAt.isEmpty() && now - edge.pulseStartedAt.peekFirst() > EDGE_PULSE_WINDOW_MS) {
            edge.pulseStartedAt.removeFirst();
        }
        if (!success) {
            edge.errors++;
        }
    }

    private EdgeState edge(String fromNodeId, String toNodeId) {
        String edgeId = fromNodeId + "->" + toNodeId;
        return edges.computeIfAbsent(edgeId, ignored -> new EdgeState(fromNodeId, toNodeId));
    }

    private String normalizeId(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    public enum GraphNodeKind {
        ROOT,
        INPUT,
        DECODER,
        HANDLER,
        STORE,
        STATE,
        WIDGET
    }

    public record GraphSnapshot(List<GraphNodeSnapshot> nodes, List<GraphEdgeSnapshot> edges, long capturedAt) {
        public Collection<GraphNodeSnapshot> nodesByKind(GraphNodeKind kind) {
            return nodes.stream()
                    .filter(node -> node.kind() == kind)
                    .sorted(Comparator.comparingLong(GraphNodeSnapshot::lastActiveAt).reversed().thenComparing(GraphNodeSnapshot::label))
                    .toList();
        }
    }

    public record GraphNodeSnapshot(
            String id,
            String label,
            GraphNodeKind kind,
            int hits,
            int errors,
            long bytes,
            long lastActiveAt
    ) {
    }

    public record GraphEdgeSnapshot(
            String fromNodeId,
            String toNodeId,
            int hits,
            int errors,
            long lastActiveAt,
            List<Long> pulseStartedAt
    ) {
    }

    private record OutputSpec(String nodeId, String label, GraphNodeKind kind) {
    }

    private record RouteSpec(
            String subchannel,
            String inputNodeId,
            String decoderNodeId,
            String handlerNodeId,
            List<OutputSpec> outputs
    ) {
    }

    private static final class NodeState {
        private final String id;
        private final String label;
        private final GraphNodeKind kind;
        private int hits;
        private int errors;
        private long bytes;
        private long lastActiveAt;

        private NodeState(String id, String label, GraphNodeKind kind) {
            this.id = id;
            this.label = label;
            this.kind = kind;
        }
    }

    private static final class EdgeState {
        private final String fromNodeId;
        private final String toNodeId;
        private final Deque<Long> pulseStartedAt = new LinkedList<>();
        private int hits;
        private int errors;
        private long lastActiveAt;

        private EdgeState(String fromNodeId, String toNodeId) {
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
        }
    }
}
