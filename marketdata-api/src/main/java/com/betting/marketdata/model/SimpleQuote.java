package com.betting.marketdata.model;

import java.time.Instant;

/**
 * Simple immutable implementation of the Quote interface.
 */
public class SimpleQuote implements Quote {

    private final String symbol;
    private final Double lastPrice;
    private final Double bid;
    private final Double ask;
    private final Long bidSize;
    private final Long askSize;
    private final Double change;
    private final Double changePercent;
    private final Double high;
    private final Double low;
    private final Double open;
    private final Double previousClose;
    private final Long volume;
    private final Instant timestamp;
    private final String status;

    private SimpleQuote(Builder builder) {
        this.symbol = builder.symbol;
        this.lastPrice = builder.lastPrice;
        this.bid = builder.bid;
        this.ask = builder.ask;
        this.bidSize = builder.bidSize;
        this.askSize = builder.askSize;
        this.change = builder.change;
        this.changePercent = builder.changePercent;
        this.high = builder.high;
        this.low = builder.low;
        this.open = builder.open;
        this.previousClose = builder.previousClose;
        this.volume = builder.volume;
        this.timestamp = builder.timestamp;
        this.status = builder.status;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public Double getLastPrice() {
        return lastPrice;
    }

    @Override
    public Double getBid() {
        return bid;
    }

    @Override
    public Double getAsk() {
        return ask;
    }

    @Override
    public Long getBidSize() {
        return bidSize;
    }

    @Override
    public Long getAskSize() {
        return askSize;
    }

    @Override
    public Double getChange() {
        return change;
    }

    @Override
    public Double getChangePercent() {
        return changePercent;
    }

    @Override
    public Double getHigh() {
        return high;
    }

    @Override
    public Double getLow() {
        return low;
    }

    @Override
    public Double getOpen() {
        return open;
    }

    @Override
    public Double getPreviousClose() {
        return previousClose;
    }

    @Override
    public Long getVolume() {
        return volume;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public boolean isRealTime() {
        return "REALTIME".equalsIgnoreCase(status) ||
               "EH_REALTIME".equalsIgnoreCase(status);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "SimpleQuote{" +
                "symbol='" + symbol + '\'' +
                ", lastPrice=" + lastPrice +
                ", bid=" + bid +
                ", ask=" + ask +
                ", change=" + change +
                ", volume=" + volume +
                '}';
    }

    public static class Builder {
        private String symbol;
        private Double lastPrice;
        private Double bid;
        private Double ask;
        private Long bidSize;
        private Long askSize;
        private Double change;
        private Double changePercent;
        private Double high;
        private Double low;
        private Double open;
        private Double previousClose;
        private Long volume;
        private Instant timestamp;
        private String status;

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder lastPrice(Double lastPrice) {
            this.lastPrice = lastPrice;
            return this;
        }

        public Builder bid(Double bid) {
            this.bid = bid;
            return this;
        }

        public Builder ask(Double ask) {
            this.ask = ask;
            return this;
        }

        public Builder bidSize(Long bidSize) {
            this.bidSize = bidSize;
            return this;
        }

        public Builder askSize(Long askSize) {
            this.askSize = askSize;
            return this;
        }

        public Builder change(Double change) {
            this.change = change;
            return this;
        }

        public Builder changePercent(Double changePercent) {
            this.changePercent = changePercent;
            return this;
        }

        public Builder high(Double high) {
            this.high = high;
            return this;
        }

        public Builder low(Double low) {
            this.low = low;
            return this;
        }

        public Builder open(Double open) {
            this.open = open;
            return this;
        }

        public Builder previousClose(Double previousClose) {
            this.previousClose = previousClose;
            return this;
        }

        public Builder volume(Long volume) {
            this.volume = volume;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public SimpleQuote build() {
            return new SimpleQuote(this);
        }
    }
}
