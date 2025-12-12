package com.omarflex5.ui.home.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
public class MovieCardAdapter extends RecyclerView.Adapter<MovieCardAdapter.MovieViewHolder> {

    private List<Movie> movies = new ArrayList<>();
    private OnMovieListener listener;
    private int selectedPosition = -1; // -1 means no selection

    public MovieCardAdapter() {
        setHasStableIds(true);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    public interface OnMovieListener {
        void onMovieSelected(Movie movie);

        void onMovieClicked(Movie movie);
    }

    @Override
    public long getItemId(int position) {
        // Use hash of ID, fallback to position if ID is invalid
        try {
            return Long.parseLong(movies.get(position).getId());
        } catch (Exception e) {
            return movies.get(position).getId().hashCode();
        }
    }

    public void setMovies(List<Movie> newMovies) {
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

        // Only reset selection if the selected movie is no longer present or valid
        if (selectedPosition >= movies.size()) {
            selectedPosition = -1;
        }
    }

    public void setListener(OnMovieListener listener) {
        this.listener = listener;
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
    public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie_card, parent, false);
        return new MovieViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
        holder.bind(movies.get(position), position, position == selectedPosition);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            for (Object payload : payloads) {
                if (payload instanceof String && payload.equals("selection")) {
                    holder.updateSelectionState(position == selectedPosition);
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return movies.size();
    }

    class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView posterImage;
        TextView titleText, yearText, ratingText, serverBadge;
        LinearLayout categoriesLayout;
        CardView cardView;
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
            Glide.with(itemView.getContext())
                    .load(movie.getPosterUrl())
                    .centerCrop()
                    .into(posterImage);

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

            // Set selected state for visual styling (red border via selector)
            cardView.setSelected(isSelected);
            // NOTE: No setNextFocusLeftId/RightId - HomeActivity.dispatchKeyEvent handles
            // RTL navigation
        }

        public void updateSelectionState(boolean isSelected) {
            cardView.setSelected(isSelected);
        }
    }
}
