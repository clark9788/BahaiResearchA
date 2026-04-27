package com.bahairesearch.android;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

/**
 * Full-screen dialog that renders a source XHTML file from assets and scrolls to the passage anchor.
 */
public class SourceViewerFragment extends DialogFragment {

    private static final String ARG_URL = "url";

    /**
     * Creates a fragment that will load the given {@code file:///android_asset/...#anchor} URL.
     */
    public static SourceViewerFragment newInstance(String url) {
        SourceViewerFragment f = new SourceViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_source_viewer, container, false);

        WebView webView = root.findViewById(R.id.webView);
        Button btnClose = root.findViewById(R.id.btnClose);

        webView.getSettings().setJavaScriptEnabled(true);

        String fullUrl = requireArguments().getString(ARG_URL);
        int hashIdx = fullUrl.indexOf('#');
        String baseUrl = hashIdx >= 0 ? fullUrl.substring(0, hashIdx) : fullUrl;
        String anchor  = hashIdx >= 0 ? fullUrl.substring(hashIdx + 1) : null;

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (anchor != null && !anchor.isEmpty()) {
                    // Scroll after layout is complete; the fragment URL alone is unreliable
                    // on large XHTML files because the engine may not have laid out the page yet.
                    view.evaluateJavascript(
                            "var el=document.getElementById('" + anchor + "');" +
                            "if(el) el.scrollIntoView({block:'center'});",
                            null);
                }
            }
        });

        webView.loadUrl(baseUrl);

        btnClose.setOnClickListener(v -> dismiss());
        return root;
    }
}
