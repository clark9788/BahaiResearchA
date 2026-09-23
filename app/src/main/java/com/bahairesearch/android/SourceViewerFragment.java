package com.bahairesearch.android;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.bahairesearch.android.config.TextScalePrefs;

/**
 * Full-screen dialog that renders a source XHTML file from assets and scrolls to the passage anchor.
 */
public class SourceViewerFragment extends DialogFragment {

    private static final String ARG_URL = "url";

    private WebView      webView;
    private PinchTextScaler textScaler;
    private Toast        sizeToast;
    private boolean      pinchGestureActive;

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

        webView = root.findViewById(R.id.webView);
        Button btnClose = root.findViewById(R.id.btnClose);

        webView.getSettings().setJavaScriptEnabled(true);
        // Leaving the built-in zoom controls off lets our pinch handler change the font size
        // (textZoom, which reflows the text) instead of magnifying the whole page.
        webView.getSettings().setSupportZoom(false);
        webView.getSettings().setBuiltInZoomControls(false);

        float initialScale = TextScalePrefs.get(requireContext());
        webView.getSettings().setTextZoom(TextScalePrefs.toPercent(initialScale));
        initPinchToScale(initialScale);

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

    // -------------------------------------------------------------------------
    // Reading text size — two-finger pinch over the source
    // -------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private void initPinchToScale(float initialScale) {
        textScaler = new PinchTextScaler(requireContext(), initialScale,
                new PinchTextScaler.OnScaleListener() {
                    @Override
                    public void onScaleChanged(float scale) {
                        applyTextScale(scale, false);
                    }

                    @Override
                    public void onScaleFinished(float scale) {
                        applyTextScale(scale, true);
                    }
                });

        webView.setOnTouchListener((view, event) -> {
            textScaler.onTouchEvent(event);

            if (textScaler.isInProgress()) {
                if (!pinchGestureActive) {
                    pinchGestureActive = true;
                    // Abandon the WebView's own gesture so the pinch is not read as a tap.
                    cancelWebViewGesture();
                }
                return true;
            }
            if (pinchGestureActive) {
                // Swallow the tail of the pinch until every finger has lifted.
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    pinchGestureActive = false;
                }
                return true;
            }
            return false;
        });
    }

    private void cancelWebViewGesture() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        webView.onTouchEvent(cancel);
        cancel.recycle();
    }

    private void applyTextScale(float scale, boolean persist) {
        if (webView != null) {
            webView.getSettings().setTextZoom(TextScalePrefs.toPercent(scale));
        }
        if (persist) {
            TextScalePrefs.set(requireContext(), scale);
            showTextSizeToast(scale);
        }
    }

    private void showTextSizeToast(float scale) {
        if (sizeToast != null) {
            sizeToast.cancel();
        }
        sizeToast = Toast.makeText(requireContext(),
                getString(R.string.text_size_percent, TextScalePrefs.toPercent(scale)),
                Toast.LENGTH_SHORT);
        sizeToast.show();
    }
}
