package com.agent.model;

/**
 * Result of mode-specific query execution.
 * 
 * Encapsulates the output from a TaskModeHandler, providing both
 * the final answer and mode-specific metadata.
 */
public class ModeExecutionResult {
    private final TaskMode mode;
    private final String answer;
    private final String metadata;  // Mode-specific info (e.g., "Found 5 cases", "Citations: ...")
    private final boolean success;
    private final String errorMessage;  // null if success=true

    /**
     * Successful execution result.
     */
    public ModeExecutionResult(TaskMode mode, String answer, String metadata) {
        this.mode = mode;
        this.answer = answer;
        this.metadata = metadata;
        this.success = true;
        this.errorMessage = null;
    }

    /**
     * Failed execution result.
     */
    public ModeExecutionResult(TaskMode mode, String errorMessage) {
        this.mode = mode;
        this.answer = null;
        this.metadata = null;
        this.success = false;
        this.errorMessage = errorMessage;
    }

    public TaskMode getMode() {
        return mode;
    }

    public String getAnswer() {
        return answer;
    }

    public String getMetadata() {
        return metadata;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ModeExecutionResult{" +
                "mode=" + mode +
                ", success=" + success +
                (success ? ", answer='" + (answer != null && answer.length() > 50 ? 
                    answer.substring(0, 50) + "..." : answer) + '\'' : 
                    ", error='" + errorMessage + '\'') +
                (metadata != null ? ", metadata='" + metadata + '\'' : "") +
                '}';
    }
}
