package com.omarflex5.temp.omerflex.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.util.Timer;
import java.util.TimerTask;

public class CursorLayout_old extends FrameLayout {
    public static final int CURSOR_DISAPPEAR_TIMEOUT = 5000;
    private static final int CURSOR_SPEED = 5;
    public static int CURSOR_RADIUS = 0;
    public static String TAG = "CursorLayout";
    public static float CURSOR_STROKE_WIDTH = 0.0f;
    public static float MAX_CURSOR_SPEED = 30.0f;
    public static int SCROLL_START_PADDING = 100;
    public static final int UNCHANGED = -100;
    public int EFFECT_DIAMETER;
    public int EFFECT_RADIUS;
    private Callback callback;
    /* access modifiers changed from: private */
    public Point cursorDirection = new Point(0, 0);
    /* access modifiers changed from: private */
    public Runnable cursorHideRunnable = new Runnable() {
        public void run() {
            Log.d(TAG, "run: cursorHideRunnable");
            CursorLayout_old.this.invalidate();
        }
    };
    /* access modifiers changed from: private */
    public PointF cursorPosition = new PointF(0.0f, 0.0f);
    /* access modifiers changed from: private */
    public PointF cursorSpeed = new PointF(0.0f, 0.0f);


    /* access modifiers changed from: private */
    public boolean dpadCenterPressed = false;
    /* access modifiers changed from: private */
    public long lastCursorUpdate = System.currentTimeMillis();
    private Paint paint = new Paint();
    PointF tmpPointF = new PointF();

    private WebView webView;

    public interface Callback {
        void onUserInteraction();
    }

    /* access modifiers changed from: private */
    public float bound(float f, float f2) {
        Log.d(TAG, "bound: ");
        if (f > f2) {
            return f2;
        }
        float f3 = -f2;
        return f < f3 ? f3 : f;
    }

    public CursorLayout_old(Context context) {
        super(context);
        init();
    }

    public CursorLayout_old(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    private void init() {
        Log.d(TAG, "init: isInEditMode:"+isInEditMode());
        if (!isInEditMode()) {
            this.paint.setAntiAlias(true);
            setWillNotDraw(false);
            Display defaultDisplay = ((WindowManager) getContext().getSystemService(getContext().WINDOW_SERVICE)).getDefaultDisplay();
            Point point = new Point();
            defaultDisplay.getSize(point);
            this.EFFECT_RADIUS = point.x / 20;
            this.EFFECT_DIAMETER = this.EFFECT_RADIUS * 2;
            CURSOR_STROKE_WIDTH = (float) (point.x / 400);
            CURSOR_RADIUS = point.x / 110;
            MAX_CURSOR_SPEED = (float) (point.x / 25);
            SCROLL_START_PADDING = point.x / 15;
        }
    }

    public void setCallback(Callback callback2) {
        this.callback = callback2;
    }

    public void setWebView(WebView webView) {
        this.webView = webView;
    }
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        Log.d(TAG, "onInterceptTouchEvent: ");
        Callback callback2 = this.callback;
        if (callback2 != null) {
            callback2.onUserInteraction();
        }
        return super.onInterceptTouchEvent(motionEvent);
    }

    /* access modifiers changed from: protected */
    public void onSizeChanged(int i, int i2, int i3, int i4) {
        Log.d(TAG, "onSizeChanged: ");
        super.onSizeChanged(i, i2, i3, i4);
        //UtilMethods.LogMethod("cursorView123_", "onSizeChanged");
        if (!isInEditMode()) {
            this.cursorPosition.set(((float) i) / 2.0f, ((float) i2) / 2.0f);
            if (getHandler() != null) {
                getHandler().postDelayed(this.cursorHideRunnable, 5000);
            }
        }
    }

    public boolean dispatchKeyEvent(KeyEvent keyEvent) {
        //UtilMethods.LogMethod("cursorView123_", "dispatchKeyEvent");
     //   Log.d(TAG, "dispatchKeyEvent: ");
//        Callback callback2 = this.callback;
//        if (callback2 != null) {
//            callback2.onUserInteraction();
//        }
        //up: i=-100 i2=+0
        //down: i=-100 i2=1
        //right: i=1 i2=-100
        //left: i=-1 i2=-100
        int keyCode = keyEvent.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK){
            return super.dispatchKeyEvent(keyEvent);
        }

