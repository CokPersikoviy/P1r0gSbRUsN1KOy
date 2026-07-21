package ru.wilyfox.client.protocol;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.wilyfox.client.boss.BossDamageInfo;
import ru.wilyfox.client.miner.ActiveMinerInfo;
import ru.wilyfox.client.pet.ActivePetInfo;
import ru.wilyfox.client.rune.RuneSetCooldownStore;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.wilyfox.FrogHelper.LOGGER;
import static ru.wilyfox.client.debug.DebugLogger.info;
import static ru.wilyfox.client.debug.DebugLogger.warn;

final class ProtocolPayloadHandlers {
    private static final int BOSSTIMER_PREVIEW_LIMIT = 8;
    private static final int BOSSTYPE_PREVIEW_LIMIT = 6;
    private static final int ACTIVE_RUNES_PREVIEW_LIMIT = 5;
    private static final int PETTYPE_PREVIEW_LIMIT = 6;
    private static final int ACTIVE_PETS_PREVIEW_LIMIT = 6;
    private static final int ABILITY_PREVIEW_LIMIT = 6;
    private static final int STAFF_PREVIEW_LIMIT = 6;
    private static final int POTION_PREVIEW_LIMIT = 6;
    private static final int BOOSTER_PREVIEW_LIMIT = 6;
    private static final int BOSSCOLLECT_GROUP_PREVIEW_LIMIT = 6;
    private static final int BOSSCOLLECT_VALUE_PREVIEW_LIMIT = 4;

    private ProtocolPayloadHandlers() {
    }

    static boolean handleBossTimers(ProtocolState state, byte[] data) {
        try {
            DwBossTimersPacket packet = DwBossTimersDecoder.decode(data);
            applyBossTimers(state, packet);

            if (packet.timers().isEmpty()) {
                info(LOGGER, "DW protocol: bosstimers parsed successfully, entries=0");
                return true;
            }

            long min = packet.timers().values().stream().min(Long::compareTo).orElse(0L);
            long max = packet.timers().values().stream().max(Long::compareTo).orElse(0L);
            String preview = packet.timers().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(BOSSTIMER_PREVIEW_LIMIT)
                    .map(entry -> entry.getKey() + "=" + ProtocolPayloadSupport.formatRemainingMillis(entry.getValue()))
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: bosstimers parsed successfully, entries={}, min={}, max={}, first={}",
                    packet.timers().size(),
                    ProtocolPayloadSupport.formatRemainingMillis(min),
                    ProtocolPayloadSupport.formatRemainingMillis(max),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse bosstimers payload", exception);
            return false;
        }
    }

    static boolean handleBossTypes(ProtocolState state, byte[] data) {
        try {
            DwBossTypesPacket packet = DwBossTypesDecoder.decode(data);
            state.bossTypes = new LinkedHashMap<>(packet.types());
            if (state.bossRepository != null) {
                packet.types().forEach((id, type) ->
                        state.bossRepository.updateProtocolMetadata(id, type.name(), type.level()));
            }

            if (packet.types().isEmpty()) {
                info(LOGGER, "DW protocol: bosstypes parsed successfully, entries=0");
                return true;
            }

            String preview = packet.types().values().stream()
                    .sorted(Comparator.comparingInt(DwBossType::level))
                    .limit(BOSSTYPE_PREVIEW_LIMIT)
                    .map(type -> type.id() + "=" + type.name() + " lvl " + type.level())
                    .collect(Collectors.joining(", "));

            String iconPreview = packet.types().values().stream()
                    .sorted(Comparator.comparingInt(DwBossType::level))
                    .limit(BOSSTYPE_PREVIEW_LIMIT)
                    .map(type -> type.id() + "={" + type.material() + ", cmd=" + type.customModelData() + "}")
                    .collect(Collectors.joining(", "));

            long raidCount = packet.types().values().stream().filter(DwBossType::raid).count();
            info(
                    LOGGER,
                    "DW protocol: bosstypes parsed successfully, entries={}, raids={}, first={}",
                    packet.types().size(),
                    raidCount,
                    preview
            );
            info(LOGGER, "DW protocol: bosstypes icon preview={}", iconPreview);
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse bosstypes payload", exception);
            return false;
        }
    }

