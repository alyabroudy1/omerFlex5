package com.omarflex5.temp.omerflex.providers;

import android.content.Context;
import android.util.Log;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaItemStatus;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaSessionStatus;

public class MediaRouterHelper {
    private static final String TAG = "MediaRouterHelper";

    private Context context;
    private MediaRouter mediaRouter;
    private MediaRouteSelector routeSelector;
    private MediaRouter.Callback routerCallback;
    private MediaRouter.RouteInfo selectedRoute;

    public MediaRouterHelper(Context context) {
        this.context = context;
        initializeMediaRouter();
    }

    private void initializeMediaRouter() {
        mediaRouter = MediaRouter.getInstance(context);

        // Create a route selector for remote playback
        routeSelector = new MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build();

        routerCallback = new MediaRouter.Callback() {
            @Override
            public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
                Log.d(TAG, "Route selected: " + route.getName());
                selectedRoute = route;
            }

            @Override
            public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
                Log.d(TAG, "Route unselected: " + route.getName());
                selectedRoute = null;
            }
        };
    }

    public void startDiscovery() {
        mediaRouter.addCallback(routeSelector, routerCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        Log.d(TAG, "Started media route discovery");
    }

    public void stopDiscovery() {
        mediaRouter.removeCallback(routerCallback);
        Log.d(TAG, "Stopped media route discovery");
    }

    public MediaRouter.RouteInfo getSelectedRoute() {
        return selectedRoute;
    }

    public boolean isRemotePlaybackAvailable() {
        return mediaRouter.getSelectedRoute() != null &&
                mediaRouter.getSelectedRoute().supportsControlCategory(
                        MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
    }
}