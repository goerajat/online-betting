package com.kalshi.client.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Generic WebSocket message wrapper for Kalshi WebSocket API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketMessage {

    @JsonProperty("type")
    private String type;

    @JsonProperty("sid")
    private Integer sid;

    @JsonProperty("seq")
    private Integer seq;

    @JsonProperty("msg")
    private JsonNode msg;

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("error")
    private JsonNode error;

    public WebSocketMessage() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getSid() {
        return sid;
    }

    public void setSid(Integer sid) {
        this.sid = sid;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public JsonNode getMsg() {
        return msg;
    }

    public void setMsg(JsonNode msg) {
        this.msg = msg;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public JsonNode getError() {
        return error;
    }

    public void setError(JsonNode error) {
        this.error = error;
    }

    public boolean isSnapshot() {
        return "orderbook_snapshot".equals(type);
    }

    public boolean isDelta() {
        return "orderbook_delta".equals(type);
    }

    public boolean isError() {
        return error != null;
    }

    public boolean isSubscribed() {
        return "subscribed".equals(type);
    }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "type='" + type + '\'' +
                ", sid=" + sid +
                ", seq=" + seq +
                '}';
    }
}
