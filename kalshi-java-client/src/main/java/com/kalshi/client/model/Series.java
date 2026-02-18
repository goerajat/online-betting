package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Represents a Kalshi Series - a template for recurring events that follow the same format and rules.
 * Examples: "Monthly Jobs Report", "Weekly Initial Jobless Claims", "Daily Weather in NYC"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Series {

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("frequency")
    private String frequency;

    @JsonProperty("title")
    private String title;

    @JsonProperty("category")
    private String category;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("settlement_sources")
    private List<SettlementSource> settlementSources;

    @JsonProperty("contract_url")
    private String contractUrl;

    @JsonProperty("contract_terms_url")
    private String contractTermsUrl;

    @JsonProperty("fee_type")
    private String feeType;

    @JsonProperty("fee_multiplier")
    private Double feeMultiplier;

    @JsonProperty("additional_prohibitions")
    private List<String> additionalProhibitions;

    @JsonProperty("product_metadata")
    private Map<String, Object> productMetadata;

    @JsonProperty("volume")
    private Long volume;

    public Series() {
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<SettlementSource> getSettlementSources() {
        return settlementSources;
    }

    public void setSettlementSources(List<SettlementSource> settlementSources) {
        this.settlementSources = settlementSources;
    }

    public String getContractUrl() {
        return contractUrl;
    }

    public void setContractUrl(String contractUrl) {
        this.contractUrl = contractUrl;
    }

    public String getContractTermsUrl() {
        return contractTermsUrl;
    }

    public void setContractTermsUrl(String contractTermsUrl) {
        this.contractTermsUrl = contractTermsUrl;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public Double getFeeMultiplier() {
        return feeMultiplier;
    }

    public void setFeeMultiplier(Double feeMultiplier) {
        this.feeMultiplier = feeMultiplier;
    }

    public List<String> getAdditionalProhibitions() {
        return additionalProhibitions;
    }

    public void setAdditionalProhibitions(List<String> additionalProhibitions) {
        this.additionalProhibitions = additionalProhibitions;
    }

    public Map<String, Object> getProductMetadata() {
        return productMetadata;
    }

    public void setProductMetadata(Map<String, Object> productMetadata) {
        this.productMetadata = productMetadata;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "Series{" +
                "ticker='" + ticker + '\'' +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", frequency='" + frequency + '\'' +
                ", volume=" + volume +
                '}';
    }
}
