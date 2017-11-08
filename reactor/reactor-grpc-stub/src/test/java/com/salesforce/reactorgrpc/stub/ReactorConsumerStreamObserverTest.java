/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import io.grpc.stub.ClientCallStreamObserver;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("unchecked")
public class ReactorConsumerStreamObserverTest {
    @Test
    public void rxConsumerIsSet() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        ReactorConsumerStreamObserver rxObs = new ReactorConsumerStreamObserver();

        rxObs.beforeStart(obs);

        assertThat(rxObs.getRxConsumer()).isNotNull();
    }

    @Test
    public void onNextDelegates() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        ReactorConsumerStreamObserver rxObs = new ReactorConsumerStreamObserver();
        Subscriber<Object> sub = mock(Subscriber.class);

        rxObs.beforeStart(obs);
        rxObs.getRxConsumer().subscribe(sub);

        Object obj = new Object();
        StepVerifier.create(rxObs.getRxConsumer())
                .then(() -> rxObs.onNext(obj))
                .expectNext(obj)
                .then(rxObs::onCompleted)
                .expectComplete()
                .verify(Duration.ofSeconds(3));
    }

    @Test
    public void onErrorDelegates() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        ReactorConsumerStreamObserver rxObs = new ReactorConsumerStreamObserver();
        Subscriber<Object> sub = mock(Subscriber.class);

        rxObs.beforeStart(obs);
        rxObs.getRxConsumer().subscribe(sub);

        Throwable obj = new Exception("test error");
        StepVerifier.create(rxObs.getRxConsumer())
                .then(() -> rxObs.onError(obj))
                .expectErrorMessage("test error")
                .verify(Duration.ofSeconds(3));
    }
}
