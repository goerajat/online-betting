package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents a Kalshi Order - a request to buy or sell contracts in a market.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Order {

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("side")
    private String side;

    @JsonProperty("action")
    private String action;

    @JsonProperty("type")
    private String type;

    @JsonProperty("status")
    private String status;

    @JsonProperty("yes_price")
    private Integer yesPrice;

    @JsonProperty("no_price")
    private Integer noPrice;

    @JsonProperty("yes_price_dollars")
    private String yesPriceDollars;

    @JsonProperty("no_price_dollars")
    private String noPriceDollars;

    @JsonProperty("fill_count")
    private Integer fillCount;

    @JsonProperty("remaining_count")
    private Integer remainingCount;

    @JsonProperty("initial_count")
    private Integer initialCount;

    @JsonProperty("taker_fees")
    private Integer takerFees;

    @JsonProperty("maker_fees")
    private Integer makerFees;

    @JsonProperty("taker_fees_dollars")
    private String takerFeesDollars;

    @JsonProperty("maker_fees_dollars")
    private String makerFeesDollars;

    @JsonProperty("taker_fill_cost")
    private Integer takerFillCost;

    @JsonProperty("maker_fill_cost")
    private Integer makerFillCost;

    @JsonProperty("taker_fill_cost_dollars")
    private String takerFillCostDollars;

    @JsonProperty("maker_fill_cost_dollars")
    private String makerFillCostDollars;

    @JsonProperty("queue_position")
    private Integer queuePosition;

    @JsonProperty("expiration_time")
    private Instant expirationTime;

    @JsonProperty("created_time")
    private Instant createdTime;

    @JsonProperty("last_update_time")
    private Instant lastUpdateTime;

    @JsonProperty("self_trade_prevention_type")
    private String selfTradePreventionType;

    @JsonProperty("order_group_id")
    private String orderGroupId;

    @JsonProperty("cancel_order_on_pause")
    private Boolean cancelOrderOnPause;

    public Order() {
    }

    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getYesPriceDollars() {
        return yesPriceDollars;
    }

    public void setYesPriceDollars(String yesPriceDollars) {
        this.yesPriceDollars = yesPriceDollars;
    }

    public String getNoPriceDollars() {
        return noPriceDollars;
    }

    public void setNoPriceDollars(String noPriceDollars) {
        this.noPriceDollars = noPriceDollars;
    }

    public Integer getFillCount() {
        return fillCount;
    }

    public void setFillCount(Integer fillCount) {
        this.fillCount = fillCount;
    }

    public Integer getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(Integer remainingCount) {
        this.remainingCount = remainingCount;
    }

    public Integer getInitialCount() {
        return initialCount;
    }

    public void setInitialCount(Integer initialCount) {
        this.initialCount = initialCount;
    }

    public Integer getTakerFees() {
        return takerFees;
    }

    public void setTakerFees(Integer takerFees) {
        this.takerFees = takerFees;
    }

    public Integer getMakerFees() {
        return makerFees;
    }

    public void setMakerFees(Integer makerFees) {
        this.makerFees = makerFees;
    }

    public String getTakerFeesDollars() {
        return takerFeesDollars;
    }

    public void setTakerFeesDollars(String takerFeesDollars) {
        this.takerFeesDollars = takerFeesDollars;
    }

    public String getMakerFeesDollars() {
        return makerFeesDollars;
    }

    public void setMakerFeesDollars(String makerFeesDollars) {
        this.makerFeesDollars = makerFeesDollars;
    }

    public Integer getTakerFillCost() {
        return takerFillCost;
    }

    public void setTakerFillCost(Integer takerFillCost) {
        this.takerFillCost = takerFillCost;
    }

    public Integer getMakerFillCost() {
        return makerFillCost;
    }

    public void setMakerFillCost(Integer makerFillCost) {
        this.makerFillCost = makerFillCost;
    }

    public String getTakerFillCostDollars() {
        return takerFillCostDollars;
    }

    public void setTakerFillCostDollars(String takerFillCostDollars) {
        this.takerFillCostDollars = takerFillCostDollars;
    }

    public String getMakerFillCostDollars() {
        return makerFillCostDollars;
    }

    public void setMakerFillCostDollars(String makerFillCostDollars) {
        this.makerFillCostDollars = makerFillCostDollars;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Integer queuePosition) {
        this.queuePosition = queuePosition;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Instant expirationTime) {
        this.expirationTime = expirationTime;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
    }

    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Instant lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getSelfTradePreventionType() {
        return selfTradePreventionType;
    }

    public void setSelfTradePreventionType(String selfTradePreventionType) {
        this.selfTradePreventionType = selfTradePreventionType;
    }

    public String getOrderGroupId() {
        return orderGroupId;
    }

    public void setOrderGroupId(String orderGroupId) {
        this.orderGroupId = orderGroupId;
    }

    public Boolean getCancelOrderOnPause() {
        return cancelOrderOnPause;
    }

    public void setCancelOrderOnPause(Boolean cancelOrderOnPause) {
        this.cancelOrderOnPause = cancelOrderOnPause;
    }

    /**
     * Check if the order is fully filled.
     */
    public boolean isFullyFilled() {
        return "executed".equals(status) || (remainingCount != null && remainingCount == 0);
    }

    /**
     * Check if the order is still active (resting).
     */
    public boolean isActive() {
        return "resting".equals(status);
    }

    /**
     * Check if the order has been canceled.
     */
    public boolean isCanceled() {
        return "canceled".equals(status);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", ticker='" + ticker + '\'' +
                ", side='" + side + '\'' +
                ", action='" + action + '\'' +
                ", status='" + status + '\'' +
                ", initialCount=" + initialCount +
                ", fillCount=" + fillCount +
                ", remainingCount=" + remainingCount +
                '}';
    }
}
