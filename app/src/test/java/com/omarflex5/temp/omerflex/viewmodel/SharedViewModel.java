package com.omarflex5.temp.omerflex.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;

public class SharedViewModel extends AndroidViewModel {
    private final MovieRepository movieRepository;
    public final LiveData<Movie> selectedMovie;

    private final MutableLiveData<Integer> selectedRowIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<Integer> selectedItemIndex = new MutableLiveData<>(-1);

    public SharedViewModel(@NonNull Application application) {
        super(application);
        movieRepository = MovieRepository.getInstance(application, ((OmerFlexApplication) application).getDatabase().movieDao());
        selectedMovie = movieRepository.getSelectedMovie();
    }

    public void updateMovie(Movie movie) {
        movieRepository.setSelectedMovie(movie);
        // Do not save the movie here, as it triggers a REPLACE and deletes history.
        // The repository should handle saving/updating when details are fetched.
        // movieRepository.saveMovie(movie);
    }

    public void setSelectedMovie(Movie movie) {
        movieRepository.setSelectedMovie(movie);
    }

    public void setSelectedPosition(int rowIndex, int itemIndex) {
        selectedRowIndex.setValue(rowIndex);
        selectedItemIndex.setValue(itemIndex);
    }

    public LiveData<Integer> getSelectedRowIndex() {
        return selectedRowIndex;
    }

    public LiveData<Integer> getSelectedItemIndex() {
        return selectedItemIndex;
    }
}
