package com.bahairesearch.android.corpus;

import android.content.Context;
import io.requery.android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class DatabaseHelper {

    private static final String DB_NAME = "corpus.db";
    private static final int BUFFER_SIZE = 65536;

    private DatabaseHelper() {}

    /**
     * Copy DB from assets to internal storage on first launch.
     * Must be called on a background thread — 35 MB copy takes a few seconds.
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

    public static SQLiteDatabase open(Context context) {
        File dbFile = context.getDatabasePath(DB_NAME);
        return SQLiteDatabase.openDatabase(
                dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
    }
}
