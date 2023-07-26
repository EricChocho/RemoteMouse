package com.viewsonic.remotemouse.gui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

//import com.viewsonic.remotemouse.gui.BuildConfig;
import com.viewsonic.remotemouse.R;
import com.viewsonic.remotemouse.helper.Helper;
import com.viewsonic.remotemouse.helper.KeyDetection;

import static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.bossKey;
import static com.viewsonic.remotemouse.engine.impl.MouseEmulationEngine.scrollSpeed;
import static com.viewsonic.remotemouse.helper.KeyDetection.changeBossKey;
import static com.viewsonic.remotemouse.services.MouseEventService.InMouseMode;

import java.security.PublicKey;

public class MainActivity extends AppCompatActivity {

    CountDownTimer repopulate;
    CheckBox cb_mouse_bordered, cb_disable_bossKey, cb_behaviour_bossKey;
    TextView gui_acc_perm, gui_acc_serv, gui_overlay_perm, gui_overlay_serv, gui_about;

    EditText et_override;
    Button bt_saveBossKeyValue;

    Spinner sp_mouse_icon;
    SeekBar dsbar_mouse_size;
    SeekBar dsbar_scroll_speed;

    public static int FinalBossKey=0;
    public static int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 701;
    public static int ACTION_ACCESSIBILITY_PERMISSION_REQUEST_CODE = 702;

    public static String deviceModelF ;
    @Override


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checksystem();

        Helper.helperContext = this;
        gui_acc_perm = findViewById(R.id.gui_acc_perm);
        gui_acc_serv = findViewById(R.id.gui_acc_serv);
        gui_overlay_perm = findViewById(R.id.gui_overlay_perm);
        gui_overlay_serv = findViewById(R.id.gui_overlay_serv);
        gui_about = findViewById(R.id.gui_about);

        bt_saveBossKeyValue = findViewById(R.id.bt_saveBossKey);
        et_override = findViewById(R.id.et_override);

        cb_mouse_bordered = findViewById(R.id.cb_border_window);
        cb_disable_bossKey = findViewById(R.id.cb_disable_bossKey);
        cb_behaviour_bossKey = findViewById(R.id.cb_behaviour_bossKey);

        sp_mouse_icon = findViewById(R.id.sp_mouse_icon);
        dsbar_mouse_size = findViewById(R.id.dsbar_mouse_size);
        dsbar_scroll_speed = findViewById(R.id.dsbar_mouse_scspeed);

        // don't like to advertise in the product, but need to mention here
        // need to increase visibility of the open source version
        gui_about.setText("VS Remote Mouse v  BuildConfig.VERSION_NAME  \nThis is an open source project. It's available for free and will always be. If you find issues / would like to help in improving this project, please contribute at \nhttps://github.com/virresh/matvt");

                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            IconStyleSpinnerAdapter iconStyleSpinnerAdapter = new IconStyleSpinnerAdapter(this, R.layout.spinner_icon_text_gui, R.id.textView, IconStyleSpinnerAdapter.getResourceList());
        // render icon style dropdown
        IconStyleSpinnerAdapter iconStyeSpinnerAdapter = new IconStyleSpinnerAdapter(this, R.layout.spinner_icon_text_gui, R.id.textView, IconStyleSpinnerAdapter.getResourceList());
        sp_mouse_icon.setAdapter(iconStyleSpinnerAdapter);


        checkValues(iconStyleSpinnerAdapter);

        bt_saveBossKeyValue.setOnClickListener(view -> {
            String dat = et_override.getText().toString();
            dat = dat.replaceAll("[^0-9]", "");
            int keyValue; if (dat.isEmpty()) keyValue = KeyEvent.KEYCODE_VOLUME_MUTE;
            else keyValue = Integer.parseInt(dat);
            isBossKeyChanged();
            Helper.setOverrideStatus(this, isBossKeyChanged());
            Helper.setBossKeyValue(this, keyValue);
            bossKey = keyValue;
            Toast.makeText(this, "New Boss key is : "+keyValue, Toast.LENGTH_SHORT).show();
        });


