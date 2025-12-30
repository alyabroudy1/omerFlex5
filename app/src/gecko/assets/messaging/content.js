// Content script - receives cookies from background and sends full result via messaging
// v5 - Added RETRY logic and smarter challenge detection
console.log("[CookieExtractor] Content script started (v5) on: " + window.location.href);

let resultSent = false; // Flag to prevent duplicate sends

// Check if this is a CF challenge frame
function isChallengeFrame() {
    if (window.self !== window.top) {
        try {
            // Check if we can access top (same origin)
            var ignored = window.top.location.href;
        } catch (e) {
            // Cross-origin iframe, likely an ad or tracker
            return true;
        }
    }
    const url = window.location.href;
    if (url.includes("challenge-platform") || url.includes("turnstile") || url.includes("challenges.cloudflare.com")) {
        return true;
    }
    return false;
}

// Check if this is an ad/tracker page
function isAdPage() {
    const url = window.location.href;
    const adDomains = [
        "crumpetprankerstench.com", "madurird.com", "googletagmanager.com",
        "googlesyndication.com", "doubleclick.net"
    ];
    for (const domain of adDomains) {
        if (url.includes(domain)) return true;
    }
    return false;
}

// Check if this is the actual target page
function isTargetPage() {
    const url = window.location.href;
    const targetDomains = [
        "faselhds.biz", "faselhd", "arabseed", "akwam", "shahid4u", "egy.best", "egybest"
    ];
    for (const domain of targetDomains) {
        if (url.includes(domain)) return true;
    }
    return false;
}

// Check HTML content for CF challenge indicators
function isChallengePage(html) {
    if (!html) return true;

    // Check TITLE specifically for the Cloudflare "Just a moment" text
    if (document.title.includes("Just a moment") || document.title.includes("لحظة")) {
        return true;
    }

    // Check for specific Cloudflare challenge form ID
    if (html.includes('id="challenge-form"') || html.includes('id="cf-challenge-running"')) {
        return true;
    }

    // Fallback: Extremely short content (likely error or empty body)
    // 1000 chars is very conservative for a real page
    if (html.length < 1000) {
        return true;
    }

    return false;
}

// Wait for page to be fully loaded
function waitForPageLoad(callback) {
    if (document.readyState === 'complete') {
        callback();
    } else {
        console.log("[CookieExtractor] Waiting for window.load...");
        window.addEventListener('load', () => {
            console.log("[CookieExtractor] window.load fired");
            callback();
        }, { once: true });
        // Fail-safe timeout
        setTimeout(() => {
            if (document.readyState !== 'complete') {
                console.log("[CookieExtractor] Timeout waiting for load, proceeding anyway state=" + document.readyState);
                callback();
            }
        }, 3000);
    }
}

// Send full result (cookies + HTML) to native via background script
function sendResultToNative(cookies, retryCount = 0) {
    if (resultSent) return;

    // Filter checks
    if (isChallengeFrame()) {
        console.log("[CookieExtractor] Skipping - challenge frame");
        return;
    }
    if (isAdPage()) {
        console.log("[CookieExtractor] Skipping - ad page: " + window.location.host);
        return;
    }
    if (!isTargetPage()) {
        // console.log("[CookieExtractor] Skipping - non-target domain: " + window.location.host);
        return;
    }

    const html = document.documentElement.outerHTML || "";

    // Double-check content validity using improved check
    if (isChallengePage(html)) {
        console.log("[CookieExtractor] Page looks like challenge/loading (len=" + html.length + ", title='" + document.title + "'). Retry " + (retryCount + 1) + "/5");

        if (retryCount < 5) {
            setTimeout(() => {
                sendResultToNative(cookies, retryCount + 1);
            }, 1500); // Wait 1.5s before retrying
        } else {
            console.log("[CookieExtractor] Max retries reached. Forcing send anyway.");
            // If we exhausted retries, maybe the page is just small? Send it anyway to avoid infinite hang.
            // But valid cookies are the priority.
            doSend(cookies, html);
        }
        return;
    }

    doSend(cookies, html);
}

function doSend(cookies, html) {
    if (resultSent) return;
    resultSent = true;
    console.log("[CookieExtractor] Sending CF_RESULT (v5): " + html.length + " chars from " + window.location.href);

    browser.runtime.sendMessage({
        type: "CF_RESULT",
        cookies: cookies,
        userAgent: navigator.userAgent,
        html: html,
        url: window.location.href
    }).then(() => {
        console.log("[CookieExtractor] CF_RESULT sent successfully");
        // Optional: Close window? No, let Native handle it.
    }).catch(err => {
        console.error("[CookieExtractor] Failed to send CF_RESULT:", err);
        resultSent = false;
    });
}

// Listen for cookie_found messages
browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "cookie_found" && message.cookies) {
        if (message.cookies.includes("cf_clearance")) {
            // We received cookies. If we are the target page, we should extract.
            if (isTargetPage() && !isChallengeFrame() && !isAdPage()) {
                console.log("[CookieExtractor] Received cookies on target page, initiating extraction...");
                waitForPageLoad(() => sendResultToNative(message.cookies, 0));
            }
        }
    }
});
