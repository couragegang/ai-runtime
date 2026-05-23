package com.couragegang.ai.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("ai")
public class AiProperties {

    /** stub | deepseek */
    private String llmProvider = "stub";

    private DeepSeek deepseek = new DeepSeek();

    private PolicyService policyService = new PolicyService();

    private AuditService auditService = new AuditService();

    public String getLlmProvider() {
        return llmProvider;
    }

    public void setLlmProvider(String llmProvider) {
        this.llmProvider = llmProvider;
    }

    public DeepSeek getDeepseek() {
        return deepseek;
    }

    public void setDeepseek(DeepSeek deepseek) {
        this.deepseek = deepseek;
    }

    public PolicyService getPolicyService() {
        return policyService;
    }

    public void setPolicyService(PolicyService policyService) {
        this.policyService = policyService;
    }

    public AuditService getAuditService() {
        return auditService;
    }

    public void setAuditService(AuditService auditService) {
        this.auditService = auditService;
    }

    @ConfigurationProperties("deepseek")
    public static class DeepSeek {

        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        /** deepseek-v4-flash | deepseek-v4-pro */
        private String model = "deepseek-v4-flash";
        /** disabled | enabled */
        private String thinkingType = "disabled";
        private int timeoutSeconds = 60;
        private String systemPrompt =
                "You are a helpful assistant for a B2B workspace platform. Reply concisely in the user's language.";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getThinkingType() {
            return thinkingType;
        }

        public void setThinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }

    @ConfigurationProperties("policy-service")
    public static class PolicyService {

        private boolean enabled = true;
        private String baseUrl = "http://localhost:8085/v1/policy";
        private String internalApiKey = "dev-internal-key";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getInternalApiKey() {
            return internalApiKey;
        }

        public void setInternalApiKey(String internalApiKey) {
            this.internalApiKey = internalApiKey;
        }
    }

    @ConfigurationProperties("audit-service")
    public static class AuditService {

        private boolean enabled = true;
        private String baseUrl = "http://localhost:8086/v1/audit";
        private String internalApiKey = "dev-internal-key";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getInternalApiKey() {
            return internalApiKey;
        }

        public void setInternalApiKey(String internalApiKey) {
            this.internalApiKey = internalApiKey;
        }
    }
}
