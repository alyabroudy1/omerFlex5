package com.omarflex5.ui.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.omarflex5.data.repository.MovieRepository;

public class HomeViewModelFactory implements ViewModelProvider.Factory {

    private final android.app.Application application;

    public HomeViewModelFactory(android.app.Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
