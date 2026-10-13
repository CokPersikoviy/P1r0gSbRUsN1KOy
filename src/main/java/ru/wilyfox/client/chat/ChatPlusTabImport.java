package ru.wilyfox.client.chat;

import com.google.gson.*;
import ru.wilyfox.client.hud.config.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads ChatPlus' serialized settings, without loading its classes or copying chat history. */
public final class ChatPlusTabImport {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    public record Tab(String id, String group, int window, String name, String pattern, String server,
                      boolean skipOthers, int priority, List<String> warnings, String error) {
        public boolean compatible() { return error == null; }
    }
    public record Selection(Tab tab, ChatTab channel) {}
    public record Result(List<String> keys, int duplicates) {}
    private ChatPlusTabImport() {}

    public static List<Path> discover(Path configDirectory) throws IOException {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/import/discover")) {
            var result = new ArrayList<Path>();
            for (var dir : List.of(configDirectory.resolve("chatplus"), configDirectory)) {
                if (!Files.isDirectory(dir)) continue;
                try (var files = Files.newDirectoryStream(dir, "chatplus*.json")) {
                    for (var file : files) {
                        if (Files.isRegularFile(file) && file.getFileName().toString().matches("chatplus(?:-v[0-9A-Za-z.\\-]+)?\\.json"))
                            result.add(file.toAbsolutePath());
                        if (result.size() >= 32) break;
                    }
                }
            }
            result.sort(Comparator.comparingLong(ChatPlusTabImport::modified).reversed().thenComparing(Path::toString));
            return List.copyOf(result);
        }
    }
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException ignored) { return 0; }
    }
    public static List<Tab> read(Path file) throws IOException {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/import/read")) {
            byte[] bytes;
            try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("ChatPlus config exceeds 2 MiB");
            return parse(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());
        }
    }
    public static List<Tab> parse(String json) throws IOException {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/import/parse")) {
            if (json.length() > MAX_BYTES) throw new IOException("ChatPlus config exceeds 2 MiB");
            // Bound JSON nesting before Gson can recurse (also protects malformed imported files).
            int depth = 0; boolean quoted = false, escaped = false;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; }
                else if (c == '"') quoted = true;
                else if ((c == '[' || c == '{') && ++depth > 64) throw new IOException("ChatPlus config nesting exceeds 64");
                else if (c == ']' || c == '}') depth--;
            }
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray windows = root.getAsJsonArray("chatWindows");
                if (windows == null) throw new IOException("Unsupported ChatPlus config: chatWindows is missing");
                if (windows.size() > 32) throw new IOException("Too many ChatPlus windows (maximum 32)");
                var result = new ArrayList<Tab>();
                for (int w = 0; w < windows.size(); w++) {
                    var window = windows.get(w).getAsJsonObject();
                    var settings = window.getAsJsonObject("tabSettings");
                    if (settings == null || !settings.has("tabs")) continue;
                    var tabs = settings.getAsJsonArray("tabs");
                    for (int t = 0; t < tabs.size(); t++) {
                        var tab = tabs.get(t).getAsJsonObject();
                        var profiles = tab.getAsJsonArray("settings");
                        if (profiles == null) throw new IOException("Unsupported ChatPlus tab: settings is missing");
                        if (profiles.size() > 16) throw new IOException("Too many server profiles in a ChatPlus tab (maximum 16)");
                        for (int p = 0; p < profiles.size(); p++) {
                            if (result.size() >= 256) throw new IOException("Too many ChatPlus tab profiles (maximum 256)");
                            result.add(profile(profiles.get(p).getAsJsonObject(), w, t, p));
                        }
                    }
                }
                if (result.isEmpty()) throw new IOException("No ChatPlus tabs found");
                // ChatPlus routes matching tabs by descending priority, then original order.
                result.sort(Comparator.comparingInt(Tab::window).thenComparing(Comparator.comparingInt(Tab::priority).reversed()));
                return List.copyOf(result);
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid ChatPlus config", invalid);
            }
        }
    }
    private static Tab profile(JsonObject profile, int window, int tab, int variant) {
        var warnings = new ArrayList<String>();
        String name = string(profile, "name", "").strip();
        if (name.isEmpty()) name = "ChatPlus " + (tab + 1);
        if (name.length() > 20) { warnings.add("Name shortened to 20 characters"); name = name.substring(0, 20); }
        String pattern = string(profile, "pattern", "(?s).*");
        boolean matchAll = pattern.equals("(?s).*") || pattern.equals("(.*?)");
        if (pattern.equals("(.*?)")) {
            pattern = "(?s).*";
            warnings.add("ChatPlus match-all shortcut converted to an equivalent regex");
        } else if (pattern.isEmpty()) {
            pattern = "^$"; // In FH a blank filter means match all; in ChatPlus it matches only an empty message.
            warnings.add("Empty ChatPlus regex converted to ^$ to preserve empty-only matching");
        }
        String server = "";
        if (profile.has("serverPattern")) server = string(profile.getAsJsonObject("serverPattern"), "pattern", "");
        if (!server.isEmpty()) warnings.add("Server profile becomes a normal FH tab; it will not switch automatically");
        if (!string(profile, "autoPrefix", "").isEmpty() || !string(profile, "autoSend", "").isEmpty())
            warnings.add("Auto prefix / auto send not copied; choose the FH channel manually");
        if (bool(profile, "alwaysAdd")) warnings.add("Always Add is not supported; only matching messages are shown");
        if (bool(profile, "skipOthers")) warnings.add("FH Skip Others excludes matches from ALL, not from other filtered tabs");
        if (profile.has("notificationSettings") || profile.has("suggestionsPatterns"))
            warnings.add("Notifications and command suggestions are not copied");
        String error = null;
        if (bool(profile, "formatted") && !matchAll) error = "Color-based filtering is unsupported; adapt the regex in ChatPlus first";
        else if (pattern.length() > ChatWidgetConfig.MAX_FILTER_LENGTH) error = "Regex exceeds 512 characters; it will not be truncated";
        else {
            String invalid = ChatTextFilter.compile(pattern, true).error();
            if (invalid != null) error = "Invalid regex: " + invalid;
        }
        String group = window + ":" + tab;
        return new Tab(group + ":" + variant, group, window + 1, name, pattern, server,
                bool(profile, "skipOthers"), profile.has("priority") ? profile.get("priority").getAsInt() : 0, List.copyOf(warnings), error);
    }
    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }
    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }
    public static boolean duplicate(HudConfig config, Tab tab, ChatTab channel) {
        return config.chatWidgets.values().stream().anyMatch(existing -> !existing.deleted && existing.channel == channel
                && existing.title.equals(tab.name()) && ((existing.regexFilter && existing.textFilter.equals(tab.pattern()))
                    || (existing.textFilter.isEmpty() && tab.pattern().equals("(?s).*")))
                && existing.skipOthers == tab.skipOthers());
    }
    /** Validate the entire selection before modifying the caller's config. Existing identities stay intact. */
    public static Result append(HudConfig config, List<Selection> selected) {
        try (var profile = ru.wilyfox.client.profiler.ModProfiler.getInstance().scope("chat/import/append")) {
            var groups = new HashSet<String>(); var additions = new ArrayList<Selection>(); int duplicates = 0;
            for (var selection : selected) {
                var tab = selection.tab();
                if (!tab.compatible() || selection.channel() == null || selection.channel() == ChatTab.FH)
                    throw new IllegalArgumentException("Incompatible ChatPlus tab: " + tab.name());
                if (!groups.add(tab.group())) throw new IllegalArgumentException("Choose one server profile per tab");
                if (duplicate(config, tab, selection.channel()) || additions.stream().anyMatch(previous ->
                        previous.channel() == selection.channel() && previous.tab().name().equals(tab.name())
                        && previous.tab().pattern().equals(tab.pattern()) && previous.tab().skipOthers() == tab.skipOthers())) { duplicates++; continue; }
                additions.add(selection);
            }
            long count = config.chatWidgets.keySet().stream().filter(WidgetCatalog::isCustomChatKey).count();
            if (count + additions.size() > ChatWidgetConfig.MAX_CUSTOM_TABS)
                throw new IllegalArgumentException("Not enough tab slots: " + (ChatWidgetConfig.MAX_CUSTOM_TABS - count) + " available");
            var keys = new ArrayList<String>();
            for (var selection : additions) {
                var imported = new ChatWidgetConfig(selection.channel());
                imported.title = selection.tab().name(); imported.textFilter = selection.tab().pattern();
                imported.skipOthers = selection.tab().skipOthers();
                String key = "ChatWidgetCustom_" + UUID.randomUUID();
                config.chatWidgets.put(key, imported); config.chatTabOrder.add(key); keys.add(key);
            }
            return new Result(List.copyOf(keys), duplicates);
        }
    }
}
