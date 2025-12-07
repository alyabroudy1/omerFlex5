package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.app.DetailsSupportFragmentBackgroundController;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieType;
import com.omerflex.server.Util;
import com.omerflex.view.listener.MovieItemViewClickedListener;
import com.omerflex.view.viewConroller.VideoDetailsFragmentController;
import com.omerflex.viewmodel.SharedViewModel;

import androidx.leanback.widget.RowPresenter;
import androidx.lifecycle.ViewModelProvider;

public class VideoDetailsFragment extends DetailsSupportFragment {

    private static final String TAG = "VideoDetailsFragment";

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private Movie mSelectedMovie;
    private ArrayObjectAdapter mAdapter;
    private ListRow listRow; // contains the header and sublist
    private ClassPresenterSelector mPresenterSelector;
    private DetailsSupportFragmentBackgroundController mDetailsBackground;
    private ProgressDialog mProgressDialog;
    private ArrayObjectAdapter listRowAdapter;
    private DetailsOverviewRow row;
    private VideoDetailsFragmentController detailsViewControl;
    private SharedViewModel sharedViewModel;

    public static final int ACTION_OPEN_DETAILS_ACTIVITY = 1;
    public static final int ACTION_OPEN_EXTERNAL_ACTIVITY = 2;
    public static final int ACTION_OPEN_NO_ACTIVITY = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initialize();
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated: ");
//        sharedViewModel.selectedMovie.observe(getViewLifecycleOwner(), movie -> {
//            if (movie != null) {
////                updateOverviewUI(movie);
//            }
//        });
        updateOverviewUI(mSelectedMovie);
    }

    private void initialize() {
        setRetainInstance(true);
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

//        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        mSelectedMovie = Util.recieveSelectedMovie(activity.getIntent());
        if (mSelectedMovie == null) {
            return;
        }

        mPresenterSelector = new ClassPresenterSelector();
        mAdapter = new ArrayObjectAdapter(mPresenterSelector);
        setupUI(); // it sets the ListRow for the sublist

        detailsViewControl = new VideoDetailsFragmentController(this, mAdapter, row, mSelectedMovie, listRow, sharedViewModel);
        detailsViewControl.fetchDetails();
    }

    private void setupUI() {
        mDetailsBackground = new DetailsSupportFragmentBackgroundController(this);
        mProgressDialog = new ProgressDialog(getContext());
        mProgressDialog.setTitle("جاري التحميل...");
        mProgressDialog.setMessage("الرجاء الانتظار...");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(true);

        setupDetailsOverviewRow();
        setupDetailsOverviewRowPresenter();
        setupRelatedMovieListRow();
        setAdapter(mAdapter);
        initializeBackground(mSelectedMovie);
        setOnItemViewClickedListener(new MovieItemViewClickedListener(this, mAdapter));
    }

    private void setupDetailsOverviewRow() {
        row = new DetailsOverviewRow(mSelectedMovie);
        row.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.default_background_2));
        int width = convertDpToPixel(getContext(), DETAIL_THUMB_WIDTH);
        int height = convertDpToPixel(getContext(), DETAIL_THUMB_HEIGHT);
        Glide.with(this)
                .load(mSelectedMovie.getCardImageUrl())
                .fitCenter()
                .error(R.drawable.default_background_2)
                .into(new SimpleTarget<Drawable>(width, height) {
                    @Override
                    public void onResourceReady(@NonNull Drawable drawable, @Nullable Transition<? super Drawable> transition) {
                        row.setImageDrawable(drawable);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }
                });
        mAdapter.add(row);
    }

    private void setupDetailsOverviewRowPresenter() {
        FullWidthDetailsOverviewRowPresenter detailsPresenter = new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        detailsPresenter.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.selected_background));

        FullWidthDetailsOverviewSharedElementHelper sharedElementHelper = new FullWidthDetailsOverviewSharedElementHelper();
        sharedElementHelper.setSharedElementEnterTransition(requireActivity(), DetailsActivity.SHARED_ELEMENT_NAME);

        detailsPresenter.setListener(sharedElementHelper);
        detailsPresenter.setParticipatingEntranceTransition(true);
        detailsPresenter.setOnActionClickedListener(action ->
                detailsViewControl.handleActionClick(mSelectedMovie, action, (ArrayObjectAdapter) listRow.getAdapter()));

        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void setupRelatedMovieListRow() {
        String headerTitle = getResources().getString(R.string.related_movie_row_header_resolutions);
//        if (mSelectedMovie.getState() == Movie.GROUP_OF_GROUP_STATE) {
//            headerTitle = getResources().getString(R.string.related_movie_row_header_session);
//        } else if (mSelectedMovie.getState() == Movie.GROUP_STATE) {
//            headerTitle = getResources().getString(R.string.related_movie_row_header_series);
//        }
        if (mSelectedMovie.getType() == MovieType.SERIES || mSelectedMovie.getType() == MovieType.COLLECTION) {
            headerTitle = getResources().getString(R.string.related_movie_row_header_session);
        } else if (mSelectedMovie.getType() == MovieType.SEASON) {
            headerTitle = getResources().getString(R.string.related_movie_row_header_series);
        }
        HeaderItem header = new HeaderItem(0, headerTitle);
        listRow = new ListRow(header, new ArrayObjectAdapter(new CardPresenter()));
        mAdapter.add(listRow);

        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
    }

    public void initializeBackground(Movie data) {
        mDetailsBackground.enableParallax();
        Glide.with(this)
                .asBitmap()
                .centerCrop()
                .error(R.drawable.default_background_2)
                .load(data.getBackgroundImageUrl())
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                        mDetailsBackground.setCoverBitmap(bitmap);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        detailsViewControl.handleActivityResult(requestCode, resultCode, data);
    }

    public void showProgressDialog(boolean uiThread) {
        if (uiThread) {
            getActivity().runOnUiThread(() -> {
                if (mProgressDialog != null) {
                    mProgressDialog.show();
                }
            });
        } else {
            if (mProgressDialog != null) {
                mProgressDialog.show();
            }
        }
    }

    public void hideProgressDialog(boolean uiThread, String message) {
        if (uiThread) {
            getActivity().runOnUiThread(() -> {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
                if (message != null) {
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            if (message != null) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int convertDpToPixel(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    public void addRelatedMoviesRow(java.util.List<Movie> movies) {
        listRowAdapter.addAll(0, movies);
    }

    public void updateOverviewUI(Movie movie) {
        Log.d(TAG, "updateOverviewUI: ");
        if (row != null) {
            row.setItem(movie);
            mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
            initializeBackground(movie);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        detailsViewControl.onResume();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        detailsViewControl.onSaveInstanceState(outState);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (detailsViewControl != null) {
            detailsViewControl.onDetach();
        }
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }
}