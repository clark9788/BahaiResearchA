package com.bahairesearch.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bahairesearch.android.model.QuoteResult;

import java.util.List;

/**
 * RecyclerView adapter that displays ranked passage results with author/title attribution.
 */
public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {

    private List<QuoteResult> results;

    public ResultsAdapter(List<QuoteResult> results) {
        this.results = results;
    }

    /**
     * Replaces the current result list and refreshes the RecyclerView.
     */
    public void setResults(List<QuoteResult> results) {
        this.results = results;
        notifyDataSetChanged();
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
        holder.itemView.setOnLongClickListener(v -> {
            showCopyMenu(v, result);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    private void showCopyMenu(View anchor, QuoteResult result) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Copy passage");
        popup.getMenu().add(0, 2, 1, "Copy with citation");
        popup.setOnMenuItemClickListener(item -> {
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
