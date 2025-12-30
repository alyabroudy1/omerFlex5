package com.omarflex5.ui.cursor;

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
import android.view.View;
import android.widget.FrameLayout;

/**
 * A FrameLayout that provides mouse cursor navigation for TV remotes.
 * Works with any View (WebView, GeckoView, etc.) as the target.
 * 
 * Features:
 * - D-pad navigation: UP/DOWN/LEFT/RIGHT move cursor
 * - Click dispatch: CENTER/ENTER triggers touch events on target
 * - Edge scrolling: Auto-scroll when cursor reaches edges
 * - Acceleration: Speed increases with continuous movement
 * - Auto-hide: Cursor disappears after 5 seconds of inactivity
 * - Visual feedback: Ripple effect on click
 * 
 * Usage:
 * 1. Wrap your WebView/GeckoView in CursorLayout in XML
 * 2. Call setTargetView(view) to set the navigation target
 */
public class CursorLayout extends FrameLayout {

    private static final String TAG = "CursorLayout";
    private static final int CURSOR_DISAPPEAR_TIMEOUT = 5000;
    private static final int BASE_SPEED = 10;
    private static final int MAX_SPEED_LEVEL = 5;
    private static final long SPEED_TIMEOUT = 1000;
    private static final int SCROLL_START_PADDING = 100;
    private static final int SCROLL_SPEED = 15;
    private static final int CURSOR_SIZE = 24;

    private final PointF cursorPosition = new PointF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View targetView;

    private int currentSpeedLevel = 1;
    private Runnable speedTimerRunnable;
    private long lastPressTime = 0;
    private boolean isTimerRunning = false;
    private int currentDirectionX = 0;
    private int currentDirectionY = 0;
    private long lastInteractionTime = SystemClock.uptimeMillis();

    private final Paint cursorPaint = new Paint();
    private final Path mousePointerPath = new Path();

    private final Runnable hideCursorRunnable = () -> invalidate();

    private float rippleRadius = 0;
    private float rippleOpacity = 0;
    private final Paint ripplePaint = new Paint();
    private final Handler animationHandler = new Handler(Looper.getMainLooper());
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

    public CursorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        cursorPaint.setAntiAlias(true);
        cursorPaint.setColor(Color.BLACK);
        cursorPaint.setStyle(Style.FILL);
        setWillNotDraw(false);
        // Note: Don't use LAYER_TYPE_HARDWARE - it causes child views to render black

        // Create mouse pointer shape
        mousePointerPath.moveTo(0, 0);
        mousePointerPath.lineTo(CURSOR_SIZE, CURSOR_SIZE / 2f);
        mousePointerPath.lineTo(CURSOR_SIZE / 2f, CURSOR_SIZE);
        mousePointerPath.lineTo(0, 0);
        mousePointerPath.addCircle(CURSOR_SIZE / 2f, CURSOR_SIZE / 2f, CURSOR_SIZE / 4f, Path.Direction.CW);

