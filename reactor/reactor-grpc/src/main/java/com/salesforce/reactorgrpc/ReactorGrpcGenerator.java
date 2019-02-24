/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import com.salesforce.jprotoc.ProtocPlugin;
import com.salesforce.reactivegrpc.gen.ReactiveGrpcGenerator;

/**
 * A protoc generator for generating Reactor bindings for gRPC.
 */
public class ReactorGrpcGenerator extends ReactiveGrpcGenerator {

    @Override
    protected String getClassPrefix() {
        return "Reactor";
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            ProtocPlugin.generate(new ReactorGrpcGenerator());
        } else {
            ProtocPlugin.debug(new ReactorGrpcGenerator(), args[0]);
        }
    }
}