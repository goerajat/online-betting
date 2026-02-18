package com.kalshi.client.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an orderbook delta (update) message received via WebSocket.
 * This contains an incremental change to the orderbook.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderbookDelta {

    @JsonProperty("market_ticker")
    private String marketTicker;

    @JsonProperty("price")
    private Integer price;

    @JsonProperty("price_dollars")
    private String priceDollars;

    @JsonProperty("delta")
    private Integer delta;

    @JsonProperty("side")
    private String side;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    public OrderbookDelta() {
    }

    public String getMarketTicker() {
        return marketTicker;
    }

    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getPriceDollars() {
        return priceDollars;
    }

    public void setPriceDollars(String priceDollars) {
        this.priceDollars = priceDollars;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    /**
     * Check if this is a yes side update.
     */
    public boolean isYesSide() {
        return "yes".equalsIgnoreCase(side);
    }

    /**
     * Check if this is a no side update.
     */
    public boolean isNoSide() {
        return "no".equalsIgnoreCase(side);
    }

    /**
     * Check if this delta was triggered by your own order.
     */
    public boolean isOwnOrder() {
        return clientOrderId != null && !clientOrderId.isEmpty();
    }

    /**
     * Check if this is an increase in quantity.
     */
    public boolean isIncrease() {
        return delta != null && delta > 0;
    }

    /**
     * Check if this is a decrease in quantity.
     */
    public boolean isDecrease() {
        return delta != null && delta < 0;
    }

    @Override
    public String toString() {
        return "OrderbookDelta{" +
                "marketTicker='" + marketTicker + '\'' +
                ", side='" + side + '\'' +
                ", price=" + price +
                ", delta=" + delta +
                (clientOrderId != null ? ", clientOrderId='" + clientOrderId + '\'' : "") +
                '}';
    }
}
