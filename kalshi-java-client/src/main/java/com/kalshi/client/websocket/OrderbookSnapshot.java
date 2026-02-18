package com.kalshi.client.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents an orderbook snapshot message received via WebSocket.
 * This contains the complete state of the orderbook at a point in time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderbookSnapshot {

    @JsonProperty("market_ticker")
    private String marketTicker;

    @JsonProperty("yes")
    private List<List<Number>> yes;

    @JsonProperty("no")
    private List<List<Number>> no;

    @JsonProperty("yes_dollars")
    private List<List<Object>> yesDollars;

    @JsonProperty("no_dollars")
    private List<List<Object>> noDollars;

    public OrderbookSnapshot() {
    }

    public String getMarketTicker() {
        return marketTicker;
    }

    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
    }

    public List<List<Number>> getYes() {
        return yes;
    }

    public void setYes(List<List<Number>> yes) {
        this.yes = yes;
    }

    public List<List<Number>> getNo() {
        return no;
    }

    public void setNo(List<List<Number>> no) {
        this.no = no;
    }

    public List<List<Object>> getYesDollars() {
        return yesDollars;
    }

    public void setYesDollars(List<List<Object>> yesDollars) {
        this.yesDollars = yesDollars;
    }

    public List<List<Object>> getNoDollars() {
        return noDollars;
    }

    public void setNoDollars(List<List<Object>> noDollars) {
        this.noDollars = noDollars;
    }

    /**
     * Get the best yes bid price in cents.
     */
    public Integer getBestYesBid() {
        if (yes == null || yes.isEmpty()) {
            return null;
        }
        return yes.get(0).get(0).intValue();
    }

    /**
     * Get the best no bid price in cents.
     */
    public Integer getBestNoBid() {
        if (no == null || no.isEmpty()) {
            return null;
        }
        return no.get(0).get(0).intValue();
    }

    @Override
    public String toString() {
        return "OrderbookSnapshot{" +
                "marketTicker='" + marketTicker + '\'' +
                ", yesLevels=" + (yes != null ? yes.size() : 0) +
                ", noLevels=" + (no != null ? no.size() : 0) +
                '}';
    }
}
