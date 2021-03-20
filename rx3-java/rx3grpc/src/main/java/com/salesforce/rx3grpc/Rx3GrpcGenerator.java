/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc;

import com.salesforce.jprotoc.ProtocPlugin;
import com.salesforce.reactivegrpc.gen.ReactiveGrpcGenerator;

/**
 * A protoc generator for generating ReactiveX 3.0 bindings for gRPC.
 */
public class Rx3GrpcGenerator extends ReactiveGrpcGenerator {

    @Override
    protected String getClassPrefix() {
        return "Rx3";
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            ProtocPlugin.generate(new Rx3GrpcGenerator());
        } else {
            ProtocPlugin.debug(new Rx3GrpcGenerator(), args[0]);
        }
    }
}
