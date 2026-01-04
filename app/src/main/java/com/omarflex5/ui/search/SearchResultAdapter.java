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
    private RecyclerView recyclerView;

    public interface OnResultClickListener {
        void onResultClick(UnifiedSearchService.SearchResult result);
    }

    public void setOnResultClickListener(OnResultClickListener listener) {
        this.listener = listener;
    }

    /**
     * Set the RecyclerView reference for scroll-into-view functionality.
     */
    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
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

            // FORCE correct layout params immediately on ViewHolder creation (fixes initial
            // state)
            if (ratingText != null
                    && ratingText.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) ratingText
                        .getLayoutParams();
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT, android.widget.RelativeLayout.TRUE);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT, 0);
                params.addRule(android.widget.RelativeLayout.CENTER_VERTICAL, android.widget.RelativeLayout.TRUE);
                ratingText.setLayoutParams(params);
            }

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

                    // Scroll-into-view: Ensure full card is visible beyond footer
                    scrollFocusedItemIntoView(v);
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

        /**
         * Scrolls the RecyclerView to ensure the focused item is fully visible,
         * accounting for the floating footer (Load More button) and header.
         */
        private void scrollFocusedItemIntoView(View focusedView) {
            if (recyclerView == null)
                return;

            // Post to ensure layout is complete
            focusedView.post(() -> {
                if (recyclerView == null)
                    return;

                float density = focusedView.getContext().getResources().getDisplayMetrics().density;

                // Get the view's position relative to screen
                int[] viewLocation = new int[2];
                focusedView.getLocationOnScreen(viewLocation);
                int viewTop = viewLocation[1];
                int viewBottom = viewLocation[1] + focusedView.getHeight();

                // Account for scale animation (1.10x scale = 10% larger, grows from center)
                int scaledHeight = (int) (focusedView.getHeight() * 1.10f);
                int extraHeight = (scaledHeight - focusedView.getHeight()) / 2;
                int viewTopScaled = viewTop - extraHeight;
                int viewBottomScaled = viewBottom + extraHeight;

                // Get RecyclerView's visible bounds
                int[] recyclerLocation = new int[2];
                recyclerView.getLocationOnScreen(recyclerLocation);
                int recyclerTop = recyclerLocation[1];
                int recyclerBottom = recyclerLocation[1] + recyclerView.getHeight();

                // Extra padding to account for floating footer (Load More button)
                // 80dp should cover the footer height + margin
                int footerPadding = (int) (80 * density);
                int visibleBottom = recyclerBottom - footerPadding;

                // Extra padding for header area (small margin for visual comfort)
                int headerPadding = (int) (16 * density);
                int visibleTop = recyclerTop + headerPadding;

                // If the card extends beyond visible bottom, scroll down
                if (viewBottomScaled > visibleBottom) {
                    int scrollAmount = viewBottomScaled - visibleBottom;
                    recyclerView.smoothScrollBy(0, scrollAmount);
                }
                // If the card extends above visible top, scroll up
                else if (viewTopScaled < visibleTop) {
                    int scrollAmount = viewTopScaled - visibleTop; // Negative = scroll up
                    recyclerView.smoothScrollBy(0, scrollAmount);
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

            // Rating - Not available in search result usually, use INVISIBLE to preserve
            // layout anchor
            ratingText.setVisibility(View.INVISIBLE);
            android.util.Log.d("RATING_DEBUG", "[SearchResult] Card: " + result.title + " | Visibility: INVISIBLE");

            // FORCE correct layout params on every bind (fixes RecyclerView recycling
            // corruption)
            if (ratingText.getLayoutParams() instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) ratingText
                        .getLayoutParams();
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT, android.widget.RelativeLayout.TRUE);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT, 0); // Remove any left rule
                params.addRule(android.widget.RelativeLayout.CENTER_VERTICAL, android.widget.RelativeLayout.TRUE);
                ratingText.setLayoutParams(params);
            }

            // Log layout positions after a slight delay to ensure layout is complete
            final String resultTitle = result.title;
            ratingText.post(() -> {
                int[] location = new int[2];
                ratingText.getLocationOnScreen(location);
                android.util.Log.d("RATING_DEBUG",
                        "[SearchResult] Card: " + resultTitle + " | RatingX: " + location[0] + " | RatingWidth: "
                                + ratingText.getWidth() + " | Parent: " + ((View) ratingText.getParent()).getWidth());
            });

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
                        params.setMargins(0, 0, 8, 0); // Right margin
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
