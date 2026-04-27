package com.bahairesearch.android;

import android.database.Cursor;
import io.requery.android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.bahairesearch.android.model.QuoteResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bahairesearch.android.corpus.DatabaseHelper;
import com.bahairesearch.android.model.ResearchReport;
import com.bahairesearch.android.research.ResearchService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main screen: author/title filters, keyword search entry, and ranked passage results.
 */
public class MainActivity extends AppCompatActivity {

    private static final String ALL_AUTHORS = "All Authors";
    private static final String ALL_TITLES  = "All Titles";

    private Spinner      spinnerAuthor;
    private Spinner      spinnerTitle;
    private EditText     editQuery;
    private Button       btnSearch;
    private TextView     tvStatus;
    private ResultsAdapter adapter;

    private SQLiteDatabase db;
    private final ResearchService  researchService = new ResearchService();
    private final ExecutorService  executor        = Executors.newSingleThreadExecutor();
    private final Handler          mainHandler     = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerAuthor = findViewById(R.id.spinnerAuthor);
        spinnerTitle  = findViewById(R.id.spinnerTitle);
        editQuery     = findViewById(R.id.editQuery);
        btnSearch     = findViewById(R.id.btnSearch);
        tvStatus      = findViewById(R.id.tvStatus);

        RecyclerView recycler = findViewById(R.id.recyclerResults);
        adapter = new ResultsAdapter(Collections.emptyList(), this::openSource);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnSearch.setOnClickListener(v -> runSearch());
        editQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });

        initAuthorSpinner();
        initDatabase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (db != null && db.isOpen()) db.close();
    }

    // -------------------------------------------------------------------------
    // DB initialisation — runs on background thread, ~35 MB copy on first launch
    // -------------------------------------------------------------------------

    private void initDatabase() {
        setUiEnabled(false);
        tvStatus.setText("Loading corpus…");
        executor.execute(() -> {
            try {
                DatabaseHelper.copyIfNeeded(this);
                SQLiteDatabase opened = DatabaseHelper.open(this);
                mainHandler.post(() -> {
                    db = opened;
                    setUiEnabled(true);
                    tvStatus.setText("Ready — enter a search term above");
                });
            } catch (IOException e) {
                mainHandler.post(() ->
                        tvStatus.setText("Error loading corpus: " + e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Spinners
    // -------------------------------------------------------------------------

    private void initAuthorSpinner() {
        List<String> authors = List.of(
                ALL_AUTHORS,
                "Baha'u'llah",
                "Bab",
                "'Abdu'l-Baha",
                "Shoghi Effendi",
                "Universal House of Justice",
                "Compilation");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, authors);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAuthor.setAdapter(adapter);

        spinnerAuthor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = (String) parent.getItemAtPosition(pos);
                if (ALL_AUTHORS.equals(selected)) {
                    spinnerTitle.setEnabled(false);
                    setTitleSpinner(null);
                } else if (db != null) {
                    setTitleSpinner(selected);
                    spinnerTitle.setEnabled(true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setTitleSpinner(String author) {
        List<String> titles = new ArrayList<>();
        titles.add(ALL_TITLES);

        if (db != null && author != null) {
            String sql = "SELECT DISTINCT title FROM documents "
                    + "WHERE lower(author) = lower(?) "
                    + "AND title IS NOT NULL AND trim(title) != '' "
                    + "ORDER BY title";
            try (Cursor cursor = db.rawQuery(sql, new String[]{author})) {
                while (cursor.moveToNext()) {
                    String t = cursor.getString(0);
                    if (t != null && !t.isBlank()) titles.add(t.trim());
                }
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTitle.setAdapter(adapter);
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    private void runSearch() {
        String query = editQuery.getText().toString().trim();
        if (query.isEmpty()) {
            tvStatus.setText("Please enter a search term.");
            return;
        }
        if (db == null) return;

        String rawAuthor = (String) spinnerAuthor.getSelectedItem();
        String rawTitle  = (String) spinnerTitle.getSelectedItem();
        String author = ALL_AUTHORS.equals(rawAuthor) ? null : rawAuthor;
        String title  = ALL_TITLES.equals(rawTitle)   ? null : rawTitle;

        setUiEnabled(false);
        tvStatus.setText("Searching…");
        adapter.setResults(Collections.emptyList());

        executor.execute(() -> {
            try {
                ResearchReport report = researchService.search(db, query, author, title);
                mainHandler.post(() -> {
                    adapter.setResults(report.quotes());
                    tvStatus.setText(report.summary());
                    setUiEnabled(true);
                });
            } catch (Exception e) {
                Log.e("BahaiSearch", "Search failed", e);
                mainHandler.post(() -> {
                    tvStatus.setText("Search error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    setUiEnabled(true);
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Source viewer
    // -------------------------------------------------------------------------

    private void openSource(QuoteResult result) {
        String canonical = result.sourceUrl();
        if (canonical == null || !canonical.endsWith(".xhtml")) return;

        String locator = result.paragraphOrPage();
        boolean hasAnchor = locator != null && !locator.isEmpty()
                && !locator.equals("Not specified");

        String url = "file:///android_asset/" + canonical
                + (hasAnchor ? "#" + locator : "");

        SourceViewerFragment.newInstance(url)
                .show(getSupportFragmentManager(), "source");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setUiEnabled(boolean enabled) {
        editQuery.setEnabled(enabled);
        btnSearch.setEnabled(enabled);
        spinnerAuthor.setEnabled(enabled);
        // Title spinner only active when an author is chosen
        Object sel = spinnerAuthor.getSelectedItem();
        boolean authorChosen = enabled && sel != null && !ALL_AUTHORS.equals(sel);
        spinnerTitle.setEnabled(authorChosen);
    }
}
