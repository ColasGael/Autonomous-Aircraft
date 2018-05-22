package simulatordemo.aa241x.team2.simulatordemo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getName();

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

    private FlightController mFlightController;
    protected TextView mConnectStatusTextView;
    private Button mBtnEnableVirtualStick;
    private Button mBtnDisableVirtualStick;
    private ToggleButton mBtnSimulator;
    private Button mBtnTakeOff;
    private Button mBtnLand;
    private Button mBtnDestination;
    private Button mBtnPIDController;

    private TextView mTextView;

    private OnScreenJoystick mScreenJoystickRight;
    private OnScreenJoystick mScreenJoystickLeft;

    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;

    private Timer mUpdateYawTimer;
    private UpdateYawTask mUpdateYawTask;

    private Timer mPIDControllerTimer;
    private PIDControllerTask mPIDControllerTask;

    private float mPitch;
    private float mRoll;
    private float mYaw;
    private float mThrottle;

    private double droneLocationLat; //degrees
    private double droneLocationLng; //degrees
    private double droneLocationAlt;
    private float droneHeading = 0.0f;

    private float distance = 0.0f;
    private float direction = 0.0f;
    private float altitude = 0.0f;
    private float speed = 0.0f;
    private final float earthRadius = 6371000.0f;

    private float Kp = 0.0f;
    private float Ki = 0.0f;
    private float Kd = 0.0f;

    private boolean isDestinationSet = false;
    private double goalPointLatRad = 0.0;
    private double goalPointLngRad = 0.0;

    private final double closeToGoalDist = 1.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_main);

        initUI();

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJISimulatorApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }

    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast( "registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("Register Success");
                            } else {
                                showToast( "Register sdk fails, check network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                            Log.d(TAG, String.format("onProductChanged oldProduct:%s, newProduct:%s", oldProduct, newProduct));
                        }
                    });
                }
            });
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
        }
    };

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTitleBar() {
        if(mConnectStatusTextView == null) return;
        boolean ret = false;
        BaseProduct product = DJISimulatorApplication.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(DJISimulatorApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if(product instanceof Aircraft) {
                    Aircraft aircraft = (Aircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        updateTitleBar();
        initFlightController();
        loginAccount();

    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view){
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        unregisterReceiver(mReceiver);
        if (null != mSendVirtualStickDataTimer) {
            mSendVirtualStickDataTask.cancel();
            mSendVirtualStickDataTask = null;
            mSendVirtualStickDataTimer.cancel();
            mSendVirtualStickDataTimer.purge();
            mSendVirtualStickDataTimer = null;
        }
        if (null != mPIDControllerTimer) {
            mPIDControllerTask.cancel();
            mPIDControllerTask = null;
            mPIDControllerTimer.cancel();
            mPIDControllerTimer.purge();
            mPIDControllerTimer = null;
        }
        super.onDestroy();
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e(TAG, "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        showToast("Login Error:"
                                + error.getDescription());
                    }
                });
    }

    private void initFlightController() {

        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("Disconnected");
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
            mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull final FlightControllerState flightControllerState) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {

                            String yaw = String.format("%.2f", mYaw);
                            String pitch = String.format("%.2f", mPitch);
                            String roll = String.format("%.2f", mRoll);

                            mTextView.setText("Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll);

                            LocationCoordinate3D pos3D = flightControllerState.getAircraftLocation();
                            droneLocationLat = pos3D.getLatitude();
                            droneLocationLng = pos3D.getLongitude();
                            droneLocationAlt = pos3D.getAltitude();
                            droneHeading = mFlightController.getCompass().getHeading();

                            if (isDestinationSet && distToGoalPos()<closeToGoalDist){

                                mFlightController.startGoHome(
                                        new CommonCallbacks.CompletionCallback() {
                                            @Override
                                            public void onResult(DJIError djiError) {
                                                if (djiError != null) {
                                                    showToast(djiError.getDescription());
                                                } else {
                                                    showToast("Start Landing");
                                                }
                                            }
                                        }
                                );

//                                mFlightController.startLanding(
//                                        new CommonCallbacks.CompletionCallback() {
//                                            @Override
//                                            public void onResult(DJIError djiError) {
//                                                if (djiError != null) {
//                                                    showToast(djiError.getDescription());
//                                                } else {
//                                                    showToast("Start Landing");
//                                                }
//                                            }
//                                        }
//                                );
                                isDestinationSet = false;


                            }

                        }
                    });
                }
            });
            mFlightController.getSimulator().setStateCallback(new SimulatorState.Callback() {
                @Override
                public void onUpdate(final SimulatorState stateData) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {

                            String yaw = String.format("%.2f", stateData.getYaw());
                            String pitch = String.format("%.2f", stateData.getPitch());
                            String roll = String.format("%.2f", stateData.getRoll());
                            String positionX = String.format("%.2f", stateData.getPositionX());
                            String positionY = String.format("%.2f", stateData.getPositionY());
                            String positionZ = String.format("%.2f", stateData.getPositionZ());

                            mTextView.setText("Yaw : " + yaw + ", Pitch : " + pitch + ", Roll : " + roll + "\n" + ", PosX : " + positionX +
                                    ", PosY : " + positionY +
                                    ", PosZ : " + positionZ);
                        }
                    });
                }
            });
        }
    }

    private void initUI() {

        mBtnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        mBtnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        mBtnTakeOff = (Button) findViewById(R.id.btn_take_off);
        mBtnLand = (Button) findViewById(R.id.btn_land);
        mBtnSimulator = (ToggleButton) findViewById(R.id.btn_start_simulator);
        mBtnDestination = (Button) findViewById(R.id.add_destination);
        mBtnPIDController = (Button) findViewById(R.id.btn_tune_pid);
        mTextView = (TextView) findViewById(R.id.textview_simulator);
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        mScreenJoystickRight = (OnScreenJoystick)findViewById(R.id.directionJoystickRight);
        mScreenJoystickLeft = (OnScreenJoystick)findViewById(R.id.directionJoystickLeft);

        mBtnEnableVirtualStick.setOnClickListener(this);
        mBtnDisableVirtualStick.setOnClickListener(this);
        mBtnTakeOff.setOnClickListener(this);
        mBtnLand.setOnClickListener(this);
        mBtnDestination.setOnClickListener(this);
        mBtnPIDController.setOnClickListener(this);

        mBtnSimulator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    mTextView.setVisibility(View.VISIBLE);

                    if (mFlightController != null) {

                        mFlightController.getSimulator()
                                .start(InitializationData.createInstance(new LocationCoordinate2D(23, 113), 10, 10),
                                        new CommonCallbacks.CompletionCallback() {
                                            @Override
                                            public void onResult(DJIError djiError) {
                                                if (djiError != null) {
                                                    showToast(djiError.getDescription());
                                                }else
                                                {
                                                    showToast("Start Simulator Success");
                                                }
                                            }
                                        });
                    }

                } else {

                    mTextView.setVisibility(View.INVISIBLE);

                    if (mFlightController != null) {
                        mFlightController.getSimulator()
                                .stop(new CommonCallbacks.CompletionCallback() {
                                          @Override
                                          public void onResult(DJIError djiError) {
                                              if (djiError != null) {
                                                  showToast(djiError.getDescription());
                                              }else
                                              {
                                                  showToast("Stop Simulator Success");
                                              }
                                          }
                                      }
                                );
                    }
                }
            }
        });

        mScreenJoystickRight.setJoystickListener(new OnScreenJoystickListener(){

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }

                float pitchJoyControlMaxSpeed = 10;
                float rollJoyControlMaxSpeed = 10;

                mPitch = (float)(pitchJoyControlMaxSpeed * pX);

                mRoll = (float)(rollJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 100, 200);
                }

            }

        });

        mScreenJoystickLeft.setJoystickListener(new OnScreenJoystickListener() {

            @Override
            public void onTouch(OnScreenJoystick joystick, float pX, float pY) {
                if(Math.abs(pX) < 0.02 ){
                    pX = 0;
                }

                if(Math.abs(pY) < 0.02 ){
                    pY = 0;
                }
                float verticalJoyControlMaxSpeed = 2;
                float yawJoyControlMaxSpeed = 30;

                mYaw = (float)(yawJoyControlMaxSpeed * pX);
                mThrottle = (float)(verticalJoyControlMaxSpeed * pY);

                if (null == mSendVirtualStickDataTimer) {
                    mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                    mSendVirtualStickDataTimer = new Timer();
                    mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
                }

            }
        });
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:
                if (mFlightController != null){

                    mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null){
                                showToast(djiError.getDescription());
                            }else
                            {
                                showToast("Enable Virtual Stick Success");
                            }
                        }
                    });

                }
                break;

            case R.id.btn_disable_virtual_stick:

                if (mFlightController != null){
                    mFlightController.setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError != null) {
                                showToast(djiError.getDescription());
                            } else {
                                showToast("Disable Virtual Stick Success");
                            }
                        }
                    });
                }
                break;

            case R.id.btn_take_off:
                if (mFlightController != null){
                    mFlightController.startTakeoff(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        showToast(djiError.getDescription());
                                    } else {
                                        showToast("Take off Success");
                                    }
                                }
                            }
                    );
                }
                Log.e(TAG,"heading "+droneHeading);
                break;

            case R.id.btn_land:
                if (mFlightController != null){

                    mFlightController.startLanding(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null) {
                                        showToast(djiError.getDescription());
                                    } else {
                                        showToast("Start Landing");
                                    }
                                }
                            }
                    );

                }

                break;

            case R.id.add_destination:{

                if (null != mFlightController) {
                    if (mFlightController.getSimulator().isSimulatorActive()) {
                        mFlightController.getSimulator()
                                .stop(new CommonCallbacks.CompletionCallback() {
                                          @Override
                                          public void onResult(DJIError djiError) {
                                              if (djiError != null) {
                                                  showToast(djiError.getDescription());
                                              } else {
                                                  showToast("Stop Simulator Success");
                                              }
                                          }
                                      }
                                );
                    }
                }

                showDestinationDialog();
                break;
            }

            case R.id.btn_tune_pid:{
                showPIDDialog();
                break;
            }

            default:
                break;
        }
    }

    class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {

            if (mFlightController != null) {
                mFlightController.sendVirtualStickFlightControlData(
                        new FlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (djiError != null) {
                                    showToast(djiError.getDescription());
                                } else {
                                    showToast("Sending FlightControlData:" +" " +mPitch + " "+mRoll+ " "+mYaw+" "+mThrottle);
                                }
                            }
                        }
                );
            }
        }
    }

    class PIDControllerTask extends TimerTask {
        private float prev_error = 0.0f;
        private float integral = 0.0f;
        private float error = 0.0f;
        private float derivative = 0.0f;
        private float output = 0.0f;
        @Override
        public void run() {

            error = direction - droneHeading;
            integral = integral +error*200/1000;
            derivative = (error-prev_error)/(200/1000);
            mYaw = Kp*error + Ki*integral + Kd*derivative;
            prev_error = error;


        }
    }

    class UpdateYawTask extends TimerTask{
        public void run() {
            double newHeading = gpsToHeading(Math.toRadians(droneLocationLat), Math.toRadians(droneLocationLng), goalPointLatRad, goalPointLngRad);
            double angVelocity = (newHeading-droneHeading)/0.5; //because period of update is 0.5 sec.
            mYaw = (float) angVelocity;
            Log.e(TAG,"updated Yaw: "+mYaw);
        }
    }

    private void showDestinationDialog(){
        LinearLayout destinationSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_destinationsetting, null);

        final TextView goalDistance_TV = (TextView) destinationSettings.findViewById(R.id.distance);
        final TextView goalDirection_TV = (TextView) destinationSettings.findViewById(R.id.direction);
        final TextView goalAltitude_TV = (TextView) destinationSettings.findViewById(R.id.altitude);
        final TextView goalSpeed_TV = (TextView) destinationSettings.findViewById(R.id.speed);

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(destinationSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        String distanceString = goalDistance_TV.getText().toString();
                        distance = Float.parseFloat(nulltoFloatDefault(distanceString));
                        String directionString = goalDirection_TV.getText().toString();
                        direction = Float.parseFloat(nulltoFloatDefault(directionString));
                        String altitudeString = goalAltitude_TV.getText().toString();
                        altitude = Float.parseFloat(nulltoFloatDefault(altitudeString));
                        String speedString = goalSpeed_TV.getText().toString();
                        speed = Float.parseFloat(nulltoFloatDefault(speedString));
                        Log.e(TAG,"distance "+distance);
                        Log.e(TAG,"direction "+direction);
                        Log.e(TAG,"altitude "+altitude);
                        Log.e(TAG,"speed "+speed);


                        double droneLocationLatRad = Math.toRadians(droneLocationLat);
                        double droneLocationLongRad = Math.toRadians(droneLocationLng);
                        double directionRad = Math.toRadians(direction);

                        goalPointLatRad = Math.asin(Math.sin(droneLocationLatRad)*Math.cos(distance/earthRadius) +
                                Math.cos(droneLocationLatRad)*Math.sin(distance/earthRadius)*Math.cos(directionRad));
                        goalPointLngRad = droneLocationLongRad + Math.atan2(Math.sin(directionRad)*Math.sin(distance/earthRadius)*Math.cos(droneLocationLatRad),
                                Math.cos(distance/earthRadius)-Math.sin(droneLocationLatRad)*Math.sin(goalPointLatRad));

                        isDestinationSet = true;

                        if (mFlightController != null){

                            mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if (djiError != null){
                                        showToast(djiError.getDescription());
                                    }else
                                    {
                                        showToast("Enable Virtual Stick Success");
                                    }
                                }
                            });
                            mFlightController.setVerticalControlMode(VerticalControlMode.POSITION);
                            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                        }

                        mThrottle = altitude;
                        mPitch = speed;
                        mRoll = 0.0f;
                        mYaw = 0.0f;

                        if (null == mSendVirtualStickDataTimer) {
                            mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                            mSendVirtualStickDataTimer = new Timer();
                            mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 25, 200);
                            Log.e(TAG,"virtual stick Task on");
                        }

                        if (null == mUpdateYawTimer) {
                            mUpdateYawTask = new UpdateYawTask();
                            mUpdateYawTimer = new Timer();
                            mUpdateYawTimer.schedule(mUpdateYawTask, 0, 500);
                            Log.e(TAG,"update yaw Task on");
                        }

