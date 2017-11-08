/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import javax.annotation.Nonnull;
import java.util.Iterator;

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
            int i = 1;

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
                try { Thread.sleep(10); } catch (InterruptedException e) {}
                return i++;
            }
        };
    }
}
