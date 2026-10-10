package ru.wilyfox.client.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A viewport, never a second message archive. References at most rows + one wrapped lines. */
public final class ChatWidgetView {
    private final ChatTabManager store;
    private ChatTab channel;
    private String query = "";
    private String filter = "";
    private int scroll, width = -1, rows = -1;
    private Font font;
    private long archiveRevision = -1, cacheRevision = -1;
    private List<Line> visible = List.of();
    private Line anchor;
    private boolean dirty = true, older;
    private long countRevision = -1;
    private int matches;

    public ChatWidgetView(ChatTabManager store, ChatTab channel) { this.store = store; this.channel = channel; }
    public ChatTab channel() { return channel; }
    public String search() { return query; }
    public int scroll() { return scroll; }
    public void restoreScroll(int value) { scroll = Math.max(0, value); anchor = null; dirty = true; }
    public boolean hasOlder() { return older; }
    public void channel(ChatTab value) { if (value != channel) { channel = value; reset(); } }
    public void search(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!query.equals(normalized)) { query = normalized; reset(); }
    }
    public void filter(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!filter.equals(normalized)) { filter = normalized; reset(); }
    }
    private boolean matches(ChatMessageEntry entry) {
        return (query.isEmpty() || entry.searchText().contains(query)) && (filter.isEmpty() || entry.searchText().contains(filter));
    }
    public void scroll(int delta) {
        if (delta > 0 && !older) return;
        scroll = Math.clamp((long) scroll + delta, 0, 1_000_000);
        anchor = null;
        dirty = true;
    }
    public void reset() { scroll = 0; anchor = null; dirty = true; countRevision = -1; }
    public int matchingMessages() {
        if (countRevision != store.archiveRevision()) {
            int[] count = {0};
            store.visitNewest(channel, entry -> { if (matches(entry)) count[0]++; return true; });
            matches = count[0]; countRevision = store.archiveRevision();
        }
        return matches;
    }

    public List<Line> lines(Font font, int width, int rows) {
        boolean geometry = this.font != font || this.width != width || this.rows != rows
                || cacheRevision != store.lineCacheRevision();
        if (!dirty && !geometry && archiveRevision == store.archiveRevision()) return visible;
        boolean keepAnchor = !dirty && !geometry && scroll > 0 && anchor != null;
        try (var ignored = ModProfiler.getInstance().scope("chat/widget/viewport")) {
            var builder = new WindowBuilder(rows, scroll, keepAnchor ? anchor : null);
            store.visitNewest(channel, entry -> {
                if (!matches(entry)) return true;
                var wrapped = store.wrappedLines(entry.message(), font, width);
                for (int i = wrapped.size() - 1; i >= 0; i--) {
                    if (!builder.add(new Line(entry.message(), wrapped.get(i), i))) return false;
                }
                return true;
            });
            builder.finish();
            visible = List.copyOf(builder.result);
            scroll = builder.offset;
            older = builder.older;
            anchor = visible.isEmpty() ? null : visible.getFirst();
            this.font = font; this.width = width; this.rows = rows;
            archiveRevision = store.archiveRevision(); cacheRevision = store.lineCacheRevision(); dirty = false;
            return visible;
        }
    }
    public record Line(GuiMessage message, FormattedCharSequence text, int index) {}

    private static final class WindowBuilder {
        final int rows;
        final ArrayList<Line> result = new ArrayList<>();
        final ArrayDeque<Line> tail = new ArrayDeque<>();
        Line anchor;
        int offset, seen;
        boolean older, ended = true;
        WindowBuilder(int rows, int offset, Line anchor) { this.rows = rows; this.offset = offset; this.anchor = anchor; }
        boolean add(Line line) {
            if (anchor != null && line.message == anchor.message && line.index == anchor.index) {
                offset = seen; result.clear(); anchor = null; older = false;
            }
            tail.addLast(line);
            if (tail.size() > rows) tail.removeFirst();
            if (seen >= offset && result.size() < rows) result.add(line);
            else if (seen >= offset + rows) {
                older = true;
                if (anchor == null) { ended = false; return false; }
            }
            seen++;
            return true;
        }
        void finish() {
            if (ended && seen < offset + rows) {
                result.clear(); result.addAll(tail); offset = Math.max(0, seen - rows); older = false;
            } else if (ended) older = seen > offset + rows;
        }
    }
}
