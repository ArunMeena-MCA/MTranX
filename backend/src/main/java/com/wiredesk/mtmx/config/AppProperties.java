package com.wiredesk.mtmx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Central knobs for the engine, bound from application.yml's "mtmx.*"
 * keys (or the matching MTMX_* / GEMINI_API_KEY / GROQ_API_KEY environment
 * variables). Mirrors the Python engine's config.py 1:1 in spirit.
 */
@Component
@ConfigurationProperties(prefix = "mtmx")
public class AppProperties {

    private String mappingsDir = "./mappings";
    private String xsdDir;
    private String provider = "gemini";
    private String modelName = "gemini-3.6-flash";
    private int maxTokens = 4096;
    private int maxConverterRetries = 2;
    private String geminiApiKey;
    private String groqApiKey;

    /**
     * Opt-in: structured address enrichment (StrtNm/TwnNm/Ctry from free-text
     * AdrLine content, via the libpostal sidecar service) is OFF by default -
     * existing deployments that haven't stood up the sidecar keep today's
     * AdrLine-only behavior unchanged. Enable ahead of the 14 November 2026
     * Swift structured/hybrid address requirement (see
     * MT103_TO_PACS00800108.yaml's ADDRESS POLICY note).
     */
    private boolean addressParserEnabled = false;
    private String addressParserUrl = "http://localhost:8090/parse-address";

    public String getMappingsDir() {
        return mappingsDir;
    }

    public void setMappingsDir(String mappingsDir) {
        this.mappingsDir = mappingsDir;
    }

    public String getXsdDir() {
        return xsdDir;
    }

    public void setXsdDir(String xsdDir) {
        this.xsdDir = xsdDir;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxConverterRetries() {
        return maxConverterRetries;
    }

    public void setMaxConverterRetries(int maxConverterRetries) {
        this.maxConverterRetries = maxConverterRetries;
    }

    public String getGroqApiKey() {
        return groqApiKey;
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public boolean isAddressParserEnabled() {
        return addressParserEnabled;
    }

    public void setAddressParserEnabled(boolean addressParserEnabled) {
        this.addressParserEnabled = addressParserEnabled;
    }

    public String getAddressParserUrl() {
        return addressParserUrl;
    }

    public void setAddressParserUrl(String addressParserUrl) {
        this.addressParserUrl = addressParserUrl;
    }
}
