package com.omarflex5.data.sniffer.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for SnifferActivity.
 * Use the Builder to create instances.
 */
public class SnifferConfig {

    public static final String DEFAULT_USER_AGENT = com.omarflex5.util.WebConfig.COMMON_USER_AGENT_LEGACY;

    private final String userAgent;
    private final long timeoutMs;
    private final Map<String, String> customHeaders;
    private final boolean allowRedirects;
    private final boolean enableContentTypeCheck;
    private final String customScript;

    private SnifferConfig(Builder builder) {
        this.userAgent = builder.userAgent;
        this.timeoutMs = builder.timeoutMs;
        this.customHeaders = builder.customHeaders;
        this.allowRedirects = builder.allowRedirects;
        this.enableContentTypeCheck = builder.enableContentTypeCheck;
        this.customScript = builder.customScript;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public boolean isAllowRedirects() {
        return allowRedirects;
    }

    public boolean isEnableContentTypeCheck() {
        return enableContentTypeCheck;
    }

    public String getCustomScript() {
        return customScript;
    }

    public static class Builder {
        private String userAgent = DEFAULT_USER_AGENT;
        private long timeoutMs = 60000; // 60 seconds
        private Map<String, String> customHeaders = new HashMap<>();
        private boolean allowRedirects = true;
        private boolean enableContentTypeCheck = false;
        private String customScript = null;

        public Builder setUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder setTimeout(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder addHeader(String key, String value) {
            this.customHeaders.put(key, value);
            return this;
        }

        public Builder setCustomHeaders(Map<String, String> headers) {
            this.customHeaders = headers;
            return this;
        }

        public Builder setAllowRedirects(boolean allow) {
            this.allowRedirects = allow;
            return this;
        }

        public Builder setEnableContentTypeCheck(boolean enable) {
            this.enableContentTypeCheck = enable;
            return this;
        }

        public Builder setCustomScript(String script) {
            this.customScript = script;
            return this;
        }

        public SnifferConfig build() {
            return new SnifferConfig(this);
        }
    }
}
