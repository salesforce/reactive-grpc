/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractServerStreamObserverAndPublisher;
import com.salesforce.reactivegrpc.common.Consumer;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import reactor.core.Fuseable;
import reactor.util.concurrent.Queues;

public class ReactorServerStreamObserverAndPublisher<T>
        extends AbstractServerStreamObserverAndPublisher<T>
        implements Fuseable.QueueSubscription<T>, Fuseable {

    public ReactorServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(serverCallStreamObserver, Queues.<T>get(DEFAULT_CHUNK_SIZE).get(), onSubscribe);
    }

    public ReactorServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(serverCallStreamObserver, Queues.<T>get(DEFAULT_CHUNK_SIZE).get(), onSubscribe, onTerminate);
    }

    public ReactorServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(serverCallStreamObserver, Queues.<T>get(prefetch).get(), prefetch, onSubscribe);
    }

    public ReactorServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(serverCallStreamObserver, Queues.<T>get(prefetch).get(), prefetch, onSubscribe, onTerminate);
    }

    @Override
    public int requestFusion(int requestedMode) {
        if ((requestedMode & Fuseable.ASYNC) != 0) {
            outputFused = true;
            return Fuseable.ASYNC;
        }
        return Fuseable.NONE;
    }
}