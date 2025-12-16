package com.omarflex5.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
                .inflate(R.layout.item_movie_card, parent, false);
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
        private final TextView yearText;
        private final TextView ratingText;
        private final android.widget.LinearLayout categoriesLayout;
        private final TextView serverBadge;

        ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.image_poster);
            title = itemView.findViewById(R.id.text_title);
            yearText = itemView.findViewById(R.id.text_year);
            ratingText = itemView.findViewById(R.id.text_rating);
            categoriesLayout = itemView.findViewById(R.id.layout_categories_badge);
            serverBadge = itemView.findViewById(R.id.text_server_badge);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onResultClick(results.get(pos));
                }
            });

            // Focus animation for TV remote navigation
            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate()
                            .scaleX(1.10f)
                            .scaleY(1.10f)
                            .setDuration(150)
                            .start();
                    v.setElevation(16f);
                } else {
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start();
                    v.setElevation(4f);
                }
            });
        }

        void bind(UnifiedSearchService.SearchResult result) {
            // Title
            title.setText(result.title);

            // Metadata: Year + Type
            StringBuilder meta = new StringBuilder();
            if (result.year != null && result.year > 0) {
                meta.append(result.year);
            }

            String typeLabel = "فيلم";
            if (result.type != null) {
                if ("SERIES".equalsIgnoreCase(result.type) || "TV".equalsIgnoreCase(result.type)) {
                    typeLabel = "مسلسل";
                } else if ("SEASON".equalsIgnoreCase(result.type)) {
                    typeLabel = "موسم";
                } else if ("EPISODE".equalsIgnoreCase(result.type)) {
                    typeLabel = "حلقة";
                }
            }
            if (meta.length() > 0) {
                meta.append(" • ").append(typeLabel);
            } else {
                meta.append(typeLabel);
            }

            if (result.alternativeSources != null && !result.alternativeSources.isEmpty()) {
                meta.append(" • +").append(result.alternativeSources.size());
            }

            yearText.setText(meta.toString());
            yearText.setVisibility(View.VISIBLE);

            // Rating - Not available in search result usually
            ratingText.setVisibility(View.GONE);

            // Categories
            if (categoriesLayout != null) {
                categoriesLayout.removeAllViews();

                if (result.categories != null && !result.categories.isEmpty()) {
                    categoriesLayout.setVisibility(View.VISIBLE);
                    int count = 0;
                    for (String cat : result.categories) {
                        if (count >= 2)
                            break; // Limit to 2

                        TextView badge = new TextView(itemView.getContext());
                        badge.setText(cat);
                        badge.setTextSize(8); // 8sp
                        badge.setTextColor(0xFFFFFFFF); // White
                        badge.setTypeface(null, android.graphics.Typeface.BOLD);
                        badge.setBackgroundResource(R.drawable.badge_background);
                        badge.setPadding(8, 2, 8, 2); // px
                        badge.setMaxLines(1);
                        badge.setEllipsize(android.text.TextUtils.TruncateAt.END);

                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0, 0, 0, 4); // Bottom margin
                        badge.setLayoutParams(params);

                        categoriesLayout.addView(badge);
                        count++;
                    }
                } else {
                    categoriesLayout.setVisibility(View.GONE);
                }
            }

            // Server badge
            if (result.serverLabel != null) {
                serverBadge.setText(result.serverLabel);
                serverBadge.setVisibility(View.VISIBLE);
            } else {
                serverBadge.setVisibility(View.GONE);
            }

            // Poster
            if (result.posterUrl != null && !result.posterUrl.isEmpty()) {
                // Fix 403: Add Headers (Cookie, UA, Referer)
                String cookie = android.webkit.CookieManager.getInstance().getCookie(result.posterUrl);
                if (cookie == null && result.pageUrl != null) {
                    cookie = android.webkit.CookieManager.getInstance().getCookie(result.pageUrl);
                }

                com.bumptech.glide.load.model.LazyHeaders.Builder builder = new com.bumptech.glide.load.model.LazyHeaders.Builder()
                        .addHeader("User-Agent", com.omarflex5.util.WebConfig.getUserAgent(poster.getContext()));

                if (cookie != null) {
                    builder.addHeader("Cookie", cookie);
                }

                if (result.pageUrl != null) {
                    builder.addHeader("Referer", result.pageUrl);
                }

                com.bumptech.glide.load.model.GlideUrl glideUrl = new com.bumptech.glide.load.model.GlideUrl(
                        result.posterUrl, builder.build());

                Glide.with(poster.getContext())
                        .load(glideUrl)
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
