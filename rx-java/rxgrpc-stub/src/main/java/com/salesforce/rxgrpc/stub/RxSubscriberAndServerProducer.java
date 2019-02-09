/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractSubscriberAndProducer;
import com.salesforce.reactivegrpc.common.AbstractSubscriberAndServerProducer;
import io.reactivex.FlowableSubscriber;

/**
 * The gRPC server-side implementation of {@link AbstractSubscriberAndProducer}.
 *
 * @param <T>
 */
public class RxSubscriberAndServerProducer<T>
        extends AbstractSubscriberAndServerProducer<T>
        implements FlowableSubscriber<T> {
}