    static boolean handleActiveRunes(ProtocolState state, byte[] data) {
        try {
            DwActiveRunesPacket packet = DwActiveRunesDecoder.decode(data);

            if (state.activeRunesStore != null) {
                state.activeRunesStore.replace(packet.runes());
            }

            String preview = packet.runes().stream()
                    .limit(ACTIVE_RUNES_PREVIEW_LIMIT)
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: activerunes parsed successfully, entries={}, first={}",
                    packet.runes().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse activerunes payload", exception);
            return false;
        }
    }

    static boolean handleServerInfo(ProtocolState state, byte[] data) {
        try {
            CurrentServerInfo serverInfo = DwServerInfoDecoder.decode(data);
            state.currentServerInfo = serverInfo;
            info(
                    LOGGER,
                    "DW protocol: serverinfo parsed successfully, family={}, server={}, mirror={}, display={}",
                    serverInfo.family(),
                    serverInfo.serverNumber(),
                    serverInfo.mirror(),
                    serverInfo.displayName()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse serverinfo payload", exception);
            return false;
        }
    }

    static boolean handlePetTypes(ProtocolState state, byte[] data) {
        try {
            DwPetTypesPacket packet = DwPetTypesDecoder.decode(data);
            state.petTypes = new LinkedHashMap<>(packet.types());
            if (state.activePetsStore != null && !state.activePetsStore.isEmpty()) {
                state.activePetsStore.replace(state.activePetsStore.getAll().stream()
                        .map(pet -> ProtocolPayloadSupport.enrichActivePet(state, pet))
                        .toList());
            }

            if (packet.types().isEmpty()) {
                info(LOGGER, "DW protocol: pettypes parsed successfully, entries=0");
                return true;
            }

            String preview = packet.types().values().stream()
                    .limit(PETTYPE_PREVIEW_LIMIT)
                    .map(type -> type.id() + "=" + type.name())
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: pettypes parsed successfully, entries={}, first={}",
                    packet.types().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse pettypes payload", exception);
            return false;
        }
    }

    static boolean handlePotionTypes(ProtocolState state, byte[] data) {
        try {
            DwPotionTypesPacket packet = DwPotionTypesDecoder.decode(data);

            if (state.potionStore != null) {
                state.potionStore.replaceTypes(packet.entries());
            }

            String preview = packet.entries().stream()
                    .limit(PETTYPE_PREVIEW_LIMIT)
                    .map(entry -> entry.id() + "={" + entry.modelId() + ", " + entry.name() + "}")
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: potiontypes parsed successfully, entries={}, first={}",
                    packet.entries().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse potiontypes payload", exception);
            return false;
        }
    }

    static boolean handleSellers(ProtocolState state, byte[] data) {
        try {
            DwSellersPacket packet = DwSellersDecoder.decode(data);

            if (state.sellerCooldownStore != null) {
                state.sellerCooldownStore.replace(packet.entries());
            }

            String preview = packet.entries().stream()
                    .limit(6)
                    .map(entry -> entry.id() + "=" + entry.name() + " (" + (entry.remainingMillis() < 0L ? "ready" : ProtocolPayloadSupport.formatRemainingMillis(entry.remainingMillis())) + ")")
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: sellers parsed successfully, entries={}, first={}",
                    packet.entries().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse sellers payload", exception);
            return false;
        }
    }

    static boolean handleCombo(ProtocolState state, byte[] data) {
        try {
            DwComboPacket packet = DwComboDecoder.decode(data);

            if (state.comboProgressStore != null) {
                state.comboProgressStore.updateCombo(packet.booster(), packet.nextBooster(), packet.blocks(), packet.requiredBlocks());
            }

            info(
                    LOGGER,
                    "DW protocol: combo parsed successfully, booster=x{}, next=x{}, blocks={}/{}, progress={}%",
                    ProtocolPayloadSupport.formatCompactMultiplier(packet.booster()),
                    ProtocolPayloadSupport.formatCompactMultiplier(packet.nextBooster()),
                    packet.blocks(),
                    packet.requiredBlocks(),
                    packet.requiredBlocks() <= 0 ? 0 : Math.round(packet.blocks() * 100.0D / packet.requiredBlocks())
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse combo payload", exception);
            return false;
        }
    }

    static boolean handleComboBlocks(ProtocolState state, byte[] data) {
        try {
            DwComboBlocksPacket packet = DwComboBlocksDecoder.decode(data);

            if (state.comboProgressStore != null) {
                state.comboProgressStore.updateBlocks(packet.blocks());
            }

            info(LOGGER, "DW protocol: comboblocks parsed successfully, blocks={}", packet.blocks());
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse comboblocks payload", exception);
            return false;
        }
    }

    static boolean handlePotionTimers(ProtocolState state, byte[] data) {
        try {
            DwPotionTimersPacket packet = DwPotionTimersDecoder.decode(data);

            if (state.potionStore != null) {
                state.potionStore.applyUpdate(packet.entries());
            }

            String preview = packet.entries().stream()
                    .limit(8)
                    .map(entry -> entry.id() + "=" + ProtocolPayloadSupport.formatRemainingMillis(entry.remainedMillis()) + " (" + entry.quality() + "%)")
                    .collect(Collectors.joining(", "));

            long activeCount = packet.entries().stream()
                    .filter(entry -> entry.remainedMillis() > 0L && entry.quality() > 0)
                    .count();

            info(
                    LOGGER,
                    "DW protocol: potiontimers parsed successfully, entries={}, active={}, first={}",
                    packet.entries().size(),
                    activeCount,
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse potiontimers payload", exception);
            return false;
        }
    }

    static boolean handleStatisticInfo(ProtocolState state, byte[] data) {
        try {
            DwStatisticInfoPacket packet = DwStatisticInfoDecoder.decode(data);
            List<ActivePetInfo> activePets = ProtocolPayloadSupport.extractActivePets(state, packet);
            List<ActiveMinerInfo> activeMiners = ProtocolPayloadSupport.extractActiveMiners(state, packet);
            String keysPreview = packet.values().keySet().stream()
                    .sorted()
                    .collect(Collectors.joining(", "));
            String gameLocation = ProtocolPayloadSupport.firstNonBlank(
                    packet.values().get("gameLocation"),
                    packet.values().get("location"),
                    packet.values().get("loc")
            );
            if (gameLocation != null) {
                state.currentGameLocation = ProtocolPayloadSupport.normalizeStatisticString(gameLocation);
            }

            if (state.activePetsStore != null) {
                if (packet.values().containsKey("pets")) {
                    state.activePetsStore.replace(activePets);
                } else {
                    state.activePetsStore.merge(activePets);
                }
            }

            if (state.activeMinersStore != null) {
                if (packet.values().containsKey("miners")) {
                    state.activeMinersStore.replace(activeMiners);
                } else {
                    state.activeMinersStore.merge(activeMiners);
                }
            }

            if (state.levelProgressStore != null) {
                state.levelProgressStore.updateCurrent(
                        packet.values().containsKey("level") ? ProtocolPayloadSupport.getInt(packet.values(), "level") : null,
                        packet.values().containsKey("blocks") ? ProtocolPayloadSupport.getInt(packet.values(), "blocks") : null,
                        packet.values().containsKey("balance") ? ProtocolPayloadSupport.getDouble(packet.values(), "balance") : null
                );
            }

            String preview = activePets.stream()
                    .limit(ACTIVE_PETS_PREVIEW_LIMIT)
                    .map(pet -> pet.name() + " [" + pet.level() + "] " + ProtocolPayloadSupport.formatEnergy(pet.energy()) + "\u26a1")
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: statisticinfo parsed successfully, keys={}, activePets={}, activeMiners={}, first={}",
                    packet.values().size(),
                    activePets.size(),
                    activeMiners.size(),
                    preview
            );
            info(LOGGER, "DW protocol: statisticinfo keys=[{}]", keysPreview);
            if (state.currentGameLocation != null) {
                info(LOGGER, "DW protocol: statisticinfo location candidate={}", state.currentGameLocation);
            }
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse statisticinfo payload", exception);
            return false;
        }
    }

    static boolean handleLevelInfo(ProtocolState state, byte[] data) {
        try {
            DwLevelInfoPacket packet = DwLevelInfoDecoder.decode(data);

            if (state.levelProgressStore != null) {
                state.levelProgressStore.updateCurrent(packet.level(), packet.blocks(), packet.money());
                state.levelProgressStore.updateRequirements(packet.requiredBlocks(), packet.requiredMoney(), packet.maxLevel());
            }

            info(
                    LOGGER,
                    "DW protocol: levelinfo parsed successfully, level={}, blocks={}, money={}, requiredBlocks={}, requiredMoney={}, max={}",
                    packet.level(),
                    packet.blocks(),
                    ProtocolPayloadSupport.formatCompactMoney(packet.money()),
                    packet.requiredBlocks(),
                    ProtocolPayloadSupport.formatCompactMoney(packet.requiredMoney()),
                    packet.maxLevel()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse levelinfo payload", exception);
            return false;
        }
    }

    static boolean handleFishingSpots(ProtocolState state, byte[] data) {
        try {
            DwFishingSpotsPacket packet = DwFishingSpotsDecoder.decode(data);
            LinkedHashSet<String> locationIds = packet.locations().keySet().stream()
                    .map(DiamondWorldProtocolClient::normalizeLocationId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, String> locationNames = packet.locations().entrySet().stream()
                    .map(entry -> Map.entry(
                            DiamondWorldProtocolClient.normalizeLocationId(entry.getKey()),
                            ProtocolPayloadSupport.normalizeStatisticString(entry.getValue())
                    ))
                    .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue() == null || entry.getValue().isBlank() ? entry.getKey() : entry.getValue(),
                            (left, right) -> right,
                            LinkedHashMap::new
                    ));

            state.fishingLocationIds.addAll(locationIds);
            state.fishingLocationNames.putAll(locationNames);

            String preview = packet.locations().entrySet().stream()
                    .limit(8)
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: fishingpots parsed successfully, entries={}, first={}",
                    packet.locations().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse fishingpots payload", exception);
            return false;
        }
    }

    static boolean handleSpotNibbles(ProtocolState state, byte[] data) {
        try {
            DwSpotNibblesPacket packet = DwSpotNibblesDecoder.decode(data);
            Map<String, Double> nibbles = packet.nibbles().entrySet().stream()
                    .map(entry -> Map.entry(
                            DiamondWorldProtocolClient.normalizeLocationId(entry.getKey()),
                            entry.getValue()
                    ))
                    .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue() == null ? 0.0D : entry.getValue(),
                            (left, right) -> right,
                            LinkedHashMap::new
                    ));
            state.fishingNibbles.putAll(nibbles);

            String preview = state.fishingNibbles.entrySet().stream()
                    .limit(8)
                    .map(entry -> entry.getKey() + "=" + String.format(java.util.Locale.ROOT, "%.3f", entry.getValue()))
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: spotnibbles parsed successfully, entries={}, first={}",
                    packet.nibbles().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse spotnibbles payload", exception);
            return false;
        }
    }

