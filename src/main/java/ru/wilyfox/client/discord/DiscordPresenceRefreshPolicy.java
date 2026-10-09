package ru.wilyfox.client.discord;

/** Client-thread scheduling: context changes bypass the progress update interval. */
final class DiscordPresenceRefreshPolicy {
    private boolean requested;
    private long contextRevision;
    private long requestedAt;

    boolean shouldUpdate(long now, long intervalMillis, long revision, long appliedAt) {
        return !requested || contextRevision != revision
                || now - Math.max(requestedAt, appliedAt) >= Math.max(1L, intervalMillis);
    }
    boolean contextChanged(long revision) { return !requested || contextRevision != revision; }
    void requested(long now, long revision) {
        requested = true; requestedAt = now; contextRevision = revision;
    }
    void reset() { requested = false; }
}
