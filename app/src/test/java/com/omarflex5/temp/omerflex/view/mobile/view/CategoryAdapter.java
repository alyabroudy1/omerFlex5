package com.omarflex5.temp.omerflex.view.mobile.view;

// CategoryAdapter.java

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.view.mobile.MobileHomepageActivity;
import com.omerflex.view.mobile.entity.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {
    private Context context;
    private List<Category> movieAdapters;

    private OnMovieClickListener onMovieClickListener;

    public CategoryAdapter(Context context, OnMovieClickListener onMovieClickListener) {
        this.context = context;
        this.movieAdapters = new ArrayList<>();
        if (onMovieClickListener instanceof MobileHomepageActivity.MovieItemClickListener){
            ((MobileHomepageActivity.MovieItemClickListener) onMovieClickListener).setCategoryAdapter(this);
        }
        this.onMovieClickListener = onMovieClickListener;
    }


    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.mobile_item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        // Set the category name
        Category movieCategory = movieAdapters.get(position);
        holder.categoryNameTextView.setText(movieCategory.getCategoryName());

        // Set up the horizontal RecyclerView for movies
        holder.horizontalRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        holder.horizontalRecyclerView.setAdapter(movieCategory.getMovieAdapter());
    }

    @Override
    public int getItemCount() {
        return movieAdapters.size();
    }
    public int size() {
        return movieAdapters.size();
    }
    public void remove(Category category) {
        movieAdapters.remove(category);
    }

    public void remove(int categoryIndex) {
         movieAdapters.remove(categoryIndex);
    }

    public Category get(int index) {
        return movieAdapters.get(index);
    }


    // Add a new category and return the HorizontalMovieAdapter
    public HorizontalMovieAdapter addCategory(String title, ArrayList<Movie> movies) {
        Category category = new Category(title, new HorizontalMovieAdapter(context, movies, onMovieClickListener));
        // Create a new HorizontalMovieAdapter
        // Add the adapter and category name to the lists
        movieAdapters.add(category);
        // Notify the adapter that a new item has been inserted


        // Return the new HorizontalMovieAdapter for immediate use
        return category.getMovieAdapter();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        TextView categoryNameTextView;
        RecyclerView horizontalRecyclerView;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryNameTextView = itemView.findViewById(R.id.categoryNameTextView);
            horizontalRecyclerView = itemView.findViewById(R.id.horizontalRecyclerView);
        }
    }


}
