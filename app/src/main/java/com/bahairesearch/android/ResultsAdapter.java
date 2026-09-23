package com.bahairesearch.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bahairesearch.android.config.TextScalePrefs;
import com.bahairesearch.common.model.QuoteResult;

import java.util.List;

/**
 * RecyclerView adapter that displays ranked passage results with author/title attribution.
 */
public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {

    /** Callback for the "Open source" long-press menu action. */
    public interface OnOpenSourceListener {
        void onOpenSource(QuoteResult result);
    }

    private List<QuoteResult> results;
    private final OnOpenSourceListener openSourceListener;
    private final float baseQuotePx;
    private final float baseAttributionPx;
    private float fontScale = TextScalePrefs.DEFAULT_SCALE;

    public ResultsAdapter(Context context, List<QuoteResult> results,
                          OnOpenSourceListener openSourceListener) {
        this.results = results;
        this.openSourceListener = openSourceListener;
        this.baseQuotePx = context.getResources().getDimension(R.dimen.quote_text_size);
        this.baseAttributionPx = context.getResources().getDimension(R.dimen.attribution_text_size);
    }

    /**
     * Replaces the current result list and refreshes the RecyclerView.
     */
    public void setResults(List<QuoteResult> results) {
        this.results = results;
        notifyDataSetChanged();
    }

    /**
     * Updates the reading text scale and re-applies it to the rows currently on screen.
     */
    public void setFontScale(float fontScale, RecyclerView recyclerView) {
        this.fontScale = fontScale;
        if (recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View row = recyclerView.getChildAt(i);
            applyTextSizes(row.findViewById(R.id.tvQuote), row.findViewById(R.id.tvAttribution));
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.result_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuoteResult result = results.get(position);
        holder.tvQuote.setText(result.quote());
        holder.tvAttribution.setText("— " + result.author() + " · " + result.bookTitle());
        applyTextSizes(holder.tvQuote, holder.tvAttribution);
        holder.itemView.setOnLongClickListener(v -> {
            showCopyMenu(v, result);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    private void applyTextSizes(TextView quote, TextView attribution) {
        quote.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseQuotePx * fontScale);
        attribution.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseAttributionPx * fontScale);
    }

    private void showCopyMenu(View anchor, QuoteResult result) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Copy passage");
        popup.getMenu().add(0, 2, 1, "Copy with citation");
        boolean hasXhtmlSource = result.sourceUrl() != null
                && result.sourceUrl().endsWith(".xhtml");
        if (hasXhtmlSource) {
            popup.getMenu().add(0, 3, 2, "Open source");
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 3) {
                if (openSourceListener != null) openSourceListener.onOpenSource(result);
                return true;
            }
            String text = (item.getItemId() == 1)
                    ? result.quote()
                    : result.quote() + "\n— " + result.author() + ", " + result.bookTitle();
            copyToClipboard(anchor.getContext(), text);
            return true;
        });
        popup.show();
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("passage", text));
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvQuote;
        final TextView tvAttribution;

        ViewHolder(View itemView) {
            super(itemView);
            tvQuote       = itemView.findViewById(R.id.tvQuote);
            tvAttribution = itemView.findViewById(R.id.tvAttribution);
        }
    }
}
