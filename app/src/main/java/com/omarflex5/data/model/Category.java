package com.omarflex5.data.model;

import java.util.List;
import java.util.Objects;

public class Category {
    private final String id;
    private final String name;
    private final List<String> movieIds;

    public Category(String id, String name, List<String> movieIds) {
        this.id = id;
        this.name = name;
        this.movieIds = movieIds;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getMovieIds() {
        return movieIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Category category = (Category) o;
        return Objects.equals(id, category.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
