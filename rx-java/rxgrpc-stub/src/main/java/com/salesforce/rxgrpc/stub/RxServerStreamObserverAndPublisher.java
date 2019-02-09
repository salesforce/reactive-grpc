/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractServerStreamObserverAndPublisher;
import com.salesforce.reactivegrpc.common.Consumer;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.reactivex.internal.fuseable.QueueFuseable;
import io.reactivex.internal.fuseable.QueueSubscription;

public class RxServerStreamObserverAndPublisher<T>
        extends AbstractServerStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

    public RxServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(serverCallStreamObserver, Queues.<T>get(DEFAULT_CHUNK_SIZE).get(), onSubscribe);
    }

    public RxServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(serverCallStreamObserver, Queues.<T>get(DEFAULT_CHUNK_SIZE).get(), onSubscribe, onTerminate);
    }

    public RxServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(serverCallStreamObserver, Queues.<T>get(prefetch).get(), prefetch, onSubscribe);
    }

    public RxServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(serverCallStreamObserver, Queues.<T>get(prefetch).get(), prefetch, onSubscribe, onTerminate);
    }

    @Override
    public int requestFusion(int requestedMode) {
        if ((requestedMode & QueueFuseable.ASYNC) != 0) {
            outputFused = true;
            return QueueFuseable.ASYNC;
        }
        return QueueFuseable.NONE;
    }

    @Override
    public boolean offer(T t, T t1) {
        throw new UnsupportedOperationException();
    }
}