package com.omarflex5.temp.omerflex.view.mobile.view;

// HorizontalMovieAdapter.java

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.omerflex.R;
import com.omerflex.entity.Movie;

import java.util.ArrayList;
import java.util.List;

public class HorizontalMovieAdapter extends RecyclerView.Adapter<HorizontalMovieAdapter.MovieViewHolder> {
    private Context context;
    private List<Movie> movieList;
    public static String TAG = "HorizontalMovieAdapter";

    private OnMovieClickListener onMovieClickListener;

    public HorizontalMovieAdapter(Context context, List<Movie> movieList, OnMovieClickListener onMovieClickListener) {
        this.context = context;
        this.movieList = movieList;
        this.onMovieClickListener = onMovieClickListener;
    }

    @NonNull
    @Override
    public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder: " + parent);
        View view = LayoutInflater.from(context).inflate(R.layout.mobile_item_movie, parent, false);
        return new MovieViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder: ");
        Movie movie = movieList.get(position);
        // Load movie image using Glide or any image loading library
        Glide.with(context).load(movie.getCardImageUrl()).into(holder.movieImageView);

        // Set an OnClickListener to open the detailed activity when a movie card is clicked
        holder.movieTitleTextView.setText(movie.getTitle());
        holder.movieRatingTextView.setText(movie.getRate());
        holder.movieStudioTextView.setText(movie.getStudio());

        // Set an OnClickListener to open the detailed activity when a movie card is clicked
        HorizontalMovieAdapter horizontalMovieAdapter = this;
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onMovieClickListener != null) {
                    onMovieClickListener.onMovieClick(movie, position, horizontalMovieAdapter);  // Trigger the callback
                }
            }
        });
    }

    public List<Movie> getMovieList() {
        return movieList;
    }

    @Override
    public int getItemCount() {
        return movieList.size();
    }

    public boolean addAll(ArrayList<Movie> movies) {
        return movieList.addAll(movies);
    }


    public static class MovieViewHolder extends RecyclerView.ViewHolder {
        ImageView movieImageView;
        TextView movieTitleTextView;
        TextView movieRatingTextView;
        TextView movieStudioTextView;

        public MovieViewHolder(@NonNull View itemView) {
            super(itemView);
            movieImageView = itemView.findViewById(R.id.movieImageView);
            movieTitleTextView = itemView.findViewById(R.id.movieTitleTextView);
            movieRatingTextView = itemView.findViewById(R.id.movieRatingTextView);
            movieStudioTextView = itemView.findViewById(R.id.movieStudioTextView);
        }
    }
}
