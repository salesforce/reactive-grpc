/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpccommon.testing;

import javax.annotation.Nonnull;
import java.util.Iterator;

/**
 * A sequence of Integers that ticks a {@link BackpressureDetector} each time it emits a new value.
 */
public class Sequence implements Iterable<Integer> {
    private final int max;
    private final BackpressureDetector backpressureDetector;

    public Sequence(int max) {
        this(max, null);
    }

    public Sequence(int max, BackpressureDetector backpressureDetector) {
        this.max = max;
        this.backpressureDetector = backpressureDetector;
    }

    @Override
    @Nonnull
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            private static final int SEQUENCE_WAIT_TIME = 10;
            private int i = 1;

            public void remove() {
                throw new UnsupportedOperationException();
            }

            public boolean hasNext() {
                return i < max;
            }

            public Integer next() {
                if (backpressureDetector != null) {
                    backpressureDetector.tick();
                }
                try {
                    Thread.sleep(SEQUENCE_WAIT_TIME);
                } catch (InterruptedException e) {

                }
                return i++;
            }
        };
    }
}
