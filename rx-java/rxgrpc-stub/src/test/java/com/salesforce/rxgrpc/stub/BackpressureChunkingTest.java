package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.ReactiveBackpressureChunker;
import io.reactivex.Flowable;
import io.reactivex.functions.LongConsumer;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BackpressureChunkingTest {
    @Test
    public void chunkOperatorCorrectlyChunks() {
        final List<Long> requests = new ArrayList<Long>();
        int chunkSize = ReactiveBackpressureChunker.DEFAULT_CHUNK_SIZE;

        TestSubscriber<Integer> testSubscriber = Flowable.range(0, chunkSize * 2 + 4)
                .doOnRequest(new LongConsumer() {
                    @Override
                    public void accept(long l) {
                        requests.add(l);
                    }
                })
                .lift(new BackpressureChunkingOperator<Integer>())
                .test();

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();

        assertThat(requests).containsExactly((long) chunkSize, (long) chunkSize, (long) chunkSize);
    }
}
