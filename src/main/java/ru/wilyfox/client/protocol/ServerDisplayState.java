package ru.wilyfox.client.protocol;

import ru.wilyfox.utils.Formatting;

/** Display only: TAB text must never change authenticated presence scope or gameplay state. */
final class ServerDisplayState {
    private CurrentServerInfo protocol = CurrentServerInfo.unknown();
    private CurrentServerInfo tab = CurrentServerInfo.unknown();
    private long revision;
    private long protocolRevision;
    private long tabRevision;

    void protocolUpdated(CurrentServerInfo next) {
        if (sameServer(protocol, next)) return;
        protocol = next;
        protocolRevision = ++revision;
    }

    void tabUpdated(String footer) {
        var next = CurrentServerInfo.fromDisplayText(Formatting.stripMinecraftFormatting(footer));
        // Changes to booster text, player counts and other footer lines are not server transitions.
        if (sameServer(tab, next)) return;
        tab = next;
        tabRevision = ++revision;
    }

    String displayName() {
        if (tab.isKnown() && (!protocol.isKnown() || tabRevision > protocolRevision)) {
            if (tab.mirror() == 0 && tab.family().equals(protocol.family())
                    && tab.serverNumber() == protocol.serverNumber()) return protocol.displayName();
            return tab.displayName();
        }
        return protocol.isKnown() ? protocol.displayName() : "";
    }

    private static boolean sameServer(CurrentServerInfo left, CurrentServerInfo right) {
        return left.family().equals(right.family()) && left.serverNumber() == right.serverNumber()
                && left.mirror() == right.mirror() && (left.family().equals("PRISONEVO")
                || left.family().equals("HUB") || java.util.Objects.equals(left.rawName(), right.rawName()));
    }
}
