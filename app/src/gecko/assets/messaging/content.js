// Content script - receives cookies from background and sends full result via messaging
// v15 - Robust DOM-based challenge detection (language-agnostic)
console.log("[CookieExtractor] Content script started (v15) on: " + window.location.href);

// CRITICAL: Immediately exit if on Cloudflare challenge domain to prevent interference
if (window.location.hostname.includes("challenges.cloudflare.com") ||
    window.location.hostname.includes("cloudflare.com")) {
    console.log("[CookieExtractor] Skipping - Cloudflare domain detected");
    // Don't run any code on Cloudflare domains
} else {

    let resultSent = false; // Flag to prevent duplicate sends

    // Tell background script a new page is loaded - this resets state for expired cookie scenarios
    // IMPORTANT: Only top frame should send this to avoid duplicate resets
    if (window.self === window.top) {
        browser.runtime.sendMessage({ type: "NEW_PAGE", url: window.location.href }).catch(() => {
            // Ignore errors - background script might not be ready yet
        });
    }

    // Check if this is a CF challenge frame
    function isChallengeFrame() {
        // ... (check implementation unchanged) ...
        if (window.self !== window.top) {
            // Treat ALL iframes as non-target frames for HTML extraction purposes
            // We only want the top-level page content
            return true;
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

    // Check if this is a valid target page (any domain that's not a challenge or ad)
    // No longer uses hardcoded domains - works for any site
    function isTargetPage() {
        // If it's a challenge or ad page, it's not a target
        if (isChallengeFrame() || isAdPage()) {
            return false;
        }
        // All other pages are valid targets
        return true;
    }

    // ROBUST challenge page detection - language-agnostic, DOM-based
    // Does NOT rely on title text which varies by language
    function isChallengePage(html) {
        if (!html) return true;

        // 1. Check for Cloudflare-specific element IDs (reliable across all languages)
        const cfElements = [
            'id="challenge-form"',          // Main CF challenge form
            'id="cf-challenge-running"',    // CF challenge running indicator
            'id="challenge-running"',       // Alternative challenge indicator
            'id="cf-please-wait"',          // CF wait indicator
            'id="cf-spinner"',              // CF loading spinner
            'id="cf-wrapper"',              // CF wrapper div
            'id="cf-hcaptcha-container"',   // CF captcha container
            'id="turnstile-wrapper"',       // Turnstile wrapper
            'class="cf-browser-verification"' // Browser verification
        ];

        for (const marker of cfElements) {
            if (html.includes(marker)) {
                console.log("[CookieExtractor] Challenge detected via: " + marker);
                return true;
            }
        }

        // 2. Check for Cloudflare turnstile script (very reliable)
        if (html.includes("challenges.cloudflare.com/turnstile")) {
            console.log("[CookieExtractor] Challenge detected: turnstile script");
            return true;
        }

        // 3. Check for Cloudflare challenge meta tags
        if (html.includes('content="noindex"') && html.includes("cloudflare")) {
            console.log("[CookieExtractor] Challenge detected: CF noindex meta");
            return true;
        }

        // 4. Check for very small HTML (error/loading pages)
        // Real content pages should be at least a few KB
        if (html.length < 2000) {
            console.log("[CookieExtractor] Challenge detected: page too small (" + html.length + " chars)");
            return true;
        }

        // 5. Check if body has minimal content (challenge pages have mostly scripts)
        const bodyMatch = html.match(/<body[^>]*>([\s\S]*?)<\/body>/i);
        if (bodyMatch) {
            const bodyContent = bodyMatch[1].replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '').trim();
            if (bodyContent.length < 500) {
                console.log("[CookieExtractor] Challenge detected: body content too small after scripts removed");
                return true;
            }
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
    // ... (skip lines) ...
    // Send full result (cookies + HTML) to native via background script
    function sendResultToNative(cookies, retryCount = 0) {
        if (resultSent) return;

        // CRITICAL: Only top frame should send HTML
        if (window.self !== window.top) {
            console.log("[CookieExtractor] Skipping - not top frame");
            return;
        }

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

    // Chunked transfer for large HTML to avoid Binder IPC overflow
    const CHUNK_SIZE = 100000; // 100KB chunks (safe under ~1MB Binder limit)

    async function doSend(cookies, html) {
        if (resultSent) return;
        resultSent = true;

        const url = window.location.href;
        const userAgent = navigator.userAgent;
        const totalChunks = Math.ceil(html.length / CHUNK_SIZE);

        console.log("[CookieExtractor] Sending CF_RESULT (v14) - " + html.length + " chars in " + totalChunks + " chunks from " + url);

        try {
            // If small enough, send as single message (backwards compatible)
            if (totalChunks === 1) {
                await browser.runtime.sendMessage({
                    type: "CF_RESULT",
                    cookies: cookies,
                    userAgent: userAgent,
                    html: html,
                    url: url
                });
                console.log("[CookieExtractor] CF_RESULT sent (single message)");
                return;
            }

            // Large HTML: use chunked transfer
            // 1. Send START message with metadata
            await browser.runtime.sendMessage({
                type: "CF_RESULT_START",
                cookies: cookies,
                userAgent: userAgent,
                url: url,
                totalChunks: totalChunks,
                totalLength: html.length
            });
            console.log("[CookieExtractor] CF_RESULT_START sent");

            // 2. Send chunks sequentially
            for (let i = 0; i < totalChunks; i++) {
                const start = i * CHUNK_SIZE;
                const end = Math.min(start + CHUNK_SIZE, html.length);
                const chunkData = html.substring(start, end);

                await browser.runtime.sendMessage({
                    type: "CF_RESULT_CHUNK",
                    chunkIndex: i,
                    data: chunkData
                });
                console.log("[CookieExtractor] Chunk " + (i + 1) + "/" + totalChunks + " sent (" + chunkData.length + " chars)");
            }

            // 3. Send END message
            await browser.runtime.sendMessage({
                type: "CF_RESULT_END"
            });
            console.log("[CookieExtractor] CF_RESULT_END sent - transfer complete");

        } catch (err) {
            console.error("[CookieExtractor] Failed to send CF_RESULT:", err);
            resultSent = false;
        }
    }

    // Request cookies from background and extract if valid
    // This is called BOTH on page load (for redirected pages) and on cookie_found message
    function checkCookiesAndExtract() {
        if (resultSent) return;

        // Skip if we're on a challenge page
        if (isChallengePage(document.documentElement.outerHTML || "")) {
            console.log("[CookieExtractor] checkCookiesAndExtract: Still on challenge page, waiting...");
            return;
        }

        // Request current cookies from background
        browser.runtime.sendMessage({ type: "GET_COOKIES" }).then(response => {
            if (response && response.cookies && response.cookies.includes("cf_clearance")) {
                console.log("[CookieExtractor] On-load cookie check: cf_clearance found, extracting...");
                waitForPageLoad(() => sendResultToNative(response.cookies, 0));
            }
        }).catch(err => {
            // Ignore - background might not be ready
        });
    }

    // Listen for cookie_found messages
    browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (message.type === "cookie_found" && message.cookies) {
            if (message.cookies.includes("cf_clearance")) {
                // We received cookies. If we are the target page and NOT on challenge, extract.
                if (isTargetPage() && !isChallengeFrame() && !isAdPage()) {
                    // IMPORTANT: Skip if still showing challenge page
                    if (isChallengePage(document.documentElement.outerHTML || "")) {
                        console.log("[CookieExtractor] Received cookies but still on challenge page, skipping...");
                        return;
                    }
                    console.log("[CookieExtractor] Received cookies on target page, initiating extraction...");
                    waitForPageLoad(() => sendResultToNative(message.cookies, 0));
                }
            }
        }
    });

    // ON PAGE LOAD: Check if cookies already exist (for pages after CF redirect)
    // This is critical - the NEW content script on the redirected page needs to check cookies
    if (isTargetPage() && !isChallengeFrame() && !isAdPage()) {
        waitForPageLoad(() => {
            // Wait a bit for page to settle after load
            setTimeout(() => {
                if (!resultSent && !isChallengePage(document.documentElement.outerHTML || "")) {
                    console.log("[CookieExtractor] Page loaded - checking cookies...");
                    checkCookiesAndExtract();
                }
            }, 500);
        });
    }

} // End of else block (non-Cloudflare domains)
