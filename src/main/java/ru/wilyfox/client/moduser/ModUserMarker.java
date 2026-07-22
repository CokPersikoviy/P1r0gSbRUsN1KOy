package ru.wilyfox.client.moduser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

/**
 * Removes the visible mod beacon ({@link ModUserStorage#MARKER}, "Ⓕ") from a chat component before it is
 * shown, so mod users see clean chat while the beacon still travels over the wire for detection. Walks the
 * component tree and rebuilds it, preserving styles/siblings and any non-literal contents.
 */
public final class ModUserMarker {
    private ModUserMarker() {
    }

    public static String strip(String text) {
        return text == null ? "" : text.replace(ModUserStorage.MARKER, "");
    }

    public static String appendToOutgoing(String input, boolean socialsEnabled) {
        if (!socialsEnabled || input == null || input.isBlank() || input.startsWith("/")
                || input.contains(ModUserStorage.MARKER) || input.contains("{fh")) {
            return input;
        }
        if (input.length() + ModUserStorage.MARKER.length() > 256) {
            return input;
        }
        return input + ModUserStorage.MARKER;
    }

    public static Component strip(Component component) {
        if (component == null) {
            return Component.empty();
        }
        if (!component.getString().contains(ModUserStorage.MARKER)) {
            return component; // nothing to strip — keep the original untouched
        }
        return stripNode(component);
    }

    private static MutableComponent stripNode(Component node) {
        ComponentContents contents = node.getContents();
        MutableComponent out;
        if (contents instanceof PlainTextContents.LiteralContents literal) {
            out = Component.literal(strip(literal.text()));
        } else {
            out = MutableComponent.create(contents);
        }
        out.setStyle(node.getStyle());
        for (Component sibling : node.getSiblings()) {
            out.append(stripNode(sibling));
        }
        return out;
    }
}
