package com.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Report from the verification step, indicating whether all citations are present.
 */
public record VerificationReport(
    @JsonProperty("passed")
    boolean passed,
    
    @JsonProperty("missingCitationLines")
    List<String> missingCitationLines,
    
    @JsonProperty("notes")
    String notes
) {
}
