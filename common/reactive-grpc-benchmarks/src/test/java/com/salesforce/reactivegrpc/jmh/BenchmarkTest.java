/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.jmh;

import java.util.Collections;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkTest {
    @Disabled
    @ParameterizedTest(name = "{1}")
    @MethodSource("data")
    public void run(String benchmark, String name) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(benchmark)
                .shouldFailOnError(true)
                .build();
        Runner runner = new Runner(opt);
        RunResult result = runner.runSingle();
    }

    public static Stream<Arguments> data() {
        BenchmarkList benchmarkList = BenchmarkList.fromResource("META-INF/BenchmarkList");

        return benchmarkList
            .getAll(null, Collections.emptyList())
            .stream()
            .map(benchmark1 -> {
                String method = benchmark1.getUsername();
                return Arguments.of(
                    method,
                    method.replace("com.salesforce.reactivegrpc.jmh.", "")
                );
            });
    }
}
