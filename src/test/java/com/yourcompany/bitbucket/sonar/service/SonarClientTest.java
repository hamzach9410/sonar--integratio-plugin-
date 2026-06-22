package com.yourcompany.bitbucket.sonar.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public class SonarClientTest {

    private SonarClient client;

    @Before
    public void setUp() {
        client = new SonarClient("http://sonar.example.com/", "test-token");
    }

    @After
    public void tearDown() {
        client.close();
    }

    @Test
    public void testConstructorDoesNotThrow() {
        assertNotNull(client);
    }

    @Test(expected = IOException.class)
    public void testGetQualityGateStatusThrowsOnUnreachableHost() throws Exception {
        SonarClient unreachable = new SonarClient("http://localhost:19999", "token");
        try {
            unreachable.getQualityGateStatus("my-project", null, null);
        } finally {
            unreachable.close();
        }
    }

    @Test(expected = IOException.class)
    public void testGetIssuesThrowsOnUnreachableHost() throws Exception {
        SonarClient unreachable = new SonarClient("http://localhost:19999", "token");
        try {
            unreachable.getIssues("my-project");
        } finally {
            unreachable.close();
        }
    }

    @Test(expected = IOException.class)
    public void testGetMetricsThrowsOnUnreachableHost() throws Exception {
        SonarClient unreachable = new SonarClient("http://localhost:19999", "token");
        try {
            unreachable.getMetrics("my-project");
        } finally {
            unreachable.close();
        }
    }
}
