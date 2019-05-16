/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.jmh;

import java.io.IOException;

import org.openjdk.jmh.runner.RunnerException;

/**
 * The main entry point for running the Reactive-gRPC performance test suite.
 */
public final class JMHRunner {
    private JMHRunner() { }

    public static void main(String... args) throws IOException, RunnerException {
        org.openjdk.jmh.Main.main(args);
    }
}