        ripplePaint.setStyle(Style.FILL);
        ripplePaint.setColor(Color.argb(128, 0, 150, 255));
    }

    /**
     * Set the target view for cursor navigation (WebView, GeckoView, etc.).
     * This view will receive touch events and scrolling commands.
     */
    public void setTargetView(View view) {
        this.targetView = view;
        Log.d(TAG, "Target view set: " + (view != null ? view.getClass().getSimpleName() : "null"));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cursorPosition.set(w / 2f, h / 2f);
        resetCursorTimeout();
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

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (isDown)
                    handleMovement(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (isDown)
                    handleMovement(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (isDown)
                    handleMovement(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (isDown)
                    handleMovement(1, 0);
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
        resetCursorTimeout();
        long currentTime = SystemClock.uptimeMillis();

        // Reset speed if direction changed or timer expired
        if (currentDirectionX != x || currentDirectionY != y ||
                currentTime - lastPressTime > SPEED_TIMEOUT) {
            resetSpeedState();
        }

        currentDirectionX = x;
        currentDirectionY = y;
        lastPressTime = currentTime;

        // Calculate and apply movement
        int speed = BASE_SPEED * currentSpeedLevel;
        cursorPosition.offset(x * speed, y * speed);

        // Send hover event for CSS :hover effects
        dispatchHoverEvent();

        enforceBounds();
        handleEdgeScrolling();

        // Manage acceleration
        if (!isTimerRunning) {
            startSpeedTimer();
        }
        currentSpeedLevel = Math.min(currentSpeedLevel + 1, MAX_SPEED_LEVEL);

        postInvalidate();
    }

    private void startSpeedTimer() {
        isTimerRunning = true;
        speedTimerRunnable = () -> resetSpeedState();
        handler.postDelayed(speedTimerRunnable, SPEED_TIMEOUT);
    }

    private void resetSpeedState() {
        currentSpeedLevel = 1;
        isTimerRunning = false;
        if (speedTimerRunnable != null) {
            handler.removeCallbacks(speedTimerRunnable);
        }
    }

    private void enforceBounds() {
        cursorPosition.x = Math.max(0, Math.min(cursorPosition.x, getWidth()));
        cursorPosition.y = Math.max(0, Math.min(cursorPosition.y, getHeight()));
    }

    private void handleEdgeScrolling() {
        if (targetView == null)
            return;

        int scrollAmount = SCROLL_SPEED * currentSpeedLevel;

        // Horizontal scrolling
        if (cursorPosition.x > getWidth() - SCROLL_START_PADDING) {
            if (targetView.canScrollHorizontally(1)) {
                targetView.scrollBy(scrollAmount, 0);
            }
        } else if (cursorPosition.x < SCROLL_START_PADDING) {
            if (targetView.canScrollHorizontally(-1)) {
                targetView.scrollBy(-scrollAmount, 0);
            }
        }

        // Vertical scrolling
        if (cursorPosition.y > getHeight() - SCROLL_START_PADDING) {
            if (targetView.canScrollVertically(1)) {
                targetView.scrollBy(0, scrollAmount);
            }
        } else if (cursorPosition.y < SCROLL_START_PADDING) {
            if (targetView.canScrollVertically(-1)) {
                targetView.scrollBy(0, -scrollAmount);
            }
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

    private void dispatchClickEvents() {
        // Define the click point at the cursor's center
        float clickX = cursorPosition.x + CURSOR_SIZE / 2f;
        float clickY = cursorPosition.y + CURSOR_SIZE / 2f;

        // Dispatch touch events to layout (will propagate to target)
        dispatchTouchEventToLayout(MotionEvent.ACTION_DOWN, clickX, clickY);
        handler.postDelayed(
                () -> dispatchTouchEventToLayout(MotionEvent.ACTION_UP, clickX, clickY), 50);
    }

    private void dispatchTouchEventToLayout(int action, float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);

        try {
            // Dispatch directly to targetView (GeckoView/WebView) for web content clicks
            if (targetView != null) {
                // Translate coordinates to targetView's coordinate space
                int[] locationOnScreen = new int[2];
                targetView.getLocationOnScreen(locationOnScreen);
                int[] myLocation = new int[2];
                this.getLocationOnScreen(myLocation);

                float adjustedX = x - (locationOnScreen[0] - myLocation[0]);
                float adjustedY = y - (locationOnScreen[1] - myLocation[1]);

                MotionEvent adjustedEvent = MotionEvent.obtain(time, time, action, adjustedX, adjustedY, 0);
                targetView.dispatchTouchEvent(adjustedEvent);
                adjustedEvent.recycle();
                Log.d(TAG, "Dispatched touch to targetView: " + action + " at (" + adjustedX + ", " + adjustedY + ")");
            } else {
                // Fallback to layout dispatch
                this.dispatchTouchEvent(event);
            }
        } finally {
            event.recycle();
        }
    }

    private void dispatchHoverEvent() {
        if (targetView == null)
            return;

        long time = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = cursorPosition.x + targetView.getScrollX();
        pc[0].y = cursorPosition.y + targetView.getScrollY();
        pc[0].pressure = 0;
        pc[0].size = 1;

        MotionEvent event = MotionEvent.obtain(
                time, time,
                MotionEvent.ACTION_HOVER_MOVE,
                1, pp, pc,
                0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_MOUSE, 0);

        try {
            targetView.dispatchGenericMotionEvent(event);
        } finally {
            event.recycle();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (SystemClock.uptimeMillis() - lastInteractionTime < CURSOR_DISAPPEAR_TIMEOUT) {
            drawCursor(canvas);
            drawRippleEffect(canvas);
        }
    }

    private void drawCursor(Canvas canvas) {
        canvas.save();
        canvas.translate(cursorPosition.x, cursorPosition.y);

        // Draw shadow
        cursorPaint.setColor(Color.argb(128, 0, 0, 0));
        cursorPaint.setStyle(Style.FILL);
        canvas.drawPath(mousePointerPath, cursorPaint);

        // Draw white outline
        cursorPaint.setColor(Color.WHITE);
        cursorPaint.setStyle(Style.STROKE);
        cursorPaint.setStrokeWidth(2);
        canvas.drawPath(mousePointerPath, cursorPaint);

        // Restore fill style
        cursorPaint.setStyle(Style.FILL);
        canvas.restore();
    }

    private void drawRippleEffect(Canvas canvas) {
        if (rippleOpacity > 0) {
            ripplePaint.setAlpha((int) (255 * rippleOpacity));
            canvas.drawCircle(
                    cursorPosition.x + CURSOR_SIZE / 2f,
                    cursorPosition.y + CURSOR_SIZE / 2f,
                    rippleRadius,
                    ripplePaint);
        }
    }

    private void resetCursorTimeout() {
        lastInteractionTime = SystemClock.uptimeMillis();
        handler.removeCallbacks(hideCursorRunnable);
        handler.postDelayed(hideCursorRunnable, CURSOR_DISAPPEAR_TIMEOUT);
        invalidate();
    }
}
