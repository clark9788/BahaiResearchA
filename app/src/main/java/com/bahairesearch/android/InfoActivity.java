package com.bahairesearch.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Displays a static information page (About & sources, or How to use) bundled under
 * {@code assets/info/}. Page text lives in HTML so it can be edited without touching Java;
 * the theme colours and app version are injected at load time.
 */
public class InfoActivity extends AppCompatActivity {

    public static final String EXTRA_PAGE = "page";
    public static final String PAGE_ABOUT = "about";
    public static final String PAGE_HELP = "help";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);

        String page = getIntent().getStringExtra(EXTRA_PAGE);
        boolean about = PAGE_ABOUT.equals(page);

        TextView title = findViewById(R.id.tvInfoTitle);
        title.setText(about ? R.string.about_title : R.string.how_to_use_title);

        Button back = findViewById(R.id.btnBack);
        back.setOnClickListener(v -> finish());

        WebView webView = findViewById(R.id.webView);
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.info_background));
        webView.getSettings().setSupportZoom(false);

        // Follow the system font-size setting, the same way the main screen's text does.
        float fontScale = getResources().getConfiguration().fontScale;
        webView.getSettings().setTextZoom(Math.round(fontScale * 100f));

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    // No browser or email app to handle the link; leave the page as it is.
                }
                return true;
            }
        });

        String html = readAsset(about ? "info/about.html" : "info/help.html")
                .replace("{{theme}}", themeCss())
                .replace("{{version}}", "Bahá'í Research "
                        + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        webView.loadDataWithBaseURL("file:///android_asset/info/", html,
                "text/html", "UTF-8", null);
    }

    /**
     * Builds the CSS custom properties that adapt the page to the current light/dark theme.
     */
    private String themeCss() {
        return String.format(Locale.US,
                ":root { --bg: %s; --fg: %s; --muted: %s; --link: %s; --rule: %s; }",
                hex(R.color.info_background),
                hex(R.color.info_text),
                hex(R.color.info_muted),
                hex(R.color.info_link),
                hex(R.color.info_rule));
    }

    private String hex(int colorRes) {
        return String.format(Locale.US, "#%06X",
                0xFFFFFF & ContextCompat.getColor(this, colorRes));
    }

    private String readAsset(String path) {
        try (InputStream in = getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "<html><body><p>This page could not be loaded.</p></body></html>";
        }
    }
}
