package com.omarflex5.temp.omerflex.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.omerflex.R;

public class CursorLayout extends FrameLayout {

    private static final String TAG = "CursorLayout";
    private static final int CURSOR_DISAPPEAR_TIMEOUT = 5000;
    private static final int BASE_SPEED = 10;
    private static final int MAX_SPEED_LEVEL = 5;
    private static final long SPEED_TIMEOUT = 1000;
    private static final int SCROLL_START_PADDING = 100;
    private static final int SCROLL_SPEED = 15;
    private static final int CURSOR_RADIUS = 20;

    private final Paint paint = new Paint();
    private final PointF cursorPosition = new PointF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;

    private int currentSpeedLevel = 1;
    private Runnable speedTimerRunnable;
    private long lastPressTime = 0;
    private boolean isTimerRunning = false;
    private int currentDirectionX = 0;
    private int currentDirectionY = 0;
    private boolean dpadCenterPressed = false;
    private long lastInteractionTime = SystemClock.uptimeMillis();

    private static final int CURSOR_SIZE = 24;
    private static final int POINTER_LENGTH = CURSOR_SIZE * 2;
    private final Paint cursorPaint = new Paint();
    private final Path mousePointerPath = new Path();

    private final Runnable hideCursorRunnable = () -> {
//        Log.d("Cursor+", "Hiding cursor at " + System.currentTimeMillis());
        invalidate();
    };

    private float rippleRadius = 0;
    private float rippleOpacity = 0;
    private final Paint ripplePaint = new Paint();
    private final Handler animationHandler = new Handler();
    private final Runnable rippleUpdater = new Runnable() {
        @Override
        public void run() {
            if (rippleRadius < CURSOR_SIZE * 3) {
                rippleRadius += CURSOR_SIZE * 0.5f;
                rippleOpacity = Math.max(0, 1 - (rippleRadius / (CURSOR_SIZE * 3)));
                invalidate();
                animationHandler.postDelayed(this, 16);
            } else {
                rippleRadius = 0;
                rippleOpacity = 0;
            }
        }
    };

    public CursorLayout(Context context) {
        super(context);
        init();
    }

    public CursorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        cursorPaint.setAntiAlias(true);
        cursorPaint.setColor(Color.BLACK);
        cursorPaint.setStyle(Style.FILL);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        mousePointerPath.moveTo(0, 0);
        mousePointerPath.lineTo(CURSOR_SIZE, CURSOR_SIZE/2);
        mousePointerPath.lineTo(CURSOR_SIZE/2, CURSOR_SIZE);
        mousePointerPath.lineTo(0, 0);
        mousePointerPath.addCircle(CURSOR_SIZE/2, CURSOR_SIZE/2, CURSOR_SIZE/4, Path.Direction.CW);