    static boolean handleStaffTypes(ProtocolState state, byte[] data) {
        try {
            DwStaffTypesPacket packet = DwStaffTypesDecoder.decode(data);
            state.staffTypes = new LinkedHashMap<>(packet.types());

            if (state.wandCooldownTracker != null) {
                state.wandCooldownTracker.replaceTypes(
                        packet.types().values().stream().collect(Collectors.toMap(DwStaffType::id, DwStaffType::name)),
                        packet.types().values().stream().collect(Collectors.toMap(DwStaffType::id, DwStaffType::modelId))
                );
            }

            String preview = packet.types().values().stream()
                    .limit(STAFF_PREVIEW_LIMIT)
                    .map(type -> type.id() + "=" + type.name() + " (cmd=" + type.modelId() + ")")
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: stafftypes parsed successfully, entries={}, first={}",
                    packet.types().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse stafftypes payload", exception);
            return false;
        }
    }

    static boolean handleStaffTimers(ProtocolState state, byte[] data) {
        try {
            DwStaffTimersPacket packet = DwStaffTimersDecoder.decode(data);
            if (state.wandCooldownTracker != null) {
                state.wandCooldownTracker.replaceProtocol(packet.timers());
            }

            String preview = packet.timers().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .limit(STAFF_PREVIEW_LIMIT)
                    .map(entry -> entry.getKey() + "=" + ProtocolPayloadSupport.formatRemainingMillis(entry.getValue()))
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: stafftimers parsed successfully, entries={}, first={}",
                    packet.timers().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse stafftimers payload", exception);
            return false;
        }
    }

