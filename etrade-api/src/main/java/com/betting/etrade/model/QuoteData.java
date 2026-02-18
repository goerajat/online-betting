package com.betting.etrade.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents quote data for a single symbol from E*TRADE API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteData {

    @JsonProperty("dateTime")
    private String dateTime;

    @JsonProperty("dateTimeUTC")
    private Long dateTimeUTC;

    @JsonProperty("quoteStatus")
    private String quoteStatus;

    @JsonProperty("ahFlag")
    private String ahFlag;

    @JsonProperty("hasMiniOptions")
    private Boolean hasMiniOptions;

    @JsonProperty("Product")
    private Product product;

    @JsonProperty("Intraday")
    private IntradayQuote intraday;

    @JsonProperty("All")
    private AllQuoteDetails all;

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public Long getDateTimeUTC() {
        return dateTimeUTC;
    }

    public void setDateTimeUTC(Long dateTimeUTC) {
        this.dateTimeUTC = dateTimeUTC;
    }

    public String getQuoteStatus() {
        return quoteStatus;
    }

    public void setQuoteStatus(String quoteStatus) {
        this.quoteStatus = quoteStatus;
    }

    public String getAhFlag() {
        return ahFlag;
    }

    public void setAhFlag(String ahFlag) {
        this.ahFlag = ahFlag;
    }

    public Boolean getHasMiniOptions() {
        return hasMiniOptions;
    }

    public void setHasMiniOptions(Boolean hasMiniOptions) {
        this.hasMiniOptions = hasMiniOptions;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public IntradayQuote getIntraday() {
        return intraday;
    }

    public void setIntraday(IntradayQuote intraday) {
        this.intraday = intraday;
    }

    public AllQuoteDetails getAll() {
        return all;
    }

    public void setAll(AllQuoteDetails all) {
        this.all = all;
    }

    /**
     * Get the symbol for this quote.
     */
    public String getSymbol() {
        return product != null ? product.getSymbol() : null;
    }

    /**
     * Get the last trade price from either Intraday or All details.
     */
    public Double getLastPrice() {
        if (intraday != null && intraday.getLastTrade() != null) {
            return intraday.getLastTrade();
        }
        if (all != null && all.getLastTrade() != null) {
            return all.getLastTrade();
        }
        return null;
    }

    /**
     * Get the timestamp as an Instant.
     */
    public Instant getTimestamp() {
        if (dateTimeUTC != null) {
            return Instant.ofEpochSecond(dateTimeUTC);
        }
        return null;
    }

    /**
     * Check if this is a real-time quote.
     */
    public boolean isRealTime() {
        return "REALTIME".equals(quoteStatus) ||
               "EH_REALTIME".equals(quoteStatus) ||
               "INDICATIVE_REALTIME".equals(quoteStatus);
    }

    @Override
    public String toString() {
        return "QuoteData{" +
                "symbol='" + getSymbol() + '\'' +
                ", lastPrice=" + getLastPrice() +
                ", quoteStatus='" + quoteStatus + '\'' +
                ", dateTime='" + dateTime + '\'' +
                '}';
    }
}
