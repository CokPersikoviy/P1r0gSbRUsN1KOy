package ru.wilyfox.client.utility;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import ru.wilyfox.client.statistic.MiningStatisticsTracker;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client break prediction succeeds even where the server forbids breaking (spawn, regions).
 * A break counts only after the server acks its sequence and the block really changed.
 */
public final class PendingBlockBreaks {
    private static final long TIMEOUT_NANOS = 3_000_000_000L;
    private static final int MAX_PENDING = 256;
    private static final Deque<Pending> PENDING = new ArrayDeque<>();

    private PendingBlockBreaks() {}

    private record Pending(BlockPos pos, BlockState original, int sequence, long createdAt) {}

    public static void predicted(BlockPos pos, BlockState original, int sequence) {
        if (PENDING.size() >= MAX_PENDING) PENDING.removeFirst();
        PENDING.addLast(new Pending(pos.immutable(), original, sequence, System.nanoTime()));
    }

    /** Called after vanilla applied the server-verified states for every prediction up to {@code sequence}. */
    public static void acknowledged(int sequence, ClientLevel level) {
        if (level == null) { PENDING.clear(); return; }
        long now = System.nanoTime();
        while (!PENDING.isEmpty()) {
            Pending next = PENDING.peekFirst();
            if (next.sequence > sequence && now - next.createdAt < TIMEOUT_NANOS) break;
            PENDING.removeFirst();
            // A rejected break restores the original state; an accepted one leaves air or a mine placeholder.
            if (level.getBlockState(next.pos) != next.original) confirmed();
        }
    }

    public static void clear() { PENDING.clear(); }

    public static void confirmed() {
        BlockBreakCounter.recordBreak();
        MiningStatisticsTracker.recordBreak();
    }
}
