package com.viewsonic.remotemouse.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.graphics.PointF;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.viewsonic.remotemouse.BuildConfig;
import com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine;
import com.viewsonic.remotemouse.engine.impl.PointerControl;
import com.viewsonic.remotemouse.helper.Helper;
import com.viewsonic.remotemouse.helper.KeyDetection;
import com.viewsonic.remotemouse.view.OverlayView;


import  static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.NeedDealFocuse;
//import static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.checknodeInfo;
//import static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.checknodelevel;
import  static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.isEnabled;
import static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.bossKey;
import static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.scrollSpeed;
import static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.setMouseModeEnabled;


import static com.viewsonic.remotemouse.gui.MainActivity.FinalBossKey;
import static com.viewsonic.remotemouse.gui.MainActivity.checksystem;

public class MouseEventService extends AccessibilityService {

    private MouseEmulationEngine mEngine;
    private static String TAG_NAME = "VSRM_SERVICE";

    public static String foregroundPackageName;
    public  static Boolean EnableForPackage;
    public  static Boolean InMouseMode;
    int oldbosskey;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {

        if(accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        {
            ComponentName cName = new ComponentName(accessibilityEvent.getPackageName().toString(),
                    accessibilityEvent.getClassName().toString());

            Log.i("Eric", "!!!!!! 0912 cNAME:" + cName);
        }


        if (accessibilityEvent.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            /*
             * 如果 与 DetectionService 相同进程，直接比较 foregroundPackageName 的值即可
             * 如果在不同进程，可以利用 Intent 或 bind service 进行通信
             */
            foregroundPackageName = accessibilityEvent.getPackageName().toString();

            /*
             * 基于以下还可以做很多事情，比如判断当前界面是否是 Activity，是否系统应用等，
             * 与主题无关就不再展开。
             */
            ComponentName cName = new ComponentName(accessibilityEvent.getPackageName().toString(),
                    accessibilityEvent.getClassName().toString());

            //!!!! 2022.10.04

            Log.i("Eric", "!!!!!! 0831 cNAME:" + cName);



            NeedDealFocuse=false;
             if(cName.toString().contains("com.lcgx.home/com.lcgx.home.activity.AppsCenterActivity"))
            {
                Log.i("Eric", "!!!!!! 0902  X11 :" + cName);
                NeedDealFocuse=true;
            }
             else
             {
                 NeedDealFocuse=false;
             }

             if(cName.toString().contains("com.android.settings/com.android.settings.SubSettings"))
             {
                Log.i("Eric","10.4 Start Check");
                 PointF d=null;
                 if(Build.MODEL.contains("ViewSonic PJ"))
                     d=new PointF(1700,162);

                 else
                     d=new PointF(1840,70);

                 AccessibilityNodeInfo node=mEngine.checknodeInfo(d,1);

                 Log.i("Eric","10.4 Start level="+mEngine.checknodelevel(d));
                 Log.i("Eric","10.4 Start node.class="+node.toString());
                 if(mEngine.checknodelevel(d)==4) {
                     if (node.toString().contains("className: android.widget.Switch")) {
                         if (!isEnabled) {
                             EnableForPackage = true;

                             setMouseModeEnabled(true, false);
                             InMouseMode = true;
                         }

                     }
                 }
                 else
                 {
                     if (isEnabled&&EnableForPackage) {
                         EnableForPackage = false;

                         setMouseModeEnabled(false, false);
                         InMouseMode = false;
                     }


                 }


             }

             if(cName.toString().contains("com.netflix.mediaclient/com.netflix.mediaclient.ui.player.PlayerActivity"))//x2000
             {   Log.i("Eric", "2022.10.04 play close");
                 setMouseModeEnabled(false,false);
                 InMouseMode=false;
                 Log.i("Eric", "2022.10.04 play close");
                 isEnabled=false;
             }


            if (cName.toString().contains("com.netflix.mediaclient/com.netflix.mediaclient.ui.home.HomeActivity") ||cName.toString().contains("com.netflix.mediaclient/com.netflix.mediaclient.ui.signup.SignupActivity")
            || /* cName.toString().contains("com.netflix.mediaclient/android.widget.FrameLayout") ||*/cName.toString().contains("com.netflix.mediaclient/com.netflix.mediaclient.ui.signup.SignupActivity")
            ||  cName.toString().contains("com.android.chrome/org.chromium.chrome.browser.ChromeTabbedActivity")    //Chrome
             || cName.toString().contains("com.netflix.mediaclient/com.netflix.mediaclient.ui.details.ShowDetailsActivity") //com.netflix.mediaclient/com.netflix.mediaclient.ui.details.ShowDetailsActivity
                //
            ) {
                Log.i("Eric", "Oldbossley => "+bossKey);
                Log.i("Eric", "FinalBossKey => "+FinalBossKey);

                String deviceModel= Build.MODEL;
                FinalBossKey=0;

                //  Log.i("Eric","2022.09.06"+deviceModel+":"+deviceModel.length());
                if(deviceModel.contains("X11-4K")) {
                    FinalBossKey= 88;
                    bossKey=88;
                    Log.i("Eric","!!!!! 2022.0906 X11 change"+FinalBossKey);
                }
                else if(deviceModel.contains("X2000-4K")) {
                    FinalBossKey= 166;
                    bossKey=166;
                    Log.i("Eric","!!!!! 2022.0906 X11 change"+FinalBossKey);
                }
                else if(deviceModel.contains("ViewSonic PJ"))
                {
                    FinalBossKey= 214;
                    bossKey=214;

                    //141 靜音 142 Play  136 settingS
                    Log.i("Eric","!!!!! 2022.0906 X change"+FinalBossKey);
                }//ViewSonic PJ X1 2

                if(!isEnabled) {
                    EnableForPackage = true;

                    setMouseModeEnabled(true, false);
                    InMouseMode=true;
                    Log.i("Eric", "2022.10.04 netflex open");
                }
            }
            else if (cName.toString().contains("com.ktcp.launcher/com.ktcp.launcher.activity.LauncherHomeActivity")||cName.toString().contains("com.ktcp.launcher/com.ktcp.launcher.activity.LauncherAppActivity")      //X2000 QQ x11
            ||cName.toString().contains("com.lcgx.home/com.lcgx.home.activity.AppsCenterActivity")||cName.toString().contains("com.lcgx.home/com.lc.mylibrary.widget.InputSourceDialog")                       //X1
                    ||cName.toString().contains("com.appo.launcher7/com.appo.launcher7.ui.activities.AppManageActivity")||cName.toString().contains("com.appo.launcher7/com.appo.launcher7.ui.activities.MainActivity")//com.appo.launcher7/com.appo.launcher7.ui.activities.AppManageActivity  x2000 WW
                    ||cName.toString().contains("com.lcgx.home/com.lcgx.home.TvLauncherMainActivity")||cName.toString().contains("com.lc.appstore/com.lc.appstore.ui.activity.AppDetailsActivity")//com.appo.launcher7/com.appo.launcher7.ui.activities.AppManageActivity  X11 WW  //com.lcgx.home/com.lcgx.home.TvLauncherMainActivity
                    || cName.toString().contains("com.geniatech.glauncher/com.geniatech.glauncher.AppActivity") ||  cName.toString().contains("com.geniatech.glauncher/com.geniatech.glauncher.GLauncher")//com.geniatech.glauncher/com.geniatech.glauncher.AppActivity x1 WW com.geniatech.glauncher/com.geniatech.glauncher.GLauncher




            )
            {
                String deviceModel= Build.MODEL;
                FinalBossKey=0;

                //  Log.i("Eric","2022.09.06"+deviceModel+":"+deviceModel.length());
                if(deviceModel.contains("X11-4K")) {
                    FinalBossKey= 88;
                    bossKey=88;
                    Log.i("Eric","!!!!! 2022.0906 X11 change"+FinalBossKey);
                }
                else if(deviceModel.contains("X2000-4K")) {
                    FinalBossKey= 166;
                    bossKey=166;
                    Log.i("Eric","!!!!! 2022.0906 X11 change"+FinalBossKey);
                }
                else if(deviceModel.contains("ViewSonic PJ"))
                {
                    FinalBossKey= 214;
                    bossKey=214;
                    Log.i("Eric","!!!!! 2022.0906 X change"+FinalBossKey);
                }//ViewSonic PJ X1 2
                if (EnableForPackage) {
                   EnableForPackage = false;

                    setMouseModeEnabled(false,false);
                    InMouseMode=false;
                }

            }
            else if(cName.toString().contains("com.android.inputmethod.pinyin/android.inputmethodservice.SoftInputWindow"))
            {
                setMouseModeEnabled(false,false);
            }
            else if(!cName.toString().contains("com.android.inputmethod.pinyin/android.inputmethodservice.SoftInputWindow"))
            {
              //  if(InMouseMode)setMouseModeEnabled(true,false);

            }



        }


    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        super.onKeyEvent(event);
        new KeyDetection(event);
        Log.i("Eric", "Eric  Received Key => " + event.getKeyCode() + ", Action => " + event.getAction() + ", Repetition value => " + event.getRepeatCount() + ", Scan code => " + event.getScanCode());
        if (Helper.isAnotherServiceInstalled(this) &&
                event.getKeyCode() == KeyEvent.KEYCODE_HOME) return true;
        if (Helper.isOverlayDisabled(this)) return false;



        return mEngine.perform(event);
    }

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG_NAME, "Starting service initialization sequence. App version " + BuildConfig.VERSION_NAME);
        bossKey = KeyEvent.KEYCODE_VOLUME_MUTE;
        //PointerControl.isBordered = Helper.getMouseBordered(this);
        PointerControl.isBordered = true;
        scrollSpeed = Helper.getScrollSpeed(this);
        MouseEmulationEngine.isBossKeyDisabled = Helper.isBossKeyDisabled(this);
        MouseEmulationEngine.isBossKeySetToToggle = Helper.isBossKeySetToToggle(this);
        if (Helper.isOverriding(this)) bossKey = Helper.getBossKeyValue(this);
        if (Settings.canDrawOverlays(this)) init();
    }

    private void init() {
        if (Helper.helperContext != null) Helper.helperContext = this;
        OverlayView mOverlayView = new OverlayView(this);
        AccessibilityServiceInfo asi = this.getServiceInfo();
        if (asi != null) {
            asi.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            this.setServiceInfo(asi);
        }

        Log.i(TAG_NAME, "Configuration -- Scroll Speed " + scrollSpeed);
        Log.i(TAG_NAME, "Configuration -- Boss Key Disabled " + MouseEmulationEngine.isBossKeyDisabled);
        Log.i(TAG_NAME, "Configuration -- Boss Key Toggleable " + MouseEmulationEngine.isBossKeySetToToggle);
        Log.i(TAG_NAME, "Configuration -- Is Bordered " + PointerControl.isBordered);
        Log.i("Eric", "!!!!! Configuration -- Boss Key value " + bossKey);

        checksystem();

        Log.i("Eric", "!!!!! Configuration -- 2 Boss Key value " + bossKey);


        mEngine = new MouseEmulationEngine(this, mOverlayView);
        EnableForPackage=false;
        mEngine.init(this);
    }
}