        if (!(keyCode == 66 || keyCode == 160)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_BUTTON_L1:
                    Log.d(TAG, "dispatchKeyEvent: 19, "+keyEvent.getAction());
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        if (this.cursorPosition.y <= 0.0f) {
                            return super.dispatchKeyEvent(keyEvent);
                        }
                        handleDirectionKeyEvent(keyEvent, -100, +1, true);
                    }
                    else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        handleDirectionKeyEvent(keyEvent, -100, +1, false);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    Log.d(TAG, "dispatchKeyEvent: 20, "+keyEvent.getAction());
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        if (this.cursorPosition.y >= ((float) getHeight())) {
                            return super.dispatchKeyEvent(keyEvent);
                        }
                        handleDirectionKeyEvent(keyEvent, -100, -1, true);
                    } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        handleDirectionKeyEvent(keyEvent, -100, -1, false);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_FORWARD:
                    Log.d(TAG, "dispatchKeyEvent: 21, "+keyEvent.getAction());
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        if (this.cursorPosition.x <= 0.0f) {
                            return super.dispatchKeyEvent(keyEvent);
                        }
                        handleDirectionKeyEvent(keyEvent, +1, -100, true);
                    } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        handleDirectionKeyEvent(keyEvent, +1, -100, false);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    Log.d(TAG, "dispatchKeyEvent: 2, "+keyEvent.getAction());
                    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        if (this.cursorPosition.x >= ((float) getWidth())) {
                            return super.dispatchKeyEvent(keyEvent);
                        }
                        handleDirectionKeyEvent(keyEvent, -1, -100, true);
                    } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        handleDirectionKeyEvent(keyEvent, -1, -100, false);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    break;
//                default:
//                    switch (keyCode) {
//                        case 268:
//                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
//                                handleDirectionKeyEvent(keyEvent, -1, -1, true);
//                            } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
//                                handleDirectionKeyEvent(keyEvent, 0, 0, false);
//                            }
//                            return true;
//                        case 269:
//                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
//                                handleDirectionKeyEvent(keyEvent, -1, 1, true);
//                            } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
//                                handleDirectionKeyEvent(keyEvent, 0, 0, false);
//                            }
//                            return true;
//                        case 270:
//                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
//                                handleDirectionKeyEvent(keyEvent, 1, -1, true);
//                            } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
//                                handleDirectionKeyEvent(keyEvent, 0, 0, false);
//                            }
//                            return true;
//                        case 271:
//                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
//                                handleDirectionKeyEvent(keyEvent, 1, 1, true);
//                            } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
//                                handleDirectionKeyEvent(keyEvent, 0, 0, false);
//                            }
//                            return true;
//                    }
            }
        }
        if (!isCursorDissappear()) {
            if (keyEvent.getAction() == 0 && !getKeyDispatcherState().isTracking(keyEvent)) {
                getKeyDispatcherState().startTracking(keyEvent, this);
                this.dpadCenterPressed = true;
                dispatchMotionEvent(this.cursorPosition.x, this.cursorPosition.y, 0);
            } else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                getKeyDispatcherState().handleUpEvent(keyEvent);
                dispatchMotionEvent(this.cursorPosition.x, this.cursorPosition.y, 1);
                this.dpadCenterPressed = false;
            }
            return true;
        }
        return super.dispatchKeyEvent(keyEvent);
    }

    /* access modifiers changed from: private */
    public void dispatchMotionEvent(float f, float f2, int i) {
        Log.d(TAG, "dispatchMotionEvent: ");
        //UtilMethods.LogMethod("cursorView123_", "dispatchMotionEvent");
        long uptimeMillis = SystemClock.uptimeMillis();
        long uptimeMillis2 = SystemClock.uptimeMillis();
        PointerProperties pointerProperties = new PointerProperties();
        pointerProperties.id = 0;
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        PointerProperties[] pointerPropertiesArr = {pointerProperties};
        PointerCoords pointerCoords = new PointerCoords();
        pointerCoords.x = f;
        pointerCoords.y = f2;
        pointerCoords.pressure = 1.0f;
        pointerCoords.size = 1.0f;
        dispatchTouchEvent(MotionEvent.obtain(uptimeMillis, uptimeMillis2, i, 1, pointerPropertiesArr, new PointerCoords[]{pointerCoords}, 0, 0, 1.0f, 1.0f, 0, 0, 0, 0));
    }

