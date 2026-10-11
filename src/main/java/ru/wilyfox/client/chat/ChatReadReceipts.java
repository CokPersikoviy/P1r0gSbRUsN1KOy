package ru.wilyfox.client.chat;

import java.util.*;

/** Compact, bounded read receipts over original messages, without copying their contents. */
final class ChatReadReceipts<T> {
    private static final long ALL = Long.MIN_VALUE;
    private final Map<String, Long> windows = new HashMap<>();
    private final IdentityHashMap<T, Receipt> messages = new IdentityHashMap<>();

    boolean unread(String key, T message) {
        var receipt = messages.get(message);
        return receipt == null || (receipt.bits & ALL) == 0 && (receipt.bits & mask(key)) == 0;
    }
    boolean read(String key, T message, boolean inAll) {
        var receipt = messages.computeIfAbsent(message, ignored -> new Receipt());
        long before = receipt.bits;
        receipt.bits |= inAll ? ALL : mask(key);
        return before != receipt.bits;
    }
    void forget(T message) { messages.remove(message); }
    void forgetWindow(String key) {
        Long mask = windows.remove(key);
        if (mask != null) messages.values().forEach(receipt -> receipt.bits &= ~mask);
    }
    void retainWindows(Set<String> keys) {
        for (String key : List.copyOf(windows.keySet())) if (!keys.contains(key)) forgetWindow(key);
    }
    void clear() { messages.clear(); windows.clear(); }
    private long mask(String key) {
        Long existing = windows.get(key);
        if (existing != null) return existing;
        long occupied = ALL;
        for (long mask : windows.values()) occupied |= mask;
        long available = Long.lowestOneBit(~occupied);
        if (available == 0) throw new IllegalStateException("Too many chat windows");
        windows.put(key, available);
        return available;
    }
    private static final class Receipt { long bits; }
}
