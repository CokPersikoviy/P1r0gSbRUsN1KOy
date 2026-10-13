package ru.wilyfox.client.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.clan.PlayerClanStorage;
import ru.wilyfox.client.hud.widget.WidgetTheme;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.config.RenderConfig;
import ru.wilyfox.client.moduser.ModUserBadge;
import ru.wilyfox.client.moduser.PresenceStore;

/** Keeps TAB and world nametags consistent without changing player identity. */
public final class PlayerNameFormatter {
    private static final java.util.Map<String, CachedName> CACHE = new java.util.LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedName> entry) {
            return size() > 256;
        }
    };
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> cachedLevel = new java.lang.ref.WeakReference<>(null);
    private PlayerNameFormatter() {}

    public static void clearCache() { CACHE.clear(); cachedLevel.clear(); }

    public static Component apply(Component serverName, String playerName) {
        RenderConfig config = ConfigManager.get().render;
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (cachedLevel.get() != level) { CACHE.clear(); cachedLevel = new java.lang.ref.WeakReference<>(level); }
        CachedName cached = CACHE.get(playerName);
        String normalized = cached != null ? cached.normalized
                : playerName == null ? null : playerName.toLowerCase(java.util.Locale.ROOT);
        String clan = !config.cleanPlayerNames || config.cleanNamesShowClan ? PlayerClanStorage.getClanNormalized(normalized) : null;
        boolean badge = config.modUserBadge && PresenceStore.isKnownNormalized(normalized);
        if (!config.cleanPlayerNames && clan == null && !badge) {
            if (cached == null) CACHE.put(playerName, new CachedName(normalized, null, null, false, false, 0, null, null));
            return baseName(serverName, playerName, false);
        }
        Component input = config.cleanPlayerNames ? null : serverName;
        // Translatable/object contents can contain mutable arguments outside the sibling tree.
        // Their uncommon path remains uncached so those changes are still visible immediately.
        boolean cacheable = plainTree(input);
        if (cacheable && cached != null && cached.result != null && cached.clean == config.cleanPlayerNames && cached.badge == badge
                && cached.color == WidgetTheme.TEXT_ACCENT && java.util.Objects.equals(cached.clan, clan)
                && java.util.Objects.equals(cached.input, input) && cached.snapshot.equals(cached.result)) {
            return cached.result;
        }
        try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("render/PlayerNameFormatter/prepare")) {
            Component result = baseName(serverName, playerName, config.cleanPlayerNames);
            if (clan != null) {
                result = Component.empty().append(Component.literal("[" + clan + "] ").withColor(WidgetTheme.TEXT_ACCENT))
                        .append(snapshot(result));
            }
            if (badge) {
                // Keep mutable server components out of the cached result.
                if (clan == null) result = snapshot(result);
                result = ModUserBadge.prefix(result);
            }
            if (cacheable) CACHE.put(playerName, new CachedName(normalized, input == null ? null : snapshot(input), clan, badge,
                    config.cleanPlayerNames, WidgetTheme.TEXT_ACCENT, result, snapshot(result)));
            return result;
        }
    }

    static Component baseName(Component serverName, String playerName, boolean clean) {
        if (clean) {
            return Component.literal(playerName).withStyle(ChatFormatting.WHITE);
        }
        return serverName != null ? serverName : Component.literal(playerName);
    }

    private static Component snapshot(Component component) {
        var copy = component.copy();
        for (int i = 0; i < copy.getSiblings().size(); i++) copy.getSiblings().set(i, snapshot(copy.getSiblings().get(i)));
        return copy;
    }

    private static boolean plainTree(Component component) {
        if (component == null) return true;
        if (!(component.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents)) return false;
        for (Component sibling : component.getSiblings()) if (!plainTree(sibling)) return false;
        return true;
    }

    private record CachedName(String normalized, Component input, String clan, boolean badge, boolean clean, int color,
                              Component result, Component snapshot) {}
}
