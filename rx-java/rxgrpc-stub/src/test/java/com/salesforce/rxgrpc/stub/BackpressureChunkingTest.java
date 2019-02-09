//package com.salesforce.rxgrpc.stub;
//
//import io.reactivex.Flowable;
//import io.reactivex.functions.Function;
//import io.reactivex.functions.LongConsumer;
//import io.reactivex.subscribers.TestSubscriber;
//import org.junit.Test;
//import org.reactivestreams.Publisher;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//public class BackpressureChunkingTest {
//    @Test
//    public void chunkOperatorCorrectlyChunksInfiniteRequest() {
//        final List<Long> requests = new ArrayList<Long>();
//        int chunkSize = ReactiveBackpressureChunker.DEFAULT_CHUNK_SIZE;
//
//        int halfChunk = chunkSize / 2;
//        int num = chunkSize * 2 + halfChunk;
//
//        TestSubscriber<Integer> testSubscriber = Flowable.range(0, num)
//                .doOnRequest(new LongConsumer() {
//                    @Override
//                    public void accept(long l) {
//                        requests.add(l);
//                    }
//                })
//                .lift(new BackpressureChunkingOperator<Integer>())
//                .test();
//
//        testSubscriber.awaitTerminalEvent();
//        testSubscriber.assertComplete();
//
//        assertThat(requests).containsExactly((long) chunkSize, (long) chunkSize, (long) chunkSize);
//    }
//
//    @Test
//    public void chunkOperatorCorrectlyChunksFiniteRequest() {
//        final List<Long> requests = new ArrayList<Long>();
//        int chunkSize = ReactiveBackpressureChunker.DEFAULT_CHUNK_SIZE;
//
//        int halfChunk = chunkSize / 2;
//        int num = chunkSize * 2 + halfChunk;
//
//        TestSubscriber<Integer> testSubscriber = Flowable.range(0, num)
//                .doOnRequest(new LongConsumer() {
//                    @Override
//                    public void accept(long l) {
//                        requests.add(l);
//                    }
//                })
//                .lift(new BackpressureChunkingOperator<Integer>())
//                .test(num);
//
//        testSubscriber.awaitTerminalEvent();
//        testSubscriber.assertComplete();
//
//        assertThat(requests).containsExactly((long) chunkSize, (long) chunkSize, (long) halfChunk);
//    }
//
//    /**
//     * https://github.com/salesforce/reactive-grpc/issues/120
//     */
//    @Test
//    public void chunkOperatorWorksWithConcatMap() {
//        TestSubscriber<Integer> testSubscriber = Flowable.range(0, 24)
//                .lift(new BackpressureChunkingOperator<Integer>())
//                // Java6 style because Android :(
//                .concatMap(new Function<Integer, Publisher<Integer>>() {
//                    @Override
//                    public Publisher<Integer> apply(Integer item) throws Exception {
//                        return Flowable.just(item).delay(3, TimeUnit.MILLISECONDS);
//                    }
//                })
//                .test();
//
//        testSubscriber.awaitTerminalEvent();
//        testSubscriber.assertNoErrors();
//    }
//}
