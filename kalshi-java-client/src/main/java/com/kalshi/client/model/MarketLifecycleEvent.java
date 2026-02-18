package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents a market lifecycle event from the WebSocket API.
 * Received on the market_lifecycle_v2 channel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketLifecycleEvent {

    @JsonProperty("market_ticker")
    private String marketTicker;

    @JsonProperty("action")
    private String actionValue;

    @JsonProperty("market")
    private Market market;

    @JsonProperty("result")
    private String result;

    @JsonProperty("close_time")
    private Instant closeTime;

    private Instant timestamp;

    public MarketLifecycleEvent() {
        this.timestamp = Instant.now();
    }

    public String getMarketTicker() {
        return marketTicker;
    }

    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
    }

    public String getActionValue() {
        return actionValue;
    }

    public void setActionValue(String actionValue) {
        this.actionValue = actionValue;
    }

    /**
     * Get the action as an enum.
     */
    public MarketLifecycleAction getAction() {
        return MarketLifecycleAction.fromValue(actionValue);
    }

    public Market getMarket() {
        return market;
    }

    public void setMarket(Market market) {
        this.market = market;
    }

    /**
     * Get the result for DETERMINED events.
     */
    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    /**
     * Get the new close time for CLOSE_DATE_UPDATED events.
     */
    public Instant getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(Instant closeTime) {
        this.closeTime = closeTime;
    }

    /**
     * Get the timestamp when this event was received.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "MarketLifecycleEvent{" +
                "marketTicker='" + marketTicker + '\'' +
                ", action=" + getAction() +
                ", result='" + result + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