        ripplePaint.setStyle(Style.FILL);
        ripplePaint.setColor(Color.argb(128, 0, 150, 255));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cursorPosition.set(w / 2f, h / 2f);
        resetCursorTimeout();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        webView = findViewById(R.id.webView);
        if (webView == null) {
            Log.e(TAG, "WebView not found in layout hierarchy!");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleDirectionKeys(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleDirectionKeys(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
//        Log.d(TAG, "handleDirectionKeys: "+keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (isDown) handleMovement(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (isDown) handleMovement(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (isDown) handleMovement(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isDown) handleMovement(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
                handleCenterKey(event);
                return true;
        }
        return false;
    }

    private void handleMovement(int x, int y) {
        resetCursorTimeout(); // Reset the auto-hide timer on movement
        long currentTime = SystemClock.uptimeMillis();

        // Reset if direction changed or timer expired
        if (currentDirectionX != x || currentDirectionY != y ||
                currentTime - lastPressTime > SPEED_TIMEOUT) {
            resetSpeedState();
        }

        // Update state
        currentDirectionX = x;
        currentDirectionY = y;
        lastPressTime = currentTime;

        // Calculate and apply movement
        int speed = BASE_SPEED * currentSpeedLevel;
        cursorPosition.offset(x * speed, y * speed);

        // Add hover event after movement
        dispatchMouseEvent(MotionEvent.ACTION_HOVER_MOVE);

        enforceBounds();
        handleEdgeScrolling();

        // Manage acceleration
        if (!isTimerRunning) {
            startSpeedTimer();
        }
        currentSpeedLevel = Math.min(currentSpeedLevel + 1, MAX_SPEED_LEVEL);

        postInvalidate();
//        Log.d(TAG, "Moved by " + speed + "px (Level " + currentSpeedLevel + ")");
    }

    private void startSpeedTimer() {
        isTimerRunning = true;
        speedTimerRunnable = () -> {
            resetSpeedState();
//            Log.d(TAG, "Speed timer expired");
        };
        handler.postDelayed(speedTimerRunnable, SPEED_TIMEOUT);
    }

    private void resetSpeedState() {
        currentSpeedLevel = 1;
        isTimerRunning = false;
        // Only remove speed timer callbacks
        if (speedTimerRunnable != null) {
            handler.removeCallbacks(speedTimerRunnable);
        }
    }

    private void enforceBounds() {
        cursorPosition.x = Math.max(0, Math.min(cursorPosition.x, getWidth()));
        cursorPosition.y = Math.max(0, Math.min(cursorPosition.y, getHeight()));
    }

    private void handleEdgeScrolling() {
        if (webView == null) return;

        boolean scrolled = false;
        int scrollAmount = SCROLL_SPEED * currentSpeedLevel;

        // Horizontal scrolling
        if (cursorPosition.x > getWidth() - SCROLL_START_PADDING) {
            if (webView.canScrollHorizontally(1)) {
                webView.scrollBy(scrollAmount, 0);
                scrolled = true;
            }
        } else if (cursorPosition.x < SCROLL_START_PADDING) {
            if (webView.canScrollHorizontally(-1)) {
                webView.scrollBy(-scrollAmount, 0);
                scrolled = true;
            }
        }

        // Vertical scrolling
        if (cursorPosition.y > getHeight() - SCROLL_START_PADDING) {
            if (webView.canScrollVertically(1)) {
                webView.scrollBy(0, scrollAmount);
                scrolled = true;
            }
        } else if (cursorPosition.y < SCROLL_START_PADDING) {
            if (webView.canScrollVertically(-1)) {
                webView.scrollBy(0, -scrollAmount);
                scrolled = true;
            }
        }

        if (scrolled) {
            enforceBounds();
        }
    }

    private void handleCenterKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            resetCursorTimeout();
            startClickAnimation();
            dispatchClickEvents();
        }
    }

    private void startClickAnimation() {
        rippleRadius = 0;
        rippleOpacity = 1;
        animationHandler.removeCallbacks(rippleUpdater);
        animationHandler.post(rippleUpdater);
    }

    /**
     * Dispatches click events using appropriate coordinates for touch and JavaScript.
     */
    private void dispatchClickEvents() {
        if (webView == null) return;

        // Calculate view coordinates (for touch events)
        int[] webViewLocation = new int[2];
        webView.getLocationInWindow(webViewLocation);
        int[] myLocation = new int[2];
        this.getLocationInWindow(myLocation);

//        float viewX = cursorPosition.x - (webViewLocation[0] - myLocation[0]);
//        float viewY = cursorPosition.y - (webViewLocation[1] - myLocation[1]);

        // Define the click point at the cursor's center
        float clickX = cursorPosition.x + CURSOR_SIZE / 2;
        float clickY = cursorPosition.y + CURSOR_SIZE / 2;

        // Calculate WebView coordinates using the adjusted click point
        float viewX = clickX - (webViewLocation[0] - myLocation[0]);
        float viewY = clickY - (webViewLocation[1] - myLocation[1]);

        // Calculate content coordinates (for JavaScript)
//        float scale = webView.getScale();
//        float contentX = (viewX + webView.getScrollX()) / scale;
//        float contentY = (viewY + webView.getScrollY()) / scale;

        // Dispatch native touch events with view coordinates
//        dispatchTouchEventToWebView(MotionEvent.ACTION_DOWN, viewX, viewY);
//        new Handler(Looper.getMainLooper()).postDelayed(
//                () -> dispatchTouchEventToWebView(MotionEvent.ACTION_UP, viewX, viewY), 50);

        dispatchTouchEventToLayout(MotionEvent.ACTION_DOWN, clickX, clickY);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> dispatchTouchEventToLayout(MotionEvent.ACTION_UP, clickX, clickY), 50);


