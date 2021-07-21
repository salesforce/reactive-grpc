/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.retry;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.retry.Retry;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("Duplicates")
public class GrpcRetryTest {
    private Flux<Integer> newThreeErrorFlux() {
        return Flux.create(new Consumer<FluxSink<Integer>>() {
            int count = 3;
            @Override
            public void accept(FluxSink<Integer> emitter) {
                if (count > 0) {
                    emitter.error(new Throwable("Not yet!"));
                    count--;
                } else {
                    emitter.next(0);
                    emitter.complete();
                }
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private Mono<Integer> newThreeErrorMono() {
        return Mono.create(new Consumer<MonoSink<Integer>>() {
            int count = 3;
            @Override
            public void accept(MonoSink<Integer> emitter){
                if (count > 0) {
                    emitter.error(new Throwable("Not yet!"));
                    count--;
                } else {
                    emitter.success(0);
                }
            }
        });
    }

    @Test
    public void noRetryMakesErrorFlowabable() {
        Flux<Integer> test = newThreeErrorFlux()
                .as(flux -> flux);

        StepVerifier.create(test)
                .expectErrorMessage("Not yet!")
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void noRetryMakesErrorSingle() {
        Mono<Integer> test = newThreeErrorMono()
                .as(mono -> mono);

        StepVerifier.create(test)
                .expectErrorMessage("Not yet!")
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void oneToManyRetryWhen() {
        Flux<Integer> test = newThreeErrorMono()
                .<Flux<Integer>>as(GrpcRetry.OneToMany.retryWhen(Mono::flux, Retry.any().retryMax(4)));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void oneToManyRetryImmediately() {
        Flux<Integer> test = newThreeErrorMono()
                .<Flux<Integer>>as(GrpcRetry.OneToMany.retryImmediately(Mono::flux));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void oneToManyRetryAfter() {
        Flux<Integer> test = newThreeErrorMono()
                .<Flux<Integer>>as(GrpcRetry.OneToMany.retryAfter(Mono::flux, Duration.ofMillis(10)));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void manyToManyRetryWhen() {
        Flux<Integer> test = newThreeErrorFlux()
                .<Integer>transformDeferred(GrpcRetry.ManyToMany.retryWhen(Function.identity(), Retry.any().retryMax(4)));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void manyToManyRetryImmediately() {
        Flux<Integer> test = newThreeErrorFlux()
                .<Integer>transformDeferred(GrpcRetry.ManyToMany.retryImmediately(Function.identity()));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void manyToManyRetryAfter() {
        Flux<Integer> test = newThreeErrorFlux()
                .<Integer>transformDeferred(GrpcRetry.ManyToMany.retryAfter(Function.identity(), Duration.ofMillis(10)));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void manyToOneRetryWhen() {
        Mono<Integer> test = newThreeErrorFlux()
                .<Mono<Integer>>as(GrpcRetry.ManyToOne.retryWhen(Flux::single, Retry.any().retryMax(4)));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void manyToOneRetryImmediately() {
        Mono<Integer> test = newThreeErrorFlux()
                .<Mono<Integer>>as(GrpcRetry.ManyToOne.retryImmediately(Flux::single));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    public void manyToOneRetryAfter() {
        Mono<Integer> test = newThreeErrorFlux()
                .<Mono<Integer>>as(GrpcRetry.ManyToOne.retryAfter(Flux::single, Duration.ofMillis(10)));

        StepVerifier.create(test)
                .expectNext(0)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }
}
