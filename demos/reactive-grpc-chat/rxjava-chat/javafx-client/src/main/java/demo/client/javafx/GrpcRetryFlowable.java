package demo.client.javafx;

import io.reactivex.Flowable;
import org.reactivestreams.Subscriber;

import java.util.function.Supplier;

public class GrpcRetryFlowable<T> extends Flowable<T> {
    private final Flowable<T> retryFlowable;

    GrpcRetryFlowable(Supplier<Flowable<T>> flowableSupplier) {
        this.retryFlowable = Flowable.defer(flowableSupplier::get).retry();
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        retryFlowable.subscribe(s);
    }

    public static <U> Flowable<U> doRetry(Flowable<U> flowable) {
        return new GrpcRetryFlowable<>(() -> flowable);
    }
}
