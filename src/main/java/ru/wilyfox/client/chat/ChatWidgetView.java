package ru.wilyfox.client.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import ru.wilyfox.client.profiler.ModProfiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded viewports over the shared archive, including one extra row during smooth scrolling. */
public final class ChatWidgetView {
    private final ChatTabManager store;
    private ChatTab channel;
    private String windowKey;
    private long routingRevision = -1;
    private String query = "";
    private ChatTextFilter filter = ChatTextFilter.EMPTY;
    private int scroll, width = -1, rows = -1;
    private Font font;
    private long archiveRevision = -1, cacheRevision = -1;
    private List<Line> visible = List.of();
    private Line anchor;
    private boolean dirty = true, older;
    private boolean preserveAnchor;
    private long countRevision = -1;
    private int matches;
    private long activityRevision = -1, latestActivity;
    private final SmoothChatScroll motion = new SmoothChatScroll();
    private boolean smooth;
    private double wheelRemainder;
    private List<Line> presented = List.of();
    private int presentedOffset = -1, presentedWidth = -1, presentedRows = -1;
    private Font presentedFont;
    private long presentedRevision = -1, presentedCacheRevision = -1;

    public ChatWidgetView(ChatTabManager store, ChatTab channel) { this.store = store; this.channel = channel; }
    public void window(String key) { windowKey = key; }
    private void syncRouting() {
        if (windowKey != null && routingRevision != ru.wilyfox.client.hud.config.ConfigManager.getLayoutRevision()) {
            routingRevision = ru.wilyfox.client.hud.config.ConfigManager.getLayoutRevision();
            preserveAnchor = true; dirty = true; presentedOffset = -1; countRevision = activityRevision = -1;
        }
    }
    public ChatTab channel() { return channel; }
    public String search() { return query; }
    public int scroll() { return scroll; }
    public void restoreScroll(int value) {
        scroll = Math.max(0, value); anchor = null; dirty = true; preserveAnchor = false;
        smooth = false; wheelRemainder = 0; motion.reset(scroll);
        presentedOffset = -1;
    }
    public boolean hasOlder() { return older; }
    public void channel(ChatTab value) { if (value != channel) { channel = value; reset(); } }
    public void search(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!query.equals(normalized)) { query = normalized; reset(); }
    }
    public void filter(String value) {
        filter(value, false);
    }
    public void filter(String value, boolean regex) {
        if (!filter.same(value, regex)) { filter = ChatTextFilter.compile(value, regex); reset(); }
    }
    private boolean matches(ChatMessageEntry entry) {
        return store.isDisplayMessage(entry.message()) && (query.isEmpty() || entry.searchText().contains(query)) && filter.matches(entry.filterText(), entry.searchText())
                && store.routesTo(windowKey, entry);
    }
    public void scroll(int delta) {
        if (delta > 0 && !older) return;
        scroll = Math.clamp((long) scroll + delta, 0, 1_000_000);
        anchor = null;
        preserveAnchor = false;
        dirty = true;
        smooth = false; wheelRemainder = 0; motion.reset(scroll);
    }
    public void smoothScroll(double delta, long now) {
        if (!Double.isFinite(delta) || delta == 0 || delta > 0 && !older) return;
        double requested = Math.clamp(scroll + wheelRemainder + delta, 0, 1_000_000);
        int next = (int) Math.round(requested);
        wheelRemainder = requested - next;
        if (next == scroll) return;
        motion.moveTo(next, now);
        scroll = next; anchor = null; dirty = true; smooth = true; preserveAnchor = false;
    }
    public void reset() {
        restoreScroll(0); countRevision = activityRevision = -1;
    }
    public List<GuiMessage> selectionMessages() {
        syncRouting();
        var result = new ArrayList<GuiMessage>();
        store.visitNewest(channel, entry -> { if (matches(entry)) result.add(entry.message()); return true; });
        return result;
    }
    /** Recheck only when messages or filters change; hidden windows never wrap their history. */
    public long latestActivityNanos() {
        syncRouting();
        if (activityRevision != store.archiveRevision()) {
            long[] latest = {0};
            store.visitNewest(channel, entry -> {
                if (entry.receivedNanos() != 0 && matches(entry)) {
                    latest[0] = entry.receivedNanos();
                    return false;
                }
                return true;
            });
            latestActivity = latest[0]; activityRevision = store.archiveRevision();
        }
        return latestActivity;
    }
    public int matchingMessages() {
        syncRouting();
        if (countRevision != store.archiveRevision()) {
            int[] count = {0};
            store.visitNewest(channel, entry -> { if (matches(entry)) count[0]++; return true; });
            matches = count[0]; countRevision = store.archiveRevision();
        }
        return matches;
    }

    public List<Line> lines(Font font, int width, int rows) {
        syncRouting();
        boolean geometry = this.font != font || this.width != width || this.rows != rows
                || cacheRevision != store.lineCacheRevision();
        if (!dirty && !geometry && archiveRevision == store.archiveRevision()) return visible;
        boolean keepAnchor = (!dirty || preserveAnchor) && scroll > 0 && anchor != null;
        int anchorCharacter = 0;
        if (keepAnchor && geometry && this.font != null) {
            var oldLines = store.wrappedLines(anchor.message(), this.font, this.width);
            for (int i = 0; i < Math.min(anchor.index(), oldLines.size()); i++) anchorCharacter += characters(oldLines.get(i));
        }
        final int character = anchorCharacter;
        final int previousScroll = scroll;
        try (var ignored = ModProfiler.getInstance().scope("chat/widget/viewport")) {
            var builder = new WindowBuilder(rows, scroll, keepAnchor ? anchor : null);
            store.visitNewest(channel, entry -> {
                if (!matches(entry)) return true;
                var wrapped = store.wrappedLines(entry.message(), font, width);
                if (geometry && builder.anchor != null && builder.anchor.message() == entry.message()) {
                    int index = 0, start = 0;
                    while (index + 1 < wrapped.size() && start + characters(wrapped.get(index)) <= character)
                        start += characters(wrapped.get(index++));
                    builder.anchor = new Line(entry.message(), wrapped.get(index), index);
                }
                for (int i = wrapped.size() - 1; i >= 0; i--) {
                    if (!builder.add(new Line(entry.message(), wrapped.get(i), i))) return false;
                }
                return true;
            });
            builder.finish();
            visible = List.copyOf(builder.result);
            scroll = builder.offset;
            older = builder.older;
            if (geometry || !smooth) {
                motion.reset(scroll); smooth = false; wheelRemainder = 0;
            } else if (keepAnchor) motion.shift(scroll - previousScroll);
            else if (scroll != previousScroll) {
                motion.limit(scroll);
                motion.moveTo(scroll, System.nanoTime()); wheelRemainder = 0;
            }
            anchor = visible.isEmpty() ? null : visible.getFirst();
            this.font = font; this.width = width; this.rows = rows;
            archiveRevision = store.archiveRevision(); cacheRevision = store.lineCacheRevision(); dirty = false; preserveAnchor = false;
            return visible;
        }
    }
    private static int characters(FormattedCharSequence text) {
        int[] count = {0};
        text.accept((index, style, codePoint) -> { count[0] += Character.charCount(codePoint); return true; });
        return count[0];
    }

    /** Rebuild only when crossing an integer row, not for each animated frame. */
    public Presentation presentation(Font font, int width, int rows, long now) {
        var targetLines = lines(font, width, rows);
        double position = smooth ? motion.value(now) : scroll;
        if (Math.abs(position - scroll) < .001) return new Presentation(targetLines, 0);
        int offset = Math.max(0, (int) Math.floor(position));
        if (presentedOffset != offset || presentedFont != font || presentedWidth != width || presentedRows != rows
                || presentedRevision != store.archiveRevision() || presentedCacheRevision != store.lineCacheRevision()) {
            var result = new ArrayList<Line>(rows + 1);
            int[] seen = {0};
            store.visitNewest(channel, entry -> {
                if (!matches(entry)) return true;
                var wrapped = store.wrappedLines(entry.message(), font, width);
                for (int i = wrapped.size() - 1; i >= 0; i--) {
                    if (seen[0]++ >= offset) result.add(new Line(entry.message(), wrapped.get(i), i));
                    if (result.size() == rows + 1) return false;
                }
                return true;
            });
            presented = List.copyOf(result);
            presentedOffset = offset; presentedFont = font; presentedWidth = width; presentedRows = rows;
            presentedRevision = store.archiveRevision(); presentedCacheRevision = store.lineCacheRevision();
        }
        return new Presentation(presented, (float) (position - offset));
    }
    public record Presentation(List<Line> lines, float shift) {}
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
