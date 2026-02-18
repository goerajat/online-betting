package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request object for amending an existing order.
 * Used to modify price and/or quantity of an existing order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AmendOrderRequest {

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("side")
    private String side;

    @JsonProperty("action")
    private String action;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonProperty("updated_client_order_id")
    private String updatedClientOrderId;

    @JsonProperty("yes_price")
    private Integer yesPrice;

    @JsonProperty("no_price")
    private Integer noPrice;

    @JsonProperty("count")
    private Integer count;

    private AmendOrderRequest(Builder builder) {
        this.ticker = builder.ticker;
        this.side = builder.side;
        this.action = builder.action;
        this.clientOrderId = builder.clientOrderId;
        this.updatedClientOrderId = builder.updatedClientOrderId;
        this.yesPrice = builder.yesPrice;
        this.noPrice = builder.noPrice;
        this.count = builder.count;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getTicker() { return ticker; }
    public String getSide() { return side; }
    public String getAction() { return action; }
    public String getClientOrderId() { return clientOrderId; }
    public String getUpdatedClientOrderId() { return updatedClientOrderId; }
    public Integer getYesPrice() { return yesPrice; }
    public Integer getNoPrice() { return noPrice; }
    public Integer getCount() { return count; }

    public static class Builder {
        private String ticker;
        private String side;
        private String action;
        private String clientOrderId;
        private String updatedClientOrderId;
        private Integer yesPrice;
        private Integer noPrice;
        private Integer count;

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

        public Builder clientOrderId(String clientOrderId) {
            this.clientOrderId = clientOrderId;
            return this;
        }

        public Builder updatedClientOrderId(String updatedClientOrderId) {
            this.updatedClientOrderId = updatedClientOrderId;
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

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public AmendOrderRequest build() {
            return new AmendOrderRequest(this);
        }
    }
}
