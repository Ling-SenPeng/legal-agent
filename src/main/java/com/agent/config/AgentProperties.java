package com.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the agent.
 */
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private int defaultTopK;
    private VerificationConfig verification;

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public VerificationConfig getVerification() {
        return verification;
    }

    public void setVerification(VerificationConfig verification) {
        this.verification = verification;
    }

    /**
     * Configuration for the verification step.
     */
    public static class VerificationConfig {
        private boolean enabled;
        private boolean repairEnabled;
        private String factualKeywords;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRepairEnabled() {
            return repairEnabled;
        }

        public void setRepairEnabled(boolean repairEnabled) {
            this.repairEnabled = repairEnabled;
        }

        public String getFactualKeywords() {
            return factualKeywords;
        }

        public void setFactualKeywords(String factualKeywords) {
            this.factualKeywords = factualKeywords;
        }
    }
}