//    private void handleDirectionKeyEvent(KeyEvent keyEvent, int i, int i2, boolean z) {
//        Log.d(TAG, "handleDirectionKeyEvent: i:"+i+", i2:"+i2+", z:"+z);
//        this.lastCursorUpdate = System.currentTimeMillis();
//        if (!z) {
//            getKeyDispatcherState().handleUpEvent(keyEvent);
//
//            this.cursorSpeed.set(0.0f, 0.0f);
//        }
//        else if (!getKeyDispatcherState().isTracking(keyEvent)) {
//            Handler handler = getHandler();
//            handler.removeCallbacks(this.cursorUpdateRunnable);
//            handler.post(this.cursorUpdateRunnable);
//            getKeyDispatcherState().startTracking(keyEvent, this);
//        }
//        else {
//            return;
//        }
//        Point point = this.cursorDirection;
//        if (i == -100) {
//            i = point.x;
//        }
//        if (i2 == -100) {
//            i2 = this.cursorDirection.y;
//        }
//        point.set(i, i2);
//    }
//
//    private Handler cursorUpdateHandler = new Handler();
//    private Runnable cursorUpdateRunnable = new Runnable() {
//        public void run() {
//            Log.d(TAG, "run: cursorUpdateRunnable");
//            cursorUpdateHandler.removeCallbacks(cursorHideRunnable);
//
//            long currentTimeMillis = System.currentTimeMillis();
//            long deltaTime = currentTimeMillis - lastCursorUpdate;
//            lastCursorUpdate = currentTimeMillis;
//
//            float deltaSpeed = ((float) deltaTime) * 0.05f;
//
//            float newXSpeed = bound(cursorSpeed.x + (bound((float) cursorDirection.x, 1.0f) * deltaSpeed), MAX_CURSOR_SPEED);
//            float newYSpeed = bound(cursorSpeed.y + (bound((float) cursorDirection.y, 1.0f) * deltaSpeed), MAX_CURSOR_SPEED);
//
//            cursorSpeed.set(newXSpeed, newYSpeed);
//            Log.d(TAG, "run: newX:"+newXSpeed+", newY:"+newYSpeed);
//
//            if (Math.abs(cursorSpeed.x) < 0.1f) {
//                cursorSpeed.x = 0.0f;
//            }
//            if (Math.abs(cursorSpeed.y) < 0.1f) {
//                cursorSpeed.y = 0.0f;
//            }
//
//            if (cursorDirection.x == 0 && cursorDirection.y == 0 && cursorSpeed.x == 0.0f && cursorSpeed.y == 0.0f) {
//                cursorUpdateHandler.postDelayed(cursorHideRunnable, 5000);
//                return;
//            }
//
//            tmpPointF.set(cursorPosition);
//            cursorPosition.offset(cursorSpeed.x, cursorSpeed.y);
//
//            Log.d("cursor1234_xxxx", String.valueOf(cursorPosition.x));
//            Log.d("cursor1234_yyyy", String.valueOf(cursorPosition.y));
//
//            if (cursorPosition.x < 0.0f) {
//                cursorPosition.x = 0.0f;
//            } else if (cursorPosition.x > (float) (getWidth() - 1)) {
//                cursorPosition.x = (float) (getWidth() - 1);
//            }
//
//            if (cursorPosition.y < 0.0f) {
//                cursorPosition.y = 0.0f;
//            } else if (cursorPosition.y > (float) (getHeight() - 1)) {
//                cursorPosition.y = (float) (getHeight() - 1);
//            }
//
//            if (!tmpPointF.equals(cursorPosition) && dpadCenterPressed) {
//                dispatchMotionEvent(cursorPosition.x, cursorPosition.y, 2);
//            }
//
//            View childAt = getChildAt(0);
//            if (childAt != null) {
//                if (cursorPosition.y > (float) (getHeight() - SCROLL_START_PADDING)) {
//                    if (cursorSpeed.y > 0.0f && childAt.canScrollVertically((int) cursorSpeed.y)) {
//                        childAt.scrollTo(childAt.getScrollX(), childAt.getScrollY() + ((int) cursorSpeed.y));
//                    }
//                } else if (cursorPosition.y < (float) SCROLL_START_PADDING && cursorSpeed.y < 0.0f && childAt.canScrollVertically((int) cursorSpeed.y)) {
//                    childAt.scrollTo(childAt.getScrollX(), childAt.getScrollY() + ((int) cursorSpeed.y));
//                }
//                if (cursorPosition.x > (float) (getWidth() - SCROLL_START_PADDING)) {
//                    if (cursorSpeed.x > 0.0f && childAt.canScrollHorizontally((int) cursorSpeed.x)) {
//                        childAt.scrollTo(childAt.getScrollX() + ((int) cursorSpeed.x), childAt.getScrollY());
//                    }
//                } else if (cursorPosition.x < (float) SCROLL_START_PADDING && cursorSpeed.x < 0.0f && childAt.canScrollHorizontally((int) cursorSpeed.x)) {
//                    childAt.scrollTo(childAt.getScrollX() + ((int) cursorSpeed.x), childAt.getScrollY());
//                }
//            }
//
//            invalidate();
//            cursorUpdateHandler.post(this);
//        }
//    };

    private Timer cursorUpdateTimer = null;
    private TimerTask cursorUpdateTask = null;

    private void handleDirectionKeyEvent(KeyEvent keyEvent, int x, int y, boolean z) {
        Log.d(TAG, "handleDirectionKeyEvent: i:" + x + ", i2:" + y + ", z:" + z);
        this.lastCursorUpdate = System.currentTimeMillis();

    //    Point point = this.cursorDirection;
        //up: i=-100 i2=-0
        //down: i=-100 i2=1
        //right: i=1 i2=-100
        //left: i=-1 i2=-100

//        cursorPosition.set(x, y);
//        tmpPointF.set(cursorPosition);
        if (!z) {
            stopCursorUpdateTask();
            this.cursorSpeed.set(0.0f, 0.0f);
        }else {
            if (x == -100) {
                if (y < 0){
                    cursorDirection.y =  + CURSOR_SPEED;
                    cursorDirection.x =  0;
                }else {
                    cursorDirection.y =  - CURSOR_SPEED;
                    cursorDirection.x =  0;
                }
            }
            if (y == -100) {
                if (x < 0){
                    cursorDirection.x =  + CURSOR_SPEED;
                    cursorDirection.y =  0;
                }else {
                    cursorDirection.x = - CURSOR_SPEED;
                    cursorDirection.y =  0;
                }
            }

            startCursorUpdateTask();
        }
//        if (!z) {
//            getKeyDispatcherState().handleUpEvent(keyEvent);
//            this.cursorSpeed.set(0.0f, 0.0f);
//            stopCursorUpdateTask();
//        } else if (!getKeyDispatcherState().isTracking(keyEvent)) {
//            startCursorUpdateTask();
//            getKeyDispatcherState().startTracking(keyEvent, this);
//        } else {
//            return;
//        }


    }

    private void startCursorUpdateTask() {
        stopCursorUpdateTask(); // Stop any existing task
        Log.d(TAG, "startCursorUpdateTask: ");
        cursorUpdateTimer = new Timer();
        cursorUpdateTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "run: cursorUpdateTask");

                long currentTimeMillis = System.currentTimeMillis();
                long deltaTime = currentTimeMillis - lastCursorUpdate;
                lastCursorUpdate = currentTimeMillis;

                //float deltaSpeed = ((float) deltaTime) * 0.05f;
                float deltaSpeed = ((float) deltaTime) * 2.05f;

                float newXSpeed = bound(cursorSpeed.x + (bound((float) cursorDirection.x, 1.0f) * deltaSpeed), MAX_CURSOR_SPEED);
                float newYSpeed = bound(cursorSpeed.y + (bound((float) cursorDirection.y, 1.0f) * deltaSpeed), MAX_CURSOR_SPEED);

                if (Math.abs(cursorSpeed.x) < 0.1f) {
                    cursorSpeed.x = 0.5f;
                }
                if (Math.abs(cursorSpeed.y) < 0.1f) {
                    cursorSpeed.y = 0.5f;
                }
                cursorSpeed.set(cursorDirection.x * CURSOR_SPEED, cursorDirection.y * CURSOR_SPEED);
