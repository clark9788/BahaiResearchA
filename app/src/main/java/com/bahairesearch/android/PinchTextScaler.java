package com.bahairesearch.android;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.bahairesearch.android.config.TextScalePrefs;

/**
 * Translates a two-finger pinch into a clamped reading text scale.
 *
 * <p>Attach it from a view's touch listener and consume the event while a pinch is in progress so
 * the host view does not also scroll:
 *
 * <pre>
 *   scaler.onTouchEvent(event);
 *   return scaler.isInProgress();
 * </pre>
 */
public class PinchTextScaler {

    /** Receives scale updates while pinching and one final notification when the pinch ends. */
    public interface OnScaleListener {
        void onScaleChanged(float scale);
        void onScaleFinished(float scale);
    }

    private final OnScaleListener listener;
    private final ScaleGestureDetector detector;
    private float scale;
    private boolean changedDuringGesture;

    public PinchTextScaler(Context context, float initialScale, OnScaleListener listener) {
        this.listener = listener;
        this.scale = TextScalePrefs.clamp(initialScale);
        this.detector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector source) {
                        changedDuringGesture = false;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector source) {
                        changedDuringGesture |= updateScale(scale * source.getScaleFactor());
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector source) {
                        if (changedDuringGesture) {
                            listener.onScaleFinished(scale);
                        }
                    }
                });
    }

    /**
     * Feeds a touch event to the pinch detector; returns true while a pinch is in progress.
     */
    public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        return isInProgress();
    }

    /**
     * Returns true while the user is actively pinching.
     */
    public boolean isInProgress() {
        return detector.isInProgress();
    }

    /**
     * Returns the current clamped scale factor.
     */
    public float getScale() {
        return scale;
    }

    private boolean updateScale(float newScale) {
        float clamped = TextScalePrefs.clamp(newScale);
        if (clamped == scale) {
            return false;
        }
        scale = clamped;
        listener.onScaleChanged(scale);
        return true;
    }
}
