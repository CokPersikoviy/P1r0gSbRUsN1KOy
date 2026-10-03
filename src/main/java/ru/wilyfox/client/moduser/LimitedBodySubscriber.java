package ru.wilyfox.client.moduser;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bound the response during receipt, before allocating a complete JSON body. */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private int received;
    private boolean failed;

    @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        delegate.onSubscribe(subscription);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
        if (failed) return;
        for (ByteBuffer buffer : buffers) {
            if (buffer.remaining() > SocialWire.MAX_MESSAGE_SIZE - received) {
                failed = true;
                subscription.cancel();
                delegate.onError(new IllegalArgumentException("Response exceeds size limit"));
                return;
            }
            received += buffer.remaining();
        }
        delegate.onNext(buffers);
    }
    @Override public void onError(Throwable failure) { if (!failed) delegate.onError(failure); }
    @Override public void onComplete() { if (!failed) delegate.onComplete(); }
}
