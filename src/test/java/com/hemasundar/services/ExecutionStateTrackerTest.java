package com.hemasundar.services;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ExecutionStateTrackerTest {

    private ExecutionStateTracker tracker;

    @BeforeMethod
    public void setUp() {
        tracker = new ExecutionStateTracker();
    }

    @Test
    public void testInitialState() {
        Assert.assertFalse(tracker.isExecutionRunning());
        Assert.assertFalse(tracker.isCancellationRequested());
        Assert.assertFalse(tracker.isAuthFailed());
        Assert.assertEquals(tracker.getCurrentExecutionTask(), "");
        Assert.assertEquals(tracker.getExecutionStartTimeMs(), 0L);
    }

    @Test
    public void testStartGlobalExecution() {
        tracker.startGlobalExecution("Running Bullish ZEBRA");
        Assert.assertTrue(tracker.isExecutionRunning());
        Assert.assertFalse(tracker.isCancellationRequested());
        Assert.assertFalse(tracker.isAuthFailed());
        Assert.assertEquals(tracker.getCurrentExecutionTask(), "Running Bullish ZEBRA");
        Assert.assertTrue(tracker.getExecutionStartTimeMs() > 0);
    }

    @Test
    public void testStartGlobalExecutionNullTask() {
        tracker.startGlobalExecution(null);
        Assert.assertTrue(tracker.isExecutionRunning());
        Assert.assertEquals(tracker.getCurrentExecutionTask(), "");
    }

    @Test
    public void testRequestCancellation() {
        tracker.requestCancellation();
        Assert.assertTrue(tracker.isCancellationRequested());
    }

    @Test
    public void testAuthFailed() {
        tracker.setAuthFailed(true);
        Assert.assertTrue(tracker.isAuthFailed());

        tracker.setAuthFailed(false);
        Assert.assertFalse(tracker.isAuthFailed());
    }

    @Test
    public void testSetCurrentExecutionTask() {
        tracker.setCurrentExecutionTask("Task A");
        Assert.assertEquals(tracker.getCurrentExecutionTask(), "Task A");

        tracker.setCurrentExecutionTask(null);
        Assert.assertEquals(tracker.getCurrentExecutionTask(), "");
    }

    @Test
    public void testFinishGlobalExecution() {
        tracker.startGlobalExecution("Initial Task");
        tracker.requestCancellation();
        Assert.assertTrue(tracker.isExecutionRunning());

        tracker.finishGlobalExecution();
        Assert.assertFalse(tracker.isExecutionRunning());
        Assert.assertFalse(tracker.isCancellationRequested());
        Assert.assertEquals(tracker.getCurrentExecutionTask(), "");
    }
}