//                Log.d("newX", "run: cursorDirection: x" + cursorDirection.x + ", Y:" + cursorDirection.y);
              //  Log.d("newX", "run: cursorSpeed: x" + cursorSpeed.x + ", Y:" + cursorSpeed.y);
//                Log.d("newX", "run: postion: x" + cursorPosition.x + ", Y:" + cursorPosition.y);
//                Log.d("newX", "run: newX:" + newXSpeed + ", newY:" + newYSpeed+", delta:"+deltaSpeed);


                if (cursorDirection.x == 0 && cursorDirection.y == 0 && cursorSpeed.x == 0.0f && cursorSpeed.y == 0.0f) {
                    stopCursorUpdateTask();
                    postDelayed(cursorHideRunnable, 5000);
                    return;
                }

                //y:+ up
                //y:- down
                //x:+ right
                //x:- left

                //cursorPosition.offset(cursorDirection.x,  cursorDirection.y);
                cursorPosition.offset(cursorSpeed.x, cursorSpeed.y);
                tmpPointF.set(cursorPosition);

//                Log.d(TAG, "cursor1234_xxxx:"+String.valueOf(cursorPosition.x));
//                Log.d(TAG, "cursor1234_yyyy:"+String.valueOf(cursorPosition.y));
//
                if (cursorPosition.x < 0.0f) {
                    cursorPosition.x = 0.0f;
                } else if (cursorPosition.x > (float) (getWidth() - 1)) {
                    cursorPosition.x = (float) (getWidth() - 1);
                }

                if (cursorPosition.y < 0.0f) {
                    cursorPosition.y = 0.0f;
                } else if (cursorPosition.y > (float) (getHeight() - 1)) {
                    cursorPosition.y = (float) (getHeight() - 1);
                }

                if (!tmpPointF.equals(cursorPosition) && dpadCenterPressed) {
                    dispatchMotionEvent(cursorPosition.x, cursorPosition.y, 2);
                }

                View childAt = getChildAt(0);
                if (childAt != null) {
                    if (cursorPosition.y > (float) (getHeight() - SCROLL_START_PADDING)) {
                        if (cursorSpeed.y > 0.0f && childAt.canScrollVertically((int) cursorSpeed.y)) {
                            childAt.scrollTo(childAt.getScrollX(), childAt.getScrollY() + ((int) cursorSpeed.y));
                        }
                    } else if (cursorPosition.y < (float) SCROLL_START_PADDING && cursorSpeed.y < 0.0f && childAt.canScrollVertically((int) cursorSpeed.y)) {
                        childAt.scrollTo(childAt.getScrollX(), childAt.getScrollY() + ((int) cursorSpeed.y));
                    }
                    if (cursorPosition.x > (float) (getWidth() - SCROLL_START_PADDING)) {
                        if (cursorSpeed.x > 0.0f && childAt.canScrollHorizontally((int) cursorSpeed.x)) {
                            childAt.scrollTo(childAt.getScrollX() + ((int) cursorSpeed.x), childAt.getScrollY());
                        }
                    } else if (cursorPosition.x < (float) SCROLL_START_PADDING && cursorSpeed.x < 0.0f && childAt.canScrollHorizontally((int) cursorSpeed.x)) {
                        childAt.scrollTo(childAt.getScrollX() + ((int) cursorSpeed.x), childAt.getScrollY());
                    }
                }

                postInvalidate();
            }
        };

        cursorUpdateTimer.scheduleAtFixedRate(cursorUpdateTask, 0, /*desired interval in milliseconds*/ 200);
    }

    private void stopCursorUpdateTask() {
        Log.d(TAG, "stopCursorUpdateTask: ");
        if (cursorUpdateTask != null) {
            cursorUpdateTask.cancel();
            cursorUpdateTask = null;
        }
        if (cursorUpdateTimer != null) {
            cursorUpdateTimer.cancel();
            cursorUpdateTimer.purge();
            cursorUpdateTimer = null;
        }
    }


    /* access modifiers changed from: protected */
    public void dispatchDraw(Canvas canvas) {
        Log.d(TAG, "dispatchDraw: ");
        super.dispatchDraw(canvas);
        //UtilMethods.LogMethod("cursorView123_", "dispatchDraw");
        if (!isInEditMode() && !isCursorDissappear()) {
            float f = this.cursorPosition.x;
            float f2 = this.cursorPosition.y;
            this.paint.setColor(Color.argb(128, 255, 255, 255));
            this.paint.setStyle(Style.FILL);
            canvas.drawCircle(f, f2, (float) CURSOR_RADIUS, this.paint);
            this.paint.setColor(-7829368);
            this.paint.setStrokeWidth(CURSOR_STROKE_WIDTH);
            this.paint.setStyle(Style.STROKE);
            canvas.drawCircle(f, f2, (float) CURSOR_RADIUS, this.paint);
        }
    }

    private boolean isCursorDissappear() {
        Log.d(TAG, "isCursorDissappear: ");
        return System.currentTimeMillis() - this.lastCursorUpdate > 5000;
    }

    /* access modifiers changed from: protected */
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    public void updateCursorPosition() {
        Log.d(TAG, "updateCursorPosition: cursorDirection:" + this.cursorDirection);
        PointF pointF = this.cursorPosition;
        pointF.x += cursorDirection.x * 0.3f;
        pointF.y += cursorDirection.y * 0.3f;
        if (pointF.x < 0.0f) {
            pointF.x = 0.0f;
        }
        if (pointF.y < 0.0f) {
            pointF.y = 0.0f;
        }
        if (pointF.x > ((float) getWidth())) {
            pointF.x = (float) getWidth();
        }
        if (pointF.y > ((float) getHeight())) {
            pointF.y = (float) getHeight();
        }
        this.cursorSpeed.set(this.cursorDirection.x * 0.3f, this.cursorDirection.y * 0.3f);
        this.tmpPointF.set(pointF.x, pointF.y);

//        checkAndScrollWebView();

        if (getHandler() != null) {
            getHandler().removeCallbacks(this.cursorHideRunnable);
            getHandler().postDelayed(this.cursorHideRunnable, 5000);
        }
        invalidate();
    }