    static boolean handleAbilityTypes(ProtocolState state, byte[] data) {
        try {
            DwAbilityTypesPacket packet = DwAbilityTypesDecoder.decode(data);
            state.abilityTypes = new LinkedHashMap<>(packet.types());

            if (state.abilityCooldownStore != null) {
                state.abilityCooldownStore.replaceTypes(
                        packet.types().values().stream().collect(Collectors.toMap(DwAbilityType::id, DwAbilityType::name))
                );
            }

            String preview = packet.types().values().stream()
                    .limit(ABILITY_PREVIEW_LIMIT)
                    .map(type -> type.id() + "=" + type.name())
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: abilitytypes parsed successfully, entries={}, first={}",
                    packet.types().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse abilitytypes payload", exception);
            return false;
        }
    }

    static boolean handleAbilityTimers(ProtocolState state, byte[] data) {
        try {
            DwAbilityTimersPacket packet = DwAbilityTimersDecoder.decode(data);
            long now = System.currentTimeMillis();
            boolean swapTriggered = ProtocolPayloadSupport.shouldTriggerRuneSetCooldown(state, packet.timers(), now);
            if (swapTriggered) {
                RuneSetCooldownStore.update(10_000L);
            }

            // TEMP diagnostic (render.debug) for T5 "first wind-rune press doesn't start the swap cd":
            // dumps each ability's decayed previous vs current remaining and the trigger decision, so we
            // can see WHY the first press isn't detected as a fresh ability use.
            if (ru.wilyfox.client.debug.DebugLogger.isEnabled()) {
                long elapsed = state.lastAbilityTimersAt > 0L ? Math.max(0L, now - state.lastAbilityTimersAt) : 0L;
                StringBuilder dump = new StringBuilder("\n=== ABILITYTIMERS (swap-cd trigger=")
                        .append(swapTriggered).append(", elapsed=").append(elapsed).append("ms) ===");
                for (Map.Entry<String, Long> entry : packet.timers().entrySet()) {
                    long prev = Math.max(0L, state.lastAbilityTimers.getOrDefault(entry.getKey(), 0L));
                    dump.append("\n  ").append(entry.getKey())
                            .append(": prevDecayed=").append(Math.max(0L, prev - elapsed))
                            .append(" current=").append(Math.max(0L, entry.getValue()));
                }
                info(LOGGER, "{}", dump.toString());
            }

            state.lastAbilityTimers = new LinkedHashMap<>(packet.timers());
            state.lastAbilityTimersAt = now;

            if (state.abilityCooldownStore != null) {
                state.abilityCooldownStore.replaceCooldowns(packet.timers());
            }

            String preview = packet.timers().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(ABILITY_PREVIEW_LIMIT)
                    .map(entry -> entry.getKey() + "=" + ProtocolPayloadSupport.formatRemainingMillis(entry.getValue()))
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: abilitytimers parsed successfully, entries={}, first={}",
                    packet.timers().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse abilitytimers payload", exception);
            return false;
        }
    }

