package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Request object for batch canceling orders.
 * Can cancel up to 20 orders at once.
 */
public class BatchCancelRequest {

    @JsonProperty("ids")
    private List<String> ids;

    public BatchCancelRequest() {
        this.ids = new ArrayList<>();
    }

    public BatchCancelRequest(List<String> ids) {
        if (ids.size() > 20) {
            throw new IllegalArgumentException("Cannot cancel more than 20 orders at once");
        }
        this.ids = new ArrayList<>(ids);
    }

    public static BatchCancelRequest of(String... orderIds) {
        return new BatchCancelRequest(Arrays.asList(orderIds));
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public BatchCancelRequest addOrderId(String orderId) {
        if (ids.size() >= 20) {
            throw new IllegalStateException("Cannot add more than 20 order IDs");
        }
        ids.add(orderId);
        return this;
    }
}
