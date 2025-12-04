package com.omarflex5.data.source;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import java.util.List;

public interface MovieDataSource {
    List<Category> getCategories();

    List<Movie> getMoviesByCategory(String categoryId);

    Movie getMovieById(String movieId);
}
