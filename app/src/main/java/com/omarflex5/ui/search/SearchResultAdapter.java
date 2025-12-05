package com.omarflex5.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.omarflex5.R;
import com.omarflex5.data.search.UnifiedSearchService;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for search results RecyclerView.
 */
public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder> {

    private List<UnifiedSearchService.SearchResult> results = new ArrayList<>();
    private OnResultClickListener listener;

    public interface OnResultClickListener {
        void onResultClick(UnifiedSearchService.SearchResult result);
    }

    public void setOnResultClickListener(OnResultClickListener listener) {
        this.listener = listener;
    }

    public void setResults(List<UnifiedSearchService.SearchResult> results) {
        this.results = results != null ? results : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        UnifiedSearchService.SearchResult result = results.get(position);
        holder.bind(result);
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    class ResultViewHolder extends RecyclerView.ViewHolder {
        private final ImageView poster;
        private final TextView title;
        private final TextView metadata;
        private final TextView serverBadge;

        ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.image_poster);
            title = itemView.findViewById(R.id.text_title);
            metadata = itemView.findViewById(R.id.text_metadata);
            serverBadge = itemView.findViewById(R.id.text_server_badge);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onResultClick(results.get(pos));
                }
            });
        }

        void bind(UnifiedSearchService.SearchResult result) {
            // Title
            title.setText(result.title);

            // Metadata: Type + Year + Sources count
            StringBuilder meta = new StringBuilder();
            meta.append("SERIES".equals(result.type) ? "مسلسل" : "فيلم");
            if (result.year != null) {
                meta.append(" • ").append(result.year);
            }
            if (result.alternativeSources != null && !result.alternativeSources.isEmpty()) {
                meta.append(" • ").append(result.alternativeSources.size() + 1).append(" مصادر");
            }
            metadata.setText(meta.toString());

            // Server badge
            serverBadge.setText(result.serverLabel);

            // Poster
            if (result.posterUrl != null && !result.posterUrl.isEmpty()) {
                Glide.with(poster.getContext())
                        .load(result.posterUrl)
                        .placeholder(R.drawable.placeholder_poster)
                        .error(R.drawable.placeholder_poster)
                        .centerCrop()
                        .into(poster);
            } else {
                poster.setImageResource(R.drawable.placeholder_poster);
            }
        }
    }
}
