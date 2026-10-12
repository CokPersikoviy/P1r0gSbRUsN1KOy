package ru.wilyfox.client.chat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** A tab's compiled filter. Regex uses full-message matching, as in ChatPlus. */
public final class ChatTextFilter {
    public static final ChatTextFilter EMPTY = compile("", false);
    private final String source, literal, error;
    private final boolean regex;
    private final Pattern pattern;
    // Repainting, counters and sound routing share the same result instead of rerunning a regex.
    private final Map<String, Boolean> recent = new LinkedHashMap<>(16, .75f, true);

    private ChatTextFilter(String value, boolean regex) {
        source = value == null ? "" : regex ? value : value.strip();
        this.regex = regex;
        literal = source.toLowerCase(Locale.ROOT);
        Pattern compiled = null;
        String failure = null;
        if (regex && !source.isEmpty()) {
            try { compiled = Pattern.compile(source); }
            catch (PatternSyntaxException invalid) { failure = invalid.getDescription(); }
        }
        pattern = compiled; error = failure;
    }
    public static ChatTextFilter compile(String value, boolean regex) { return new ChatTextFilter(value, regex); }
    public String source() { return source; }
    public boolean regex() { return regex; }
    public String error() { return error; }
    public boolean same(String value, boolean mode) { return regex == mode && source.equals(value == null ? "" : mode ? value : value.strip()); }
    public boolean matches(String text) {
        return matches(text, null);
    }
    public boolean matches(String text, String normalizedText) {
        if (source.isEmpty()) return true;
        if (error != null || text == null) return false;
        if (!regex) return (normalizedText != null ? normalizedText : text.toLowerCase(Locale.ROOT)).contains(literal);
        Boolean cached = recent.get(text);
        if (cached != null) return cached;
        boolean matched;
        try { matched = pattern.matcher(new BudgetText(text)).matches(); }
        catch (MatchLimit | StackOverflowError tooExpensive) { matched = false; }
        // Extremely long messages aren't retained in the small result cache.
        if (text.length() <= 4096) {
            if (recent.size() >= 128) recent.remove(recent.keySet().iterator().next());
            recent.put(text, matched);
        }
        return matched;
    }

    /** Bound backtracking on the render thread, while retaining Java lookarounds and backreferences. */
    private static final class BudgetText implements CharSequence {
        private final String text;
        private final BudgetText root;
        private final int start, end;
        private int reads = 65_536;
        BudgetText(String text) { this.text = text; root = this; start = 0; end = text.length(); }
        private BudgetText(BudgetText parent, int start, int end) {
            text = parent.text; root = parent.root; this.start = parent.start + start; this.end = parent.start + end;
        }
        @Override public int length() { return end - start; }
        @Override public char charAt(int index) {
            if (--root.reads < 0) throw MatchLimit.INSTANCE;
            if (index < 0 || index >= length()) throw new IndexOutOfBoundsException(index);
            return text.charAt(start + index);
        }
        @Override public CharSequence subSequence(int start, int end) {
            if (start < 0 || end < start || end > length()) throw new IndexOutOfBoundsException();
            return new BudgetText(this, start, end);
        }
        @Override public String toString() { return text.substring(start, end); }
    }
    private static final class MatchLimit extends RuntimeException {
        private static final MatchLimit INSTANCE = new MatchLimit();
        private MatchLimit() { super(null, null, false, false); }
    }
}
