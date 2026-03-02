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
    private HybridConfig hybrid;

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

    public HybridConfig getHybrid() {
        return hybrid;
    }

    public void setHybrid(HybridConfig hybrid) {
        this.hybrid = hybrid;
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

    /**
     * Configuration for hybrid search.
     */
    public static class HybridConfig {
        private boolean enabled = true;
        private int topKVector = 10;
        private int topKKeyword = 10;
        private double alpha = 0.6;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopKVector() {
            return topKVector;
        }

        public void setTopKVector(int topKVector) {
            this.topKVector = topKVector;
        }

        public int getTopKKeyword() {
            return topKKeyword;
        }

        public void setTopKKeyword(int topKKeyword) {
            this.topKKeyword = topKKeyword;
        }

        public double getAlpha() {
            return alpha;
        }

        public void setAlpha(double alpha) {
            this.alpha = alpha;
        }
    }
}
