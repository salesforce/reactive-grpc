/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
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
 * See {@code com.salesforce.rxgrpc.stub.FusionAwareQueueSubscriptionAdapter}
 * See {@code com.salesforce.reactorgrpc.stub.FusionAwareQueueSubscriptionAdapter}
 */
public interface FusionModeAwareSubscription extends Subscription {

    int mode();
}
