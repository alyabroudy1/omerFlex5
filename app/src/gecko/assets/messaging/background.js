// Background script v8 - Added GET_COOKIES handler for redirected page cookie check
// Reset state on NEW_PAGE, fixed chunked transfer
// Uses sendNativeMessage instead of port for stateless messaging
console.log("[CookieExtractor] Background script started (v8 - GET_COOKIES support)");

let resultSent = false; // Flag to stop polling once we have HTML result
let cookieCheckInterval = null;

// Send message to native app via sendNativeMessage (stateless, no port)
function sendToNative(message) {
    // Don't block chunked messages - only check resultSent for final messages
    const isFinalMessage = (message.type === "CF_RESULT" || message.type === "CF_RESULT_END");

    if (resultSent && isFinalMessage) {
        console.log("[CookieExtractor] Result already sent, skipping final message...");
        return;
    }

    try {
        // Use sendNativeMessage - this is stateless and routes to current MessageDelegate
        browser.runtime.sendNativeMessage("GeckoCfBypass", message).then(() => {
            // Only set resultSent after final message (not START or CHUNK)
            if (isFinalMessage) {
                resultSent = true;
                console.log("[CookieExtractor] Sent via sendNativeMessage: type=" + message.type + " (FINAL)");

                // Stop polling after success
                if (cookieCheckInterval) {
                    clearInterval(cookieCheckInterval);
                    cookieCheckInterval = null;
                    console.log("[CookieExtractor] Stopped cookie polling after success");
                }
            } else {
                console.log("[CookieExtractor] Sent via sendNativeMessage: type=" + message.type);
            }
        }).catch(err => {
            console.error("[CookieExtractor] sendNativeMessage error:", err);
        });
    } catch (e) {
        console.error("[CookieExtractor] sendNativeMessage exception:", e);
    }
}

// Listen for messages from content scripts AND native
browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log("[CookieExtractor] Received from content script:", message.type);

    // Handle NEW_PAGE - reset state for new page load (important for expired cookie scenarios)
    if (message.type === "NEW_PAGE") {
        console.log("[CookieExtractor] NEW_PAGE received from: " + message.url);
        // Only reset if we already sent a result (prevents unnecessary resets)
        if (resultSent) {
            console.log("[CookieExtractor] Resetting state for new page (previous result was sent)");
            resetState();
        }
        return false;
    }

    // Handle GET_COOKIES - content script requesting current cookies on page load
    if (message.type === "GET_COOKIES") {
        // Return cookies asynchronously
        browser.cookies.getAll({}).then((cookies) => {
            let cookieString = cookies.map(c => `${c.name}=${c.value}`).join("; ");
            sendResponse({ cookies: cookieString });
        }).catch(err => {
            sendResponse({ cookies: "" });
        });
        return true; // Async response
    }

    // Forward all CF_RESULT types to native (including chunked messages)
    if (message.type === "CF_RESULT" ||
        message.type === "CF_RESULT_START" ||
        message.type === "CF_RESULT_CHUNK" ||
        message.type === "CF_RESULT_END") {
        sendToNative(message);
    }

    return false; // Sync response
});

// Reset function to clear state for new CF bypass session
function resetState() {
    console.log("[CookieExtractor] RESET - clearing state for new CF bypass session");
    resultSent = false;
    if (!cookieCheckInterval) {
        cookieCheckInterval = setInterval(checkAndSendCookies, 2000);
        console.log("[CookieExtractor] Cookie polling restarted");
    }
}

// IMPORTANT: Reset state on every script load (background script restarts = new session)
// This ensures fresh state when GeckoCfBypassActivity launches
resetState();

// Establish native port connection to receive RESET from Java
let nativePort = null;
try {
    nativePort = browser.runtime.connectNative("GeckoCfBypass");
    console.log("[CookieExtractor] Native port connected");

    nativePort.onMessage.addListener((message) => {
        console.log("[CookieExtractor] Native port message:", JSON.stringify(message));
        if (message && message.type === "RESET") {
            resetState();
        }
    });

    nativePort.onDisconnect.addListener(() => {
        console.log("[CookieExtractor] Native port disconnected");
        nativePort = null;
    });
} catch (e) {
    console.log("[CookieExtractor] connectNative failed (expected if not supported):", e);
}

// Broadcast cookie found to all tabs
// We do NOT use a one-off flag here because the first broadcast might hit 
// a challenge page (which filters it out). We need to keep broadcasting
// until resultSent is true (i.e., until we get the final HTML).
function checkAndSendCookies() {
    if (resultSent) {
        // Already got result, stop checking
        if (cookieCheckInterval) {
            clearInterval(cookieCheckInterval);
            cookieCheckInterval = null;
        }
        return;
    }

    browser.cookies.getAll({}).then((cookies) => {
        let clearanceCookie = cookies.find(c => c.name === "cf_clearance");
        if (clearanceCookie) {

            // Build cookie string from all cookies
            let cookieString = cookies.map(c => `${c.name}=${c.value}`).join("; ");

            // Send to ALL tabs to ensure reception
            browser.tabs.query({}).then((tabs) => {
                // Log only occasionally or if tabs found to avoid spam
                if (tabs.length > 0) {
                    // console.log("[CookieExtractor] Broadcasting cookies to " + tabs.length + " tabs");
                }

                for (let tab of tabs) {
                    browser.tabs.sendMessage(tab.id, {
                        type: "cookie_found",
                        cookies: cookieString
                    }).catch(err => {
                        // Ignore errors for tabs that don't have content script
                    });
                }
            });
        }
    }).catch(e => {
        console.error("[CookieExtractor] Cookie check error:", e);
    });
}

// Start polling every 2 seconds (will stop after success)
cookieCheckInterval = setInterval(checkAndSendCookies, 2000);

// Also listen for cookie changes for faster detection
try {
    browser.cookies.onChanged.addListener((changeInfo) => {
        if (!changeInfo.removed && changeInfo.cookie.name === "cf_clearance" && !resultSent) {
            console.log("[CookieExtractor] cf_clearance cookie changed, broadcasting immediately...");
            checkAndSendCookies();
        }
    });
} catch (e) {
    console.error("[CookieExtractor] onChanged listener error:", e);
}
// Resource Sniffing Logic
try {
    browser.webRequest.onBeforeRequest.addListener(
        function (details) {
            if (details.url) {
                const lowerUrl = details.url.toLowerCase();
                if (lowerUrl.includes(".m3u8") || lowerUrl.includes(".mp4") ||
                    lowerUrl.includes(".mpd") || lowerUrl.includes("/hls/") ||
                    lowerUrl.includes("manifest")) {

                    console.log("[CookieExtractor] Video resource detected: " + details.url);

                    // Send to native app
                    sendToNative({
                        type: "RESOURCE_LOADED",
                        url: details.url
                    });
                }
            }
        },
        { urls: ["<all_urls>"] },
        []
    );
} catch (e) {
    console.error("[CookieExtractor] webRequest listener error:", e);
}
