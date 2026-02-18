package com.betting.etrade.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents comprehensive quote details from E*TRADE API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AllQuoteDetails {

    @JsonProperty("ask")
    private Double ask;

    @JsonProperty("askSize")
    private Long askSize;

    @JsonProperty("askTime")
    private String askTime;

    @JsonProperty("bid")
    private Double bid;

    @JsonProperty("bidSize")
    private Long bidSize;

    @JsonProperty("bidTime")
    private String bidTime;

    @JsonProperty("changeClose")
    private Double changeClose;

    @JsonProperty("changeClosePercentage")
    private Double changeClosePercentage;

    @JsonProperty("companyName")
    private String companyName;

    @JsonProperty("daysToExpiration")
    private Integer daysToExpiration;

    @JsonProperty("dirLast")
    private String dirLast;

    @JsonProperty("dividend")
    private Double dividend;

    @JsonProperty("eps")
    private Double eps;

    @JsonProperty("estEarnings")
    private Double estEarnings;

    @JsonProperty("exDividendDate")
    private Long exDividendDate;

    @JsonProperty("high")
    private Double high;

    @JsonProperty("high52")
    private Double high52;

    @JsonProperty("lastTrade")
    private Double lastTrade;

    @JsonProperty("low")
    private Double low;

    @JsonProperty("low52")
    private Double low52;

    @JsonProperty("marketCap")
    private Double marketCap;

    @JsonProperty("open")
    private Double open;

    @JsonProperty("openInterest")
    private Long openInterest;

    @JsonProperty("optionStyle")
    private String optionStyle;

    @JsonProperty("optionUnderlier")
    private String optionUnderlier;

    @JsonProperty("previousClose")
    private Double previousClose;

    @JsonProperty("previousDayVolume")
    private Long previousDayVolume;

    @JsonProperty("primaryExchange")
    private String primaryExchange;

    @JsonProperty("symbolDescription")
    private String symbolDescription;

    @JsonProperty("totalVolume")
    private Long totalVolume;

    @JsonProperty("upc")
    private Integer upc;

    @JsonProperty("pe")
    private Double pe;

    @JsonProperty("week52HiDate")
    private Long week52HiDate;

    @JsonProperty("week52LowDate")
    private Long week52LowDate;

    @JsonProperty("beta")
    private Double beta;

    @JsonProperty("yield")
    private Double yield;

    @JsonProperty("declaredDividend")
    private Double declaredDividend;

    @JsonProperty("dividendPayableDate")
    private Long dividendPayableDate;

    @JsonProperty("annualDividend")
    private Double annualDividend;

    // Getters and Setters

    public Double getAsk() {
        return ask;
    }

    public void setAsk(Double ask) {
        this.ask = ask;
    }

    public Long getAskSize() {
        return askSize;
    }

    public void setAskSize(Long askSize) {
        this.askSize = askSize;
    }

    public String getAskTime() {
        return askTime;
    }

    public void setAskTime(String askTime) {
        this.askTime = askTime;
    }

    public Double getBid() {
        return bid;
    }

    public void setBid(Double bid) {
        this.bid = bid;
    }

    public Long getBidSize() {
        return bidSize;
    }

    public void setBidSize(Long bidSize) {
        this.bidSize = bidSize;
    }

    public String getBidTime() {
        return bidTime;
    }

    public void setBidTime(String bidTime) {
        this.bidTime = bidTime;
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

    public Integer getDaysToExpiration() {
        return daysToExpiration;
    }

    public void setDaysToExpiration(Integer daysToExpiration) {
        this.daysToExpiration = daysToExpiration;
    }

    public String getDirLast() {
        return dirLast;
    }

    public void setDirLast(String dirLast) {
        this.dirLast = dirLast;
    }

    public Double getDividend() {
        return dividend;
    }

    public void setDividend(Double dividend) {
        this.dividend = dividend;
    }

    public Double getEps() {
        return eps;
    }

    public void setEps(Double eps) {
        this.eps = eps;
    }

    public Double getEstEarnings() {
        return estEarnings;
    }

    public void setEstEarnings(Double estEarnings) {
        this.estEarnings = estEarnings;
    }

    public Long getExDividendDate() {
        return exDividendDate;
    }

    public void setExDividendDate(Long exDividendDate) {
        this.exDividendDate = exDividendDate;
    }

    public Double getHigh() {
        return high;
    }

    public void setHigh(Double high) {
        this.high = high;
    }

    public Double getHigh52() {
        return high52;
    }

    public void setHigh52(Double high52) {
        this.high52 = high52;
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

    public Double getLow52() {
        return low52;
    }

    public void setLow52(Double low52) {
        this.low52 = low52;
    }

    public Double getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(Double marketCap) {
        this.marketCap = marketCap;
    }

    public Double getOpen() {
        return open;
    }

    public void setOpen(Double open) {
        this.open = open;
    }

    public Long getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(Long openInterest) {
        this.openInterest = openInterest;
    }

    public String getOptionStyle() {
        return optionStyle;
    }

    public void setOptionStyle(String optionStyle) {
        this.optionStyle = optionStyle;
    }

    public String getOptionUnderlier() {
        return optionUnderlier;
    }

    public void setOptionUnderlier(String optionUnderlier) {
        this.optionUnderlier = optionUnderlier;
    }

    public Double getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(Double previousClose) {
        this.previousClose = previousClose;
    }

    public Long getPreviousDayVolume() {
        return previousDayVolume;
    }

    public void setPreviousDayVolume(Long previousDayVolume) {
        this.previousDayVolume = previousDayVolume;
    }

    public String getPrimaryExchange() {
        return primaryExchange;
    }

    public void setPrimaryExchange(String primaryExchange) {
        this.primaryExchange = primaryExchange;
    }

    public String getSymbolDescription() {
        return symbolDescription;
    }

    public void setSymbolDescription(String symbolDescription) {
        this.symbolDescription = symbolDescription;
    }

    public Long getTotalVolume() {
        return totalVolume;
    }

    public void setTotalVolume(Long totalVolume) {
        this.totalVolume = totalVolume;
    }

    public Integer getUpc() {
        return upc;
    }

    public void setUpc(Integer upc) {
        this.upc = upc;
    }

    public Double getPe() {
        return pe;
    }

    public void setPe(Double pe) {
        this.pe = pe;
    }

    public Long getWeek52HiDate() {
        return week52HiDate;
    }

    public void setWeek52HiDate(Long week52HiDate) {
        this.week52HiDate = week52HiDate;
    }

    public Long getWeek52LowDate() {
        return week52LowDate;
    }

    public void setWeek52LowDate(Long week52LowDate) {
        this.week52LowDate = week52LowDate;
    }

    public Double getBeta() {
        return beta;
    }

    public void setBeta(Double beta) {
        this.beta = beta;
    }

    public Double getYield() {
        return yield;
    }

    public void setYield(Double yield) {
        this.yield = yield;
    }

    public Double getDeclaredDividend() {
        return declaredDividend;
    }

    public void setDeclaredDividend(Double declaredDividend) {
        this.declaredDividend = declaredDividend;
    }

    public Long getDividendPayableDate() {
        return dividendPayableDate;
    }

    public void setDividendPayableDate(Long dividendPayableDate) {
        this.dividendPayableDate = dividendPayableDate;
    }

    public Double getAnnualDividend() {
        return annualDividend;
    }

    public void setAnnualDividend(Double annualDividend) {
        this.annualDividend = annualDividend;
    }

    @Override
    public String toString() {
        return "AllQuoteDetails{" +
                "lastTrade=" + lastTrade +
                ", bid=" + bid +
                ", ask=" + ask +
                ", companyName='" + companyName + '\'' +
                '}';
    }
}
