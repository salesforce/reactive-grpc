/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Neither gRPC nor rxJava provide a direct way to detect that backpressure occurred. However, we can detect
 * it statistically. Backpressure is signalled by delaying calls to request(). If messages were flowing without
 * backpressure, the rate at which request() is called would be relatively constant. In the presence of backpressure,
 * calls to request() are delayed while the consumer deals with its backlog. We can measure these delays by looking
 * for statistical outliers in the durations between calls to request(). Large outliers indicate the presence of a
 * backpressure event.
 *
 * https://eurekastatistics.com/using-the-median-absolute-deviation-to-find-outliers/
 */
public class BackpressureDetector {
    private final List<Double> timestamps = new LinkedList<>();
    private final int madMultiple;

    public BackpressureDetector(int madMultipleCutoff) {
        this.madMultiple = madMultipleCutoff;
    }

    public void tick() {
        timestamps.add(System.currentTimeMillis() * 1.0);
    }

    public void reset() {
        timestamps.clear();
    }

    public List<Double> getTimesBetweenNextCalls() {
        List<Double> ret = new LinkedList<>();
        for (int i = 1; i < timestamps.size(); i++) {
            ret.add(timestamps.get(i) - timestamps.get(i - 1));
        }
        return ret;
    }

    public boolean backpressureDelayOcurred() {
        try {
            return getOutliers().size() > 0;
        } catch (IndexOutOfBoundsException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public List<Double> getOutliers() {
        List<Double> samples = getTimesBetweenNextCalls();
        double median = median(samples);
        double mad = mad(samples);
        return samples.stream()
                .map(s -> Math.abs(s - median) / mad)
                .filter(s -> s > madMultiple)
                .collect(Collectors.toList());
    }

    private double mad(List<Double> samples) {
        // Compute median absolute deviation
        double median = median(samples);
        List<Double> deviations = samples.stream().map(s -> Math.abs(s - median)).collect(Collectors.toList());
        return median(deviations);
    }

    private double median(List<Double> samples) {
        // Compute the statistical median of a SORTED list of data
        samples.sort(Comparator.naturalOrder());
        int c = samples.size() / 2;
        if (samples.size() % 2 == 1) { // ODD number of samples
            return samples.get(c);
        } else {
            return (samples.get(c - 1) + samples.get(c)) / 2.0;
        }
    }
}
