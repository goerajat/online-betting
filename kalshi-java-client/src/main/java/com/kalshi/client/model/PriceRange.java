package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Price range configuration for a market.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceRange {

    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;

    @JsonProperty("step")
    private String step;

    public PriceRange() {
    }

    public PriceRange(String start, String end, String step) {
        this.start = start;
        this.end = end;
        this.step = step;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    @Override
    public String toString() {
        return "PriceRange{" +
                "start='" + start + '\'' +
                ", end='" + end + '\'' +
                ", step='" + step + '\'' +
                '}';
    }
}
