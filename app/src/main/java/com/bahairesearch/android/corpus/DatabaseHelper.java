package com.bahairesearch.android.corpus;

import android.content.Context;
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

    private DatabaseHelper() {}

    /**
     * Copies the corpus database from assets to internal storage if not already present.
     */
    public static void copyIfNeeded(Context context) throws IOException {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (dbFile.exists()) {
            return;
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
