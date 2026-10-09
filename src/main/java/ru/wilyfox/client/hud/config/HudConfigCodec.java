package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.JsonElement;

import java.util.Set;

public final class HudConfigCodec {
    private static final Set<String> LEGACY_ACTIVE_CONFIGS = Set.of(
            "BossWidgetConfig", "BlocksPerSecondWidgetConfig", "DailyBlocksConfig", "EstimatedTpsConfig",
            "BoostersConfig", "CraftRecipeConfig", "PotionTimersConfig", "SellerCooldownConfig",
            "ComboProgressConfig", "BossBarConfig", "ScoreboardConfig", "WandCooldownConfig",
            "ActiveRunesConfig", "ActivePetsConfig", "ActiveMinersConfig", "AbilityCooldownConfig",
            "ActiveEffectsConfig", "BossDamageConfig", "VisibilityStatusConfig", "DungeonMapConfig",
            "EntityInspectConfig", "OutgoingChatQueueConfig", "ProtocolGraphWidgetConfig", "PopUpsConfig",
            "LevelProgressConfig"
    );
    private HudConfigCodec() { }

    public static Gson createGson() {
        // Old toggles are readable for migration, but never saved as a second source of truth.
        return new GsonBuilder().setPrettyPrinting().addSerializationExclusionStrategy(new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) {
                String type = field.getDeclaringClass().getSimpleName();
                return field.getName().equals("active") && LEGACY_ACTIVE_CONFIGS.contains(type)
                        || type.equals("FishingConfig") && (field.getName().equals("showFishingNibblesWidget")
                            || field.getName().equals("showFishingQuestsWidget"))
                        || type.equals("WidgetLayoutConfig") && field.getName().equals("hiddenInGameplay");
            }
        }).create();
    }

    public static HudConfig decode(Gson gson, JsonElement source) {
        HudConfig config = HudConfigSanitizer.sanitize(gson.fromJson(source, HudConfig.class));
        if (source != null && source.isJsonObject() && !source.getAsJsonObject().has("mainLayout")) {
            WidgetCatalog.migrateLegacy(config);
        }
        return config;
    }
}
