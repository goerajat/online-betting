package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Represents a Kalshi Market - a specific binary outcome within an event that users can trade on.
 * Markets have yes/no positions, current prices, volume, and settlement rules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Market {

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("event_ticker")
    private String eventTicker;

    @JsonProperty("market_type")
    private String marketType;

    @JsonProperty("title")
    private String title;

    @JsonProperty("subtitle")
    private String subtitle;

    @JsonProperty("yes_sub_title")
    private String yesSubTitle;

    @JsonProperty("no_sub_title")
    private String noSubTitle;

    @JsonProperty("created_time")
    private Instant createdTime;

    @JsonProperty("open_time")
    private Instant openTime;

    @JsonProperty("close_time")
    private Instant closeTime;

    @JsonProperty("expiration_time")
    private Instant expirationTime;

    @JsonProperty("latest_expiration_time")
    private Instant latestExpirationTime;

    @JsonProperty("expected_expiration_time")
    private Instant expectedExpirationTime;

    @JsonProperty("settlement_timer_seconds")
    private Integer settlementTimerSeconds;

    @JsonProperty("status")
    private String status;

    @JsonProperty("response_price_units")
    private String responsePriceUnits;

    // Prices in cents
    @JsonProperty("yes_bid")
    private Integer yesBid;

    @JsonProperty("yes_ask")
    private Integer yesAsk;

    @JsonProperty("no_bid")
    private Integer noBid;

    @JsonProperty("no_ask")
    private Integer noAsk;

    @JsonProperty("last_price")
    private Integer lastPrice;

    // Prices in dollars (string format)
    @JsonProperty("yes_bid_dollars")
    private String yesBidDollars;

    @JsonProperty("yes_ask_dollars")
    private String yesAskDollars;

    @JsonProperty("no_bid_dollars")
    private String noBidDollars;

    @JsonProperty("no_ask_dollars")
    private String noAskDollars;

    @JsonProperty("last_price_dollars")
    private String lastPriceDollars;

    // Previous prices
    @JsonProperty("previous_yes_bid")
    private Integer previousYesBid;

    @JsonProperty("previous_yes_ask")
    private Integer previousYesAsk;

    @JsonProperty("previous_yes_bid_dollars")
    private String previousYesBidDollars;

    @JsonProperty("previous_yes_ask_dollars")
    private String previousYesAskDollars;

    @JsonProperty("previous_price_dollars")
    private String previousPriceDollars;

    // Volume and interest
    @JsonProperty("volume")
    private Long volume;

    @JsonProperty("volume_24h")
    private Long volume24h;

    @JsonProperty("open_interest")
    private Long openInterest;

    @JsonProperty("notional_value")
    private Long notionalValue;

    @JsonProperty("notional_value_dollars")
    private String notionalValueDollars;

    @JsonProperty("liquidity_dollars")
    private String liquidityDollars;

    // Settlement
    @JsonProperty("result")
    private String result;

    @JsonProperty("settlement_value")
    private Integer settlementValue;

    @JsonProperty("settlement_value_dollars")
    private String settlementValueDollars;

    @JsonProperty("settlement_ts")
    private Instant settlementTs;

    @JsonProperty("expiration_value")
    private String expirationValue;

    // Rules and structure
    @JsonProperty("rules_primary")
    private String rulesPrimary;

    @JsonProperty("rules_secondary")
    private String rulesSecondary;

    @JsonProperty("price_level_structure")
    private String priceLevelStructure;

    @JsonProperty("price_ranges")
    private List<PriceRange> priceRanges;

    // Flags
    @JsonProperty("can_close_early")
    private Boolean canCloseEarly;

    @JsonProperty("is_provisional")
    private Boolean isProvisional;

    // Strike configuration
    @JsonProperty("strike_type")
    private String strikeType;

    @JsonProperty("floor_strike")
    private Double floorStrike;

    @JsonProperty("cap_strike")
    private Double capStrike;

    @JsonProperty("functional_strike")
    private String functionalStrike;

    // Fee waiver
    @JsonProperty("fee_waiver_expiration_time")
    private Instant feeWaiverExpirationTime;

    @JsonProperty("early_close_condition")
    private String earlyCloseCondition;

    // MVE (Multivariate Events)
    @JsonProperty("mve_collection_ticker")
    private String mveCollectionTicker;

    public Market() {
    }

    // Getters and Setters
    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getEventTicker() {
        return eventTicker;
    }

    public void setEventTicker(String eventTicker) {
        this.eventTicker = eventTicker;
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getYesSubTitle() {
        return yesSubTitle;
    }

    public void setYesSubTitle(String yesSubTitle) {
        this.yesSubTitle = yesSubTitle;
    }

    public String getNoSubTitle() {
        return noSubTitle;
    }

    public void setNoSubTitle(String noSubTitle) {
        this.noSubTitle = noSubTitle;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
    }

    public Instant getOpenTime() {
        return openTime;
    }

    public void setOpenTime(Instant openTime) {
        this.openTime = openTime;
    }

    public Instant getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(Instant closeTime) {
        this.closeTime = closeTime;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Instant expirationTime) {
        this.expirationTime = expirationTime;
    }

    public Instant getLatestExpirationTime() {
        return latestExpirationTime;
    }

    public void setLatestExpirationTime(Instant latestExpirationTime) {
        this.latestExpirationTime = latestExpirationTime;
    }

    public Instant getExpectedExpirationTime() {
        return expectedExpirationTime;
    }

    public void setExpectedExpirationTime(Instant expectedExpirationTime) {
        this.expectedExpirationTime = expectedExpirationTime;
    }

    public Integer getSettlementTimerSeconds() {
        return settlementTimerSeconds;
    }

    public void setSettlementTimerSeconds(Integer settlementTimerSeconds) {
        this.settlementTimerSeconds = settlementTimerSeconds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponsePriceUnits() {
        return responsePriceUnits;
    }

    public void setResponsePriceUnits(String responsePriceUnits) {
        this.responsePriceUnits = responsePriceUnits;
    }

    public Integer getYesBid() {
        return yesBid;
    }

    public void setYesBid(Integer yesBid) {
        this.yesBid = yesBid;
    }

    public Integer getYesAsk() {
        return yesAsk;
    }

    public void setYesAsk(Integer yesAsk) {
        this.yesAsk = yesAsk;
    }

    public Integer getNoBid() {
        return noBid;
    }

    public void setNoBid(Integer noBid) {
        this.noBid = noBid;
    }

    public Integer getNoAsk() {
        return noAsk;
    }

    public void setNoAsk(Integer noAsk) {
        this.noAsk = noAsk;
    }

    public Integer getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(Integer lastPrice) {
        this.lastPrice = lastPrice;
    }

    public String getYesBidDollars() {
        return yesBidDollars;
    }

    public void setYesBidDollars(String yesBidDollars) {
        this.yesBidDollars = yesBidDollars;
    }

    public String getYesAskDollars() {
        return yesAskDollars;
    }

    public void setYesAskDollars(String yesAskDollars) {
        this.yesAskDollars = yesAskDollars;
    }

    public String getNoBidDollars() {
        return noBidDollars;
    }

    public void setNoBidDollars(String noBidDollars) {
        this.noBidDollars = noBidDollars;
    }

    public String getNoAskDollars() {
        return noAskDollars;
    }

    public void setNoAskDollars(String noAskDollars) {
        this.noAskDollars = noAskDollars;
    }

    public String getLastPriceDollars() {
        return lastPriceDollars;
    }

    public void setLastPriceDollars(String lastPriceDollars) {
        this.lastPriceDollars = lastPriceDollars;
    }

    public Integer getPreviousYesBid() {
        return previousYesBid;
    }

    public void setPreviousYesBid(Integer previousYesBid) {
        this.previousYesBid = previousYesBid;
    }

    public Integer getPreviousYesAsk() {
        return previousYesAsk;
    }

    public void setPreviousYesAsk(Integer previousYesAsk) {
        this.previousYesAsk = previousYesAsk;
    }

    public String getPreviousYesBidDollars() {
        return previousYesBidDollars;
    }

    public void setPreviousYesBidDollars(String previousYesBidDollars) {
        this.previousYesBidDollars = previousYesBidDollars;
    }

    public String getPreviousYesAskDollars() {
        return previousYesAskDollars;
    }

    public void setPreviousYesAskDollars(String previousYesAskDollars) {
        this.previousYesAskDollars = previousYesAskDollars;
    }

    public String getPreviousPriceDollars() {
        return previousPriceDollars;
    }

    public void setPreviousPriceDollars(String previousPriceDollars) {
        this.previousPriceDollars = previousPriceDollars;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public Long getVolume24h() {
        return volume24h;
    }

    public void setVolume24h(Long volume24h) {
        this.volume24h = volume24h;
    }

    public Long getOpenInterest() {
        return openInterest;
    }

    public void setOpenInterest(Long openInterest) {
        this.openInterest = openInterest;
    }

    public Long getNotionalValue() {
        return notionalValue;
    }

    public void setNotionalValue(Long notionalValue) {
        this.notionalValue = notionalValue;
    }

    public String getNotionalValueDollars() {
        return notionalValueDollars;
    }

    public void setNotionalValueDollars(String notionalValueDollars) {
        this.notionalValueDollars = notionalValueDollars;
    }

    public String getLiquidityDollars() {
        return liquidityDollars;
    }

    public void setLiquidityDollars(String liquidityDollars) {
        this.liquidityDollars = liquidityDollars;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getSettlementValue() {
        return settlementValue;
    }

    public void setSettlementValue(Integer settlementValue) {
        this.settlementValue = settlementValue;
    }

    public String getSettlementValueDollars() {
        return settlementValueDollars;
    }

    public void setSettlementValueDollars(String settlementValueDollars) {
        this.settlementValueDollars = settlementValueDollars;
    }

    public Instant getSettlementTs() {
        return settlementTs;
    }

    public void setSettlementTs(Instant settlementTs) {
        this.settlementTs = settlementTs;
    }

    public String getExpirationValue() {
        return expirationValue;
    }

    public void setExpirationValue(String expirationValue) {
        this.expirationValue = expirationValue;
    }

    public String getRulesPrimary() {
        return rulesPrimary;
    }

    public void setRulesPrimary(String rulesPrimary) {
        this.rulesPrimary = rulesPrimary;
    }

    public String getRulesSecondary() {
        return rulesSecondary;
    }

    public void setRulesSecondary(String rulesSecondary) {
        this.rulesSecondary = rulesSecondary;
    }

    public String getPriceLevelStructure() {
        return priceLevelStructure;
    }

    public void setPriceLevelStructure(String priceLevelStructure) {
        this.priceLevelStructure = priceLevelStructure;
    }

    public List<PriceRange> getPriceRanges() {
        return priceRanges;
    }

    public void setPriceRanges(List<PriceRange> priceRanges) {
        this.priceRanges = priceRanges;
    }

    public Boolean getCanCloseEarly() {
        return canCloseEarly;
    }

    public void setCanCloseEarly(Boolean canCloseEarly) {
        this.canCloseEarly = canCloseEarly;
    }

    public Boolean getIsProvisional() {
        return isProvisional;
    }

    public void setIsProvisional(Boolean isProvisional) {
        this.isProvisional = isProvisional;
    }

    public String getStrikeType() {
        return strikeType;
    }

    public void setStrikeType(String strikeType) {
        this.strikeType = strikeType;
    }

    public Double getFloorStrike() {
        return floorStrike;
    }

    public void setFloorStrike(Double floorStrike) {
        this.floorStrike = floorStrike;
    }

    public Double getCapStrike() {
        return capStrike;
    }

    public void setCapStrike(Double capStrike) {
        this.capStrike = capStrike;
    }

    public String getFunctionalStrike() {
        return functionalStrike;
    }

    public void setFunctionalStrike(String functionalStrike) {
        this.functionalStrike = functionalStrike;
    }

    public Instant getFeeWaiverExpirationTime() {
        return feeWaiverExpirationTime;
    }

    public void setFeeWaiverExpirationTime(Instant feeWaiverExpirationTime) {
        this.feeWaiverExpirationTime = feeWaiverExpirationTime;
    }

    public String getEarlyCloseCondition() {
        return earlyCloseCondition;
    }

    public void setEarlyCloseCondition(String earlyCloseCondition) {
        this.earlyCloseCondition = earlyCloseCondition;
    }

    public String getMveCollectionTicker() {
        return mveCollectionTicker;
    }

    public void setMveCollectionTicker(String mveCollectionTicker) {
        this.mveCollectionTicker = mveCollectionTicker;
    }

    @Override
    public String toString() {
        return "Market{" +
                "ticker='" + ticker + '\'' +
                ", eventTicker='" + eventTicker + '\'' +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                ", yesBidDollars='" + yesBidDollars + '\'' +
                ", yesAskDollars='" + yesAskDollars + '\'' +
                ", volume=" + volume +
                '}';
    }
}
