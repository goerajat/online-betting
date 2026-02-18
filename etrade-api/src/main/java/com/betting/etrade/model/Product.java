package com.betting.etrade.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a product (security) in E*TRADE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("securityType")
    private String securityType;

    @JsonProperty("securitySubType")
    private String securitySubType;

    @JsonProperty("callPut")
    private String callPut;

    @JsonProperty("expiryYear")
    private Integer expiryYear;

    @JsonProperty("expiryMonth")
    private Integer expiryMonth;

    @JsonProperty("expiryDay")
    private Integer expiryDay;

    @JsonProperty("strikePrice")
    private Double strikePrice;

    @JsonProperty("exchangeCode")
    private String exchangeCode;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    public String getSecuritySubType() {
        return securitySubType;
    }

    public void setSecuritySubType(String securitySubType) {
        this.securitySubType = securitySubType;
    }

    public String getCallPut() {
        return callPut;
    }

    public void setCallPut(String callPut) {
        this.callPut = callPut;
    }

    public Integer getExpiryYear() {
        return expiryYear;
    }

    public void setExpiryYear(Integer expiryYear) {
        this.expiryYear = expiryYear;
    }

    public Integer getExpiryMonth() {
        return expiryMonth;
    }

    public void setExpiryMonth(Integer expiryMonth) {
        this.expiryMonth = expiryMonth;
    }

    public Integer getExpiryDay() {
        return expiryDay;
    }

    public void setExpiryDay(Integer expiryDay) {
        this.expiryDay = expiryDay;
    }

    public Double getStrikePrice() {
        return strikePrice;
    }

    public void setStrikePrice(Double strikePrice) {
        this.strikePrice = strikePrice;
    }

    public String getExchangeCode() {
        return exchangeCode;
    }

    public void setExchangeCode(String exchangeCode) {
        this.exchangeCode = exchangeCode;
    }

    @Override
    public String toString() {
        return "Product{symbol='" + symbol + "', securityType='" + securityType + "'}";
    }
}
