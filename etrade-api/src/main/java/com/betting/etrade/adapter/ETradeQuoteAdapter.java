package com.betting.etrade.adapter;

import com.betting.etrade.model.QuoteData;
import com.betting.marketdata.model.Quote;
import com.betting.marketdata.model.SimpleQuote;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter to convert E*TRADE QuoteData to the generic Quote interface.
 */
public class ETradeQuoteAdapter {

    /**
     * Convert E*TRADE QuoteData to a generic Quote.
     */
    public static Quote toQuote(QuoteData quoteData) {
        SimpleQuote.Builder builder = SimpleQuote.builder()
                .symbol(quoteData.getSymbol())
                .lastPrice(quoteData.getLastPrice())
                .timestamp(quoteData.getTimestamp())
                .status(quoteData.getQuoteStatus());

        if (quoteData.getIntraday() != null) {
            var intraday = quoteData.getIntraday();
            builder.lastPrice(intraday.getLastTrade())
                    .bid(intraday.getBid())
                    .ask(intraday.getAsk())
                    .change(intraday.getChangeClose())
                    .changePercent(intraday.getChangeClosePercentage())
                    .high(intraday.getHigh())
                    .low(intraday.getLow())
                    .volume(intraday.getTotalVolume());
        }

        if (quoteData.getAll() != null) {
            var all = quoteData.getAll();
            builder.lastPrice(all.getLastTrade())
                    .bid(all.getBid())
                    .ask(all.getAsk())
                    .bidSize(all.getBidSize())
                    .askSize(all.getAskSize())
                    .change(all.getChangeClose())
                    .changePercent(all.getChangeClosePercentage())
                    .high(all.getHigh())
                    .low(all.getLow())
                    .open(all.getOpen())
                    .previousClose(all.getPreviousClose())
                    .volume(all.getTotalVolume());
        }

        return builder.build();
    }

    /**
     * Convert a list of E*TRADE QuoteData to generic Quotes.
     */
    public static List<Quote> toQuotes(List<QuoteData> quoteDataList) {
        return quoteDataList.stream()
                .map(ETradeQuoteAdapter::toQuote)
                .collect(Collectors.toList());
    }
}
