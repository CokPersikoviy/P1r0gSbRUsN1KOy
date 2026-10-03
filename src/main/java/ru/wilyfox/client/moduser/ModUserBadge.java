package ru.wilyfox.client.moduser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;

/**
 * The frog badge shown on a FrogHelper user's nametag. It's a single glyph (U+F8FF) rendered through our
 * own {@code froghelper:mod_badge} bitmap font (mapped to {@code textures/font/mod_badge.png}), so it
 * scales and aligns with the nametag text instead of needing world-space quad math.
 */
public final class ModUserBadge {
    private static final String GLYPH = ""; // U+F8FF: PUA slot DW does NOT use (U+E000 = DW trade.png)
    private static final Identifier FONT = Identifier.fromNamespaceAndPath("froghelper", "mod_badge");
    private static final Style BADGE_STYLE = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(FONT));

    private ModUserBadge() {
    }

    /** Prepend the frog badge (plus a thin space) to a player nametag component. */
    public static Component prefix(Component nameTag) {
        return Component.empty()
                .append(Component.literal(GLYPH).withStyle(BADGE_STYLE))
                .append(Component.literal(" "))
                .append(nameTag);
    }

    public static Component insert(Component component, int characterIndex) {
        if (component == null) {
            return Component.empty();
        }

        InsertState state = new InsertState(Math.max(0, characterIndex));
        MutableComponent result = Component.empty();
        appendWithBadge(result, component, Style.EMPTY, state);
        if (!state.inserted) {
            appendBadge(result);
        }
        return result;
    }

    private static void appendWithBadge(MutableComponent output, Component node, Style parentStyle, InsertState state) {
        Style effectiveStyle = node.getStyle().applyTo(parentStyle);
        ComponentContents contents = node.getContents();
        if (contents instanceof PlainTextContents.LiteralContents literal) {
            appendLiteralWithBadge(output, literal.text(), effectiveStyle, state);
        } else {
            MutableComponent own = MutableComponent.create(contents).setStyle(effectiveStyle);
            String ownText = own.getString();
            if (!state.inserted && state.remaining <= ownText.length()) {
                appendBadge(output);
                state.inserted = true;
            }
            output.append(own);
            if (!state.inserted) {
                state.remaining -= ownText.length();
            }
        }

        for (Component sibling : node.getSiblings()) {
            appendWithBadge(output, sibling, effectiveStyle, state);
        }
    }

    private static void appendLiteralWithBadge(MutableComponent output, String text, Style style, InsertState state) {
        if (state.inserted || state.remaining > text.length()) {
            if (!text.isEmpty()) {
                output.append(Component.literal(text).withStyle(style));
            }
            if (!state.inserted) {
                state.remaining -= text.length();
            }
            return;
        }

        int splitIndex = state.remaining;
        if (splitIndex > 0) {
            output.append(Component.literal(text.substring(0, splitIndex)).withStyle(style));
        }
        appendBadge(output);
        state.inserted = true;
        if (splitIndex < text.length()) {
            output.append(Component.literal(text.substring(splitIndex)).withStyle(style));
        }
    }

    private static void appendBadge(MutableComponent output) {
        output.append(Component.literal(GLYPH).withStyle(BADGE_STYLE));
        output.append(Component.literal(" "));
    }

    public static String strip(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(GLYPH + " ", "").replace(GLYPH, "");
    }

    public static Component strip(Component component) {
        if (component == null) {
            return Component.empty();
        }
        if (!component.getString().contains(GLYPH)) {
            return component;
        }
        return stripNode(component, new StripState());
    }

    private static MutableComponent stripNode(Component node, StripState state) {
        ComponentContents contents = node.getContents();
        MutableComponent out;
        if (contents instanceof PlainTextContents.LiteralContents literal) {
            String text = literal.text();
            StringBuilder cleaned = new StringBuilder(text.length());
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (state.removeSeparator) {
                    state.removeSeparator = false;
                    if (character == ' ') {
                        continue;
                    }
                }
                if (character == GLYPH.charAt(0)) {
                    state.removeSeparator = true;
                } else {
                    cleaned.append(character);
                }
            }
            out = Component.literal(cleaned.toString());
        } else {
            out = MutableComponent.create(contents);
            if (!out.getString().isEmpty()) {
                state.removeSeparator = false;
            }
        }
        out.setStyle(node.getStyle());
        for (Component sibling : node.getSiblings()) {
            out.append(stripNode(sibling, state));
        }
        return out;
    }

    private static final class StripState {
        private boolean removeSeparator;
    }

    private static final class InsertState {
        private int remaining;
        private boolean inserted;

        private InsertState(int remaining) {
            this.remaining = remaining;
        }
    }
}
