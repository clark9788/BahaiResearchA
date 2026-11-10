package com.bahairesearch.android.corpus;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Book-scoped fallback hits are filler and must sort after real BM25 matches. Their SQL
 * sentinel has to stay positive; a negative value falls below SearchCore.PHRASE_SCORE_THRESHOLD
 * and promotes every filler hit into the phrase tier.
 */
public class LocalCorpusSearchServiceTest {

    @Test
    public void bookScopedFallbackHitsUseAPositiveSentinel() {
        String sql = LocalCorpusSearchService.buildBookScopedSql(false, false);

        assertTrue(sql, sql.contains("+99998.0 AS score"));
        assertFalse(sql, sql.contains("-99998.0"));
    }
}
