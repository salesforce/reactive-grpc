package com.salesforce.reactivegrpc.jmh;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RunWith(Parameterized.class)
public class BenchmarkTest {
    private final String benchmark;


    public BenchmarkTest(String benchmark, String name) {
        this.benchmark = benchmark;
    }

    @Test
    @Ignore
    public void run() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(benchmark)
                .shouldFailOnError(true)
                .build();
        Runner runner = new Runner(opt);
        RunResult result = runner.runSingle();
    }

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> data() {
        BenchmarkList benchmarkList = BenchmarkList.fromResource("META-INF/BenchmarkList");

        List<Object[]> ret = new ArrayList<>();
        for (BenchmarkListEntry benchmark : benchmarkList.getAll(null, Collections.emptyList())) {
            String method = benchmark.getUsername();
            ret.add(new Object[] {method, method.replace("com.salesforce.reactivegrpc.jmh.", "")});
        }

        return ret;
    }
}
