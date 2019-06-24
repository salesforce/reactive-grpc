package com.salesforce.servicelibs.reactivegrpc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BazelProofTest {
    @Test
    public void bazelProof() throws Exception {
        BazelProof proof = new BazelProof();
        try {
            proof.startServer();
            String result = proof.doClient("World");
            assertEquals("Hello World", result);
        } finally {
            proof.stopServer();
        }
    }
}
