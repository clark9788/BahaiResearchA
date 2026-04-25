package com.bahairesearch.android.model;

import java.util.List;

public final class ResearchReport {
    private final String summary;
    private final List<QuoteResult> quotes;

    public ResearchReport(String summary, List<QuoteResult> quotes) {
        this.summary = summary;
        this.quotes = quotes;
    }

    public String summary()          { return summary; }
    public List<QuoteResult> quotes(){ return quotes; }
}
