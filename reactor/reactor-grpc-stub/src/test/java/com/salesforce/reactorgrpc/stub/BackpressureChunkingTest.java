package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.ReactiveBackpressureChunker;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Operators;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BackpressureChunkingTest {
    @Test
    public void chunkOperatorCorrectlyChunks() {
        final List<Long> requests = new ArrayList<>();
        int chunkSize = ReactiveBackpressureChunker.DEFAULT_CHUNK_SIZE;

        Flux<Integer> chunked = Flux.range(0, chunkSize + 4)
                .doOnRequest(requests::add)
                .transform(Operators.lift(new BackpressureChunkingLifter<Integer>()));

        StepVerifier.create(chunked)
                .expectNext(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19)
                .verifyComplete();

        assertThat(requests).containsExactly((long) chunkSize, (long) chunkSize);
    }
}