//                        if (null == mPIDControllerTimer) {
//                            mPIDControllerTask = new PIDControllerTask();
//                            mPIDControllerTimer = new Timer();
//                            mPIDControllerTimer.schedule(mPIDControllerTask, 75, 200);
//                            Log.e(TAG,"PID controller on");
//                        }




                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    private void showPIDDialog(){
        LinearLayout pidSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_pidsetting, null);

        final TextView kp_TV = (TextView) pidSettings.findViewById(R.id.kp);
        final TextView kd_TV = (TextView) pidSettings.findViewById(R.id.kd);
        final TextView ki_TV = (TextView) pidSettings.findViewById(R.id.ki);

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(pidSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {

                        String kpString = kp_TV.getText().toString();
                        Kp = Float.parseFloat(kpString);
                        String kdString = kd_TV.getText().toString();
                        Kd = Float.parseFloat(kdString);
                        String kiString = ki_TV.getText().toString();
                        Ki = Float.parseFloat(kiString);

                        Log.e(TAG,"Kp "+Kp);
                        Log.e(TAG,"Kd "+Kd);
                        Log.e(TAG,"Ki "+Ki);

                    }

                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }

                })
                .create()
                .show();
    }

    String nulltoIntegerDefalt(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    String nulltoFloatDefault(String value){
        try{
            Float.parseFloat(value);
        }
        catch(NumberFormatException e){
            value = "0";
        }
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    double distToGoalPos(){
        double phi1 = Math.toRadians(droneLocationLat);
        double phi2 = goalPointLatRad;
        double dphi = Math.toRadians(Math.toDegrees(goalPointLatRad)-droneLocationLat);
        double dlbda = Math.toRadians(Math.toDegrees(goalPointLngRad)-droneLocationLng);

        double a = Math.sin(dphi/2)*Math.sin(dphi/2)+Math.cos(phi1)*Math.cos(phi2)*Math.sin(dlbda/2)*Math.sin(dlbda/2);
        double c = 2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
        return earthRadius*c;
    }

    double gpsToHeading(double lat1rad, double lng1rad, double lat2rad, double lng2rad){
        double dphi = lat2rad-lat1rad;
        double dlbda = lng2rad-lng1rad;
        double y = Math.sin(dlbda)*Math.cos(lat2rad);
        double x = Math.cos(lat1rad)*Math.sin(lat2rad)-Math.sin(lat1rad)*Math.cos(lat2rad)*Math.cos(dlbda);
        return Math.toDegrees(Math.atan2(y,x));
    }
}