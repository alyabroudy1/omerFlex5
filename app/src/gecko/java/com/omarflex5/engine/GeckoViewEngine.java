package com.omarflex5.engine;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.webkit.ValueCallback;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.StorageController;

import java.util.HashMap;
import java.util.Map;

/**
 * GeckoView implementation of IWebEngine.
 * Wraps Mozilla's GeckoView for the gecko flavor.
 * Provides better Cloudflare bypass capabilities than standard WebView.
 */
public class GeckoViewEngine implements IWebEngine {

    private static final String TAG = "GeckoViewEngine";

    private final GeckoView geckoView;
    private final GeckoSession session;
    private final Context context;
    private IWebEngineClient client;
    private String currentUrl;
    private String currentTitle;
    private WebEngineSettings engineSettings;
    private boolean canGoBackState = false;
    private boolean canGoForwardState = false;
    private CookieExtractionCallback cookieCallback;
    private static boolean extensionInstalled = false;

    /**
     * Callback interface for WebExtension cookie extraction.
     * Called when cf_clearance cookie is extracted via the alert bridge.
     */
    public interface CookieExtractionCallback {
        void onCookiesExtracted(String cookies, String userAgent);
    }

    /**
     * Set a callback to receive extracted cookies from the WebExtension.
     */
    public void setCookieExtractionCallback(CookieExtractionCallback callback) {
        this.cookieCallback = callback;
    }

    public GeckoViewEngine(Context context, GeckoRuntime runtime) {
        this.context = context;
        this.geckoView = new GeckoView(context);

        // Create session with settings
        GeckoSessionSettings.Builder settingsBuilder = new GeckoSessionSettings.Builder();
        settingsBuilder.usePrivateMode(false);
        settingsBuilder.useTrackingProtection(false);

        this.session = new GeckoSession(settingsBuilder.build());
        session.open(runtime);
        geckoView.setSession(session);

        // Apply default settings
        applySettings(WebEngineSettings.createDefault());

        // Set up delegates
        setupDelegates();

        // Install WebExtension for cookie extraction (only once per runtime)
        installWebExtension(runtime);
    }

    /**
     * Install the built-in WebExtension for extracting HttpOnly cookies
     * (cf_clearance).
     * The extension uses browser.cookies API and sends results via alert() bridge.
     */
    private void installWebExtension(GeckoRuntime runtime) {
        if (extensionInstalled) {
            Log.d(TAG, "WebExtension already installed, skipping");
            return;
        }

        runtime.getWebExtensionController()
                .installBuiltIn("resource://android/assets/messaging/")
                .accept(
                        extension -> {
                            extensionInstalled = true;
                            Log.d(TAG, "WebExtension installed: " + extension.metaData.name);
                        },
                        error -> {
                            Log.e(TAG, "WebExtension install failed: " + error.getMessage());
                        });
    }

    private void setupDelegates() {
        // Navigation delegate for page lifecycle events
        // Note: Using v101-compatible method signatures
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(GeckoSession session, String url) {
                currentUrl = url;
                Log.d(TAG, "Location changed: " + url);
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession session, String uri, WebRequestError error) {
                Log.e(TAG, "Load error: " + uri + " - " + error.code);
                if (client != null) {
                    client.onError(GeckoViewEngine.this, error.code, error.toString(), uri);
                }
                return null;
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession session, GeckoSession.NavigationDelegate.LoadRequest request) {
                String url = request.uri;
                Log.d(TAG, "Load request: " + url);

                if (client != null) {
                    boolean override = client.shouldOverrideUrlLoading(GeckoViewEngine.this, url);
                    if (override) {
                        return GeckoResult.deny();
                    }
                }

                // Header Persistence for Redirects (v101 may not have isRedirect - check and
                // handle)
                try {
                    if (request.isRedirect && (currentReferrer != null || currentHeaders != null)) {
                        Log.d(TAG, "Intercepting redirect to persist headers: " + url);
                        GeckoSession.Loader loader = new GeckoSession.Loader().uri(url);

                        if (currentReferrer != null) {
                            loader.referrer(currentReferrer);
                        }

                        if (currentHeaders != null && !currentHeaders.isEmpty()) {
                            loader.additionalHeaders(currentHeaders);
                        }

                        session.load(loader);
                        return GeckoResult.deny();
                    }
                } catch (NoSuchFieldError e) {
                    // v101 may not have isRedirect field
                    Log.d(TAG, "isRedirect not available in this GeckoView version");
                }

                return GeckoResult.allow();
            }

            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                canGoBackState = canGoBack;
            }

