package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Settlement source for series/market determination.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettlementSource {

    @JsonProperty("name")
    private String name;

    @JsonProperty("url")
    private String url;

    public SettlementSource() {
    }

    public SettlementSource(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "SettlementSource{" +
                "name='" + name + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
