package ru.wilyfox.client.hud.config;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import ru.wilyfox.client.keybinds.KeyBinds;
import java.io.IOException;
import java.util.List;

public final class ConfigProfiles {
    private static ConfigProfileStore store;
    private static boolean initialized;
    private static String primaryBody;
    private static JsonObject savedBindings;
    private ConfigProfiles() {}
    private static List<KeyMapping> bindings() {
        return List.of(KeyBinds.AUTO_ATTACK, KeyBinds.CLICKER_MODE, KeyBinds.EDITING_MODE, KeyBinds.QUICK_ACCESS, KeyBinds.SETTINGS_MENU,
                KeyBinds.CLAN_HIDE, KeyBinds.CLAN_SIEGE_MAP, KeyBinds.RUNES_BAG, KeyBinds.SOCIAL);
    }
    private static JsonObject currentBindings() {
        var keys = new JsonObject();
        for (var binding : bindings()) keys.addProperty(binding.getName(), binding.saveString());
        return keys;
    }
    private static JsonObject capture() {
        return SharedConfigCodec.pack(ConfigManager.snapshot(), currentBindings());
    }
    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (initialized || client.options == null || client.getWindow() == null) return;
            initialized = true;
            init();
            if (store != null) ru.wilyfox.client.Client.getInstance().getHudRenderer().refreshConfiguration();
        });
    }
    public static void init() {
        primaryBody = null;
        savedBindings = null;
        try {
            store = new ConfigProfileStore(FabricLoader.getInstance().getConfigDir().resolve("froghelper-profiles"), capture());
            apply(store.read(store.activeId()));
        } catch (IOException | RuntimeException failure) {
            ru.wilyfox.FrogHelper.LOGGER.warn("Config profile library unavailable: {}", failure.getClass().getSimpleName());
            store = null;
        }
    }
    public static void saveIfReady() throws IOException {
        if (store != null) {
            var value = capture();
            store.saveActive(value);
            savedBindings = value.getAsJsonObject("keybinds");
            if (ConfigProfileStore.MAIN.equals(store.activeId())) primaryBody = null;
        }
    }
    public static List<ConfigProfileStore.Entry> entries() { return store == null ? List.of() : store.entries(); }
    public static String activeId() { return store == null ? ConfigProfileStore.MAIN : store.activeId(); }
    public static JsonObject primaryShared() throws IOException {
        if (store == null) throw new IOException("Config library unavailable");
        saveChangedBindings();
        return store.read(ConfigProfileStore.MAIN);
    }
    /** Publish the saved profile; only native Controls changes need polling outside our save path. */
    public static String primarySharedEncoded() throws IOException {
        if (store == null) throw new IOException("Config library unavailable");
        saveChangedBindings();
        if (primaryBody == null) {
            try (var ignored = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("config/share/encode")) {
                primaryBody = SharedConfigCodec.encode(store.read(ConfigProfileStore.MAIN));
            }
        }
        return primaryBody;
    }
    private static void saveChangedBindings() throws IOException {
        // Small key strings instead of copying and normalizing the complete settings tree.
        if (!currentBindings().equals(savedBindings)) saveIfReady();
    }
    public static String download(String author, JsonObject value) throws IOException {
        if (store == null) throw new IOException("Config library unavailable");
        return store.add(author, value);
    }
    public static void select(String id) throws IOException {
        if (store == null) throw new IOException("Config library unavailable");
        if (id.equals(store.activeId())) return;
        ConfigManager.save(); saveIfReady();
        var target = store.read(id);
        store.select(id);
        apply(target);
        ru.wilyfox.client.Client.getInstance().getHudRenderer().refreshConfiguration();
    }
    public static void delete(String id) throws IOException {
        if (store == null || ConfigProfileStore.MAIN.equals(id)) throw new IOException("Cannot delete Main");
        if (id.equals(store.activeId())) select(ConfigProfileStore.MAIN);
        store.delete(id);
    }
    private static void apply(JsonObject value) {
        var keys = value.getAsJsonObject("keybinds");
        for (var binding : bindings()) if (keys.has(binding.getName())) {
            try { binding.setKey(InputConstants.getKey(keys.get(binding.getName()).getAsString())); }
            catch (IllegalArgumentException ignored) { binding.setKey(binding.getDefaultKey()); }
        }
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        ConfigManager.setEditorLayout(null);
        ConfigManager.restoreSnapshot(value.get("settings"));
    }
}
