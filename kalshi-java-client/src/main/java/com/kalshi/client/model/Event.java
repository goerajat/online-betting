package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a Kalshi Event - a real-world occurrence that can be traded on.
 * Examples: an election, sports game, or economic indicator release.
 * Events contain one or more markets where users can place trades on different outcomes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {

    @JsonProperty("event_ticker")
    private String eventTicker;

    @JsonProperty("series_ticker")
    private String seriesTicker;

    @JsonProperty("title")
    private String title;

    @JsonProperty("sub_title")
    private String subTitle;

    @JsonProperty("category")
    private String category;

    @JsonProperty("collateral_return_type")
    private String collateralReturnType;

    @JsonProperty("mutually_exclusive")
    private Boolean mutuallyExclusive;

    @JsonProperty("available_on_brokers")
    private Boolean availableOnBrokers;

    @JsonProperty("product_metadata")
    private Map<String, Object> productMetadata;

    @JsonProperty("strike_date")
    private Instant strikeDate;

    @JsonProperty("strike_period")
    private String strikePeriod;

    @JsonProperty("markets")
    private List<Market> markets;

    public Event() {
    }

    public String getEventTicker() {
        return eventTicker;
    }

    public void setEventTicker(String eventTicker) {
        this.eventTicker = eventTicker;
    }

    public String getSeriesTicker() {
        return seriesTicker;
    }

    public void setSeriesTicker(String seriesTicker) {
        this.seriesTicker = seriesTicker;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCollateralReturnType() {
        return collateralReturnType;
    }

    public void setCollateralReturnType(String collateralReturnType) {
        this.collateralReturnType = collateralReturnType;
    }

    public Boolean getMutuallyExclusive() {
        return mutuallyExclusive;
    }

    public void setMutuallyExclusive(Boolean mutuallyExclusive) {
        this.mutuallyExclusive = mutuallyExclusive;
    }

    public Boolean getAvailableOnBrokers() {
        return availableOnBrokers;
    }

    public void setAvailableOnBrokers(Boolean availableOnBrokers) {
        this.availableOnBrokers = availableOnBrokers;
    }

    public Map<String, Object> getProductMetadata() {
        return productMetadata;
    }

    public void setProductMetadata(Map<String, Object> productMetadata) {
        this.productMetadata = productMetadata;
    }

    public Instant getStrikeDate() {
        return strikeDate;
    }

    public void setStrikeDate(Instant strikeDate) {
        this.strikeDate = strikeDate;
    }

    public String getStrikePeriod() {
        return strikePeriod;
    }

    public void setStrikePeriod(String strikePeriod) {
        this.strikePeriod = strikePeriod;
    }

    public List<Market> getMarkets() {
        return markets;
    }

    public void setMarkets(List<Market> markets) {
        this.markets = markets;
    }

    @Override
    public String toString() {
        return "Event{" +
                "eventTicker='" + eventTicker + '\'' +
                ", seriesTicker='" + seriesTicker + '\'' +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", mutuallyExclusive=" + mutuallyExclusive +
                ", strikeDate=" + strikeDate +
                '}';
    }
}
