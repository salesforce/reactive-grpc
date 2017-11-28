package com.salesforce.servicelibs.reactivegrpc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GradleProofTest {
    @Test
    public void gradleProof() throws Exception {
        GradleProof proof = new GradleProof();
        try {
            proof.startServer();
            String result = proof.doClient("World");
            assertEquals("Hello World", result);
        } finally {
            proof.stopServer();
        }
    }
}
