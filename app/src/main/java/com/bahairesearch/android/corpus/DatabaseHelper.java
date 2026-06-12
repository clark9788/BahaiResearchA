package com.bahairesearch.android.corpus;

import android.content.Context;
import android.content.SharedPreferences;
import com.bahairesearch.android.BuildConfig;
import io.requery.android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles first-launch copy of the bundled corpus database from assets and opens it read-only.
 */
public final class DatabaseHelper {

    private static final String DB_NAME = "corpus.db";
    private static final int BUFFER_SIZE = 65536;
    private static final String PREFS_NAME = "db_prefs";
    private static final String KEY_DB_VERSION = "db_copy_version_code";

    private DatabaseHelper() {}

    /**
     * Copies the corpus database from assets to internal storage if not already present,
     * or if the app versionCode has changed since the last copy (picks up corpus updates).
     */
    public static void copyIfNeeded(Context context) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int storedVersion = prefs.getInt(KEY_DB_VERSION, -1);
        File dbFile = context.getDatabasePath(DB_NAME);

        if (storedVersion == BuildConfig.VERSION_CODE && dbFile.exists()) {
            return;
        }

        if (dbFile.exists()) {
            dbFile.delete();
        }
        File parent = dbFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (InputStream in = context.getAssets().open(DB_NAME);
             OutputStream out = new FileOutputStream(dbFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }

        prefs.edit().putInt(KEY_DB_VERSION, BuildConfig.VERSION_CODE).apply();
    }

    /**
     * Opens the corpus database read-only from internal storage.
     */
    public static SQLiteDatabase open(Context context) {
        File dbFile = context.getDatabasePath(DB_NAME);
        return SQLiteDatabase.openDatabase(
                dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
    }
}
