// Background script v5 - Uses sendNativeMessage instead of port for stateless messaging
// This ensures messages reach the current Activity's MessageDelegate
// v5: Added RESET handling to support multiple GeckoCfBypassActivity sessions
console.log("[CookieExtractor] Background script started (v5 - with RESET support)");

let resultSent = false; // Flag to stop polling once we have HTML result
let cookieCheckInterval = null;

// Send message to native app via sendNativeMessage (stateless, no port)
function sendToNative(message) {
    if (resultSent) {
        console.log("[CookieExtractor] Result already sent, skipping...");
        return;
    }

    try {
        // Use sendNativeMessage - this is stateless and routes to current MessageDelegate
        browser.runtime.sendNativeMessage("GeckoCfBypass", message).then(() => {
            resultSent = true;
            console.log("[CookieExtractor] Sent via sendNativeMessage: type=" + message.type);

            // Stop polling after success
            if (cookieCheckInterval) {
                clearInterval(cookieCheckInterval);
                cookieCheckInterval = null;
                console.log("[CookieExtractor] Stopped cookie polling after success");
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

    if (message.type === "CF_RESULT") {
        // Forward to native app via sendNativeMessage (stateless)
        sendToNative(message);
    }

    return false; // Sync response
});

// Listen for native messages (including RESET)
browser.runtime.onConnectNative && browser.runtime.onMessageExternal && console.log("[CookieExtractor] External listeners available");

// Reset function to clear state for new CF bypass session
function resetState() {
    console.log("[CookieExtractor] RESET received - resetting state for new CF bypass session");
    resultSent = false;
    if (!cookieCheckInterval) {
        cookieCheckInterval = setInterval(checkAndSendCookies, 2000);
        console.log("[CookieExtractor] Cookie polling restarted");
    }
}

// Register as message handler to receive RESET from native
// The native app will call this via port message when GeckoCfBypassActivity starts
try {
    browser.runtime.onConnect.addListener((port) => {
        console.log("[CookieExtractor] Port connected: " + port.name);
        port.onMessage.addListener((message) => {
            console.log("[CookieExtractor] Port message received:", message);
            if (message.type === "RESET") {
                resetState();
            }
        });
    });
} catch (e) {
    console.log("[CookieExtractor] onConnect not available:", e);
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
