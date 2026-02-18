package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents a Kalshi Trade/Fill - when an order is matched and executed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trade {

    @JsonProperty("fill_id")
    private String fillId;

    @JsonProperty("trade_id")
    private String tradeId;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("market_ticker")
    private String marketTicker;

    @JsonProperty("side")
    private String side;

    @JsonProperty("action")
    private String action;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("price")
    private Integer price;

    @JsonProperty("yes_price")
    private Integer yesPrice;

    @JsonProperty("no_price")
    private Integer noPrice;

    @JsonProperty("yes_price_fixed")
    private String yesPriceFixed;

    @JsonProperty("no_price_fixed")
    private String noPriceFixed;

    @JsonProperty("is_taker")
    private Boolean isTaker;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonProperty("created_time")
    private Instant createdTime;

    @JsonProperty("ts")
    private Long timestamp;

    @JsonProperty("purchased_side")
    private String purchasedSide;

    public Trade() {
    }

    // Getters and Setters
    public String getFillId() {
        return fillId;
    }

    public void setFillId(String fillId) {
        this.fillId = fillId;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getMarketTicker() {
        return marketTicker;
    }

    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public Integer getYesPrice() {
        return yesPrice;
    }

    public void setYesPrice(Integer yesPrice) {
        this.yesPrice = yesPrice;
    }

    public Integer getNoPrice() {
        return noPrice;
    }

    public void setNoPrice(Integer noPrice) {
        this.noPrice = noPrice;
    }

    public String getYesPriceFixed() {
        return yesPriceFixed;
    }

    public void setYesPriceFixed(String yesPriceFixed) {
        this.yesPriceFixed = yesPriceFixed;
    }

    public String getNoPriceFixed() {
        return noPriceFixed;
    }

    public void setNoPriceFixed(String noPriceFixed) {
        this.noPriceFixed = noPriceFixed;
    }

    public Boolean getIsTaker() {
        return isTaker;
    }

    public void setIsTaker(Boolean isTaker) {
        this.isTaker = isTaker;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getPurchasedSide() {
        return purchasedSide;
    }

    public void setPurchasedSide(String purchasedSide) {
        this.purchasedSide = purchasedSide;
    }

    /**
     * Get the effective trade ID (fill_id or trade_id).
     */
    public String getEffectiveId() {
        return fillId != null ? fillId : tradeId;
    }

    /**
     * Get the effective ticker (ticker or market_ticker).
     */
    public String getEffectiveTicker() {
        return ticker != null ? ticker : marketTicker;
    }

    @Override
    public String toString() {
        return "Trade{" +
                "fillId='" + getEffectiveId() + '\'' +
                ", ticker='" + getEffectiveTicker() + '\'' +
                ", side='" + side + '\'' +
                ", action='" + action + '\'' +
                ", count=" + count +
                ", yesPrice=" + yesPrice +
                ", isTaker=" + isTaker +
                '}';
    }
}
