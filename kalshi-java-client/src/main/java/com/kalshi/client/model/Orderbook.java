package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a Kalshi Orderbook - shows all active bid orders for both yes and no sides of a binary market.
 * Note: Only bids are returned because a bid for yes at price X is equivalent to an ask for no at price (100-X).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Orderbook {

    @JsonProperty("yes")
    private List<List<Number>> yes;

    @JsonProperty("no")
    private List<List<Number>> no;

    @JsonProperty("yes_dollars")
    private List<List<Object>> yesDollars;

    @JsonProperty("no_dollars")
    private List<List<Object>> noDollars;

    public Orderbook() {
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
     * @return Best yes bid price or null if no bids
     */
    public Integer getBestYesBid() {
        if (yes == null || yes.isEmpty()) {
            return null;
        }
        return yes.get(0).get(0).intValue();
    }

    /**
     * Get the best no bid price in cents.
     * @return Best no bid price or null if no bids
     */
    public Integer getBestNoBid() {
        if (no == null || no.isEmpty()) {
            return null;
        }
        return no.get(0).get(0).intValue();
    }

    /**
     * Get the total depth of yes bids.
     * @return Total number of contracts on the yes side
     */
    public long getTotalYesDepth() {
        if (yes == null) {
            return 0;
        }
        return yes.stream()
                .mapToLong(level -> level.get(1).longValue())
                .sum();
    }

    /**
     * Get the total depth of no bids.
     * @return Total number of contracts on the no side
     */
    public long getTotalNoDepth() {
        if (no == null) {
            return 0;
        }
        return no.stream()
                .mapToLong(level -> level.get(1).longValue())
                .sum();
    }

    @Override
    public String toString() {
        return "Orderbook{" +
                "yesLevels=" + (yes != null ? yes.size() : 0) +
                ", noLevels=" + (no != null ? no.size() : 0) +
                ", bestYesBid=" + getBestYesBid() +
                ", bestNoBid=" + getBestNoBid() +
                '}';
    }
}
