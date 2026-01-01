package com.omarflex6.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex6.R;
import com.omarflex6.data.model.Movie;

import java.util.ArrayList;
import java.util.List;

public class MovieCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<Movie> movies = new ArrayList<>();
    private OnMovieListener listener;

    private static final int TYPE_MOVIE = 0;
    private static final int TYPE_FOOTER = 1;

    public interface OnMovieListener {
        void onMovieClicked(Movie movie);

        void onMovieFocused(Movie movie);

        void onLoadMoreClicked(); // New callback
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
        notifyDataSetChanged();
    }

    public void addMovies(List<Movie> newMovies) {
        int startPos = this.movies.size();
        this.movies.addAll(newMovies);
        notifyItemRangeInserted(startPos, newMovies.size());
    }

    public void setListener(OnMovieListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == movies.size()) {
            return TYPE_FOOTER;
        }
        return TYPE_MOVIE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_FOOTER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_load_more_ps4, parent, false);
            return new FooterViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie_card_ps4, parent, false);
        return new MovieViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MovieViewHolder) {
            Movie movie = movies.get(position);
            ((MovieViewHolder) holder).bind(movie);
        } else if (holder instanceof FooterViewHolder) {
            ((FooterViewHolder) holder).bind();
        }
    }

    @Override
    public int getItemCount() {
        return movies.size() + 1; // Always show footer for now
    }

    class MovieViewHolder extends RecyclerView.ViewHolder {
        TextView title, year, rating, badge;
        ImageView poster;
        ProgressBar progress;
        View container;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView;
            title = itemView.findViewById(R.id.text_title);
            year = itemView.findViewById(R.id.text_year);
            rating = itemView.findViewById(R.id.text_rating);
            badge = itemView.findViewById(R.id.text_server_badge);
            poster = itemView.findViewById(R.id.image_poster);
            progress = itemView.findViewById(R.id.progress_watch);

            itemView.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null && pos < movies.size()) {
                    listener.onMovieClicked(movies.get(pos));
                }
            });

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && listener != null && pos < movies.size()) {
                        listener.onMovieFocused(movies.get(pos));
                    }
                    // Scale animation is handled by stateListAnimator in XML
                }
            });
        }

        public void bind(Movie movie) {
            if (title != null)
                title.setText(movie.getTitle());
            if (year != null)
                year.setText(movie.getYear());
            if (rating != null)
                rating.setText(movie.getRating());
            if (badge != null) {
                if (movie.getSourceBadge() != null && !movie.getSourceBadge().isEmpty()) {
                    badge.setText(movie.getSourceBadge());
                    badge.setVisibility(View.VISIBLE);
                } else {
                    badge.setVisibility(View.GONE);
                }
            }

            // Load poster image - assuming external library or placeholder for now
            // Just referencing logic, likely need Glide/Picasso
            if (poster != null) {
                // Placeholder: Set a color or default image
                poster.setBackgroundColor(0xFF333333);
            }
        }
    }

    class FooterViewHolder extends RecyclerView.ViewHolder {
        View container;

        public FooterViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView;

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLoadMoreClicked();
                }
            });
        }

        public void bind() {
            // Can set state like loading spinner here
        }
    }
}