            @Override
            public void onCanGoForward(GeckoSession session, boolean canGoForward) {
                canGoForwardState = canGoForward;
            }
        });

        // Progress delegate for loading state
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                currentUrl = url;
                Log.d(TAG, "Page started: " + url);
                if (client != null) {
                    client.onPageStarted(GeckoViewEngine.this, url, null);
                }
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                Log.d(TAG, "Page stopped, success: " + success);
                if (client != null && currentUrl != null) {
                    client.onPageFinished(GeckoViewEngine.this, currentUrl);
                }
            }

            @Override
            public void onProgressChange(GeckoSession session, int progress) {
                if (client != null) {
                    client.onProgressChanged(GeckoViewEngine.this, progress);
                }
            }

            @Override
            public void onSecurityChange(GeckoSession session,
                    GeckoSession.ProgressDelegate.SecurityInformation securityInfo) {
                // Not used for now
            }

            @Override
            public void onSessionStateChange(GeckoSession session, GeckoSession.SessionState sessionState) {
                // Not used for now
            }
        });

        // Content delegate for title changes
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                currentTitle = title;
                if (client != null) {
                    client.onTitleChanged(GeckoViewEngine.this, title);
                }
            }

            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                // Handle fullscreen if needed
            }

            @Override
            public void onContextMenu(GeckoSession session, int screenX, int screenY,
                    GeckoSession.ContentDelegate.ContextElement element) {
                // Handle context menu if needed
            }

            @Override
            public void onCrash(GeckoSession session) {
                Log.e(TAG, "GeckoSession crashed!");
            }

            @Override
            public void onFirstContentfulPaint(GeckoSession session) {
                Log.d(TAG, "First contentful paint");
            }
        });

        // PromptDelegate to intercept CF_COOKIE_FOUND alerts from WebExtension
        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session, AlertPrompt prompt) {
                String msg = prompt.message;
                if (msg != null && msg.startsWith("CF_COOKIE_FOUND:")) {
                    Log.d(TAG, "Intercepted CF_COOKIE_FOUND alert from WebExtension");
                    String payload = msg.substring("CF_COOKIE_FOUND:".length());

                    // Parse USER_AGENT|||COOKIES format
                    String[] parts = payload.split("\\|\\|\\|");
                    String ua = parts.length > 1 ? parts[0] : null;
                    String cookies = parts.length > 1 ? parts[1] : payload;

                    Log.d(TAG, "Extracted cookies: "
                            + (cookies.length() > 50 ? cookies.substring(0, 50) + "..." : cookies));

                    // Notify callback
                    if (cookieCallback != null) {
                        cookieCallback.onCookiesExtracted(cookies, ua);
                    }

                    // Dismiss the alert so user doesn't see it
                    return GeckoResult.fromValue(prompt.dismiss());
                }
                // Let other alerts through
                return GeckoResult.fromValue(null);
            }
        });
    }

    @Override
    public View getView() {
        return geckoView;
    }

    @Override
    public void loadUrl(String url) {
        session.loadUri(url);
    }

    private String currentReferrer;
    private Map<String, String> currentHeaders;

    @Override
    public void loadUrl(String url, Map<String, String> headers) {
        // Store headers for persistence across redirects
        this.currentHeaders = headers != null ? new HashMap<>(headers) : null;
        this.currentReferrer = headers != null ? headers.get("Referer") : null;
        if (this.currentHeaders != null) {
            this.currentHeaders.remove("Referer"); // Remove Referer from additional headers map
        }

        // Use GeckoSession.Loader to pass headers
        // Note: Referer is a forbidden header in additionalHeaders, use referrer()
        // instead
        if (headers != null && !headers.isEmpty()) {
            GeckoSession.Loader loader = new GeckoSession.Loader().uri(url);

            // Handle Referer specially - use referrer() method
            String referer = headers.get("Referer");
            if (referer != null) {
                loader = loader.referrer(referer);
                Log.d(TAG, "Setting referrer via loader.referrer(): " + referer);
            }

            // Add other headers (excluding Referer which is forbidden)
            Map<String, String> otherHeaders = new HashMap<>(headers);
            otherHeaders.remove("Referer");
            if (!otherHeaders.isEmpty()) {
                loader = loader.additionalHeaders(otherHeaders);
                Log.d(TAG, "Setting additional headers: " + otherHeaders);
            }

            session.load(loader);
            Log.d(TAG, "Loading URL with referrer: " + url);
        } else {
            session.loadUri(url);
        }
    }

    @Override
    public void loadHtml(String html, String baseUrl) {
        // Use load with data URI as alternative
        session.load(new GeckoSession.Loader().data(html, "text/html"));
    }

    @Override
    public void evaluateJavascript(String script, ValueCallback<String> callback) {
        // GeckoView requires WebExtensions for JS evaluation in newer versions
        // Using loadUri with javascript: protocol as workaround for basic cases
        if (callback != null) {
            // For now, we can't easily get results back without WebExtensions
            Log.w(TAG, "evaluateJavascript limited in GeckoView - no result callback");
            callback.onReceiveValue(null);
        }
        session.loadUri("javascript:" + script);
    }

    @Override
    public void addJavascriptInterface(Object object, String name) {
        // GeckoView uses a different mechanism - MessageDelegate
        // For now, log a warning. Full implementation would use WebExtensions or
        // MessageDelegate.
        Log.w(TAG, "addJavascriptInterface not directly supported in GeckoView. Use MessageDelegate instead.");
    }

    @Override
    public void setEngineClient(IWebEngineClient client) {
        this.client = client;
    }

    @Override
    public void applySettings(WebEngineSettings settings) {
        this.engineSettings = settings;

        // Apply settings to GeckoSession
        GeckoSessionSettings geckoSettings = session.getSettings();

        // JavaScript
        // Note: API method usage based on availability.
        // setJavaScriptEnabled -> likely setAllowJavascript
        geckoSettings.setAllowJavascript(settings.isJavaScriptEnabled());

        // Autoplay
        // setAllowMediaPlayWithoutUserGesture is missing in this version.
        // We rely on setUserAgentOverride to spoof a desktop UA which might help,
        // or we need to set prefs globally.
        // geckoSettings.setSuspendMediaWhenInactive(true); // default

        // Handle User Agent
        if (settings.getUserAgent() != null) {
            geckoSettings.setUserAgentOverride(settings.getUserAgent());
        }

        Log.d(TAG, "Settings applied: JS=" + settings.isJavaScriptEnabled() +
                ", Autoplay=" + !settings.isMediaPlaybackRequiresUserGesture() +
                ", UA=" + settings.getUserAgent());
    }

    @Override
    public String getUserAgent() {
        // GeckoView's default UA or custom from settings
        if (engineSettings != null && engineSettings.getUserAgent() != null) {
            return engineSettings.getUserAgent();
        }
        return "Mozilla/5.0 (Android; Mobile; rv:148.0) Gecko/148.0 Firefox/148.0";
    }

    @Override
    public String getCurrentUrl() {
        return currentUrl;
    }

    @Override
    public boolean canGoBack() {
        return canGoBackState;
    }

    @Override
    public void goBack() {
        session.goBack();
    }

    @Override
    public void stopLoading() {
        session.stop();
    }

    @Override
    public void reload() {
        session.reload();
    }

    @Override
    public void destroy() {
        session.close();
    }

    /**
     * Get the underlying GeckoSession for advanced usage.
     * Use sparingly - prefer the IWebEngine interface.
     */
    public GeckoSession getSession() {
        return session;
    }

    /**
     * Get the underlying GeckoView for advanced usage.
     */
    public GeckoView getGeckoView() {
        return geckoView;
    }
}
