package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents a market position from the Kalshi WebSocket API.
 * Contains position size, cost basis, realized P&L, fees, and volume.
 *
 * <p>All monetary values are stored in centi-cents (1/10,000th of a dollar).
 * Use the helper methods (e.g., {@link #getPositionCostDollars()}) to get dollar values.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Position {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("market_ticker")
    private String marketTicker;

    /**
     * Current net position (positive for long, negative for short).
     */
    @JsonProperty("position")
    private Integer position;

    /**
     * Cost basis in centi-cents (1/10,000th dollar).
     */
    @JsonProperty("position_cost")
    private Long positionCost;

    /**
     * Realized profit/loss in centi-cents.
     */
    @JsonProperty("realized_pnl")
    private Long realizedPnl;

    /**
     * Total fees paid in centi-cents.
     */
    @JsonProperty("fees_paid")
    private Long feesPaid;

    /**
     * Total volume traded.
     */
    @JsonProperty("volume")
    private Integer volume;

    public Position() {
    }

    public Position(String marketTicker, int position) {
        this.marketTicker = marketTicker;
        this.position = position;
    }

    // Getters and Setters

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMarketTicker() {
        return marketTicker;
    }

    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public Long getPositionCost() {
        return positionCost;
    }

    public void setPositionCost(Long positionCost) {
        this.positionCost = positionCost;
    }

    public Long getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(Long realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public Long getFeesPaid() {
        return feesPaid;
    }

    public void setFeesPaid(Long feesPaid) {
        this.feesPaid = feesPaid;
    }

    public Integer getVolume() {
        return volume;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }

    // Helper methods for dollar conversions

    /**
     * Convert centi-cents to dollars.
     */
    private BigDecimal centiCentsToDollars(Long centiCents) {
        if (centiCents == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(centiCents).divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
    }

    /**
     * Get position cost in dollars.
     */
    public BigDecimal getPositionCostDollars() {
        return centiCentsToDollars(positionCost);
    }

    /**
     * Get realized P&L in dollars.
     */
    public BigDecimal getRealizedPnlDollars() {
        return centiCentsToDollars(realizedPnl);
    }

    /**
     * Get fees paid in dollars.
     */
    public BigDecimal getFeesPaidDollars() {
        return centiCentsToDollars(feesPaid);
    }

    /**
     * Check if this is a long position (positive contracts).
     */
    public boolean isLong() {
        return position != null && position > 0;
    }

    /**
     * Check if this is a short position (negative contracts).
     */
    public boolean isShort() {
        return position != null && position < 0;
    }

    /**
     * Check if this position is flat (zero contracts).
     */
    public boolean isFlat() {
        return position == null || position == 0;
    }

    /**
     * Get absolute position size.
     */
    public int getAbsoluteSize() {
        return position != null ? Math.abs(position) : 0;
    }

    @Override
    public String toString() {
        return "Position{" +
                "marketTicker='" + marketTicker + '\'' +
                ", position=" + position +
                ", positionCost=$" + getPositionCostDollars() +
                ", realizedPnl=$" + getRealizedPnlDollars() +
                ", feesPaid=$" + getFeesPaidDollars() +
                ", volume=" + volume +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position1 = (Position) o;
        return marketTicker != null && marketTicker.equals(position1.marketTicker);
    }

    @Override
    public int hashCode() {
        return marketTicker != null ? marketTicker.hashCode() : 0;
    }
}
