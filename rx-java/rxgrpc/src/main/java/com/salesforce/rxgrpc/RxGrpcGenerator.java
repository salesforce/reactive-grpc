/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.salesforce.jprotoc.ProtocPlugin;
import com.salesforce.reactivegrpc.gen.ReactiveGrpcGenerator;

/**
 * A protoc generator for generating ReactiveX 2.0 bindings for gRPC.
 */
public class RxGrpcGenerator extends ReactiveGrpcGenerator {

    @Override
    protected String getClassPrefix() {
        return "Rx";
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            ProtocPlugin.generate(new RxGrpcGenerator());
        } else {
            ProtocPlugin.debug(new RxGrpcGenerator(), args[0]);
        }
    }
}