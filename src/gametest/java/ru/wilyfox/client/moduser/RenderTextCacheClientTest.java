package ru.wilyfox.client.moduser;

import com.sun.management.ThreadMXBean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.locale.Language;
import ru.wilyfox.client.clan.PlayerClanStorage;
import ru.wilyfox.client.hud.config.ConfigManager;
import ru.wilyfox.client.hud.healthbar.PlayerHealthBarRenderer;
import ru.wilyfox.client.utility.PlayerNameFormatter;
import java.lang.management.ManagementFactory;
import java.util.List;

public final class RenderTextCacheClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var snapshot = context.computeOnClient(client -> ConfigManager.snapshot());
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                String oldClan = PlayerClanStorage.getClan("CacheTest");
                try {
                    var config = ConfigManager.get().render;
                    config.cleanPlayerNames = false; config.cleanNamesShowClan = true; config.modUserBadge = true;
                    PlayerClanStorage.update("CacheTest", "Nexus"); PresenceStore.replace(List.of("CacheTest"));
                    MutableComponent sibling = Component.literal("ServerName");
                    MutableComponent input = Component.empty().append(sibling);
                    Component cached = PlayerNameFormatter.apply(input, "CacheTest");
                    check(cached == PlayerNameFormatter.apply(input.copy(), "CacheTest"), "Equivalent server components rebuilt the nametag");
                    check(ModUserBadge.strip(cached).getString().equals("[Nexus] ServerName"), "Cached name changed formatting");
                    sibling.append(" changed");
                    Component changed = PlayerNameFormatter.apply(input, "CacheTest");
                    check(changed != cached && changed.getString().contains(" changed"), "In-place server component change was ignored");
                    ((MutableComponent) changed).append(" polluted");
                    check(!PlayerNameFormatter.apply(input, "CacheTest").getString().contains("polluted"), "A consumer mutation contaminated the cached name");
                    var argument = Component.literal("Argument");
                    var translated = Component.translatableWithFallback("audit.missing", "%s", argument);
                    PlayerNameFormatter.apply(translated, "CacheTest").getVisualOrderText();
                    argument.append(" changed");
                    check(sequenceText(PlayerNameFormatter.apply(translated, "CacheTest").getVisualOrderText()).contains("Argument changed"),
                            "Mutable translation arguments were hidden by the name cache");
                    PlayerClanStorage.update("CacheTest", "Other");
                    check(PlayerNameFormatter.apply(input, "CacheTest").getString().contains("[Other]"), "Clan update was delayed");
                    PresenceStore.clear();
                    check(!PlayerNameFormatter.apply(input, "CacheTest").getString().contains("\uf8ff"), "Presence update left a badge");
                    config.cleanPlayerNames = true;
                    config.cleanNamesShowClan = false;
                    check(PlayerNameFormatter.apply(input, "CacheTest").getString().equals("CacheTest"), "Settings update retained an old name");
                    PresenceStore.replace(List.of("CacheTest"));
                    config.cleanNamesShowClan = true;
                    var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
                    if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                    for (int i = 0; i < 1000; i++) PlayerNameFormatter.apply(input, "CacheTest").getVisualOrderText();
                    long id = Thread.currentThread().threadId(), start = bean.getThreadAllocatedBytes(id);
                    for (int i = 0; i < 5000; i++) PlayerNameFormatter.apply(input, "CacheTest").getVisualOrderText();
                    long bytes = bean.getThreadAllocatedBytes(id) - start;
                    check(bytes < 64_000, "Steady name rendering allocated " + bytes + " bytes");
                    verifyHealthText(client.font, bean);
                    PlayerNameFormatter.clearCache();
                    var field = PlayerNameFormatter.class.getDeclaredField("CACHE"); field.setAccessible(true);
                    for (int i = 0; i < 300; i++) PlayerNameFormatter.apply(input, "Bounded" + i);
                    check(((java.util.Map<?, ?>) field.get(null)).size() == 256, "Name cache grew without a bound");
                    PlayerNameFormatter.clearCache();
                    check(((java.util.Map<?, ?>) field.get(null)).isEmpty(), "Disconnect cleanup retained names");
                    System.out.println("Render text caches: names 5000 frames=" + bytes + " bytes; immediate data/mutation updates and bounded eviction passed");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                finally { PlayerClanStorage.update("CacheTest", oldClan); PresenceStore.clear(); PlayerNameFormatter.clearCache(); }
            });
        } finally { context.runOnClient(client -> ConfigManager.restoreSnapshot(snapshot)); }
    }

    private static void verifyHealthText(net.minecraft.client.gui.Font font, ThreadMXBean bean) throws ReflectiveOperationException {
        var method = PlayerHealthBarRenderer.class.getDeclaredMethod("healthText", float.class, float.class); method.setAccessible(true);
        var lookup = java.lang.invoke.MethodHandles.privateLookupIn(PlayerHealthBarRenderer.class, java.lang.invoke.MethodHandles.lookup());
        var handle = lookup.unreflect(method);
        try {
            for (float health : new float[]{0, .01f, 1, 19.9f, 20, Float.NaN, Float.POSITIVE_INFINITY}) {
                var sequence = (net.minecraft.util.FormattedCharSequence) handle.invokeExact(health, 20f);
                String expected = Math.max(0, net.minecraft.util.Mth.ceil(health)) + "/20";
                check(sequenceText(sequence).equals(expected) && font.width(sequence) == font.width(expected), "Health formatting/width changed");
            }
            var sequence = (net.minecraft.util.FormattedCharSequence) handle.invokeExact(19.9f, 20f);
            check(sequence == (net.minecraft.util.FormattedCharSequence) handle.invokeExact(19.5f, 20f), "Identical rounded health rebuilt text");
            long id = Thread.currentThread().threadId(), start = bean.getThreadAllocatedBytes(id);
            for (int i = 0; i < 10000; i++) {
                var result = (net.minecraft.util.FormattedCharSequence) handle.invokeExact(19.9f, 20f);
                check(result == sequence, "Unchanged health lost its cached sequence");
            }
            long bytes = bean.getThreadAllocatedBytes(id) - start;
            check(bytes < 64_000, "HP text cache allocated " + bytes + " bytes");
            Language original = Language.getInstance();
            try {
                Language.inject(new Language() {
                    @Override public String getOrDefault(String key, String fallback) { return original.getOrDefault(key, fallback); }
                    @Override public boolean has(String key) { return original.has(key); }
                    @Override public boolean isDefaultRightToLeft() { return original.isDefaultRightToLeft(); }
                    @Override public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text) { return original.getVisualOrder(text); }
                });
                check(sequence != (net.minecraft.util.FormattedCharSequence) handle.invokeExact(19.9f, 20f), "Language reload retained prepared HP text");
            } finally { Language.inject(original); }
            for (int i = 0; i < 400; i++) { var ignored = (net.minecraft.util.FormattedCharSequence) handle.invokeExact((float) i, 500f); }
            var cache = PlayerHealthBarRenderer.class.getDeclaredField("HEALTH_TEXT"); cache.setAccessible(true);
            check(((java.util.Map<?, ?>) cache.get(null)).size() == 256, "Health cache grew without a bound");
            System.out.println("HP text cache: 10000 frames=" + bytes + " bytes; formatting, font widths, language reload and eviction passed");
        } catch (Throwable error) { throw new AssertionError(error); }
    }
    private static String sequenceText(net.minecraft.util.FormattedCharSequence sequence) {
        var text = new StringBuilder(); sequence.accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; }); return text.toString();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
