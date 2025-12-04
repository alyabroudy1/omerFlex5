package com.omarflex5.ui.home.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.omarflex5.R;
import com.omarflex5.data.model.Movie;

import java.util.ArrayList;
import java.util.List;

public class MovieCardAdapter extends RecyclerView.Adapter<MovieCardAdapter.MovieViewHolder> {

    private List<Movie> movies = new ArrayList<>();
    private OnMovieListener listener;

    public interface OnMovieListener {
        void onMovieSelected(Movie movie);

        void onMovieClicked(Movie movie);
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
        notifyDataSetChanged();
    }

    public void setListener(OnMovieListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie_card, parent, false);
        return new MovieViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
        holder.bind(movies.get(position));
    }

    @Override
    public int getItemCount() {
        return movies.size();
    }

    class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView posterImage;
        View cardView;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            posterImage = itemView.findViewById(R.id.image_poster);
            cardView = itemView.findViewById(R.id.card_view);

            cardView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        // Scale up
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                        if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                            listener.onMovieSelected(movies.get(getAdapterPosition()));
                        }
                    } else {
                        // Scale down
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    }
                }
            });

            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                        listener.onMovieClicked(movies.get(getAdapterPosition()));
                    }
                }
            });
        }

        public void bind(Movie movie) {
            Glide.with(itemView.getContext())
                    .load(movie.getPosterUrl())
                    .centerCrop()
                    .into(posterImage);
        }
    }
}
