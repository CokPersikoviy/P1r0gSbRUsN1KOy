package ru.wilyfox.client.moduser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * The frog badge shown on a FrogHelper user's nametag. It's a single glyph (U+E000) rendered through our
 * own {@code froghelper:mod_badge} bitmap font (mapped to {@code textures/font/mod_badge.png}), so it
 * scales and aligns with the nametag text instead of needing world-space quad math.
 */
public final class ModUserBadge {
    private static final String GLYPH = ""; // U+F8FF: PUA slot DW does NOT use (U+E000 = DW trade.png)
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("froghelper", "mod_badge");
    private static final Style BADGE_STYLE = Style.EMPTY.withFont(FONT);

    private ModUserBadge() {
    }

    /** Prepend the frog badge (plus a thin space) to a player nametag component. */
    public static Component prefix(Component nameTag) {
        return Component.empty()
                .append(Component.literal(GLYPH).withStyle(BADGE_STYLE))
                .append(Component.literal(" "))
                .append(nameTag);
    }
}
