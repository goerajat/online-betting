package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request object for creating a new order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateOrderRequest {

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("side")
    private String side;

    @JsonProperty("action")
    private String action;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("type")
    private String type;

    @JsonProperty("yes_price")
    private Integer yesPrice;

    @JsonProperty("no_price")
    private Integer noPrice;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonProperty("expiration_ts")
    private Long expirationTs;

    @JsonProperty("time_in_force")
    private String timeInForce;

    @JsonProperty("buy_max_cost")
    private Integer buyMaxCost;

    @JsonProperty("post_only")
    private Boolean postOnly;

    @JsonProperty("reduce_only")
    private Boolean reduceOnly;

    @JsonProperty("self_trade_prevention_type")
    private String selfTradePreventionType;

    @JsonProperty("order_group_id")
    private String orderGroupId;

    @JsonProperty("cancel_order_on_pause")
    private Boolean cancelOrderOnPause;

    private CreateOrderRequest(Builder builder) {
        this.ticker = builder.ticker;
        this.side = builder.side;
        this.action = builder.action;
        this.count = builder.count;
        this.type = builder.type;
        this.yesPrice = builder.yesPrice;
        this.noPrice = builder.noPrice;
        this.clientOrderId = builder.clientOrderId;
        this.expirationTs = builder.expirationTs;
        this.timeInForce = builder.timeInForce;
        this.buyMaxCost = builder.buyMaxCost;
        this.postOnly = builder.postOnly;
        this.reduceOnly = builder.reduceOnly;
        this.selfTradePreventionType = builder.selfTradePreventionType;
        this.orderGroupId = builder.orderGroupId;
        this.cancelOrderOnPause = builder.cancelOrderOnPause;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getTicker() { return ticker; }
    public String getSide() { return side; }
    public String getAction() { return action; }
    public Integer getCount() { return count; }
    public String getType() { return type; }
    public Integer getYesPrice() { return yesPrice; }
    public Integer getNoPrice() { return noPrice; }
    public String getClientOrderId() { return clientOrderId; }
    public Long getExpirationTs() { return expirationTs; }
    public String getTimeInForce() { return timeInForce; }
    public Integer getBuyMaxCost() { return buyMaxCost; }
    public Boolean getPostOnly() { return postOnly; }
    public Boolean getReduceOnly() { return reduceOnly; }
    public String getSelfTradePreventionType() { return selfTradePreventionType; }
    public String getOrderGroupId() { return orderGroupId; }
    public Boolean getCancelOrderOnPause() { return cancelOrderOnPause; }

    public static class Builder {
        private String ticker;
        private String side;
        private String action;
        private Integer count;
        private String type = "limit";
        private Integer yesPrice;
        private Integer noPrice;
        private String clientOrderId;
        private Long expirationTs;
        private String timeInForce;
        private Integer buyMaxCost;
        private Boolean postOnly;
        private Boolean reduceOnly;
        private String selfTradePreventionType;
        private String orderGroupId;
        private Boolean cancelOrderOnPause;

        public Builder ticker(String ticker) {
            this.ticker = ticker;
            return this;
        }

        public Builder side(OrderSide side) {
            this.side = side.getValue();
            return this;
        }

        public Builder side(String side) {
            this.side = side;
            return this;
        }

        public Builder action(OrderAction action) {
            this.action = action.getValue();
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder type(OrderType type) {
            this.type = type.getValue();
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder yesPrice(int priceInCents) {
            this.yesPrice = priceInCents;
            return this;
        }

        public Builder noPrice(int priceInCents) {
            this.noPrice = priceInCents;
            return this;
        }

        public Builder clientOrderId(String clientOrderId) {
            this.clientOrderId = clientOrderId;
            return this;
        }

        public Builder expirationTs(long expirationTs) {
            this.expirationTs = expirationTs;
            return this;
        }

        public Builder timeInForce(TimeInForce timeInForce) {
            this.timeInForce = timeInForce.getValue();
            return this;
        }

        public Builder timeInForce(String timeInForce) {
            this.timeInForce = timeInForce;
            return this;
        }

        public Builder buyMaxCost(int buyMaxCost) {
            this.buyMaxCost = buyMaxCost;
            return this;
        }

        public Builder postOnly(boolean postOnly) {
            this.postOnly = postOnly;
            return this;
        }

        public Builder reduceOnly(boolean reduceOnly) {
            this.reduceOnly = reduceOnly;
            return this;
        }

        public Builder selfTradePreventionType(String selfTradePreventionType) {
            this.selfTradePreventionType = selfTradePreventionType;
            return this;
        }

        public Builder orderGroupId(String orderGroupId) {
            this.orderGroupId = orderGroupId;
            return this;
        }

        public Builder cancelOrderOnPause(boolean cancelOrderOnPause) {
            this.cancelOrderOnPause = cancelOrderOnPause;
            return this;
        }

        public CreateOrderRequest build() {
            if (ticker == null || ticker.isEmpty()) {
                throw new IllegalStateException("ticker is required");
            }
            if (side == null || side.isEmpty()) {
                throw new IllegalStateException("side is required");
            }
            if (action == null || action.isEmpty()) {
                throw new IllegalStateException("action is required");
            }
            if (count == null || count < 1) {
                throw new IllegalStateException("count must be at least 1");
            }
            return new CreateOrderRequest(this);
        }
    }
}
