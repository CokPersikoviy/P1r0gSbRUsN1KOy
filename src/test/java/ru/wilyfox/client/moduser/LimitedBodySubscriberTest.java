package ru.wilyfox.client.moduser;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;

class LimitedBodySubscriberTest {
    private static final class Subscription implements Flow.Subscription {
        boolean cancelled;
        @Override public void request(long count) {}
        @Override public void cancel() { cancelled = true; }
    }

    @Test void accumulatesFragmentsAndRejectsOversizedResponsesDuringReceipt() {
        var subscriber = new LimitedBodySubscriber();
        var subscription = new Subscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3})));
        subscriber.onComplete();
        assertArrayEquals(new byte[]{1, 2, 3}, subscriber.getBody().toCompletableFuture().join());
        assertFalse(subscription.cancelled);

        var oversized = new LimitedBodySubscriber();
        var oversizedSubscription = new Subscription();
        oversized.onSubscribe(oversizedSubscription);
        oversized.onNext(List.of(ByteBuffer.allocate(SocialWire.MAX_MESSAGE_SIZE)));
        oversized.onNext(List.of(ByteBuffer.wrap(new byte[]{1})));
        assertTrue(oversizedSubscription.cancelled);
        assertThrows(CompletionException.class, () -> oversized.getBody().toCompletableFuture().join());
    }
}
