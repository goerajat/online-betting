package com.betting.etrade.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wrapper for E*TRADE quote API response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteResponse {

    @JsonProperty("QuoteResponse")
    private QuoteResponseData quoteResponse;

    public QuoteResponseData getQuoteResponse() {
        return quoteResponse;
    }

    public void setQuoteResponse(QuoteResponseData quoteResponse) {
        this.quoteResponse = quoteResponse;
    }

    /**
     * Get the list of quote data.
     */
    public List<QuoteData> getQuotes() {
        return quoteResponse != null ? quoteResponse.getQuoteData() : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteResponseData {

        @JsonProperty("QuoteData")
        private List<QuoteData> quoteData;

        @JsonProperty("Messages")
        private Messages messages;

        public List<QuoteData> getQuoteData() {
            return quoteData;
        }

        public void setQuoteData(List<QuoteData> quoteData) {
            this.quoteData = quoteData;
        }

        public Messages getMessages() {
            return messages;
        }

        public void setMessages(Messages messages) {
            this.messages = messages;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Messages {

        @JsonProperty("Message")
        private List<Message> messages;

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {

        @JsonProperty("description")
        private String description;

        @JsonProperty("code")
        private Integer code;

        @JsonProperty("type")
        private String type;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    @Override
    public String toString() {
        List<QuoteData> quotes = getQuotes();
        return "QuoteResponse{quotes=" + (quotes != null ? quotes.size() : 0) + " items}";
    }
}
