package ru.wilyfox.client.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Temporary selection of whole messages, shared by the dock and detached chat windows. */
public final class ChatMessageSelection {
    private static final ChatMessageSelection INSTANCE = new ChatMessageSelection();
    private final Set<GuiMessage> selected = Collections.newSetFromMap(new IdentityHashMap<>());
    private List<GuiMessage> newestFirst = List.of();
    private Object source;
    private GuiMessage anchor;
    private boolean dragging;

    public static ChatMessageSelection getInstance() { return INSTANCE; }
    public boolean isSelected(GuiMessage message) { return selected.contains(message); }
    public boolean hasSelection() { return !selected.isEmpty(); }
    public boolean isDragging() { return dragging; }
    public void release() { dragging = false; }
    public void clear() { selected.clear(); newestFirst = List.of(); source = null; anchor = null; dragging = false; }

    public void press(Object source, List<GuiMessage> messages, GuiMessage hovered, boolean control, boolean shift) {
        if (hovered == null) return;
        if (!Objects.equals(this.source, source)) clear();
        this.source = source;
        updateMessages(messages);
        if (shift && anchor != null) {
            selectRange(hovered);
        } else {
            if (!control) selected.clear();
            if (control && selected.contains(hovered)) selected.remove(hovered);
            else selected.add(hovered);
            anchor = hovered;
        }
        dragging = true;
    }

    public boolean drag(Object source, GuiMessage hovered) {
        if (!dragging || !Objects.equals(this.source, source)) return false;
        if (hovered != null) selectRange(hovered);
        return true;
    }

    private void updateMessages(List<GuiMessage> messages) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<GuiMessage, Boolean>());
        var ordered = new ArrayList<GuiMessage>();
        for (var message : messages) if (seen.add(message)) ordered.add(message);
        newestFirst = List.copyOf(ordered);
        selected.retainAll(seen);
        if (anchor != null && !seen.contains(anchor)) anchor = null;
    }

    private void selectRange(GuiMessage hovered) {
        int first = identityIndex(anchor), last = identityIndex(hovered);
        if (first < 0 || last < 0) return;
        for (int i = Math.min(first, last); i <= Math.max(first, last); i++) selected.add(newestFirst.get(i));
    }

    private int identityIndex(GuiMessage message) {
        for (int i = 0; i < newestFirst.size(); i++) if (newestFirst.get(i) == message) return i;
        return -1;
    }

    public String copiedText(boolean fullMessage) {
        var text = new ArrayList<String>();
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            var message = newestFirst.get(i);
            if (!selected.contains(message)) continue;
            String value = ChatMessageCopyExtractor.selectCopiedText(ChatMessageSanitizer.forLogic(
                    ru.wilyfox.utils.Formatting.stripMinecraftFormatting(message.content().getString())), fullMessage);
            if (!value.isBlank()) text.add(value);
        }
        return String.join("\n", text);
    }
}
