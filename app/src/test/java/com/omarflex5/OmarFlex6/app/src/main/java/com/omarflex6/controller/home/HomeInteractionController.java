package com.omarflex6.controller.home;

import android.content.Context;
import android.widget.Toast;

import com.omarflex6.controller.base.AbstractBaseController;
import com.omarflex6.controller.base.ItemClickController;
import com.omarflex6.data.model.Category;
import com.omarflex6.data.model.Movie;

import java.lang.ref.WeakReference;

public class HomeInteractionController extends AbstractBaseController {

    private final WeakReference<Context> contextRef;
    private final InteractionCallback callback;

    public interface InteractionCallback {
        void onCategorySelected(Category category);

        void onMovieFocused(Movie movie);

        void onLoadMoreRequested(); // New callback
    }

    public HomeInteractionController(Context context, InteractionCallback callback) {
        this.contextRef = new WeakReference<>(context);
        this.callback = callback;
    }

    public ItemClickController<Category> getCategoryClickHandler() {
        return new ItemClickController<Category>() {
            @Override
            public void onItemClick(Category item) {
                if (callback != null) {
                    callback.onCategorySelected(item);
                }
            }

            @Override
            public void onItemFocus(Category item) {
                // Optional: Handle focus on category
                if (callback != null) {
                    callback.onCategorySelected(item);
                }
            }
        };
    }

    public ItemClickController<Movie> getMovieClickHandler() {
        return new ItemClickController<Movie>() {
            @Override
            public void onItemClick(Movie item) {
                Context context = contextRef.get();
                if (context != null) {
                    Toast.makeText(context, "Clicked: " + item.getTitle(), Toast.LENGTH_SHORT).show();
                    // Navigate to details activity here
                }
            }

            @Override
            public void onItemFocus(Movie item) {
                if (callback != null) {
                    callback.onMovieFocused(item);
                }
            }
        };
    }

    public Runnable getLoadMoreClickHandler() {
        return () -> {
            if (callback != null) {
                callback.onLoadMoreRequested();
            }
        };
    }
}
