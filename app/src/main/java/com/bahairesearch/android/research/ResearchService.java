package com.bahairesearch.android.research;

import io.requery.android.database.sqlite.SQLiteDatabase;

import com.bahairesearch.android.config.AppConfig;
import com.bahairesearch.android.corpus.LocalCorpusSearchService;
import com.bahairesearch.android.model.ResearchReport;

import java.util.Collections;
import java.util.List;

public class ResearchService {

    public ResearchReport search(SQLiteDatabase db, String topic, String selectedAuthor, String selectedTitle) {
        if (topic == null || topic.trim().isEmpty()) {
            return new ResearchReport("Please enter a topic before searching.", Collections.emptyList());
        }
        return LocalCorpusSearchService.search(
                db, topic.trim(), selectedAuthor, selectedTitle, AppConfig.defaults());
    }
}