    static boolean handleBossDamage(ProtocolState state, byte[] data) {
        try {
            DwBossDamagePacket packet = DwBossDamageDecoder.decode(data);
            DwBossType type = state.bossTypes.get(packet.bossId());
            String bossName = type != null ? type.name() : ProtocolPayloadSupport.prettifyId(packet.bossId());
            int bossLevel = type != null ? type.level() : 0;

            if (state.bossDamageStore != null) {
                state.bossDamageStore.update(new BossDamageInfo(
                        packet.bossId(),
                        bossName,
                        bossLevel,
                        packet.damage(),
                        System.currentTimeMillis()
                ));
            }

            info(
                    LOGGER,
                    "DW protocol: bossdamage parsed successfully, boss={} [{}], damage={}",
                    bossName,
                    bossLevel,
                    packet.damage()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse bossdamage payload", exception);
            return false;
        }
    }

    static boolean handleBossCollect(ProtocolState state, byte[] data) {
        try {
            DwBossCollectPacket packet = DwBossCollectDecoder.decode(data);
            state.bossCollectibles = new LinkedHashMap<>(packet.collectibles());

            long nonEmptyGroups = packet.collectibles().values().stream()
                    .filter(values -> !values.isEmpty())
                    .count();
            long totalEntries = packet.collectibles().values().stream()
                    .mapToLong(Set::size)
                    .sum();
            String preview = packet.collectibles().entrySet().stream()
                    .limit(BOSSCOLLECT_GROUP_PREVIEW_LIMIT)
                    .map(entry -> {
                        String valuesPreview = entry.getValue().stream()
                                .sorted()
                                .limit(BOSSCOLLECT_VALUE_PREVIEW_LIMIT)
                                .collect(Collectors.joining(", "));
                        String suffix = entry.getValue().size() > BOSSCOLLECT_VALUE_PREVIEW_LIMIT ? ", ..." : "";
                        return entry.getKey() + "=" + entry.getValue().size() + (valuesPreview.isBlank() ? "" : " [" + valuesPreview + suffix + "]");
                    })
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: bosscollect parsed successfully, groups={}, nonEmptyGroups={}, totalEntries={}, first={}",
                    packet.collectibles().size(),
                    nonEmptyGroups,
                    totalEntries,
                    preview.isBlank() ? "<empty>" : preview
            );

            if (!packet.collectibles().isEmpty()) {
                String emptyGroups = packet.collectibles().entrySet().stream()
                        .filter(entry -> entry.getValue().isEmpty())
                        .map(Map.Entry::getKey)
                        .sorted()
                        .limit(BOSSCOLLECT_GROUP_PREVIEW_LIMIT)
                        .collect(Collectors.joining(", "));
                if (!emptyGroups.isBlank()) {
                    info(LOGGER, "DW protocol: bosscollect empty groups preview={}", emptyGroups);
                }
            }

            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse bosscollect payload", exception);
            return false;
        }
    }