//        // Dispatch JavaScript click with content coordinates
//        new Handler(Looper.getMainLooper()).postDelayed(
//                () -> dispatchJavaScriptClickEvent(contentX, contentY), 50);
    }

    private void dispatchTouchEventToLayout(int action, float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                time,
                time,
                action,
                x,
                y,
                0
        );

        try {
            this.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /**
     * Dispatches a touch event to the WebView using specified view coordinates.
     */
    private void dispatchTouchEventToWebView(int action, float x, float y) {
        if (webView == null) return;

        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                time,
                time,
                action,
                x,
                y,
                0
        );

//
//        MotionEvent downEvent = MotionEvent.obtain(
//                time,
//                time,
//                MotionEvent.ACTION_DOWN,
//                x,
//                y,
//                0
//        );
//        webView.dispatchTouchEvent(downEvent);
//        downEvent.recycle();
//
//        MotionEvent upEvent = MotionEvent.obtain(
//                time,
//                time,
//                MotionEvent.ACTION_UP,
//                x,
//                y,
//                0
//        );
//        webView.dispatchTouchEvent(upEvent);
//        upEvent.recycle();

        try {
            webView.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    /**
     * Dispatches a JavaScript click event using content coordinates.
     */
    private void dispatchJavaScriptClickEvent(float x, float y) {
        if (webView == null) return;

        String js = String.format(
                "(function() {" +
                        "   var elem = document.elementFromPoint(%f, %f);" +
                        "   if(elem) {" +
                        "       var clickEvent = new MouseEvent('click', {" +
                        "           bubbles: true," +
                        "           clientX: %f," +
                        "           clientY: %f," +
                        "           view: window" +
                        "       });" +
                        "       elem.dispatchEvent(clickEvent);" +
                        "   }" +
                        "})();",
                x, y, x, y
        );
        webView.evaluateJavascript(js, null);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (SystemClock.uptimeMillis() - lastInteractionTime < CURSOR_DISAPPEAR_TIMEOUT) {
            drawCursor(canvas);
            drawRippleEffect(canvas);
        }
    }

    private void drawRippleEffect(Canvas canvas) {
        if (rippleOpacity > 0) {
            ripplePaint.setAlpha((int)(255 * rippleOpacity));
            canvas.drawCircle(
                    cursorPosition.x + CURSOR_SIZE/2,
                    cursorPosition.y + CURSOR_SIZE/2,
                    rippleRadius,
                    ripplePaint
            );
        }
    }
    private void dispatchMouseEvent(int action) {
//        Log.d(TAG, "dispatchMouseEvent: ");
        if (webView == null) return;

        long time = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = cursorPosition.x + webView.getScrollX();
        pc[0].y = cursorPosition.y + webView.getScrollY();
        pc[0].pressure = 1;
        pc[0].size = 1;

        int metaState = 0;
        int buttonState = (action == MotionEvent.ACTION_DOWN) ?
                MotionEvent.BUTTON_PRIMARY : 0;

        MotionEvent event = MotionEvent.obtain(
                time,
                time,
                action,
                1, // pointerCount
                pp,
                pc,
                metaState,
                buttonState,
                1, // xPrecision
                1, // yPrecision
                0, // deviceId
                0, // edgeFlags
                InputDevice.SOURCE_MOUSE,
                0  // flags
        );

        try {
            webView.dispatchTouchEvent(event);

            // For click detection, send UP immediately after DOWN
            if (action == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(() -> {
                    dispatchMouseEvent(MotionEvent.ACTION_UP);
                    cursorPaint.setColor(Color.BLACK);
                    postInvalidate();
                }, 50);
            }
        } finally {
            event.recycle();
        }
    }

    // Enhanced motion event dispatch
    private void dispatchMotionEvent(int action) {
//        Log.d(TAG, "dispatchMotionEvent: "+ action);
        if (webView == null) {
            Log.d(TAG, "dispatchMotionEvent: webview not found");
            return;
        }

        // Convert coordinates to WebView's content space
        float contentX = cursorPosition.x + webView.getScrollX();
        float contentY = cursorPosition.y + webView.getScrollY();

        MotionEvent event = MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                action,
                contentX,
                contentY,
                0
        );

        try {
            webView.dispatchTouchEvent(event);

            // For better click handling, add a small delay between down/up
            if (action == MotionEvent.ACTION_DOWN) {
//                Log.d(TAG, "dispatchMotionEvent: MotionEvent.ACTION_DOWN");
                handler.postDelayed(() -> {
                    MotionEvent upEvent = MotionEvent.obtain(
                            SystemClock.uptimeMillis(),
                            SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_UP,
                            contentX,
                            contentY,
                            0
                    );
                    webView.dispatchTouchEvent(upEvent);
                    upEvent.recycle();
                }, 50); // 50ms click duration
            }
        } finally {
            event.recycle();
        }
    }

    private void drawCursor(Canvas canvas) {
        canvas.save();
        canvas.translate(cursorPosition.x, cursorPosition.y);

        // Draw mouse pointer shadow
        cursorPaint.setColor(Color.argb(128, 0, 0, 0));
        canvas.drawPath(mousePointerPath, cursorPaint);

        // Draw white outline
        cursorPaint.setColor(Color.WHITE);
        cursorPaint.setStyle(Style.STROKE);
        cursorPaint.setStrokeWidth(2);
        canvas.drawPath(mousePointerPath, cursorPaint);

        // Restore fill color
        cursorPaint.setStyle(Style.FILL);
        cursorPaint.setColor(Color.BLACK);
        canvas.restore();
    }

    private void resetCursorTimeout() {
        lastInteractionTime = SystemClock.uptimeMillis();
//        Log.d("Cursor+", "Reset timeout at " + lastInteractionTime);
        handler.removeCallbacks(hideCursorRunnable);
        handler.postDelayed(hideCursorRunnable, CURSOR_DISAPPEAR_TIMEOUT);
        invalidate();
    }
}