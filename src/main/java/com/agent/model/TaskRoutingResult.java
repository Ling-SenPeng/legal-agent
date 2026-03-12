package com.agent.model;

/**
 * Result of task routing analysis.
 * 
 * Records the detected task mode and confidence level for routing decisions.
 */
public class TaskRoutingResult {
    private final TaskMode mode;
    private final double confidence;  // [0.0, 1.0] - higher is more certain
    private final String reasoning;   // Explanation of why this mode was selected

    public TaskRoutingResult(TaskMode mode, double confidence, String reasoning) {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        this.mode = mode;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }

    public TaskMode getMode() {
        return mode;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    @Override
    public String toString() {
        return "TaskRoutingResult{" +
                "mode=" + mode +
                ", confidence=" + String.format("%.2f", confidence) +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }
}