    static boolean handleGourmetCooldown(ProtocolState state, byte[] data) {
        try {
            DwCooldownValuePacket packet = DwCooldownValueDecoder.decode(data);
            long remaining = Math.max(0L, packet.remainingMillis());

            if (state.abilityCooldownStore != null) {
                state.abilityCooldownStore.replaceExternalCooldown("gourmetcd", "Гурман", remaining);
            }

            info(
                    LOGGER,
                    "DW protocol: gourmetcd parsed successfully, remaining={}, raw={}",
                    remaining > 0L ? ProtocolPayloadSupport.formatRemainingMillis(remaining) : "ready",
                    packet.remainingMillis()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse gourmetcd payload", exception);
            return false;
        }
    }

    static boolean handleHarpoonCooldown(ProtocolState state, byte[] data) {
        try {
            DwCooldownValuePacket packet = DwCooldownValueDecoder.decode(data);
            long remaining = Math.max(0L, packet.remainingMillis());

            if (state.wandCooldownTracker != null) {
                state.wandCooldownTracker.replaceSpecialCooldown(
                        "harpooncd",
                        new ItemStack(Items.TRIDENT),
                        "Гарпун",
                        remaining
                );
            }

            info(
                    LOGGER,
                    "DW protocol: harpooncd parsed successfully, remaining={}, raw={}",
                    remaining > 0L ? ProtocolPayloadSupport.formatRemainingMillis(remaining) : "ready",
                    packet.remainingMillis()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse harpooncd payload", exception);
            return false;
        }
    }

    static boolean handlePotionCooldowns(ProtocolState state, byte[] data) {
        try {
            DwPotionCooldownsPacket packet = DwPotionCooldownsDecoder.decode(data);

            if (state.potionStore != null) {
                state.potionStore.applyCooldownUpdate(packet.cooldowns());
            }

            String preview = packet.cooldowns().entrySet().stream()
                    .limit(POTION_PREVIEW_LIMIT)
                    .map(entry -> entry.getKey() + "=" + ProtocolPayloadSupport.formatRemainingMillis(entry.getValue()))
                    .collect(Collectors.joining(", "));
            info(
                    LOGGER,
                    "DW protocol: potioncd parsed successfully, entries={}, first={}",
                    packet.cooldowns().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse potioncd payload", exception);
            return false;
        }
    }

    static boolean handleNamedCooldown(String typeId, String displayName, byte[] data) {
        try {
            DwCooldownValuePacket packet = DwCooldownValueDecoder.decode(data);
            long remaining = Math.max(0L, packet.remainingMillis());
            info(
                    LOGGER,
                    "DW protocol: {} parsed successfully, displayName={}, remaining={}, raw={}",
                    typeId,
                    displayName,
                    remaining > 0L ? ProtocolPayloadSupport.formatRemainingMillis(remaining) : "ready",
                    packet.remainingMillis()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse {} payload", typeId, exception);
            return false;
        }
    }

    static boolean handleToken(byte[] data) {
        try {
            DwTokenPacket packet = DwTokenDecoder.decode(data);
            info(
                    LOGGER,
                    "DW protocol: token parsed successfully, present={}, length={}, value={}",
                    packet.value() != null,
                    packet.value() != null ? packet.value().length() : 0,
                    packet.value()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse token payload", exception);
            return false;
        }
    }

    static boolean handleGameEvent(ProtocolState state, byte[] data) {
        try {
            DwGameEventPacket packet = DwGameEventDecoder.decode(data);
            state.currentGameEvent = packet.event();
            info(
                    LOGGER,
                    "DW protocol: gameevent parsed successfully, event={}, display={}",
                    packet.event().name(),
                    packet.event().displayName()
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse gameevent payload", exception);
            return false;
        }
    }

    static boolean handleClanInfo(ProtocolState state, byte[] data) {
        try {
            DwClanInfoPacket packet = DwClanInfoDecoder.decode(data);
            state.clanInfo = new LinkedHashMap<>(packet.values());

            String preview = packet.values().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .limit(12)
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: claninfo parsed successfully, entries={}, first={}",
                    packet.values().size(),
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse claninfo payload", exception);
            return false;
        }
    }

    static boolean handleBoosters(ProtocolState state, byte[] data) {
        try {
            DwBoostersPacket packet = DwBoostersDecoder.decode(data);

            if (state.boosterStore != null) {
                for (ru.wilyfox.client.booster.BoosterStore.Kind kind : ru.wilyfox.client.booster.BoosterStore.Kind.values()) {
                    state.boosterStore.replace(kind, packet.boosters().get(kind));
                }
            }

            long totalEntries = packet.boosters().values().stream()
                    .mapToLong(List::size)
                    .sum();
            String preview = packet.boosters().entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(value -> entry.getKey() + "=x" + ProtocolPayloadSupport.formatCompactMultiplier(value.multiplier()) + " " + ProtocolPayloadSupport.formatRemainingMillis(value.remainingMillis())))
                    .limit(BOOSTER_PREVIEW_LIMIT)
                    .collect(Collectors.joining(", "));

            info(
                    LOGGER,
                    "DW protocol: boosters parsed successfully, groups={}, entries={}, first={}",
                    packet.boosters().size(),
                    totalEntries,
                    preview
            );
            return true;
        } catch (Exception exception) {
            warn(LOGGER, "DW protocol: failed to parse boosters payload", exception);
            return false;
        }
    }

    private static void applyBossTimers(ProtocolState state, DwBossTimersPacket packet) {
        if (state.bossRepository == null) {
            return;
        }

        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : packet.timers().entrySet()) {
            String bossId = entry.getKey();
            DwBossType type = state.bossTypes.get(bossId);

            String bossName = bossId;
            int level = 0;
            if (type != null) {
                bossName = type.name();
                level = type.level();
            }

            state.bossRepository.upsertProtocol(bossId, bossName, now + entry.getValue(), level);
        }
    }
}