//    private void checkAndScrollWebView() {
//        if (this.webView != null) {
//            int scrollX = this.webView.getScrollX();
//            int scrollY = this.webView.getScrollY();
//            int maxScrollX = this.webView.computeHorizontalScrollRange() - this.webView.getWidth();
//            int maxScrollY = this.webView.computeVerticalScrollRange() - this.webView.getHeight();
//            boolean shouldScroll = false;
//
//            if (this.cursorPosition.x > getWidth() - SCROLL_START_PADDING && scrollX < maxScrollX) {
//                this.webView.scrollBy(10, 0);
//                shouldScroll = true;
//            } else if (this.cursorPosition.x < SCROLL_START_PADDING && scrollX > 0) {
//                this.webView.scrollBy(-10, 0);
//                shouldScroll = true;
//            }
//
//            if (this.cursorPosition.y > getHeight() - SCROLL_START_PADDING && scrollY < maxScrollY) {
//                this.webView.scrollBy(0, 10);
//                shouldScroll = true;
//            } else if (this.cursorPosition.y < SCROLL_START_PADDING && scrollY > 0) {
//                this.webView.scrollBy(0, -10);
//                shouldScroll = true;
//            }
//
//            if (shouldScroll) {
//                postInvalidate();
//            }
//        }
//    }

}