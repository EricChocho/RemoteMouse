package com.viewsonic.remotemouse.engine.impl;

import static com.viewsonic.remotemouse.gui.MainActivity.deviceModelF;

import android.graphics.PointF;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.viewsonic.remotemouse.view.MouseCursorView;
import com.viewsonic.remotemouse.view.OverlayView;

public class PointerControl {
    private static String LOG_TAG = "POINTER_CONTROL";

    // constants
    public static int LEFT = 0;
    public static int UP = 1;
    public static int RIGHT = 2;
    public static int DOWN = 3;

    // Left, Up, Right, Down
    public static int[] dirX = {-1, 0, 1,  0};
    public static int[] dirY = { 0, -1, 0, 1};

    // pointer location in screen coordinates
    private final PointF mPointerLocation = new PointF();

    // view to display the pointer
    private final OverlayView mPointerLayerView;

    // cursor view
    private final MouseCursorView mCursorView;

    // constructor
    PointerControl(@NonNull OverlayView pv, MouseCursorView mv){
        mPointerLayerView = pv;
        mCursorView = mv;
        reset();
    }

    /**
     * Reset pointer location by centering it
     */
    public void reset () {
        mCursorView.updateFromPreferences();
        mPointerLocation.x = mPointerLayerView.getWidth() / 2f;
        mPointerLocation.y = mPointerLayerView.getHeight() / 2f;
        Log.i(LOG_TAG, "View W, H: " + mPointerLayerView.getWidth() + " " + mPointerLayerView.getHeight());
        Log.i(LOG_TAG, "Location X, Y: " + mPointerLocation.x + " " + mPointerLocation.y);
        mCursorView.updatePosition(mPointerLocation);
    }

    public void disappear () {
        if (mCursorView.getVisibility() == View.VISIBLE) {
            mCursorView.setVisibility(View.INVISIBLE);
        }
    }

    public void reappear () {
        if (mCursorView.getVisibility() == View.INVISIBLE) {
            mCursorView.setVisibility(View.VISIBLE);
        }
    }

    public static boolean isBordered = false;

    public void move (int direction, int momentum) {

        Log.i("Eric","2022.09.22 !! Checkdevice:"+ deviceModelF);
        if(deviceModelF.equals("ViewSonic PJ"))
            Log.i("Eric","2022.09.22 !! equal"+ deviceModelF);


        int movementX = (int) (dirX[direction] * ((momentum)));
        int movementY = (int) (dirY[direction] * ((momentum)));

        // free mode
        if (!isBordered) {
            mPointerLocation.x += movementX;
            if (mPointerLocation.x > mPointerLayerView.getWidth()) {
                mPointerLocation.x -= mPointerLayerView.getWidth();
            } else if (mPointerLocation.x < 0) {
                mPointerLocation.x += mPointerLayerView.getWidth();
            }

            mPointerLocation.y += movementY;
            if (mPointerLocation.y > mPointerLayerView.getHeight()) {
                mPointerLocation.y -= mPointerLayerView.getHeight();
            } else if (mPointerLocation.y < 0) {
                mPointerLocation.y += mPointerLayerView.getHeight();
            }
        }

        else {
            MouseEmulationEngine.stuckAtSide = 0;

            if (mPointerLocation.x + movementX >= 0 && mPointerLocation.x + movementX <= mPointerLayerView.getWidth())
                mPointerLocation.x += movementX;
            else {
                if (mPointerLocation.x < (mPointerLayerView.getWidth() / 2f)) {
                    MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_DPAD_LEFT;
                    if(deviceModelF.equals("ViewSonic PJ"))
                        MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_F2;

                    mPointerLocation.x = 0;
                }
                else {
                    MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_DPAD_RIGHT;
                    if(deviceModelF.equals("ViewSonic PJ"))
                        MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_F4;
                    mPointerLocation.x = mPointerLayerView.getWidth();
                }
            }
            if (mPointerLocation.y + movementY >= 0 && mPointerLocation.y + movementY <= mPointerLayerView.getHeight())
                mPointerLocation.y += movementY;
            else {
                if (mPointerLocation.y < (mPointerLayerView.getHeight() / 2f)) {
                    MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_DPAD_UP;
                    if(deviceModelF.equals("ViewSonic PJ"))
                        MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_F1;
                    mPointerLocation.y = 0;
                } else {
                    MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_DPAD_DOWN;
                    if(deviceModelF.equals("ViewSonic PJ"))
                        MouseEmulationEngine.stuckAtSide = KeyEvent.KEYCODE_F3;
                    mPointerLocation.y = mPointerLayerView.getHeight();
                }
            }
        }

        mCursorView.updatePosition(mPointerLocation);
    }

    @NonNull
    PointF getPointerLocation() {
        return mPointerLocation;
    }

    PointF getCenterPointOfView() {
        Log.i("Eric","!!!### 2022.09.22 in Get mPointerLayerView.getWidth():"+mPointerLayerView.getWidth());
        Log.i("Eric","!!!### 2022.09.22 in Get mPointerLayerView.getHight():"+mPointerLayerView.getHeight());
     //   return new PointF(mPointerLayerView.getWidth() / 2f,mPointerLayerView.getWidth() / 2f);
        return new PointF(mPointerLayerView.getWidth() / 2f,mPointerLayerView.getHeight() / 2f);
    }
}
