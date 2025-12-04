package com.omarflex5.ui.home.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

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

    public interface OnMovieListener {
        void onMovieSelected(Movie movie);

        void onMovieClicked(Movie movie);
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
        selectedPosition = -1; // Reset selection when data changes
        notifyDataSetChanged();
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
        CardView cardView;
        AnimatorSet pulseAnimator;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            posterImage = itemView.findViewById(R.id.image_poster);

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

            // Set selected state for visual styling (red border via selector)
            cardView.setSelected(isSelected);

            // Constrain left/right within row
            if (position == 0) {
                cardView.setNextFocusLeftId(cardView.getId());
            } else {
                cardView.setNextFocusLeftId(View.NO_ID);
            }

            if (position == getItemCount() - 1) {
                cardView.setNextFocusRightId(cardView.getId());
            } else {
                cardView.setNextFocusRightId(View.NO_ID);
            }
        }

        public void updateSelectionState(boolean isSelected) {
            cardView.setSelected(isSelected);
        }
    }
}
