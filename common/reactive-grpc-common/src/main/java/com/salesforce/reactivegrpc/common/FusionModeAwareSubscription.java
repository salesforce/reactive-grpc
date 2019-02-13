/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import org.reactivestreams.Subscription;

/**
 * This is interfaces which allows to access to fussed mode.
 * This class is used in {@link AbstractSubscriberAndProducer} in order to do
 * {@link Subscription} setup in one atomic action by providing a specific
 * {@link Subscription} decorator
 *
 * @see {@link com.salesforce.rxgrpc.stub.FusionAwareQueueSubscriptionAdapter}
 * @see {@link com.salesforce.reactorgrpc.stub.FusionAwareQueueSubscriptionAdapter}
 */
public interface FusionModeAwareSubscription extends Subscription {

    int mode();
}
