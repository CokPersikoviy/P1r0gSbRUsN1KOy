package ru.wilyfox.client.moduser;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import ru.wilyfox.client.hud.config.*;
import ru.wilyfox.client.keybinds.KeyBinds;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigSharingClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var originalId = context.computeOnClient(client -> ConfigProfiles.activeId());
        var before = context.computeOnClient(client -> ConfigProfiles.entries());
        var downloaded = new AtomicReference<String>();
        var published = new AtomicReference<JsonObject>();
        final String custom = "ChatWidgetCustom_11111111-2222-3333-4444-555555555555";
        final String remoteID = "a".repeat(64);
        HttpServer server;
        try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (java.io.IOException failure) { throw new AssertionError(failure); }
        var remote = context.computeOnClient(client -> {
            var value = SharedConfigCodec.pack(ConfigManager.snapshot(), new JsonObject());
            value.getAsJsonObject("settings").getAsJsonObject("playerHealthBars").addProperty("verticalOffset", 17);
            value.getAsJsonObject("keybinds").addProperty("Social", "key.keyboard.g");
            var settings = value.getAsJsonObject("settings");
            settings.getAsJsonObject("chatWidgets").getAsJsonObject("ChatWidgetGlobal").addProperty("title", "Remote G");
            var tab = new JsonObject(); tab.addProperty("channel", "ALL"); tab.addProperty("title", "Remote tab"); tab.addProperty("textFilter", "hello");
            settings.getAsJsonObject("chatWidgets").add(custom, tab);
            settings.getAsJsonArray("chatTabOrder").add(custom);
            return value;
        });
        server.createContext("/v1/config", exchange -> {
            try {
                if (!"Bearer fixture".equals(exchange.getRequestHeaders().getFirst("Authorization"))) { exchange.sendResponseHeaders(401, -1); return; }
                if (exchange.getRequestMethod().equals("PUT")) {
                    published.set(SharedConfigCodec.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    var envelope = new JsonObject(); envelope.addProperty("version", 1); envelope.addProperty("id", remoteID);
                    envelope.addProperty("name", "ConfigSource"); envelope.add("config", remote);
                    byte[] body = envelope.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
                }
            } finally { exchange.close(); }
        });
        server.start();
        var api = new BackendConfigClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), HttpClient.newHttpClient(),
                () -> { throw new AssertionError("Unexpected unauthorized response"); });
        try (var world = context.worldBuilder().create()) {
            var main = context.computeOnClient(client -> {
                try { return ConfigProfiles.primaryShared(); } catch (Exception failure) { throw new AssertionError(failure); }
            });
            context.runOnClient(client -> api.tick(true, "fixture"));
            for (int i = 0; i < 100 && published.get() == null; i++) context.waitTicks(2);
            check(main.equals(published.get()), "Publisher sent a downloaded profile or lost settings/binds");
            var request = context.computeOnClient(client -> api.download(new SocialWire.Player(remoteID, "ConfigSource", "HUB0:0", null, false, true), "fixture"));
            for (int i = 0; i < 100 && !request.isDone(); i++) context.waitTicks(2);
            check(request.isDone(), "Authenticated config download did not finish");
            context.runOnClient(client -> {
                var added = ConfigProfiles.entries().stream().filter(entry -> before.stream().noneMatch(old -> old.id().equals(entry.id()))).findFirst().orElseThrow();
                downloaded.set(added.id());
                check(ConfigProfiles.activeId().equals(originalId), "Download automatically replaced the active config");
                client.gui.setScreen(new ConfigProfilesScreen(null));
            });
            context.waitTicks(3); context.takeScreenshot("configs-library-downloaded");
            context.runOnClient(client -> {
                var screen = (ConfigProfilesScreen) client.gui.screen();
                clickProfile(screen, downloaded.get(), false);
                check(ConfigProfiles.activeId().equals(downloaded.get()), "Profile selection button failed");
                check(ConfigManager.get().playerHealthBars.verticalOffset == 17, "Profile did not apply settings");
                check(KeyBinds.SOCIAL.saveString().equals("key.keyboard.g"), "Profile did not apply a native mod binding");
                check(ConfigManager.get().chatWidgets.get(custom).textFilter.equals("hello"), "Custom tab/filter was lost");
                check(ConfigManager.get().chatWidgets.get("ChatWidgetGlobal").title.equals("Remote G"), "Built-in tab settings were lost");
                check(ConfigManager.get().chatTabOrder.contains(custom), "Custom tab order was lost");
                check(ru.wilyfox.client.Client.getInstance().getHudRenderer().getWidgets().stream()
                        .anyMatch(widget -> widget instanceof ru.wilyfox.client.hud.widget.ChatWidget chat && custom.equals(chat.getConfigKey())),
                        "Downloaded chat widget was not registered");
            });
            context.waitTicks(3); context.takeScreenshot("configs-library-selected");
            context.runOnClient(client -> {
                try {
                    check(main.equals(ConfigProfiles.primaryShared()), "Selecting a download overwrote Main");
                    clickProfile((ConfigProfilesScreen) client.gui.screen(), downloaded.get(), true);
                    check(ConfigProfiles.activeId().equals("main"), "Deleting the active download did not restore Main");
                    check(!ConfigManager.get().chatWidgets.containsKey(custom), "Deleted profile left its custom chat tab behind");
                    boolean rejected = false;
                    try { ConfigProfiles.delete("main"); } catch (java.io.IOException expected) { rejected = true; }
                    check(rejected, "Main config can be deleted");
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            context.waitTicks(3); context.takeScreenshot("configs-library-main-protected");
        } finally {
            server.stop(0);
            context.runOnClient(client -> {
                api.reset(); client.gui.setScreen(null);
                try {
                    ConfigProfiles.select(originalId);
                    if (downloaded.get() != null && ConfigProfiles.entries().stream().anyMatch(e -> e.id().equals(downloaded.get()))) ConfigProfiles.delete(downloaded.get());
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
        }
    }
    private static void clickProfile(ConfigProfilesScreen screen, String id, boolean delete) {
        try {
            var x = ConfigProfilesScreen.class.getDeclaredMethod("panelX"); x.setAccessible(true);
            var y = ConfigProfilesScreen.class.getDeclaredMethod("panelY"); y.setAccessible(true);
            var width = ConfigProfilesScreen.class.getDeclaredMethod("panelWidth"); width.setAccessible(true);
            var scroll = ConfigProfilesScreen.class.getDeclaredField("scroll"); scroll.setAccessible(true);
            int index = -1; var entries = ConfigProfiles.entries();
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(id)) index = i;
            screen.mouseScrolled(0,0,0,-100);
            int row = index - scroll.getInt(screen);
            screen.mouseClicked(new MouseButtonEvent((int)x.invoke(screen) + (delete ? (int)width.invoke(screen) - 25 : 30),
                    (int)y.invoke(screen) + 44 + row * 26 + 10, new MouseButtonInfo(0,0)), false);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
