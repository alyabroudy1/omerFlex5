package com.omarflex5.data.source;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.model.MovieActionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DummyDataProvider implements MovieDataSource {

    private final Map<String, Movie> movies = new HashMap<>();
    private final List<Category> categories = new ArrayList<>();
    private final Map<String, List<Movie>> categoryMovies = new HashMap<>();

    public DummyDataProvider() {
        initializeData();
    }

    private void initializeData() {
        // Create dummy movies with different action types

        // EXOPLAYER action - plays in native ExoPlayer
        Movie m1 = new Movie("1", "Stranger Things",
                "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces, and one strange little girl.",
                "https://picsum.photos/id/237/1920/1080",
                "https://picsum.photos/id/237/500/750",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "2016", "9.5", MovieActionType.EXOPLAYER);

        // EXOPLAYER action
        Movie m2 = new Movie("2", "The Crown",
                "Follows the political rivalries and romance of Queen Elizabeth II's reign and the events that shaped the second half of the twentieth century.",
                "https://picsum.photos/id/10/1920/1080",
                "https://picsum.photos/id/10/500/750",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "2016", "8.7", MovieActionType.EXOPLAYER);

        // BROWSER action - will open in browser (to be implemented)
        Movie m3 = new Movie("3", "Breaking Bad",
                "A high school chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine in order to secure his family's future.",
                "https://picsum.photos/id/20/1920/1080",
                "https://picsum.photos/id/20/500/750",
                "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                "https://example.com/breaking-bad",
                "2008", "9.5", MovieActionType.BROWSER);

        // DETAILS action - will show details (to be implemented)
        Movie m4 = new Movie("4", "Inception",
                "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
                "https://picsum.photos/id/25/1920/1080",
                "https://picsum.photos/id/25/500/750",
                "",
                "",
                "2010", "8.8", MovieActionType.DETAILS);

        // EXTEND action - extensible for future use
        Movie m5 = new Movie("5", "The Dark Knight",
                "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice.",
                "https://picsum.photos/id/30/1920/1080",
                "https://picsum.photos/id/30/500/750",
                "",
                "",
                "2008", "9.0", MovieActionType.SEARCH);

        movies.put(m1.getId(), m1);
        movies.put(m2.getId(), m2);
        movies.put(m3.getId(), m3);
        movies.put(m4.getId(), m4);
        movies.put(m5.getId(), m5);

        // Create categories
        List<String> trendingIds = Arrays.asList("1", "2", "3");
        List<String> actionIds = Arrays.asList("4", "5", "1");
        List<String> dramaIds = Arrays.asList("2", "3");

        Category c1 = new Category("c1", "Trending Now", trendingIds);
        Category c2 = new Category("c2", "Action & Adventure", actionIds);
        Category c3 = new Category("c3", "Drama", dramaIds);

        categories.add(c1);
        categories.add(c2);
        categories.add(c3);

        // Map movies to categories for easier lookup
        categoryMovies.put(c1.getId(), Arrays.asList(m1, m2, m3));
        categoryMovies.put(c2.getId(), Arrays.asList(m4, m5, m1));
        categoryMovies.put(c3.getId(), Arrays.asList(m2, m3));
    }

    @Override
    public List<Category> getCategories() {
        // Simulate network delay
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(categories);
    }

    @Override
    public List<Movie> getMoviesByCategory(String categoryId) {
        // Simulate network delay
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return categoryMovies.getOrDefault(categoryId, new ArrayList<>());
    }

    @Override
    public Movie getMovieById(String movieId) {
        return movies.get(movieId);
    }
}
