package com.betting.etrade.client;

import com.betting.etrade.exception.ETradeApiException;
import com.betting.etrade.model.DetailFlag;
import com.betting.etrade.model.QuoteData;
import com.betting.etrade.model.QuoteResponse;
import com.betting.etrade.oauth.OAuthConfig;
import com.betting.etrade.oauth.OAuthService;
import com.betting.etrade.oauth.OAuthToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * E*TRADE API client for fetching market quotes.
 */
public class ETradeClient {

    private static final Logger log = LoggerFactory.getLogger(ETradeClient.class);
    private static final int MAX_SYMBOLS_PER_REQUEST = 25;
    private static final String QUOTE_ENDPOINT = "/v1/market/quote/";

    private final OAuthService oauthService;
    private final ObjectMapper objectMapper;

    /**
     * Create a new E*TRADE client with the given configuration.
     */
    public ETradeClient(OAuthConfig config) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.oauthService = new OAuthService(config, httpClient);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Create a new E*TRADE client with custom HTTP client.
     */
    public ETradeClient(OAuthConfig config, OkHttpClient httpClient) {
        this.oauthService = new OAuthService(config, httpClient);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Check if the client is authenticated.
     */
    public boolean isAuthenticated() {
        return oauthService.isAuthenticated();
    }

    /**
     * Get the OAuth service for manual authentication flow.
     */
    public OAuthService getOAuthService() {
        return oauthService;
    }

    /**
     * Set access token directly (for token persistence).
     */
    public void setAccessToken(OAuthToken token) {
        oauthService.setAccessToken(token);
    }

    /**
     * Perform the full OAuth authentication flow interactively.
     * Returns the authorization URL for the user to visit.
     */
    public String startAuthentication() throws IOException {
        OAuthToken requestToken = oauthService.getRequestToken();
        return oauthService.getAuthorizationUrl(requestToken);
    }

    /**
     * Complete authentication with the verifier code from the user.
     */
    public void completeAuthentication(OAuthToken requestToken, String verifier) throws IOException {
        oauthService.getAccessToken(requestToken, verifier);
    }

    /**
     * Get quotes for the given symbols with INTRADAY detail.
     */
    public List<QuoteData> getIntradayQuotes(String... symbols) throws IOException {
        return getQuotes(DetailFlag.INTRADAY, symbols);
    }

    /**
     * Get quotes for the given symbols with INTRADAY detail.
     */
    public List<QuoteData> getIntradayQuotes(Collection<String> symbols) throws IOException {
        return getQuotes(DetailFlag.INTRADAY, symbols.toArray(new String[0]));
    }

    /**
     * Get quotes for the given symbols.
     */
    public List<QuoteData> getQuotes(DetailFlag detailFlag, String... symbols) throws IOException {
        if (symbols == null || symbols.length == 0) {
            return Collections.emptyList();
        }

        if (!oauthService.isAuthenticated()) {
            throw new ETradeApiException("Client is not authenticated. Call startAuthentication() first.");
        }

        List<QuoteData> allQuotes = new ArrayList<>();

        // Split into batches if more than MAX_SYMBOLS_PER_REQUEST
        List<List<String>> batches = splitIntoBatches(Arrays.asList(symbols), MAX_SYMBOLS_PER_REQUEST);

        for (List<String> batch : batches) {
            List<QuoteData> batchQuotes = fetchQuoteBatch(batch, detailFlag);
            allQuotes.addAll(batchQuotes);
        }

        return allQuotes;
    }

    /**
     * Fetch a batch of quotes from the API.
     */
    private List<QuoteData> fetchQuoteBatch(List<String> symbols, DetailFlag detailFlag) throws IOException {
        String symbolList = String.join(",", symbols);
        String url = oauthService.getConfig().getBaseUrl() + QUOTE_ENDPOINT + symbolList;

        Map<String, String> params = new HashMap<>();
        params.put("detailFlag", detailFlag.name());

        log.debug("Fetching quotes for symbols: {}", symbolList);

        try (Response response = oauthService.executeRequest("GET", url, params, null)) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new ETradeApiException(response.code(), null,
                        "Failed to fetch quotes: " + response.code() + " - " + body);
            }

            String body = response.body().string();
            log.debug("Quote response: {}", body);

            QuoteResponse quoteResponse = objectMapper.readValue(body, QuoteResponse.class);
            List<QuoteData> quotes = quoteResponse.getQuotes();

            return quotes != null ? quotes : Collections.emptyList();
        }
    }

    /**
     * Split a list into batches of the specified size.
     */
    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    /**
     * Get a single quote for a symbol.
     */
    public Optional<QuoteData> getQuote(String symbol, DetailFlag detailFlag) throws IOException {
        List<QuoteData> quotes = getQuotes(detailFlag, symbol);
        return quotes.isEmpty() ? Optional.empty() : Optional.of(quotes.get(0));
    }

    /**
     * Get a single intraday quote for a symbol.
     */
    public Optional<QuoteData> getIntradayQuote(String symbol) throws IOException {
        return getQuote(symbol, DetailFlag.INTRADAY);
    }
}
