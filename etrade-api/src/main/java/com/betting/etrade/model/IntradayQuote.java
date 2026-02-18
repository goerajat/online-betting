package com.betting.etrade.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents intraday quote details from E*TRADE API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntradayQuote {

    @JsonProperty("ask")
    private Double ask;

    @JsonProperty("bid")
    private Double bid;

    @JsonProperty("changeClose")
    private Double changeClose;

    @JsonProperty("changeClosePercentage")
    private Double changeClosePercentage;

    @JsonProperty("companyName")
    private String companyName;

    @JsonProperty("high")
    private Double high;

    @JsonProperty("lastTrade")
    private Double lastTrade;

    @JsonProperty("low")
    private Double low;

    @JsonProperty("totalVolume")
    private Long totalVolume;

    public Double getAsk() {
        return ask;
    }

    public void setAsk(Double ask) {
        this.ask = ask;
    }

    public Double getBid() {
        return bid;
    }

    public void setBid(Double bid) {
        this.bid = bid;
    }

    public Double getChangeClose() {
        return changeClose;
    }

    public void setChangeClose(Double changeClose) {
        this.changeClose = changeClose;
    }

    public Double getChangeClosePercentage() {
        return changeClosePercentage;
    }

    public void setChangeClosePercentage(Double changeClosePercentage) {
        this.changeClosePercentage = changeClosePercentage;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Double getHigh() {
        return high;
    }

    public void setHigh(Double high) {
        this.high = high;
    }

    public Double getLastTrade() {
        return lastTrade;
    }

    public void setLastTrade(Double lastTrade) {
        this.lastTrade = lastTrade;
    }

    public Double getLow() {
        return low;
    }

    public void setLow(Double low) {
        this.low = low;
    }

    public Long getTotalVolume() {
        return totalVolume;
    }

    public void setTotalVolume(Long totalVolume) {
        this.totalVolume = totalVolume;
    }

    @Override
    public String toString() {
        return "IntradayQuote{" +
                "lastTrade=" + lastTrade +
                ", bid=" + bid +
                ", ask=" + ask +
                ", changeClose=" + changeClose +
                ", volume=" + totalVolume +
                '}';
    }
}
