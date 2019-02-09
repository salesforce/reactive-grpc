/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractSubscriberAndClientProducer;
import com.salesforce.reactivegrpc.common.AbstractSubscriberAndProducer;
import reactor.core.CoreSubscriber;

/**
 * The gRPC client-side implementation of {@link AbstractSubscriberAndProducer}.
 *
 * @param <T>
 */
public class ReactorSubscriberAndClientProducer<T>
        extends AbstractSubscriberAndClientProducer<T>
        implements CoreSubscriber<T> {
}
