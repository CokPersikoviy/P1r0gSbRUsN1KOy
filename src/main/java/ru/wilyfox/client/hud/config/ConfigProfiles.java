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
    private ConfigProfiles() {}
    private static List<KeyMapping> bindings() {
        return List.of(KeyBinds.AUTO_ATTACK, KeyBinds.EDITING_MODE, KeyBinds.QUICK_ACCESS, KeyBinds.SETTINGS_MENU,
                KeyBinds.CLAN_HIDE, KeyBinds.CLAN_SIEGE_MAP, KeyBinds.RUNES_BAG, KeyBinds.SOCIAL);
    }
    private static JsonObject capture() {
        var keys = new JsonObject();
        for (var binding : bindings()) keys.addProperty(binding.getName(), binding.saveString());
        return SharedConfigCodec.pack(ConfigManager.snapshot(), keys);
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
        try {
            store = new ConfigProfileStore(FabricLoader.getInstance().getConfigDir().resolve("froghelper-profiles"), capture());
            apply(store.read(store.activeId()));
        } catch (IOException | RuntimeException failure) {
            ru.wilyfox.FrogHelper.LOGGER.warn("Config profile library unavailable: {}", failure.getClass().getSimpleName());
            store = null;
        }
    }
    public static void saveIfReady() throws IOException {
        if (store != null) store.saveActive(capture());
    }
    public static List<ConfigProfileStore.Entry> entries() { return store == null ? List.of() : store.entries(); }
    public static String activeId() { return store == null ? ConfigProfileStore.MAIN : store.activeId(); }
    public static JsonObject primaryShared() throws IOException {
        if (store == null) throw new IOException("Config library unavailable");
        saveIfReady();
        return store.read(ConfigProfileStore.MAIN);
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
