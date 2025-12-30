package com.omarflex5.data.scraper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.omarflex5.R;
import com.omarflex5.engine.GeckoViewEngineFactory;
import com.omarflex5.ui.cursor.CursorLayout;
import android.view.KeyEvent;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GeckoView-based Cloudflare bypass activity.
 * Uses GeckoView with WebExtension to pass CF challenges that require BigInt.
 * Returns extracted cookies to the caller via activity result.
 */
public class GeckoCfBypassActivity extends AppCompatActivity {

    private static final String TAG = "GeckoCfBypass";

    public static final String EXTRA_TARGET_URL = "target_url";
    public static final String EXTRA_SERVER_ID = "server_id";
    public static final String RESULT_COOKIES = "cookies";
    public static final String RESULT_USER_AGENT = "user_agent";
    public static final String RESULT_HTML = "html";

    private CursorLayout cursorLayout;
    private GeckoView geckoView;
    private GeckoSession geckoSession;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private String targetUrl;
    private long serverId;
    private boolean isChallengePassed = false;
    private static boolean extensionInstalled = false;
    private String finalUrl = null; // Track resolved URL after redirects

    // Modern Chrome User-Agent for Cloudflare compatibility
    // NOTE: This will be overridden by WebConfig.getUserAgent(this) in
    // setupGeckoView()
    // to ensure consistency with OkHealth/WebViewScraperManager
    private String userAgent;

    public static Intent createIntent(Context context, String url, long serverId) {
        Intent intent = new Intent(context, GeckoCfBypassActivity.class);
        intent.putExtra(EXTRA_TARGET_URL, url);
        intent.putExtra(EXTRA_SERVER_ID, serverId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gecko_cf_bypass);

        geckoView = findViewById(R.id.geckoView);
        cursorLayout = findViewById(R.id.cursorLayout);
        if (cursorLayout != null) {
            cursorLayout.setTargetView(geckoView);
        }
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);

        // Close button handler
        findViewById(R.id.btn_close).setOnClickListener(v -> {
            log("User cancelled CF bypass");
            setResult(RESULT_CANCELED);
            finish();
        });

        targetUrl = getIntent().getStringExtra(EXTRA_TARGET_URL);
        serverId = getIntent().getLongExtra(EXTRA_SERVER_ID, -1);

        if (targetUrl == null) {
            log("ERROR: No target URL");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        log("Starting CF Bypass...");
        // Initialize unified User-Agent
        userAgent = com.omarflex5.util.WebConfig.getUserAgent(this);
        setupGeckoView();
    }

