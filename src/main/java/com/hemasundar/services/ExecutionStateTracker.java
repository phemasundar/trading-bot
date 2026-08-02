package com.hemasundar.services;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe component managing global strategy/screener execution lifecycle state.
 */
@Component
public class ExecutionStateTracker {

    private final AtomicBoolean executionRunning = new AtomicBoolean(false);
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private final AtomicReference<String> currentExecutionTask = new AtomicReference<>("");
    private final AtomicBoolean authFailed = new AtomicBoolean(false);

    @Getter
    private volatile long executionStartTimeMs;

    public boolean isExecutionRunning() {
        return executionRunning.get();
    }

    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    public void requestCancellation() {
        cancellationRequested.set(true);
    }

    public boolean isAuthFailed() {
        return authFailed.get();
    }

    public void setAuthFailed(boolean failed) {
        authFailed.set(failed);
    }

    public String getCurrentExecutionTask() {
        return currentExecutionTask.get();
    }

    public void setCurrentExecutionTask(String task) {
        currentExecutionTask.set(task != null ? task : "");
    }

    public void startGlobalExecution(String initialTask) {
        executionRunning.set(true);
        cancellationRequested.set(false);
        authFailed.set(false);
        executionStartTimeMs = System.currentTimeMillis();
        currentExecutionTask.set(initialTask != null ? initialTask : "");
    }

    public void finishGlobalExecution() {
        executionRunning.set(false);
        cancellationRequested.set(false);
        currentExecutionTask.set("");
    }
}
