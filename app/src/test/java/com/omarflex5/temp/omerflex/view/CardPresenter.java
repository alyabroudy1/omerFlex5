package com.omarflex5.temp.omerflex.view;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.entity.ServerConfig;
import com.omerflex.server.config.ServerConfigRepository;

import java.util.Map;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
    private static final String TAG = "CardPresenter";

    private static final int CARD_WIDTH = 360; //313
    private static final int CARD_HEIGHT = 511; // 176
    private static int sSelectedBackgroundColor;
    private static int sDefaultBackgroundColor;
    private Drawable mDefaultCardImage;

    // Custom ViewHolder to hold ProgressBar
    public static class CustomViewHolder extends Presenter.ViewHolder {
        public final ProgressBar progressBar;

        public CustomViewHolder(ImageCardView view, ProgressBar progressBar) {
            super(view);
            this.progressBar = progressBar;
        }
    }

    private static void updateCardBackgroundColor(ImageCardView view, boolean selected) {
        int color = selected ? sSelectedBackgroundColor : sDefaultBackgroundColor;
        // Both background colors should be set because the view"s background is temporarily visible
        // during animations.
        view.setBackgroundColor(color);
        view.setInfoAreaBackgroundColor(color);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
//        Log.d(TAG, "onCreateViewHolder");

        sDefaultBackgroundColor = ContextCompat.getColor(parent.getContext(), R.color.default_background);
        sSelectedBackgroundColor = ContextCompat.getColor(parent.getContext(), R.color.selected_background);
        mDefaultCardImage = ContextCompat.getDrawable(parent.getContext(), R.drawable.default_background);

        ImageCardView cardView = new ImageCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        // Create and configure ProgressBar
        ProgressBar progressBar = new ProgressBar(parent.getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setId(View.generateViewId());
        progressBar.setMax(100);
        progressBar.setProgressTintList(ColorStateList.valueOf(Color.RED));

        // Use BaseCardView.LayoutParams for the ProgressBar
        BaseCardView.LayoutParams params = new BaseCardView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                10 // Height of the progress bar
        );
        params.gravity = Gravity.BOTTOM;
        progressBar.setLayoutParams(params);

        // Add ProgressBar directly to the ImageCardView
        cardView.addView(progressBar);

        // Set the card's info area to be visible and have a background
        cardView.setInfoAreaBackgroundColor(sDefaultBackgroundColor);
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        updateCardBackgroundColor(cardView, false);

        // Store ProgressBar in ViewHolder for later access
        return new CustomViewHolder(cardView, progressBar);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        Movie movie = (Movie) item;
        ImageCardView cardView = (ImageCardView) viewHolder.view;
        CustomViewHolder customViewHolder = (CustomViewHolder) viewHolder;

        if (movie == null) {
            return;
        }

//        Log.d(TAG, "onBindViewHolder: " + movie.getCardImageUrl());

        cardView.setTitleText(movie.getTitle());
        // Updated to use the year from the movie object.
        cardView.setContentText(movie.getRate() + " (" + movie.getStudio() + ")");
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);

        // Update ProgressBar with dynamic progress
        ProgressBar progressBar = customViewHolder.progressBar;
        if (progressBar != null) {
            // Assuming Movie has a getWatchedProgress() method.
            int progress = (int) movie.getPlayedTime(); // Use a real method to get progress
            progressBar.setProgress(Math.min(Math.max(progress, 0), 100));
        }

        // Use the new helper method to handle image loading logic
        loadMovieImage(cardView, movie, mDefaultCardImage);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ImageCardView cardView = (ImageCardView) viewHolder.view;
        // Remove references to images to free up memory
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
    }

    /**
     * A helper method to handle all the image loading logic, including adding custom headers.
     * @param cardView The ImageCardView to load the image into.
     * @param movie The Movie object containing the image URL and server configuration.
     * @param defaultImage The drawable to show if the image fails to load.
     */
    private void loadMovieImage(ImageCardView cardView, Movie movie, Drawable defaultImage) {
        // Ensure URL is not null or empty, use a fallback if needed
        String imageUrl = movie.getCardImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
//            Log.e(TAG, "loadMovieImage: movie.getCardImageUrl() is NULL or EMPTY!");
            Glide.with(cardView.getContext()).load(defaultImage).into(cardView.getMainImageView());
            return;
        }

        ServerConfig config = ServerConfigRepository.getInstance().getConfig(movie.getStudio());
        // Check if there are any headers to add
        boolean hasHeaders = config != null && config.getHeaders() != null && !config.getHeaders().isEmpty();

        if (hasHeaders) {
            LazyHeaders.Builder builder = new LazyHeaders.Builder();
            String cookies = config.getStringCookies();
            if (cookies != null && !cookies.isEmpty()) {
                builder.addHeader("Cookie", cookies);
            }
            for (Map.Entry<String, String> entry : config.getHeaders().entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }

            GlideUrl glideUrl = new GlideUrl(imageUrl, builder.build());
            try {
                Glide.with(cardView.getContext())
                        .load(glideUrl)
                        .fitCenter()
                        .error(defaultImage)
                        .into(cardView.getMainImageView());
            } catch (Exception e) {
                Log.d(TAG, "loadMovieImage: error with headers: " + e.getMessage());
            }
        } else {
            // Load without headers
            Glide.with(cardView.getContext())
                    .load(imageUrl)
                    .fitCenter()
                    .error(defaultImage)
                    .into(cardView.getMainImageView());
//            Log.d(TAG, "loadMovieImage: no headers needed.");
        }
    }
}