        populateText();
        findViewById(R.id.gui_setup_perm).setOnClickListener(view -> askPermissions());
        InMouseMode=false;
    }  //onCreate()

   public static void checksystem()
   {
       String deviceModel = Build.MODEL;  //X11-4K :85   X2000-4K:126
       deviceModelF=Build.MODEL;
       String deviceBrand = Build.BRAND;
       String device=Build.DEVICE;
       FinalBossKey=0;

     //  Log.i("Eric","2022.09.06"+deviceModel+":"+deviceModel.length());
       if(deviceModel.contains("X11-4K")) {
           FinalBossKey= 88;   //88 = previous track  85 Play 176 settings
           bossKey=88;
           Log.i("Eric","!!!!! 2022.0906 X11 change"+FinalBossKey);
       }
       else if(deviceModel.contains("X2000-4K")) {
           FinalBossKey= 166; //88 = previous track  126 Play 82 settings
           bossKey=166;
           Log.i("Eric","!!!!! 2022.0906 X2000 change"+FinalBossKey);
       }
       else if(deviceModel.contains("ViewSonic PJ"))
       {
           FinalBossKey= 214;
           bossKey=214;
           Log.i("Eric","!!!!! 2022.0906 X change"+FinalBossKey);
       }//ViewSonic PJ X1 2
       else
       {
           FinalBossKey= 0;
           Log.i("Eric","!!!!! 2022.0906 not change"+bossKey);
       }
       Log.i("Eric","!!!!! 2022.09.06 model"+deviceModel+":"+deviceBrand+":"+device+":"+bossKey);
       //TelephonyManag
       // er manager = getSystemService(Context.TELEPHONY_SERVICE);
      // String imei = manager.getDeviceId();
   }

    public void populateText() {


        findViewById(R.id.BossLayout).setVisibility(View.GONE);
        findViewById(R.id.BossLayout2).setVisibility(View.GONE);
        findViewById(R.id.BossLayout3).setVisibility(View.GONE);

        if (Helper.isOverlayDisabled(this))  gui_overlay_perm.setText(R.string.perm_overlay_denied);
        else gui_overlay_perm.setText(R.string.perm_overlay_allowed);

        if (Helper.isAccessibilityDisabled(this)) {
            gui_acc_perm.setText(R.string.perm_acc_denied);
            gui_acc_serv.setText(R.string.serv_acc_denied);
            gui_overlay_serv.setText(R.string.serv_overlay_denied);


        }
        else gui_acc_perm.setText(R.string.perm_acc_allowed);

        if (Helper.isAccessibilityDisabled(this) && Helper.isOverlayDisabled(this)) {
            gui_acc_perm.setText(R.string.perm_acc_denied);
            gui_acc_serv.setText(R.string.serv_acc_denied);
            gui_overlay_perm.setText(R.string.perm_overlay_denied);
            gui_overlay_serv.setText(R.string.serv_overlay_denied);
        }

        if (!Helper.isAccessibilityDisabled(this) && !Helper.isOverlayDisabled(this)) {
            gui_acc_perm.setText(R.string.perm_acc_allowed);
            gui_acc_serv.setText(R.string.serv_acc_allowed);
            gui_overlay_perm.setText(R.string.perm_overlay_allowed);
            gui_overlay_serv.setText(R.string.serv_overlay_allowed);
            findViewById(R.id.gui_setup_perm).setVisibility(View.GONE);
            if(FinalBossKey==0) {
                findViewById(R.id.BossLayout).setVisibility(View.VISIBLE);
            }
            else {
                findViewById(R.id.BossLayout2).setVisibility(View.VISIBLE);
                findViewById(R.id.BossLayout3).setVisibility(View.VISIBLE);
            }
        }
    }

    private boolean isBossKeyChanged() {
        return Helper.getBossKeyValue(this) != 164;
    }
    private void checkValues(IconStyleSpinnerAdapter adapter) {
        Context ctx = getApplicationContext();
        String val = String.valueOf(Helper.getBossKeyValue(ctx));
        et_override.setText(val);
        String iconStyle = Helper.getMouseIconPref(ctx);
        sp_mouse_icon.setSelection(adapter.getPosition(iconStyle));

        int mouseSize = Helper.getMouseSizePref(ctx);
        dsbar_mouse_size.setProgress(Math.max(Math.min(mouseSize, dsbar_mouse_size.getMax()), 0));

        int scrollSpeed = Helper.getScrollSpeed(ctx);
        dsbar_scroll_speed.setProgress(Math.max(Math.min(scrollSpeed, dsbar_scroll_speed.getMax()), 0));

        boolean bordered = Helper.getMouseBordered(ctx);
       // cb_mouse_bordered.setChecked(bordered);
        cb_mouse_bordered.setChecked(true);


        boolean bossKeyStatus = Helper.isBossKeyDisabled(ctx);
        cb_disable_bossKey.setChecked(bossKeyStatus);

        boolean bossKeyBehaviour = Helper.isBossKeySetToToggle(ctx);
        cb_behaviour_bossKey.setChecked(bossKeyBehaviour);
    } @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
            if (Helper.isOverlayDisabled(this)) {
                Toast.makeText(this, "Overlay Permissions Denied", Toast.LENGTH_SHORT).show();
            } else checkAccPerms();
        if (requestCode == ACTION_ACCESSIBILITY_PERMISSION_REQUEST_CODE)
            if (Helper.isAccessibilityDisabled(this)) {
                Toast.makeText(this, "Accessibility Services not running", Toast.LENGTH_SHORT).show();
            }
    }


    private void askPermissions() {
        if (Helper.isOverlayDisabled(this)) {
            try {
               startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                       ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);

              //  startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (Exception unused) {
                Toast.makeText(this, "Overlay Permission Handler not Found", Toast.LENGTH_SHORT).show();
            }
        }
        if (!Helper.isOverlayDisabled(this) && Helper.isAccessibilityDisabled(this)) {
            checkAccPerms();
        }
    }

    private void checkAccPerms() {
        if (Helper.isAccessibilityDisabled(this))
            try {


                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);



            } catch (Exception e) {
                Log.i("Eric","exception :"+e.toString());
             //   Toast.makeText(this, "Acessibility Handler not Found", Toast.LENGTH_SHORT).show();
                try {

                    ComponentName name = new ComponentName("com.android.tv.settings",
                            "com.android.tv.settings.MainSettings");
                    Intent i = new Intent(Intent.ACTION_MAIN);

                    i.addCategory(Intent.CATEGORY_LAUNCHER);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    i.setComponent(name);

                    startActivity(i);
                }
                catch ( Exception e2)
                {
                    Log.i("Eric","error 2"+e2.toString());
                }
            }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Checking services status
        checkServiceStatus();

        if (et_override != null)
            et_override.setText(Helper.getBossKeyValue(this)+"");
    }

    private void checkServiceStatus() {
        //checking for changed every 2 sec
        repopulate = new CountDownTimer(2000, 2000) {
            @Override
            public void onTick(long l) { }
            @Override
            public void onFinish() {
                populateText();
                repopulate.start(); //restarting the timer
            }
        };
        repopulate.start();
    }

    public void callDetect(View view) {

        startActivity(new Intent(this, KeyDetection.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }


}