package com.omarflex5.ui.home.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.omarflex5.R;
import com.omarflex5.data.model.Movie;

import java.util.ArrayList;
import java.util.List;

/**
 * MovieCardAdapter with distinct focus and selection states:
 * - Focused: visual animation (scale up, pulse) when navigating with D-pad
 * - Selected: persistent red border when item is clicked
 */
public class MovieCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MOVIE = 0;
    private static final int VIEW_TYPE_LOAD_MORE = 1;

    private List<Movie> movies = new ArrayList<>();
    private OnMovieListener listener;
    private OnLoadMoreListener loadMoreListener;
    private int selectedPosition = -1; // -1 means no selection
    private boolean showLoadMore = true; // Show load more button by default

    public MovieCardAdapter() {
        setHasStableIds(true);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    public interface OnMovieListener {
        void onMovieSelected(Movie movie);

        void onMovieClicked(Movie movie);
    }

    public interface OnLoadMoreListener {
        void onLoadMore();
    }

    @Override
    public long getItemId(int position) {
        // Load more button doesn't have stable ID
        if (getItemViewType(position) == VIEW_TYPE_LOAD_MORE) {
            return -1;
        }
        // Use hash of ID, fallback to position if ID is invalid
        try {
            return Long.parseLong(movies.get(position).getId());
        } catch (Exception e) {
            return movies.get(position).getId().hashCode();
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (showLoadMore && position == movies.size()) {
            return VIEW_TYPE_LOAD_MORE;
        }
        return VIEW_TYPE_MOVIE;
    }

    @Override
    public int getItemCount() {
        return showLoadMore ? movies.size() + 1 : movies.size();
    }

    public void setMovies(List<Movie> newMovies) {
        // Detect if this is a "Load More" operation
        final boolean isLoadMore;
        final int oldSize;
        if (movies != null && newMovies != null && newMovies.size() > movies.size()) {
            isLoadMore = true;
            oldSize = movies.size();
        } else {
            isLoadMore = false;
            oldSize = 0;
        }

        if (movies == null) {
            movies = newMovies;
            notifyItemRangeInserted(0, newMovies.size());
            return;
        }

        androidx.recyclerview.widget.DiffUtil.DiffResult result = androidx.recyclerview.widget.DiffUtil
                .calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return movies.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newMovies.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return movies.get(oldItemPosition).getId().equals(newMovies.get(newItemPosition).getId());
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        Movie oldMovie = movies.get(oldItemPosition);
                        Movie newMovie = newMovies.get(newItemPosition);

                        // Null-safe comparison
                        boolean titleSame = java.util.Objects.equals(oldMovie.getTitle(), newMovie.getTitle());
                        boolean posterSame = java.util.Objects.equals(oldMovie.getPosterUrl(), newMovie.getPosterUrl());

                        return titleSame && posterSame;
                    }
                });

        // Update list reference
        this.movies = newMovies;

        // Dispatch updates - this preserves focus!
        result.dispatchUpdatesTo(this);

        // Handling for "Load More" focus preservation
        if (isLoadMore && loadMoreFocusCallback != null) {
            // When items are added, the Load More button moves to the end.
            // We want to focus on the FIRST newly added item so the user sees what's new.
            // The first new item is at index 'oldSize'.

            // We notify the callback to request focus on the specific position
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (loadMoreFocusCallback != null) {
                    loadMoreFocusCallback.onFocusRequest(oldSize);
                }
            }, 100);
        }

        // Only reset selection if the selected movie is no longer present or valid
        if (selectedPosition >= movies.size()) {
            selectedPosition = -1;
        }
    }

    // Callback for focus requests
    public interface OnFocusRequestCallback {
        void onFocusRequest(int position);
    }

    private OnFocusRequestCallback loadMoreFocusCallback;

    public void setLoadMoreFocusCallback(OnFocusRequestCallback callback) {
        this.loadMoreFocusCallback = callback;
    }

    public void setListener(OnMovieListener listener) {
        this.listener = listener;
    }

    public void setLoadMoreListener(OnLoadMoreListener loadMoreListener) {
        this.loadMoreListener = loadMoreListener;
    }

    public void setShowLoadMore(boolean showLoadMore) {
        this.showLoadMore = showLoadMore;
        notifyDataSetChanged();
    }

    /**
     * Programmatically select a movie (updates visual and notifies listener)
     */
    public void selectMovie(int position) {
        if (position >= 0 && position < movies.size()) {
            int oldPosition = selectedPosition;
            selectedPosition = position;
            if (oldPosition >= 0) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(selectedPosition);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOAD_MORE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_load_more, parent, false);
            return new LoadMoreViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie_card, parent, false);
        return new MovieViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MovieViewHolder) {
            ((MovieViewHolder) holder).bind(movies.get(position), position, position == selectedPosition);
        } else if (holder instanceof LoadMoreViewHolder) {
            ((LoadMoreViewHolder) holder).bind();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            if (holder instanceof MovieViewHolder) {
                for (Object payload : payloads) {
                    if (payload instanceof String && payload.equals("selection")) {
                        ((MovieViewHolder) holder).updateSelectionState(position == selectedPosition);
                    }
                }
            }
        }
    }

    class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView posterImage;
        TextView titleText, yearText, ratingText, serverBadge;
        LinearLayout categoriesLayout;
        CardView cardView;
        ProgressBar watchProgress;
        AnimatorSet pulseAnimator;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            posterImage = itemView.findViewById(R.id.image_poster);
            titleText = itemView.findViewById(R.id.text_title);
            yearText = itemView.findViewById(R.id.text_year);
            ratingText = itemView.findViewById(R.id.text_rating);
            categoriesLayout = itemView.findViewById(R.id.layout_categories_badge);
            serverBadge = itemView.findViewById(R.id.text_server_badge);
            watchProgress = itemView.findViewById(R.id.progress_watch);

            // Focus change - only visual animation (scale + pulse)
            cardView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate()
                            .scaleX(1.15f)
                            .scaleY(1.15f)
                            .setDuration(200)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .start();

                    cardView.setCardElevation(16f);
                    startPulseAnimation(v);
                } else {
                    stopPulseAnimation();
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .start();
                    cardView.setCardElevation(4f);
                }
            });

            // Click - first click selects, second click (when already selected) executes
            // action
            cardView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    if (selectedPosition == position) {
                        // Already selected - execute action (play movie)
                        if (listener != null) {
                            listener.onMovieClicked(movies.get(position));
                        }
                    } else {
                        // Not selected - select it and update hero
                        int oldPosition = selectedPosition;
                        selectedPosition = position;

                        // Use payload to avoid full rebind which causes focus loss
                        if (oldPosition >= 0) {
                            notifyItemChanged(oldPosition, "selection");
                        }
                        notifyItemChanged(selectedPosition, "selection");

                        // Request focus back on this view AFTER notify completes
                        v.post(() -> v.requestFocus());

                        // Notify listener to update hero
                        if (listener != null) {
                            listener.onMovieSelected(movies.get(position));
                        }
                    }
                }
            });
        }

        private void startPulseAnimation(View v) {
            stopPulseAnimation();

            ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.15f, 1.18f);
            ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.15f, 1.18f);
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 1.18f, 1.15f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 1.18f, 1.15f);

            AnimatorSet pulseUp = new AnimatorSet();
            pulseUp.playTogether(scaleUpX, scaleUpY);
            pulseUp.setDuration(400);

            AnimatorSet pulseDown = new AnimatorSet();
            pulseDown.playTogether(scaleDownX, scaleDownY);
            pulseDown.setDuration(400);

            pulseAnimator = new AnimatorSet();
            pulseAnimator.playSequentially(pulseUp, pulseDown);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (pulseAnimator != null && cardView.hasFocus()) {
                        pulseAnimator.start();
                    }
                }
            });
            pulseAnimator.start();
        }

        private void stopPulseAnimation() {
            if (pulseAnimator != null) {
                pulseAnimator.cancel();
                pulseAnimator = null;
            }
        }

        public void bind(Movie movie, int position, boolean isSelected) {
            // Fix 403: Add Headers
            String posterUrl = movie.getPosterUrl();
            String cookie = null;
            if (posterUrl != null) {
                cookie = android.webkit.CookieManager.getInstance().getCookie(posterUrl);
            }
            if (cookie == null && movie.getVideoUrl() != null) {
                cookie = android.webkit.CookieManager.getInstance().getCookie(movie.getVideoUrl());
            }

            com.bumptech.glide.load.model.LazyHeaders.Builder builder = new com.bumptech.glide.load.model.LazyHeaders.Builder()
                    .addHeader("User-Agent", com.omarflex5.util.WebConfig.getUserAgent(itemView.getContext()));

            if (cookie != null) {
                builder.addHeader("Cookie", cookie);
            }

            if (movie.getVideoUrl() != null) {
                builder.addHeader("Referer", movie.getVideoUrl());
            }

            if (posterUrl != null) {
                com.bumptech.glide.load.model.GlideUrl glideUrl = new com.bumptech.glide.load.model.GlideUrl(
                        posterUrl, builder.build());

                Glide.with(itemView.getContext())
                        .load(glideUrl)
                        .centerCrop()
                        .into(posterImage);
            } else {
                Glide.with(itemView.getContext())
                        .load((Object) null)
                        .centerCrop()
                        .into(posterImage);
            }

            if (titleText != null)
                titleText.setText(movie.getTitle());

            if (yearText != null) {
                String meta = movie.getYear();
                String type = movie.isTvShow() ? "Series" : "Film";
                if (meta != null && !meta.isEmpty()) {
                    meta += " • " + type;
                } else {
                    meta = type;
                }
                yearText.setText(meta);
            }

            if (ratingText != null && movie.getRating() != null && !movie.getRating().isEmpty()) {
                ratingText.setText("★ " + movie.getRating());
                ratingText.setVisibility(View.VISIBLE);
            } else if (ratingText != null) {
                ratingText.setVisibility(View.GONE);
            }

            if (categoriesLayout != null) {
                categoriesLayout.removeAllViews();
                if (movie.getCategories() != null && !movie.getCategories().isEmpty()) {
                    categoriesLayout.setVisibility(View.VISIBLE);
                    // Add max 2 categories to avoid overflow
                    int count = 0;
                    for (String cat : movie.getCategories()) {
                        if (count >= 2)
                            break;

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
                        params.setMargins(0, 0, 0, 4); // Bottom margin for stack
                        badge.setLayoutParams(params);

                        categoriesLayout.addView(badge);
                        count++;
                    }
                } else {
                    categoriesLayout.setVisibility(View.GONE);
                }
            }

            if (serverBadge != null) {
                if (movie.getSourceName() != null) {
                    serverBadge.setText(movie.getSourceName());
                    serverBadge.setVisibility(View.VISIBLE);
                } else {
                    serverBadge.setVisibility(View.GONE);
                }
            }

            // Watch Progress Bar (YouTube style)
            if (watchProgress != null) {
                if (movie.getDuration() > 0 && movie.getWatchProgress() > 0) {
                    int percent = (int) ((movie.getWatchProgress() * 100) / movie.getDuration());
                    // Clamp to 1-100%
                    percent = Math.max(1, Math.min(100, percent));
                    watchProgress.setProgress(percent);
                    watchProgress.setVisibility(View.VISIBLE);
                } else {
                    watchProgress.setVisibility(View.GONE);
                }
            }

            // Set selected state for visual styling (red border via selector)
            cardView.setSelected(isSelected);
            // NOTE: No setNextFocusLeftId/RightId - HomeActivity.dispatchKeyEvent handles
            // RTL navigation
        }

        public void updateSelectionState(boolean isSelected) {
            cardView.setSelected(isSelected);
            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.border_selected);
            } else {
                itemView.setBackgroundResource(0);
            }
        }
    }

    class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView countText;

        public LoadMoreViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_load_more);
            countText = itemView.findViewById(R.id.text_load_more_count);

            // Handle Focus - Attach to cardView which is the focusable element
            cardView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    animateFocus(v, true);
                } else {
                    animateFocus(v, false);
                }
            });

            // Handle Click - Attach to cardView
            cardView.setOnClickListener(v -> {
                if (loadMoreListener != null) {
                    loadMoreListener.onLoadMore();
                }
            });
        }

        public void bind() {
            // Can update text dynamically if needed, e.g. "Load 30 more"
        }

        private void animateFocus(View view, boolean hasFocus) {
            float scale = hasFocus ? 1.05f : 1.0f;
            float elevation = hasFocus ? 12f : 4f;

            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", scale);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", scale);
            ObjectAnimator elevAnim = ObjectAnimator.ofFloat(view, "elevation", elevation);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(scaleX, scaleY, elevAnim);
            set.setDuration(200);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.start();
        }
    }
}
