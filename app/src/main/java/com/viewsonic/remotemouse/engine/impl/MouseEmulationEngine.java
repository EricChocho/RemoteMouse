package com.viewsonic.remotemouse.engine.impl;

import static android.content.Context.TELECOM_SERVICE;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;

import static com.viewsonic.remotemouse.helper.Helper.helperContext;
import static com.viewsonic.remotemouse.services.MouseEventService.EnableForPackage;
import static com.viewsonic.remotemouse.services.MouseEventService.foregroundPackageName;

import com.viewsonic.remotemouse.R;
import com.viewsonic.remotemouse.view.MouseCursorView;
import com.viewsonic.remotemouse.view.OverlayView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MouseEmulationEngine {

    private static boolean DPAD_SELECT_PRESSED = false;
    private static String LOG_TAG = "MOUSE_EMULATION";


    CountDownTimer waitToChange;

    CountDownTimer disappearTimer;

    private static boolean isInScrollMode = false;

    // service which started this engine
    private static AccessibilityService mService;

    private static  PointerControl mPointerControl;

    public static int stuckAtSide = 0;

    private int momentumStack;

    public static boolean isEnabled;

    public static int bossKey;

    public static int scrollSpeed;

    public static boolean isBossKeyDisabled;

    public static boolean isBossKeySetToToggle;

    public static boolean NeedDealFocuse;

    private Handler timerHandler;

    private Point DPAD_Center_Init_Point = new Point();

    private Runnable previousRunnable;

    // tells which keycodes correspond to which pointer movement in scroll and movement mode
    // scroll directions don't match keycode instruction because that's how swiping works
    private static final Map<Integer, Integer> scrollCodeMap;
    static {
        Map<Integer, Integer> integerMap = new HashMap<>();
        integerMap.put(KeyEvent.KEYCODE_DPAD_UP, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_F1, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_DPAD_DOWN, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_F3, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_DPAD_LEFT, PointerControl.RIGHT);
        integerMap.put(KeyEvent.KEYCODE_F2, PointerControl.RIGHT);
        integerMap.put(KeyEvent.KEYCODE_DPAD_RIGHT, PointerControl.LEFT);
        integerMap.put(KeyEvent.KEYCODE_F4, PointerControl.LEFT);
        integerMap.put(KeyEvent.KEYCODE_PROG_GREEN, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_PROG_RED, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_PROG_BLUE, PointerControl.RIGHT);
        integerMap.put(KeyEvent.KEYCODE_PROG_YELLOW, PointerControl.LEFT);
        scrollCodeMap = Collections.unmodifiableMap(integerMap);
    }

    private static final Map<Integer, Integer> movementCodeMap;
    static {
        Map<Integer, Integer> integerMap = new HashMap<>();
        integerMap.put(KeyEvent.KEYCODE_DPAD_UP, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_DPAD_DOWN, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_DPAD_LEFT, PointerControl.LEFT);
        integerMap.put(KeyEvent.KEYCODE_DPAD_RIGHT, PointerControl.RIGHT);
        integerMap.put(KeyEvent.KEYCODE_F1, PointerControl.UP);
        integerMap.put(KeyEvent.KEYCODE_F3, PointerControl.DOWN);
        integerMap.put(KeyEvent.KEYCODE_F2, PointerControl.LEFT);
        integerMap.put(KeyEvent.KEYCODE_F4, PointerControl.RIGHT);
        movementCodeMap = Collections.unmodifiableMap(integerMap);
    }

    private static final Set<Integer> actionableKeyMap;
    static {
        Set<Integer> integerSet = new HashSet<>();
        integerSet.add(KeyEvent.KEYCODE_DPAD_UP);
        integerSet.add(KeyEvent.KEYCODE_DPAD_DOWN);
        integerSet.add(KeyEvent.KEYCODE_DPAD_LEFT);
        integerSet.add(KeyEvent.KEYCODE_DPAD_RIGHT);
        integerSet.add(KeyEvent.KEYCODE_F1);
        integerSet.add(KeyEvent.KEYCODE_F2);
        integerSet.add(KeyEvent.KEYCODE_F3);
        integerSet.add(KeyEvent.KEYCODE_F4);
        integerSet.add(KeyEvent.KEYCODE_PROG_GREEN);
        integerSet.add(KeyEvent.KEYCODE_PROG_YELLOW);
        integerSet.add(KeyEvent.KEYCODE_PROG_BLUE);
        integerSet.add(KeyEvent.KEYCODE_PROG_RED);
        actionableKeyMap = Collections.unmodifiableSet(integerSet);
    }

    private static final Set<Integer> colorSet;
    static {
        Set<Integer> integerSet = new HashSet<>();
        integerSet.add(KeyEvent.KEYCODE_PROG_GREEN);
        integerSet.add(KeyEvent.KEYCODE_PROG_YELLOW);
        integerSet.add(KeyEvent.KEYCODE_PROG_BLUE);
        integerSet.add(KeyEvent.KEYCODE_PROG_RED);
        colorSet = Collections.unmodifiableSet(integerSet);
    }

    public MouseEmulationEngine (Context c, OverlayView ov) {
        momentumStack = 0;
        // overlay view for drawing mouse
        MouseCursorView mCursorView = new MouseCursorView(c);
        ov.addFullScreenLayer(mCursorView);
        mPointerControl = new PointerControl(ov, mCursorView);
        mPointerControl.disappear();
        Log.i(LOG_TAG, "X, Y: " + mPointerControl.getPointerLocation().x + ", " + mPointerControl.getPointerLocation().y);
    }

    public void init(@NonNull AccessibilityService s) {
        this.mService = s;
        mPointerControl.reset();
        timerHandler = new Handler();
        isEnabled = false;
        X1Command(false);
    }

    private void attachTimer (final int direction) {
        if (previousRunnable != null) {
            detachPreviousTimer();
        }
        previousRunnable = new Runnable() {
            @Override
            public void run() {
                mPointerControl.reappear();
                mPointerControl.move(direction, momentumStack);
                momentumStack += 1;
                timerHandler.postDelayed(this, 30);
            }
        };
        timerHandler.postDelayed(previousRunnable, 0);
    }

    /**
     * Send input via Android's gestureAPI
     * Only sends swipes
     * see {@link MouseEmulationEngine#createClick(PointF, long)} for clicking at a point
     * @param originPoint
     * @param direction
     */
    private void attachGesture (final PointF originPoint, final int direction) {
        if (previousRunnable != null) {
            detachPreviousTimer();
        }
        previousRunnable = new Runnable() {
            @Override
            public void run() {
                mPointerControl.reappear();
                mService.dispatchGesture(createSwipe(originPoint, direction, 20 + momentumStack), null, null);
                momentumStack += 1;
                timerHandler.postDelayed(this, 30);
            }
        };
        timerHandler.postDelayed(previousRunnable, 0);
    }

    private void createSwipeForSingle (final PointF originPoint, final int direction) {
        if (previousRunnable != null) {
            detachPreviousTimer();
        }
        previousRunnable = new Runnable() {
            @Override
            public void run() {
                mPointerControl.reappear();
                mService.dispatchGesture(createSwipe(originPoint, direction, 20 + momentumStack), null, null);
                momentumStack += 1;
                timerHandler.postDelayed(this, 30);
            }
        };
        timerHandler.postDelayed(previousRunnable, 0);
    }


    /**
     * Auto Disappear mouse after some duration and reset momentum
     */
    private void detachPreviousTimer () {
        if (disappearTimer != null) {
            disappearTimer.cancel();
        }
        if (previousRunnable != null) {
            timerHandler.removeCallbacks(previousRunnable);
            momentumStack = 0;
            disappearTimer = new CountDownTimer(10000, 10000) {
                @Override
                public void onTick(long l) { }

                @Override
                public void onFinish() {
                    mPointerControl.disappear();
                }
            };
            disappearTimer.start();
        }
    }

    private static GestureDescription createClick (PointF clickPoint, long duration) {
        final int DURATION = 1 + (int) duration;

        Log.i("Eric", "!!!!!! 2022.09.13 createClick " + DURATION);

        Log.i("Eric", "Actual Duration used -- " + DURATION);
        Path clickPath = new Path();
        clickPath.moveTo(clickPoint.x, clickPoint.y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, DURATION);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    private static GestureDescription createSwipe (PointF originPoint, int direction, int momentum) {
        Log.i("Eric", "!!!!!! 2022.09.13 createSwip " );
        final int DURATION = scrollSpeed + 8;
        Path clickPath = new Path();
        PointF lineDirection = new PointF(originPoint.x + momentum * PointerControl.dirX[direction], originPoint.y + momentum * PointerControl.dirY[direction]);
        clickPath.moveTo(originPoint.x, originPoint.y);
        clickPath.lineTo(lineDirection.x, lineDirection.y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, DURATION);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    public boolean perform (KeyEvent keyEvent) {

        Log.i("Eric","20220823 !!!!"+EnableForPackage+"!!! "+keyEvent.getKeyCode());


        // toggle mouse mode if going via bossKey
        if (keyEvent.getKeyCode() == bossKey && !isBossKeyDisabled && !isBossKeySetToToggle) {
            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                if (waitToChange != null) {
                    // cancel change countdown
                    waitToChange.cancel();
                    if (isEnabled) return true;
                }
            }
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                waitToChange();
                if (isEnabled){
                    isInScrollMode = !isInScrollMode;
                    if(isInScrollMode)Toast.makeText(mService, R.string.ScrollMode_E,Toast.LENGTH_SHORT).show();
                    else Toast.makeText(mService, R.string.ScrollMode_D,Toast.LENGTH_SHORT).show();
               //     Toast.makeText(mService, isInScrollMode ? "Scroll Mode: Enabled" : "Scroll Mode: Disabled",
                //            Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        }
        else if (keyEvent.getKeyCode() == bossKey && !isBossKeyDisabled && isBossKeySetToToggle) {
            // keep a three way toggle. Dpad Mode -> Mouse Mode -> Scroll Mode -> Dpad Mode
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                if (isEnabled && isInScrollMode) {
                    // Scroll Mode -> Dpad mode
                    setMouseModeEnabled(false,true);
                    isInScrollMode = false;
                } else if (isEnabled && !isInScrollMode) {
                    // Mouse Mode -> Scroll Mode
                    Toast.makeText(mService, R.string.Scroll_Mode, Toast.LENGTH_SHORT).show();
                    isInScrollMode = true;
                } else if (!isEnabled) {
                    // Dpad mode -> Mouse mode
                    setMouseModeEnabled(true,true);
                    isInScrollMode = false;
                }
            }
            // bossKey is enabled. Handle this here itself and don't let it reach system
            return true;
        }
        else if (keyEvent.getKeyCode() == bossKey && isBossKeyDisabled) {
            // bossKey is set to disabled, let system do it's thing
            return false;
        }
        // keep full functionality on full size remotes
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_INFO) {
            if (this.isEnabled) {
                // mouse already enabled, disable it and make it go away
                this.isEnabled = false;
                mPointerControl.disappear();
                X1Command(false);
                Toast.makeText(mService, R.string.Dpad_Mode, Toast.LENGTH_SHORT).show();
                return true;
            } else {
                // mouse is disabled, enable it, reset it and show it
                this.isEnabled = true;
                mPointerControl.reset();
                mPointerControl.reappear();
                X1Command(true);
                Toast.makeText(mService, "Mouse/Scroll", Toast.LENGTH_SHORT).show();
            }
        }

    //    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == 216) {
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == 87||keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == 216) {
            if (this.isEnabled) {
                int action = AccessibilityNodeInfo.ACTION_FOCUS;
               // int action2 = AccessibilityNodeInfo.ACTION_A;
                Point CurrentPoint = new Point((int) mPointerControl.getPointerLocation().x, (int) mPointerControl.getPointerLocation().y);

                Log.i("Eric","!!!! 2022.09.28 X:"+mPointerControl.getPointerLocation().x);
                Log.i("Eric","!!!! 2022.09.28 Y:"+mPointerControl.getPointerLocation().y);

                List<AccessibilityWindowInfo> windowList = mService.getWindows();

                for (AccessibilityWindowInfo window : windowList) {

                    List<AccessibilityNodeInfo> nodeHierarchy = findNode(window.getRoot(), action, CurrentPoint);
                    //2022.09.02

                    Log.i("Eric","2022.09.12 !!! :"+nodeHierarchy.size());
                    for (int i = nodeHierarchy.size() - 1; i >= 0; i--) {



                        Log.i("Eric", "20220823 Pressed A");
                        AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
                        Log.i("Eric", "2022.09.28 hitnode "+i+":"+hitNode.toString()+"|||||"+hitNode.getPackageName());
                        /*
                        if(i==nodeHierarchy.size() - 2) {
                            Log.i("Eric", "2022.09.27 Pressed A");
                            Bundle argument1=new Bundle();
                            argument1.putInt(ACTION_ARGUMENT_COLUMN_INT,4);

                            //hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(),argument1);
                            hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId());
                            Log.i("Eric", "2022.09.27 Pressed B");
                        }

                         */
                    }
                }
               // Toast.makeText(mService, "20220912 ASED"+CurrentPoint.x+":"+CurrentPoint.y, Toast.LENGTH_SHORT).show();
                return true;
            }

        }
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == 164)
        {Log.i("Eric","2022.10.18  : 164 ");
            ShellCommand("Enter Shell Command");
            return true;
        }
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == 88) {
            if (this.isEnabled) {
                int action = AccessibilityNodeInfo.ACTION_FOCUS;
                // int action2 = AccessibilityNodeInfo.ACTION_A;
                Point CurrentPoint = new Point((int) mPointerControl.getPointerLocation().x, (int) mPointerControl.getPointerLocation().y);

                List<AccessibilityWindowInfo> windowList = mService.getWindows();

                for (AccessibilityWindowInfo window : windowList) {

                    List<AccessibilityNodeInfo> nodeHierarchy = findNode(window.getRoot(), action, CurrentPoint);
                    //2022.09.02

                    Log.i("Eric","2022.09.12 !!! :"+nodeHierarchy.size());
                    for (int i = nodeHierarchy.size() - 1; i >= 0; i--) {



                        Log.i("Eric", "20220823 Pressed A");
                        AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
                        Log.i("Eric", "2022.09.12 hitnode:"+hitNode.toString()+"QQ"+hitNode.getPackageName());
                        //hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION)
                        if(i==nodeHierarchy.size() - 2) {
                            Log.i("Eric", "2022.09.27 Pressed A");
                            Bundle argument1=new Bundle();
                            argument1.putInt(ACTION_ARGUMENT_COLUMN_INT,4);

                            //hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(),argument1);
                            hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId());
                            Log.i("Eric", "2022.09.27 Pressed B");
                        }
                    }
                }
                Toast.makeText(mService, "20220912 ASED"+CurrentPoint.x+":"+CurrentPoint.y, Toast.LENGTH_SHORT).show();
                return true;
            }

        }


        if (!isEnabled) {
            // mouse is disabled, don't do anything and let the system consume this event
            return false;
        }
        boolean consumed = false;
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN){
            Log.i("Eric","20220823 !!!! KeyEvent.ACTION_DOWN:"+keyEvent.getKeyCode());

            if (scrollCodeMap.containsKey(keyEvent.getKeyCode())) {
                if (isInScrollMode || colorSet.contains(keyEvent.getKeyCode())) {

                    if (Build.MODEL.contains("ViewSonic PJ")) {  //x1 netflix scroll mode
                        Log.i("Eric", "2022.0928 !!! X1");
                        if (foregroundPackageName.contains("com.netflix.mediaclient")) {
                            Log.i("Eric", "2022.0928 !!! In NextFilx");

                            if(keyEvent.getKeyCode() ==KeyEvent.KEYCODE_F2 )
                                HandlerX1LEFTRIGT( mPointerControl.getPointerLocation(),true,false);
                            else if (keyEvent.getKeyCode() ==KeyEvent.KEYCODE_F4 )
                                HandlerX1LEFTRIGT( mPointerControl.getPointerLocation(),true,true);
                            else
                                attachGesture(mPointerControl.getPointerLocation(), scrollCodeMap.get(keyEvent.getKeyCode()));


                        }
                    }
                   else {
                        attachGesture(mPointerControl.getPointerLocation(), scrollCodeMap.get(keyEvent.getKeyCode()));
                    }

                }
                    else if (!isInScrollMode && stuckAtSide != 0 && keyEvent.getKeyCode() == stuckAtSide) {
                 Log.i("Eric","!!!!2022.09.22 Enter atside");
                     if(mPointerControl.getPointerLocation().x <=0 || mPointerControl.getPointerLocation().x>=(mPointerControl.getCenterPointOfView().x*2) ) {
                        Log.i("Eric", "!!!!***2022.09.22 LEFT Right");
                        if (mPointerControl.getPointerLocation().y >0 || mPointerControl.getPointerLocation().y<(mPointerControl.getCenterPointOfView().y*2) ) {
                            //2022.09.28  String deviceModel = Build.MODEL;  //X11-4K :85   X2000-4K:126
                            //
                            //        if(deviceModel.contains("ViewSonic PJ")) {


                            if (Build.MODEL.contains("ViewSonic PJ") ){
                                Log.i("Eric", "2022.0928 !!! X1");
                                if (foregroundPackageName.contains("com.netflix.mediaclient") ) {
                                    Log.i("Eric", "2022.0928 !!! In NextFilx");

                                    if(mPointerControl.getPointerLocation().x <=0 )
                                    HandlerX1LEFTRIGT( mPointerControl.getPointerLocation(),false,false);
                                    else
                                        HandlerX1LEFTRIGT( mPointerControl.getPointerLocation(),false,true);



                                }
                            } else {
                                Log.i("Eric", "!!!!***2022.09.22 Speciall LEFT Right");
                                //  PointF newP=new PointF(mPointerControl.getCenterPointOfView().x,mPointerControl.getPointerLocation().y);
                                PointF newP = null;
                                Boolean canSwip = true;
                                if (mPointerControl.getPointerLocation().x <= 0) {
                                   // newP = new PointF(40, mPointerControl.getPointerLocation().y);
                                    newP = new PointF(550, mPointerControl.getPointerLocation().y);  //550 for x11 disney
                                    canSwip = checknode(newP);

                                    Log.i("Eric", "2022.09.28 CanSwip?  LEFT KEY ::" + canSwip);
                                } else {
                                    newP = new PointF((mPointerControl.getCenterPointOfView().x * 2) - 5, mPointerControl.getPointerLocation().y);
                                    canSwip = checknode(newP);

                                    Log.i("Eric", "2022.09.28 CanSwip?  Right KEy ::" + canSwip);
                                }
                                //   NeedDealFocuse=false; + canSwip);
                                if (canSwip)
                                    createSwipeForSingle(newP, scrollCodeMap.get(keyEvent.getKeyCode()));
/*
                            List<AccessibilityWindowInfo> windowList = mService.getWindows();
                            int action2 = AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                            Point newP2= new Point(Integer.parseInt(String.valueOf(newP.x)),Integer.parseInt(String.valueOf(newP.y)));
                            for (AccessibilityWindowInfo window : windowList) {
                                List<AccessibilityNodeInfo> nodeHierarchy = findNode(window.getRoot(), action2, newP2);
                                //2022.09.02
                                ScrollFocus(nodeHierarchy, newP2);
                            }
                            */
                            }
                        }
                        else {// Log.i("Eric", "!!!!***2022.09.22 UP DOWN");
                            if(mPointerControl.getPointerLocation().y <=0 || mPointerControl.getPointerLocation().y>=(mPointerControl.getCenterPointOfView().y*2) ) {
                                Log.i("Eric", "!!!!***2022.09.22 LEFT Right");
                                if (mPointerControl.getPointerLocation().x >0 || mPointerControl.getPointerLocation().x<(mPointerControl.getCenterPointOfView().y*2) ) {
                                    PointF newP = null;
                                    Boolean canSwip = true;
                                    if(mPointerControl.getPointerLocation().y >0) {
                                        newP = new PointF(mPointerControl.getPointerLocation().x, 5);
                                        canSwip = checknode(newP);
                                        createSwipeForSingle(mPointerControl.getCenterPointOfView(), scrollCodeMap.get(keyEvent.getKeyCode()));
                                    }
                                else
                                    {
                                        newP = new PointF(mPointerControl.getPointerLocation().x, mPointerControl.getCenterPointOfView().y*2-5);
                                        canSwip = checknode(newP);
                                        createSwipeForSingle(mPointerControl.getCenterPointOfView(), scrollCodeMap.get(keyEvent.getKeyCode()));
                                    }
                                    if(canSwip)
                                        createSwipeForSingle(newP, scrollCodeMap.get(keyEvent.getKeyCode()));
                                   // createSwipeForSingle(mPointerControl.getCenterPointOfView(), scrollCodeMap.get(keyEvent.getKeyCode()));
                                }

                            }
                        }
                    }
                    else
                    {
                        createSwipeForSingle(mPointerControl.getCenterPointOfView(), scrollCodeMap.get(keyEvent.getKeyCode()));
                    }

                }
                else if (movementCodeMap.containsKey(keyEvent.getKeyCode()))
                    attachTimer(movementCodeMap.get(keyEvent.getKeyCode()));
                consumed = true;
            }
            else if(keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER||keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER ||
                    keyEvent.getKeyCode() == KeyEvent.KEYCODE_F5

            ) {
                // just consume this event to prevent propagation
                DPAD_Center_Init_Point = new Point((int) mPointerControl.getPointerLocation().x, (int) mPointerControl.getPointerLocation().y);
                DPAD_SELECT_PRESSED = true;
                consumed = true;
            }
        }
        else if (keyEvent.getAction() == KeyEvent.ACTION_UP ) {

            Log.i("Eric","NeedDealFocuse:"+NeedDealFocuse);

            // key released, cancel any ongoing effects and clean-up
            // since bossKey is also now a part of this stuff, consume it if events enabled
            if (actionableKeyMap.contains(keyEvent.getKeyCode())
                    || keyEvent.getKeyCode() == bossKey) {
                detachPreviousTimer();
                consumed = true;
            }
            else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER||keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER ||
                    keyEvent.getKeyCode() == KeyEvent.KEYCODE_F5

            ) {

                Log.i("Eric","***** 2022.09.22 Pressed UP !!!! pInt consumed:"+consumed);

                DPAD_SELECT_PRESSED = false;
                detachPreviousTimer();
//                if (keyEvent.getEventTime() - keyEvent.getDownTime() > 500) {
                // unreliable long click event if button was pressed for more than 500 ms
                int action = AccessibilityNodeInfo.ACTION_CLICK;

                Point pInt = new Point((int) mPointerControl.getPointerLocation().x, (int) mPointerControl.getPointerLocation().y);
                Log.i("Eric","20220823 Pressed UP !!!! pInt consumed:"+consumed);

                if (DPAD_Center_Init_Point.equals(pInt)) {

                    Log.i("Eric","20220823 Pressed !!!! DPAD_Center_Init_Point.equals(pInt)");

                    List<AccessibilityWindowInfo> windowList = mService.getWindows();
                    boolean wasIME = false, focused = false;
                    for (AccessibilityWindowInfo window : windowList) {
                        if (consumed || wasIME) {
                            break;
                        }
                        List<AccessibilityNodeInfo> nodeHierarchy = findNode(window.getRoot(), action, pInt);
                        //2022.09.02
                        if(NeedDealFocuse)
                            DealFocus(nodeHierarchy,pInt);

                        for (int i = nodeHierarchy.size() - 1; i >= 0; i--) {
                            if (consumed || focused) {
                                break;
                            }

                            Log.i("Eric","20220823 Pressed A");
                            AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
                            Log.i("Eric","20220823 Pressed A"+hitNode.getPackageName());
                            Log.i("Eric","20220823 Pressed A"+hitNode.getClassName());
                            Log.i("Eric","20220823 Pressed A"+hitNode.toString());

                            List<AccessibilityNodeInfo.AccessibilityAction> availableActions = hitNode.getActionList();
                            if (availableActions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)) {
                                Log.i("Eric","20220823 Pressed  B  ACTION_ACCESSIBILITY_FOCUS");
                                focused = hitNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                                // hitNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                                Log.i("Eric","20220823 Pressed  B  is focusable:"+hitNode.isFocusable());
                                Log.i("Eric","20220823 Pressed  B  is focused:"+hitNode.isFocused());
                            }
                            if (hitNode.isFocused() && availableActions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SELECT)) {
                                Log.i("Eric","20220823 Pressed  C  ACTION_SELECT");
                                hitNode.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                                Log.i("Eric","20220823 Pressed Select c");
                                Log.i("Eric","20220823 Pressed Select c is focusused"+hitNode.isFocused());
                            }
                            if (hitNode.isFocused() && availableActions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)) {
                                Log.i("Eric","20220823 Pressed  D  ACTION_CLICK");
                                consumed = hitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                Log.i("Eric","20220823 Pressed Click D +consumed:"+consumed);
                            }
                            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD && !(hitNode.getPackageName()).toString().contains("leankeyboard")) {

                                Log.i("Eric","2022.09.13 is Type_INPUT_METHOD");

                                if (hitNode.getPackageName().equals("com.amazon.tv.ime") && keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && helperContext != null) {
                                    InputMethodManager imm = (InputMethodManager) helperContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                                    imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                                    consumed = wasIME = true;
                                } else {
                                    Log.i("Eric","2022.09.13 is Type_INPUT_METHOD else");

                                    wasIME = true;
                                    Boolean clicked;
                                    clicked=hitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    hitNode.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                                    hitNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);

                                    Log.i("Eric","2022.09.13 is Type_INPUT_METHOD else hitnote"+hitNode.getPackageName());
                                    List<AccessibilityNodeInfo.AccessibilityAction> d=hitNode.getActionList();
                                    ;

                                    Log.i("Eric","2022.09.13 is Type_INPUT_METHOD else child"+hitNode.getChildCount());

                                    Log.i("Eric","2022.09.13 is Type_INPUT_METHOD else hitnote"+d.size());
                                    for(AccessibilityNodeInfo.AccessibilityAction e :d)
                                    {
                                        Log.i("Eric","2022.09.13+e:"+e.toString());
                                    }
                                    Log.i("Eric","2022.09.13 is Type_INPUT_METHOD else clicked"+clicked);

                                    consumed = clicked;
                                }
                                break;
                            }

                            if ((hitNode.getPackageName().equals("com.google.android.tvlauncher")
                                    && availableActions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK))) {
                                if (hitNode.isFocusable()) {
                                    focused = hitNode.performAction(AccessibilityNodeInfo.FOCUS_INPUT);
                                }
                                consumed = hitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            }
                        }
                    }
                    if (!consumed && !wasIME) {
                        mService.dispatchGesture(createClick(mPointerControl.getPointerLocation(), keyEvent.getEventTime() - keyEvent.getDownTime()), null, null);
                    }
                }
                else{
                    //Implement Drag Function here
                }
            }
            Log.i("Eric","20220823 Pressed UP FINISH");
        }
        Log.i("Eric","20220823 Pressed UP FINISH:"+consumed);

        return consumed;
    }



    //2022.09.

    private void ScrollFocus(List<AccessibilityNodeInfo> nodeHierarchy , Point pInt) {

        Log.i("Eric","2022.09.22 deal scall PX"+pInt.x+":"+pInt.y);

       // boolean setfocus=false;
        for (int i = nodeHierarchy.size() - 1; i >= 0; i--) {

             Log.i("Eric", "Node siez:" + nodeHierarchy.size() + ": i:" + i);
            AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
            List<AccessibilityNodeInfo.AccessibilityAction> availableActions = hitNode.getActionList();

            Rect Recthit = new Rect();
            hitNode.getBoundsInScreen(Recthit);
            Log.i("Eric", "Rect" + Recthit.left + ":" + Recthit.top);
            Log.i("Eric", "Rect" + Recthit.left + ":" + Recthit.bottom);
            Log.i("Eric", "Rect" + Recthit.right + ":" + Recthit.top);
            Log.i("Eric", "Rect" + Recthit.right + ":" + Recthit.bottom);
            Log.i("Eric", "POINT:Rect" + pInt.x + ":" + pInt.y);

            Log.i("Eric", "In or Out:" + Recthit.contains(pInt.x, pInt.y));

            Log.i("Eric", "OK:"+i );
            if (availableActions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT))
            {

            //    if(!setfocus) {
                 //   boolean re=hitNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
               //     boolean re2=hitNode.performAction(AccessibilityNodeInfo.ACTION_SELECT);

                    Log.i("Eric", "!!!!!!!!!1Set select:"+i+":" );
                    Log.i("Eric", "!!!!!!!!!1Set select:"+i+":" );

                 //   setfocus=true;
               // }
            }

        }

    }

    private void DealFocus(List<AccessibilityNodeInfo> nodeHierarchy , Point pInt) {

        Log.i("Eric","2022.09.02 deal focus PX"+pInt.x+":"+pInt.y);

        boolean setfocus=false;
        for (int i = nodeHierarchy.size() - 1; i >= 0; i--) {


            Log.i("Eric", "Node siez:" + nodeHierarchy.size() + ": i:" + i);
            AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
            Rect Recthit = new Rect();
            hitNode.getBoundsInScreen(Recthit);
            Log.i("Eric", "Rect" + Recthit.left + ":" + Recthit.top);
            Log.i("Eric", "Rect" + Recthit.left + ":" + Recthit.bottom);
            Log.i("Eric", "Rect" + Recthit.right + ":" + Recthit.top);
            Log.i("Eric", "Rect" + Recthit.right + ":" + Recthit.bottom);
            Log.i("Eric", "POINT:Rect" + pInt.x + ":" + pInt.y);

            Log.i("Eric", "In or Out:" + Recthit.contains(pInt.x, pInt.y));


            List<AccessibilityNodeInfo.AccessibilityAction> availableActions = hitNode.getActionList();
            if (availableActions.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS))
            {
                Log.i("Eric", "OK:"+i );
                if(!setfocus) {
                    boolean re=hitNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    boolean re2=hitNode.performAction(AccessibilityNodeInfo.ACTION_SELECT);

                    Log.i("Eric", "!!!!!!!!!1Set select:"+i+":"+re );
                    Log.i("Eric", "!!!!!!!!!1Set select:"+i+":"+re2 );

                    setfocus=true;
                }
             }
              else{
                Log.i("Eric", "NO" );

                  }

        }

    }

    public static void setMouseModeEnabled(boolean enable,Boolean show) {
        if (enable) {
            // Enable Mouse Mode
            isEnabled = true;
            isInScrollMode = false;
            mPointerControl.reset();
            mPointerControl.reappear();
            X1Command(true);
            if(show)
            Toast.makeText(mService,R.string.Mouse_Mode, Toast.LENGTH_SHORT).show();
        }
        else {
            // Disable Mouse Mode
            isEnabled = false;
            mPointerControl.disappear();
            X1Command(false);
            if(show)
             Toast.makeText(mService, R.string.Dpad_Mode, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Simple count down timer for checking keypress duration
     */
    private void waitToChange() {
        waitToChange = new CountDownTimer(800, 800) {
            @Override
            public void onTick(long l) { }
            @Override
            public void onFinish() {
                setMouseModeEnabled(!isEnabled,true);
            }
        };
        waitToChange.start();
    }


    private void waitToChange2() {
        waitToChange = new CountDownTimer(800, 3000) {
            @Override
            public void onTick(long l) { }
            @Override
            public void onFinish() {
                setMouseModeEnabled(!isEnabled,true);
            }
        };
        waitToChange.start();
    }


    //// below code is for supporting legacy devices as per my understanding of evia face cam source
    //// this is only used for long clicks here and isn't exactly something reliable
    //// leaving it in for reference just in case needed in future, because looking up face cam
    //// app's source might be a daunting task

    private List<AccessibilityNodeInfo> findNode (AccessibilityNodeInfo node, int action, Point pInt) {
        if (node == null) {
            node = mService.getRootInActiveWindow();
        }
        if (node == null) {
            Log.i(LOG_TAG, "Root Node ======>>>>>" + ((node != null) ? node.toString() : "null"));
        }
        List<AccessibilityNodeInfo> nodeInfos = new ArrayList<>();
        Log.i(LOG_TAG, "Node found ?" + ((node != null) ? node.toString() : "null"));
        node = findNodeHelper(node, action, pInt, nodeInfos);
        Log.i(LOG_TAG, "Node found ?" + ((node != null) ? node.toString() : "null"));
        Log.i(LOG_TAG, "Number of Nodes ?=>>>>> " + nodeInfos.size());
        return nodeInfos;



    }

    private AccessibilityNodeInfo findNodeHelper (AccessibilityNodeInfo node, int action, Point pInt, List<AccessibilityNodeInfo> nodeList) {
        if (node == null) {
            return null;
        }
        Rect tmp = new Rect();
        node.getBoundsInScreen(tmp);
        if (!tmp.contains(pInt.x, pInt.y)) {
            // node doesn't contain cursor`
            return null;
        }
        // node contains cursor, add to node hierarchy
        nodeList.add(node);
        AccessibilityNodeInfo result = null;
        result = node;

//        if ((node.getActions() & action) != 0 && node != null) {
//            // possible to use this one, but keep searching children as well
//            nodeList.add(node);
//        }
        int childCount = node.getChildCount();
        for (int i=0; i<childCount; i++) {
            AccessibilityNodeInfo child = findNodeHelper(node.getChild(i), action, pInt, nodeList);
            if (child != null) {
                // always picks the last innermost clickable child
                result = child;
            }
        }
        return result;
    }


    public static void ShellCommand(String command)
    {

        Log.i("Eric","2022.10.18  Enter Shellcommand ");

            String deviceModel = Build.MODEL;  //X11-4K :85   X2000-4K:126


            BufferedReader reader = null;
            StringBuffer buffer = new StringBuffer();
            String temp;

            String EnterMouse = "setprop persist.sys.mouse.enable true";
            String LeaveMouse = "setprop persist.sys.mouse.enable false";
            String A_Key="input keyevent 96";

            Log.i("Eric", "Enable Shell Command:"+command+"::"+A_Key);
            try {
                //screencap -p /sdcard/Download/s1.png
                //Process p =

             //   Process p = Runtime.getRuntime().exec("su");
                Process p1 = null;

                    p1 = Runtime.getRuntime().exec(A_Key);

                Log.i("Eric", "!!!!: X1 command Finish");
            } catch (Exception e) {
                Log.i("Eric", "!!!!: X1 command error :" + e.toString());
            }

    }

    public static void X1Command(Boolean mouse)
    {

        String deviceModel = Build.MODEL;  //X11-4K :85   X2000-4K:126

        if(deviceModel.contains("ViewSonic PJ")) {
            BufferedReader reader = null;
            StringBuffer buffer = new StringBuffer();
            String temp;

            String EnterMouse = "setprop persist.sys.mouse.enable true";
            String LeaveMouse = "setprop persist.sys.mouse.enable false";

            Log.i("Eric", "Enable X1 Command");
            try {
                //screencap -p /sdcard/Download/s1.png
                //Process p =

                Process p = Runtime.getRuntime().exec("su");
                Process p1 = null;
                if (mouse)

                    p1 = Runtime.getRuntime().exec(EnterMouse);
                else
                    p1 = Runtime.getRuntime().exec(LeaveMouse);
/*
            reader = null;
            buffer = new StringBuffer();
             reader = new BufferedReader(new InputStreamReader(p1.getInputStream()));
            while ((temp = reader.readLine()) != null) {
                buffer.append(temp);
                buffer.append("\n");
            }
*/
                Log.i("Eric", "!!!!: X1 command Finish");
            } catch (Exception e) {
                Log.i("Eric", "!!!!: X1 command error :" + e.toString());
            }
        }
    }


    public AccessibilityNodeInfo checknodeInfo(PointF checkpoint, int level) {
        int action = AccessibilityNodeInfo.ACTION_FOCUS;
        //int action = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        Point CurrentPoint = new Point((int) checkpoint.x, (int) checkpoint.y);

        List<AccessibilityWindowInfo> windowList = mService.getWindows();
        AccessibilityNodeInfo hitNode1=null;
        for (AccessibilityWindowInfo window : windowList) {
            // checkpoint
            List<AccessibilityNodeInfo> nodeHierarchy = findNode(window.getRoot(), action, CurrentPoint);
            //2022.09.02

            Log.i("Eric", "2022.09.12 !!! :" + nodeHierarchy.size());
            /*
            for (int i = nodeHierarchy.size() - 1; i >=0 ; i--) {



                Log.i("Eric", "2022.09.23  Check ");
                AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
                Log.i("Eric", "2022.09.23 hitnode:"+hitNode.isFocusable()+"::!!::"+hitNode.getPackageName());

                Log.i("Eric", "2022.09.23 hitnode:"+hitNode.toString()+"::!!::"+hitNode.getPackageName());

                //hitNode.getParent().getChildCount();
            }
            */

             hitNode1 = nodeHierarchy.get(nodeHierarchy.size() - level);
           // return hitNode1;
        }
        //if(hitNode1!=null)
        return hitNode1;

    }
        public  int checknodelevel(PointF checkpoint) {
            int action = AccessibilityNodeInfo.ACTION_FOCUS;
            //int action = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            Point CurrentPoint = new Point((int) checkpoint.x, (int) checkpoint.y);

            List<AccessibilityWindowInfo> windowList = mService.getWindows();
            List<AccessibilityNodeInfo> nodeHierarchy=null;
            for (AccessibilityWindowInfo window : windowList) {
                // checkpoint
                 nodeHierarchy = findNode(window.getRoot(), action, CurrentPoint);
                //2022.09.02

                Log.i("Eric", "2022.09.12 !!! :" + nodeHierarchy.size());
            /*
            for (int i = nodeHierarchy.size() - 1; i >=0 ; i--) {



                Log.i("Eric", "2022.09.23  Check ");
                AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
                Log.i("Eric", "2022.09.23 hitnode:"+hitNode.isFocusable()+"::!!::"+hitNode.getPackageName());

                Log.i("Eric", "2022.09.23 hitnode:"+hitNode.toString()+"::!!::"+hitNode.getPackageName());

                //hitNode.getParent().getChildCount();
            }
            */

                //  AccessibilityNodeInfo hitNode1 = nodeHierarchy.get(nodeHierarchy.size() - 1);



            }
            return nodeHierarchy.size();
        }



    private Boolean checknode(PointF checkpoint )
    {
        int action = AccessibilityNodeInfo.ACTION_FOCUS;
        //int action = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        Point CurrentPoint = new Point((int) checkpoint.x, (int) checkpoint.y);

        List<AccessibilityWindowInfo> windowList = mService.getWindows();

        for (AccessibilityWindowInfo window : windowList) {
            // checkpoint
            List<AccessibilityNodeInfo> nodeHierarchy = findNode(window.getRoot(), action, CurrentPoint);
            //2022.09.02

            Log.i("Eric", "2022.09.12 !!! :" + nodeHierarchy.size());
            /*
            for (int i = nodeHierarchy.size() - 1; i >=0 ; i--) {



                Log.i("Eric", "2022.09.23  Check ");
                AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
                Log.i("Eric", "2022.09.23 hitnode:"+hitNode.isFocusable()+"::!!::"+hitNode.getPackageName());

                Log.i("Eric", "2022.09.23 hitnode:"+hitNode.toString()+"::!!::"+hitNode.getPackageName());

                //hitNode.getParent().getChildCount();
            }
            */

            AccessibilityNodeInfo hitNode1 = nodeHierarchy.get(nodeHierarchy.size() - 1);
            AccessibilityNodeInfo hitNode2 = nodeHierarchy.get(nodeHierarchy.size() - 2);

            if (hitNode1.isScrollable() == false) {
                return hitNode2.isScrollable();
            }
        }
            return true;
        }

    private void HandlerX1LEFTRIGT(PointF nowPF,Boolean ScrollMode,Boolean Right) {

        Log.i("Eric","!!!! 2022.09.28 current:"+(int)nowPF.x);
        Log.i("Eric","!!!! 2022.09.28 current:"+(int)nowPF.y);


        List<AccessibilityWindowInfo> windowList = mService.getWindows();

        for (AccessibilityWindowInfo window : windowList) {

            int action = AccessibilityNodeInfo.ACTION_FOCUS;
            Point nowP=null;
            if(!ScrollMode) {
                if(!Build.MODEL.contains("X2000-4K")) {
                    if (nowPF.x >= mPointerControl.getCenterPointOfView().x * 2)
                        nowP = new Point((int) nowPF.x - 3, (int) nowPF.y);
                    else if (nowPF.x <= 0)
                        nowP = new Point((int) nowPF.x + 3, (int) nowPF.y);
                }
                else
                {
                    if (nowPF.x >= mPointerControl.getCenterPointOfView().x * 2)
                        nowP = new Point((int) nowPF.x - 10, (int) nowPF.y);
                    else if (nowPF.x <= 0)
                        nowP = new Point((int) nowPF.x + 130, (int) nowPF.y);
                }
            }
            else
            {
                nowP = new Point((int) nowPF.x, (int) nowPF.y);

            }
            List<AccessibilityNodeInfo> nodeHierarchy = findNode(window.getRoot(), action, nowP);
            //2022.09.02

            Log.i("Eric","2022.09.12 !!! :"+nodeHierarchy.size());
            for (int i = nodeHierarchy.size() - 1; i >= 0; i--) {



                Log.i("Eric", "20220823 Pressed A");
                AccessibilityNodeInfo hitNode = nodeHierarchy.get(i);
                Log.i("Eric", "2022.09.12 hitnode "+i+":"+hitNode.toString()+"|||||"+hitNode.getPackageName());

                if(hitNode.isScrollable()) {
                    if(hitNode.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)) {
                        Log.i("Eric", "2022.09.29  " + i + ":" + hitNode.getClass() + "|||||");
                        Log.i("Eric", "2022.09.29  " + i + ":" + hitNode.getActionList() + "|||||");

                        if(Right) {
                            hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId());
                            Log.i("Eric", "2022.09.29 Scroll Right");
                            break;
                        }
                    }if(hitNode.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)) {
                        Log.i("Eric", "2022.09.29  " + i + ":" + hitNode.getClass() + "|||||");
                        Log.i("Eric", "2022.09.29  " + i + ":" + hitNode.getActionList() + "|||||");

                        if(!Right) {
                            hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.getId());
                            Log.i("Eric", "2022.09.29 Scroll LeFT");
                            break;
                        }
                    }
                }

                //hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION)
               /*
                if(i==nodeHierarchy.size() - 2) {
                    Log.i("Eric", "2022.09.27 Pressed A");
                    Bundle argument1=new Bundle();
                    argument1.putInt(ACTION_ARGUMENT_COLUMN_INT,4);

                    //hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(),argument1);
                    hitNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.getId());
                    Log.i("Eric", "2022.09.27 Pressed B");
                }

                */
            }
        }

    }

    /** Not used
     * Letting this stay here just in case the code needs porting back to an obsolete version
     * sometime in future
     //    private void attachActionable (final int action, final AccessibilityNodeInfo node) {
     //        if (previousRunnable != null) {
     //            detachPreviousTimer();
     //        }
     //        previousRunnable = new Runnable() {
     //            @Override
     //            public void run() {
     //                mPointerControl.reappear();
     //                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
     //                node.performAction(action);
     //                node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
     //                timerHandler.postDelayed(this, 30);
     //            }
     //        };
     //        timerHandler.postDelayed(previousRunnable, 0);
     //    }
     **/
}