    private void setupGeckoView() {
        GeckoRuntime runtime = GeckoViewEngineFactory.getRuntime();

        // Note: We no longer clear cookies - GeckoView maintains cookies across
        // sessions
        // which speeds up subsequent CF challenges significantly

        geckoSession = new GeckoSession();
        geckoSession.open(runtime);
        geckoView.setSession(geckoSession);

        // Progress Delegate
        geckoSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                log("Page Started: " + url);
                finalUrl = url; // Track resolved URL for domain redirect detection
                runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                log("Page Finished (success=" + success + ")");
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));

                // Phase 2: If CF passed and we're on content page, request HTML
                if (cfPassed && success && pendingCookies != null) {
                    // Small delay to ensure page is rendered
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        requestHtmlExtraction();
                    }, 500);
                }
            }

            @Override
            public void onProgressChange(GeckoSession session, int progress) {
                runOnUiThread(() -> progressBar.setProgress(progress));
            }
        });

        // Content Delegate
        geckoSession.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                log("Title: " + title);
            }
        });

        // PromptDelegate to intercept CF_COOKIE_FOUND alerts from WebExtension
        // Content script sends cookies + SUCCESS marker (not full HTML to avoid alert
        // size limit)
        geckoSession.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session, AlertPrompt prompt) {
                String msg = prompt.message;

                // Handle cookie found from WebExtension
                // Format: CF_COOKIE_FOUND:USER_AGENT|||COOKIES|||SUCCESS
                if (msg != null && msg.startsWith("CF_COOKIE_FOUND:")) {
                    String payload = msg.substring("CF_COOKIE_FOUND:".length());
                    String[] parts = payload.split("\\|\\|\\|", 3);

                    String ua = parts.length > 0 ? parts[0] : userAgent;
                    String cookies = parts.length > 1 ? parts[1] : "";
                    String status = parts.length > 2 ? parts[2] : "";

                    log("Received alert: cookies="
                            + (cookies.length() > 50 ? cookies.substring(0, 50) + "..." : cookies) + ", status="
                            + status);

                    if (cookies.contains("cf_clearance")) {
                        // Check for SUCCESS marker (content script indicates we're on real page)
                        if ("SUCCESS".equals(status)) {
                            // We have cookies and content script confirmed we're on real page
                            // Don't send HTML - let the caller re-fetch with cookies
                            finishWithSuccess(cookies, ua, "");
                        } else {
                            // Still waiting for content page
                            log("Cookie found but waiting for SUCCESS marker...");
                            pendingCookies = cookies;
                            pendingUserAgent = ua;
                            cfPassed = true;
                        }
                    }
                    return GeckoResult.fromValue(prompt.dismiss());
                }

                // Handle HTML extraction request result (Phase 2 - legacy, may not be used)
                if (msg != null && msg.startsWith("CF_HTML_EXTRACT:")) {
                    String html = msg.substring("CF_HTML_EXTRACT:".length());
                    if (pendingCookies != null && html.length() > 1000) {
                        finishWithSuccess(pendingCookies, pendingUserAgent, html);
                    }
                    return GeckoResult.fromValue(prompt.dismiss());
                }

                return GeckoResult.fromValue(null);
            }
        });

        // Install WebExtension (if not installed) and set MessageDelegate for THIS
        // Activity
        // Note: MessageDelegate must be set for each Activity instance, not just on
        // first install
        final GeckoRuntime finalRuntime = runtime;
        final String EXPECTED_VERSION = "15.0"; // Must match manifest.json version

        if (!extensionInstalled) {
            // First install
            runtime.getWebExtensionController()
                    .installBuiltIn("resource://android/assets/messaging/")
                    .accept(
                            extension -> {
                                extensionInstalled = true;
                                log("WebExtension installed: " + extension.metaData.name + " v"
                                        + extension.metaData.version);
                                setupMessageDelegate(extension);
                            },
                            error -> log("Extension install error: " + error.getMessage()));
        } else {
            // Already installed - check version and reinstall if outdated
            runtime.getWebExtensionController().list().accept(
                    extensions -> {
                        org.mozilla.geckoview.WebExtension existingExt = null;
                        for (org.mozilla.geckoview.WebExtension ext : extensions) {
                            if (ext.metaData != null && "CookieExtractor".equals(ext.metaData.name)) {
                                existingExt = ext;
                                break;
                            }
                        }

                        if (existingExt != null) {
                            String currentVersion = existingExt.metaData.version;
                            log("Found extension: " + existingExt.metaData.name + " v" + currentVersion);

                            if (EXPECTED_VERSION.equals(currentVersion)) {
                                // Version matches, just set delegate
                                setupMessageDelegate(existingExt);
                            } else {
                                // Version mismatch - uninstall and reinstall
                                log("Extension version mismatch: have " + currentVersion + ", need " + EXPECTED_VERSION
                                        + " - reinstalling...");
                                final org.mozilla.geckoview.WebExtension toUninstall = existingExt;
                                finalRuntime.getWebExtensionController().uninstall(toUninstall).accept(
                                        unused -> {
                                            log("Old extension uninstalled, installing new version...");
                                            finalRuntime.getWebExtensionController()
                                                    .installBuiltIn("resource://android/assets/messaging/")
                                                    .accept(
                                                            extension -> {
                                                                log("WebExtension updated to v"
                                                                        + extension.metaData.version);
                                                                setupMessageDelegate(extension);
                                                            },
                                                            error -> log(
                                                                    "Extension update error: " + error.getMessage()));
                                        },
                                        error -> log("Extension uninstall error: " + error.getMessage()));
                            }
                        } else {
                            // Extension not found, install fresh
                            log("Extension not found, installing...");
                            extensionInstalled = false;
                            finalRuntime.getWebExtensionController()
                                    .installBuiltIn("resource://android/assets/messaging/")
                                    .accept(
                                            extension -> {
                                                extensionInstalled = true;
                                                log("WebExtension installed: " + extension.metaData.name + " v"
                                                        + extension.metaData.version);
                                                setupMessageDelegate(extension);
                                            },
                                            error -> log("Extension install error: " + error.getMessage()));
                        }
                    },
                    error -> log("Extension list error: " + error.getMessage()));
        }

        // Set User-Agent
        geckoSession.getSettings().setUserAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        geckoSession.getSettings().setUserAgentOverride(userAgent);

        // Load the target URL
        geckoSession.loadUri(targetUrl);
    }

    // Phase 2 state
    private boolean cfPassed = false;
    private String pendingCookies = null;
    private String pendingUserAgent = null;
    private String lastUrl = null;

    // Set up MessageDelegate for the given extension (for this Activity instance)
    private void setupMessageDelegate(org.mozilla.geckoview.WebExtension extension) {
        log("Setting up MessageDelegate for extension: " + extension.metaData.name);

        extension.setMessageDelegate(new org.mozilla.geckoview.WebExtension.MessageDelegate() {
            @Override
            public GeckoResult<Object> onMessage(
                    String nativeApp,
                    Object message,
                    org.mozilla.geckoview.WebExtension.MessageSender sender) {
                // This handles sendNativeMessage (one-off messages)
                log("Received one-off message from: " + nativeApp);
                handleExtensionMessage(message);
                return GeckoResult.fromValue(null);
            }

            @Override
            public void onConnect(org.mozilla.geckoview.WebExtension.Port port) {
                log("Native port connected: " + port.name);

                // Send RESET message to reset background script state for new CF bypass session
                try {
                    org.json.JSONObject resetMsg = new org.json.JSONObject();
                    resetMsg.put("type", "RESET");
                    port.postMessage(resetMsg);
                    log("Sent RESET message to background script");
                } catch (org.json.JSONException e) {
                    log("Failed to send RESET: " + e.getMessage());
                }

                // Set message delegate on the PORT to receive postMessage calls
                port.setDelegate(new org.mozilla.geckoview.WebExtension.PortDelegate() {
                    @Override
                    public void onPortMessage(Object message,
                            org.mozilla.geckoview.WebExtension.Port port) {
                        log("Received port message");
                        handleExtensionMessage(message);
                    }

                    @Override
                    public void onDisconnect(org.mozilla.geckoview.WebExtension.Port port) {
                        log("Port disconnected: " + port.name);
                    }
                });
            }
        }, "GeckoCfBypass");

    }

    // Handle messages from WebExtension (port or one-off)
    // Chunked transfer state
    private String chunkedCookies = null;
    private String chunkedUserAgent = null;
    private String chunkedUrl = null;
    private int totalChunks = 0;
    private String[] htmlChunks = null;
    private int receivedChunks = 0;

    private void handleExtensionMessage(Object message) {
        if (message instanceof org.json.JSONObject) {
            org.json.JSONObject json = (org.json.JSONObject) message;
            String type = json.optString("type", "");

            if ("CF_RESULT".equals(type)) {
                // Single message (small HTML)
                String cookies = json.optString("cookies", "");
                String html = json.optString("html", "");
                String ua = json.optString("userAgent", userAgent);
                String url = json.optString("url", "");

                log("CF_RESULT received: html=" + html.length() + " chars, url=" + url);

                if (cookies.contains("cf_clearance")) {
                    finishWithSuccess(cookies, ua, html);
                }
            } else if ("CF_RESULT_START".equals(type)) {
                // Start of chunked transfer
                chunkedCookies = json.optString("cookies", "");
                chunkedUserAgent = json.optString("userAgent", userAgent);
                chunkedUrl = json.optString("url", "");
                totalChunks = json.optInt("totalChunks", 0);
                int totalLength = json.optInt("totalLength", 0);

                htmlChunks = new String[totalChunks];
                receivedChunks = 0;

                log("CF_RESULT_START: expecting " + totalChunks + " chunks (" + totalLength + " chars total)");

            } else if ("CF_RESULT_CHUNK".equals(type)) {
                // Receive a chunk
                int chunkIndex = json.optInt("chunkIndex", -1);
                String data = json.optString("data", "");

                if (htmlChunks != null && chunkIndex >= 0 && chunkIndex < totalChunks) {
                    htmlChunks[chunkIndex] = data;
                    receivedChunks++;
                    log("CF_RESULT_CHUNK: received " + receivedChunks + "/" + totalChunks + " (" + data.length()
                            + " chars)");
                } else {
                    log("CF_RESULT_CHUNK: invalid chunkIndex=" + chunkIndex);
                }

            } else if ("CF_RESULT_END".equals(type)) {
                // End of chunked transfer - reassemble HTML
                log("CF_RESULT_END: reassembling " + receivedChunks + " chunks");

                if (htmlChunks != null && receivedChunks == totalChunks) {
                    StringBuilder sb = new StringBuilder();
                    for (String chunk : htmlChunks) {
                        if (chunk != null) {
                            sb.append(chunk);
                        }
                    }
                    String fullHtml = sb.toString();
                    log("Chunked transfer complete: " + fullHtml.length() + " chars reassembled");

                    if (chunkedCookies != null && chunkedCookies.contains("cf_clearance")) {
                        finishWithSuccess(chunkedCookies, chunkedUserAgent, fullHtml);
                    }
                } else {
                    log("CF_RESULT_END: incomplete transfer, received=" + receivedChunks + ", expected=" + totalChunks);
                }

                // Clear chunked state
                htmlChunks = null;
                chunkedCookies = null;
                chunkedUserAgent = null;
                chunkedUrl = null;
            }
        } else {
            log("Extension message is not JSONObject: " + (message != null ? message.getClass().getName() : "null"));
        }
    }

    private void requestHtmlExtraction() {
        if (geckoSession != null && pendingCookies != null) {
            log("Requesting HTML extraction from content page...");
            // Ask the content script to extract and alert HTML
            geckoSession.loadUri("javascript:alert('CF_HTML_EXTRACT:' + document.documentElement.outerHTML);");
        }
    }

    private void finishWithSuccess(String cookies, String userAgent, String html) {
        if (isFinishing() || isChallengePassed)
            return;

        isChallengePassed = true;

        // Write HTML to cache file to avoid Intent IPC size limit (~1MB)
        // Intent extras with large strings cause Binder overflow + device freeze
        String htmlFilePath = null;
        if (html != null && html.length() > 0) {
            try {
                java.io.File cacheDir = getCacheDir();
                java.io.File htmlFile = new java.io.File(cacheDir,
                        "cf_bypass_html_" + System.currentTimeMillis() + ".html");
                java.io.FileWriter writer = new java.io.FileWriter(htmlFile);
                writer.write(html);
                writer.close();
                htmlFilePath = htmlFile.getAbsolutePath();
                log("HTML written to cache file: " + htmlFilePath + " (" + html.length() + " chars)");
            } catch (java.io.IOException e) {
                log("Failed to write HTML to cache file: " + e.getMessage());
                // Fallback: truncate to 100KB (safe for Intent)
                final int SAFE_SIZE = 100 * 1024;
                if (html.length() > SAFE_SIZE) {
                    html = html.substring(0, SAFE_SIZE);
                    log("Fallback: HTML truncated to " + SAFE_SIZE + " chars for Intent");
                }
            }
        }

        log("SUCCESS: cf_clearance found with HTML (" + (html != null ? html.length() : 0) + " chars)");

        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_COOKIES, cookies);
        resultIntent.putExtra(RESULT_USER_AGENT, userAgent);
        // Pass file path instead of HTML content (safe for Intent IPC)
        if (htmlFilePath != null) {
            resultIntent.putExtra("html_file_path", htmlFilePath);
        } else if (html != null) {
            // Fallback for small HTML or if file write failed
            resultIntent.putExtra(RESULT_HTML, html);
        }
        // Include final URL for domain redirect detection
        if (finalUrl != null) {
            resultIntent.putExtra("final_url", finalUrl);
        }
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (cursorLayout != null && cursorLayout.dispatchKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void log(String message) {
        Log.d(TAG, message);
        if (!isFinishing() && tvStatus != null) {
            runOnUiThread(() -> {
                if (!isFinishing() && tvStatus != null) {
                    tvStatus.setText(message);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        // Stop any ongoing operations
        if (geckoSession != null) {
            try {
                geckoSession.stop();
                // Detach from view before closing
                geckoView.releaseSession();
                geckoSession.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing GeckoSession: " + e.getMessage());
            }
            geckoSession = null;
        }
        super.onDestroy();
    }
}
