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
 * A single copyright footer follows the last result; it is not shown when the list is empty.
 */
public class ResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    /** Callback for the "Open source" long-press menu action. */
    public interface OnOpenSourceListener {
        void onOpenSource(QuoteResult result);
    }

    private List<QuoteResult> results;
    private final OnOpenSourceListener openSourceListener;
    private final float baseQuotePx;
    private final float baseAttributionPx;
    private final float baseCopyrightPx;
    private float fontScale = TextScalePrefs.DEFAULT_SCALE;

    public ResultsAdapter(Context context, List<QuoteResult> results,
                          OnOpenSourceListener openSourceListener) {
        this.results = results;
        this.openSourceListener = openSourceListener;
        this.baseQuotePx = context.getResources().getDimension(R.dimen.quote_text_size);
        this.baseAttributionPx = context.getResources().getDimension(R.dimen.attribution_text_size);
        this.baseCopyrightPx = context.getResources().getDimension(R.dimen.copyright_text_size);
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
            applyTextSizes(recyclerView.getChildAt(i));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position < results.size() ? TYPE_ITEM : TYPE_FOOTER;
    }

    @Override
    public int getItemCount() {
        return results.isEmpty() ? 0 : results.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FOOTER) {
            return new FooterViewHolder(
                    inflater.inflate(R.layout.results_footer, parent, false));
        }
        return new ItemViewHolder(inflater.inflate(R.layout.result_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterViewHolder) {
            applyTextSizes(holder.itemView);
            return;
        }

        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        QuoteResult result = results.get(position);
        itemHolder.tvQuote.setText(result.quote());
        itemHolder.tvAttribution.setText("— " + result.author() + " · " + result.bookTitle());
        applyTextSizes(itemHolder.itemView);
        itemHolder.itemView.setOnLongClickListener(v -> {
            showCopyMenu(v, result);
            return true;
        });
    }

    private void applyTextSizes(View row) {
        TextView quote = row.findViewById(R.id.tvQuote);
        if (quote != null) {
            TextView attribution = row.findViewById(R.id.tvAttribution);
            quote.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseQuotePx * fontScale);
            attribution.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseAttributionPx * fontScale);
        }
        TextView footer = row.findViewById(R.id.tvFooter);
        if (footer != null) {
            footer.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseCopyrightPx * fontScale);
        }
    }

    /**
     * A row cached off screen keeps the text size it was bound with, so re-apply the current
     * scale whenever a row scrolls back into view. Without this the footer can look unchanged
     * after a pinch that happened while it was below the fold.
     */
    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        applyTextSizes(holder.itemView);
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

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        final TextView tvQuote;
        final TextView tvAttribution;

        ItemViewHolder(View itemView) {
            super(itemView);
            tvQuote       = itemView.findViewById(R.id.tvQuote);
            tvAttribution = itemView.findViewById(R.id.tvAttribution);
        }
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(View itemView) {
            super(itemView);
        }
    }
}
