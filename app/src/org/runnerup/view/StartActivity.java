/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.view;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.common.tracker.TrackerState;
import org.runnerup.common.util.Constants;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.db.DBHelper;
import org.runnerup.hr.MockHRProvider;
import org.runnerup.notification.GpsBoundState;
import org.runnerup.notification.GpsSearchingState;
import org.runnerup.notification.NotificationManagerDisplayStrategy;
import org.runnerup.notification.NotificationStateManager;
import org.runnerup.tracker.GpsInformation;
import org.runnerup.tracker.Tracker;
import org.runnerup.tracker.component.TrackerHRM;
import org.runnerup.tracker.component.TrackerWear;
import org.runnerup.util.Formatter;
import org.runnerup.util.SafeParse;
import org.runnerup.util.TickListener;
import org.runnerup.widget.ClassicSpinner;
import org.runnerup.widget.SpinnerInterface;
import org.runnerup.widget.SpinnerInterface.OnCloseDialogListener;
import org.runnerup.widget.SpinnerInterface.OnSetValueListener;
import org.runnerup.widget.TitleSpinner;
import org.runnerup.widget.WidgetUtil;
import org.runnerup.workout.Dimension;
import org.runnerup.workout.Workout;
import org.runnerup.workout.Workout.StepListEntry;
import org.runnerup.workout.WorkoutBuilder;
import org.runnerup.workout.WorkoutSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@TargetApi(Build.VERSION_CODES.FROYO)
public class StartActivity extends AppCompatActivity implements TickListener, GpsInformation {

    private final static String TAB_BASIC = "basic";
    private final static String TAB_INTERVAL = "interval";
    final static String TAB_ADVANCED = "advanced";

    private boolean skipStopGps = false;
    private Tracker mTracker = null;
    private org.runnerup.tracker.GpsStatus mGpsStatus = null;

    private TabHost tabHost = null;
    private View startButton = null;
    private ViewGroup gpsLayout = null;
    private TextView gpsMessage = null;
    private Button gpsEnable = null;

    private TextView gpsIndicator = null;
    private View hrIndicator = null;
    private View watchIndicator = null;

    boolean batteryLevelMessageShown = false;

    TitleSpinner simpleTargetType = null;
    TitleSpinner simpleTargetPaceValue = null;
    TitleSpinner simpleTargetHrz = null;
    AudioSchemeListAdapter simpleAudioListAdapter = null;
    HRZonesListAdapter hrZonesAdapter = null;

    TitleSpinner intervalType = null;
    TitleSpinner intervalTime = null;
    TitleSpinner intervalDistance = null;
    TitleSpinner intervalRestType = null;
    TitleSpinner intervalRestTime = null;
    TitleSpinner intervalRestDistance = null;
    AudioSchemeListAdapter intervalAudioListAdapter = null;

    TitleSpinner advancedWorkoutSpinner = null;
    WorkoutListAdapter advancedWorkoutListAdapter = null;
    Button advancedDownloadWorkoutButton = null;
    Workout advancedWorkout = null;
    ListView advancedStepList = null;
    final WorkoutStepsAdapter advancedWorkoutStepsAdapter = new WorkoutStepsAdapter();
    AudioSchemeListAdapter advancedAudioListAdapter = null;

    SQLiteDatabase mDB = null;

    Formatter formatter = null;
    private NotificationStateManager notificationStateManager;
    private GpsSearchingState gpsSearchingState;
    private GpsBoundState gpsBoundState;
    private boolean headsetRegistered = false;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        mDB = DBHelper.getWritableDatabase(this);
        formatter = new Formatter(this);

        bindGpsTracker();
        mGpsStatus = new org.runnerup.tracker.GpsStatus(this);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationStateManager = new NotificationStateManager(new NotificationManagerDisplayStrategy(notificationManager));
        gpsSearchingState = new GpsSearchingState(this, this);
        gpsBoundState = new GpsBoundState(this);

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        setContentView(R.layout.start);

        ClassicSpinner sportSpinner = (ClassicSpinner) findViewById(R.id.sport_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.sportEntries, R.layout.actionbar_spinner);
        adapter.setDropDownViewResource(R.layout.actionbar_dropdown_spinner);
        sportSpinner.setAdapter(adapter);

        startButton = findViewById(R.id.start_button);
        startButton.setOnClickListener(startButtonClick);

        gpsLayout = (ViewGroup) findViewById(R.id.gps_layout);
        gpsMessage = (TextView) findViewById(R.id.gps_message);
        gpsEnable = (Button) findViewById(R.id.gps_enable_button);
        gpsEnable.setOnClickListener(gpsEnableClick);

        ViewGroup indicatorLayout = (ViewGroup) findViewById(R.id.indicator_layout);
        gpsIndicator = (TextView) findViewById(R.id.gps_indicator);
        hrIndicator = findViewById(R.id.hr_indicator);
        watchIndicator = findViewById(R.id.watch_indicator);
        indicatorLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                String gpsAccuracy = getGpsAccuracy();
                String hrDetail = getHRDetailString();
                String toastString =
                        (gpsAccuracy.length() != 0 && hrDetail.length() != 0) ?
                                gpsAccuracy + " • " + hrDetail : gpsAccuracy + hrDetail;
                if (toastString.length() == 0)
                    toastString = getString(R.string.GPS_is_required);
                Toast.makeText(StartActivity.this,
                        toastString,
                        Toast.LENGTH_SHORT).show();
            }
        });

        tabHost = (TabHost) findViewById(R.id.tabhost_start);
        tabHost.setup();
        TabSpec tabSpec = tabHost.newTabSpec(TAB_BASIC);
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.Basic)));
        tabSpec.setContent(R.id.tab_basic);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(TAB_INTERVAL);
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.Interval)));
        tabSpec.setContent(R.id.tab_interval);
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec(TAB_ADVANCED);
        tabSpec.setIndicator(WidgetUtil.createHoloTabIndicator(this, getString(R.string.Advanced)));
        tabSpec.setContent(R.id.tab_advanced);
        tabHost.addTab(tabSpec);

        tabHost.setOnTabChangedListener(onTabChangeListener);
        //tabHost.getTabWidget().setBackgroundColor(Color.DKGRAY);

        simpleAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
        simpleAudioListAdapter.reload();
        TitleSpinner simpleAudioSpinner = (TitleSpinner) findViewById(R.id.basic_audio_cue_spinner);
        simpleAudioSpinner.setAdapter(simpleAudioListAdapter);
        simpleAudioSpinner.setOnSetValueListener(new OnConfigureAudioListener(simpleAudioListAdapter));
        simpleTargetType = (TitleSpinner) findViewById(R.id.tab_basic_target_type);
        simpleTargetPaceValue = (TitleSpinner) findViewById(R.id.tab_basic_target_pace_max);
        hrZonesAdapter = new HRZonesListAdapter(this, inflater);
        simpleTargetHrz = (TitleSpinner) findViewById(R.id.tab_basic_target_hrz);
        simpleTargetHrz.setAdapter(hrZonesAdapter);
        simpleTargetType.setOnCloseDialogListener(simpleTargetTypeClick);

        intervalType = (TitleSpinner) findViewById(R.id.interval_type);
        intervalTime = (TitleSpinner) findViewById(R.id.interval_time);
        intervalTime.setOnSetValueListener(onSetTimeValidator);
        intervalDistance = (TitleSpinner) findViewById(R.id.interval_distance);
        intervalType.setOnSetValueListener(intervalTypeSetValue);
        intervalRestType = (TitleSpinner) findViewById(R.id.interval_rest_type);
        intervalRestTime = (TitleSpinner) findViewById(R.id.interval_rest_time);
        intervalRestTime.setOnSetValueListener(onSetTimeValidator);
        intervalRestDistance = (TitleSpinner) findViewById(R.id.interval_rest_distance);
        intervalRestType.setOnSetValueListener(intervalRestTypeSetValue);
        intervalAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
        intervalAudioListAdapter.reload();
        TitleSpinner intervalAudioSpinner = (TitleSpinner) findViewById(R.id.interval_audio_cue_spinner);
        intervalAudioSpinner.setAdapter(intervalAudioListAdapter);
        intervalAudioSpinner.setOnSetValueListener(new OnConfigureAudioListener(intervalAudioListAdapter));

        advancedAudioListAdapter = new AudioSchemeListAdapter(mDB, inflater, false);
        advancedAudioListAdapter.reload();
        TitleSpinner advancedAudioSpinner = (TitleSpinner) findViewById(R.id.advanced_audio_cue_spinner);
        advancedAudioSpinner.setAdapter(advancedAudioListAdapter);
        advancedAudioSpinner.setOnSetValueListener(new OnConfigureAudioListener(advancedAudioListAdapter));

        advancedWorkoutSpinner = (TitleSpinner) findViewById(R.id.advanced_workout_spinner);
        advancedWorkoutListAdapter = new WorkoutListAdapter(inflater);
        advancedWorkoutListAdapter.reload();
        advancedWorkoutSpinner.setAdapter(advancedWorkoutListAdapter);
        advancedWorkoutSpinner.setOnSetValueListener(new OnConfigureWorkoutsListener(advancedWorkoutListAdapter));
        advancedStepList = (ListView) findViewById(R.id.advanced_step_list);
        advancedStepList.setDividerHeight(0);
        advancedStepList.setAdapter(advancedWorkoutStepsAdapter);
        advancedDownloadWorkoutButton = (Button) findViewById(R.id.advanced_download_button);
        advancedDownloadWorkoutButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StartActivity.this, ManageWorkoutsActivity.class);
                StartActivity.this.startActivityForResult(intent, 113);
            }
        });

        if (getParent() != null && getParent().getIntent() != null) {
            Intent i = getParent().getIntent();
            if (i.hasExtra("mode")) {
                if (i.getStringExtra("mode").equals(TAB_ADVANCED)) {
                    tabHost.setCurrentTab(2);
                    i.removeExtra("mode");
                }
            }
        }

        updateTargetView();
    }

    private class OnConfigureAudioListener implements OnSetValueListener {
        AudioSchemeListAdapter adapter;

        OnConfigureAudioListener(AudioSchemeListAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public String preSetValue(String newValue) throws IllegalArgumentException {
            if (newValue != null && newValue.contentEquals(getString(R.string.Manage_audio_cues___))) {
                Intent i = new Intent(StartActivity.this, AudioCueSettingsActivity.class);
                startActivity(i);
                throw new IllegalArgumentException();
            }
            return newValue;
        }

        @Override
        public int preSetValue(int newValueId) throws IllegalArgumentException {
            return newValueId;
        }
    }

    private class OnConfigureWorkoutsListener implements OnSetValueListener {
        WorkoutListAdapter adapter;

        OnConfigureWorkoutsListener(WorkoutListAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public String preSetValue(String newValue) throws IllegalArgumentException {
            if (newValue != null && newValue.contentEquals(getString(R.string.Manage_workouts___))) {
                Intent i = new Intent(StartActivity.this, ManageWorkoutsActivity.class);
                startActivity(i);
                throw new IllegalArgumentException();
            }
            loadAdvanced(newValue);
            return newValue;
        }

        @Override
        public int preSetValue(int newValueId) throws IllegalArgumentException {
            loadAdvanced(null);
            return newValueId;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        registerStartEventListener();
    }

    @Override
    public void onResume() {
        super.onResume();
        simpleAudioListAdapter.reload();
        intervalAudioListAdapter.reload();
        advancedAudioListAdapter.reload();
        advancedWorkoutListAdapter.reload();
        hrZonesAdapter.reload();
        simpleTargetHrz.setAdapter(hrZonesAdapter);
        if (!hrZonesAdapter.hrZones.isConfigured()) {
            simpleTargetType.addDisabledValue(DB.DIMENSION.HRZ);
        } else {
            simpleTargetType.clearDisabled();
        }

        if (tabHost.getCurrentTabTag().contentEquals(TAB_ADVANCED)) {
            loadAdvanced(null);
        }

        if (!mIsBound || mTracker == null) {
            bindGpsTracker();
        } else {
            onGpsTrackerBound();
        }
        this.updateView();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (getAutoStartGps()) {
            /**
             * If autoStartGps, then stop it during pause
             */
            stopGps();
        } else {
            if (mTracker != null &&
                    ((mTracker.getState() == TrackerState.INITIALIZED) ||
                            (mTracker.getState() == TrackerState.INITIALIZING))) {
                Log.e(getClass().getName(), "mTracker.reset()");
                mTracker.reset();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        unregisterStartEventListener();
    }

    @Override
    public void onDestroy() {
        stopGps();
        unbindGpsTracker();
        mGpsStatus = null;
        mTracker = null;

        DBHelper.closeDB(mDB);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!getAutoStartGps() && mGpsStatus.isLogging()) {
            stopGps();
            updateView();
        } else {
            super.onBackPressed();
        }
    }

    private final BroadcastReceiver startEventBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mTracker == null || startButton.getVisibility() != View.VISIBLE)
                        return;

                    if (mTracker.getState() == TrackerState.INIT /* this will start gps */ ||
                            mTracker.getState() == TrackerState.INITIALIZED /* ...start a workout*/ ||
                            mTracker.getState() == TrackerState.CONNECTED) {
                        startButton.performClick();
                    }
                }
            });
        }
    };

    private void registerStartEventListener() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.Intents.START_WORKOUT);
        registerReceiver(startEventBroadcastReceiver, intentFilter);

        if (StartActivityHeadsetButtonReceiver.getAllowStartStopFromHeadsetKey(this)) {
            headsetRegistered = true;
            StartActivityHeadsetButtonReceiver.registerHeadsetListener(this);
        }
    }

    private void unregisterStartEventListener() {
        try {
            unregisterReceiver(startEventBroadcastReceiver);
        } catch (Exception e) {
        }
        if (headsetRegistered) {
            headsetRegistered = false;
            StartActivityHeadsetButtonReceiver.unregisterHeadsetListener(this);
        }
    }

    private void onGpsTrackerBound() {
        if (getAutoStartGps()) {
            startGps();
        } else {
            switch (mTracker.getState()) {
                case INIT:
                case CLEANUP:
                    mTracker.setup();
                    break;
                case INITIALIZING:
                case INITIALIZED:
                    break;
                case CONNECTING:
                case CONNECTED:
                case STARTED:
                case PAUSED:
                    if (BuildConfig.DEBUG) {
                        //Seem to happen when returning to RunnerUp
                        Log.e(getClass().getName(), "onGpsTrackerBound unexpected tracker state: " + mTracker.getState().toString());
                    }
                    break;
                case ERROR:
                    break;
            }
        }
        updateView();
    }

    private boolean getAutoStartGps() {
        Context ctx = getApplicationContext();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        return pref.getBoolean(getString(R.string.pref_startgps), false);
    }

    private void startGps() {
        Log.e(getClass().getName(), "StartActivity.startGps()");
        if (mGpsStatus != null && !mGpsStatus.isLogging())
            mGpsStatus.start(this);

        if (mTracker != null) {
            mTracker.connect();
        }

        notificationStateManager.displayNotificationState(gpsSearchingState);
    }

    private void stopGps() {
        Log.e(getClass().getName(), "StartActivity.stopGps() skipStop: " + this.skipStopGps);
        if (skipStopGps)
            return;

        if (mGpsStatus != null)
            mGpsStatus.stop(this);

        if (mTracker != null)
            mTracker.reset();

        notificationStateManager.cancelNotification();
    }

    private void notificationBatteryLevel(int batteryLevel) {
        if ((batteryLevel < 0) || (batteryLevel > 100)) {
            return;
        }

        final String pref_key = getString(R.string.pref_battery_level_low_notification_discard);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int batteryLevelHighThreshold = SafeParse.parseInt(prefs.getString(getString(
                R.string.pref_battery_level_high_threshold), "75"), 75);
        if ((batteryLevel > batteryLevelHighThreshold) && (prefs.contains(pref_key))) {
            prefs.edit().remove(pref_key).commit();
            return;
        }

        int batteryLevelLowThreshold = SafeParse.parseInt(prefs.getString(getString(
                R.string.pref_battery_level_low_threshold), "15"), 15);
        if (batteryLevel > batteryLevelLowThreshold) {
            return;
        }

        if (prefs.getBoolean(pref_key, false)) {
            return;
        }

        AlertDialog.Builder prompt = new AlertDialog.Builder(this);
        final CheckBox dontShowAgain = new CheckBox(this);
        dontShowAgain.setText(getResources().getText(R.string.Do_not_show_again));
        prompt.setView(dontShowAgain);

        prompt.setCancelable(false);
        prompt.setMessage(getResources().getText(R.string.Low_HRM_battery_level)
                + "\n" + getResources().getText(R.string.Battery_level) + ": " + batteryLevel + "%");
        prompt.setTitle(getResources().getText(R.string.Warning));

        prompt.setPositiveButton(getResources().getText(R.string.OK), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (dontShowAgain.isChecked()) {
                    prefs.edit().putBoolean(pref_key, true).commit();
                }
            }
        });

        prompt.show();
    }

    private final OnTabChangeListener onTabChangeListener = new OnTabChangeListener() {

        @Override
        public void onTabChanged(String tabId) {
            if (tabId.contentEquals(TAB_ADVANCED)) {
                loadAdvanced(null);
            }
            updateView();
        }
    };

    private Workout prepareWorkout() {
        Context ctx = getApplicationContext();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences audioPref = null;
        Workout w = null;

        if (tabHost.getCurrentTabTag().contentEquals(TAB_BASIC)) {
            audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref,
                    getString(R.string.pref_basic_audio));
            Dimension target = Dimension.valueOf(simpleTargetType.getValueInt());
            w = WorkoutBuilder.createDefaultWorkout(getResources(), pref, target);
            w.setWorkoutType(Constants.WORKOUT_TYPE.BASIC);
        } else if (tabHost.getCurrentTabTag().contentEquals(TAB_INTERVAL)) {
            audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref,
                    getString(R.string.pref_interval_audio));
            w = WorkoutBuilder.createDefaultIntervalWorkout(getResources(), pref);
            w.setWorkoutType(Constants.WORKOUT_TYPE.INTERVAL);
        } else if (tabHost.getCurrentTabTag().contentEquals(TAB_ADVANCED)) {
            audioPref = WorkoutBuilder.getAudioCuePreferences(ctx, pref,
                    getString(R.string.pref_advanced_audio));
            w = advancedWorkout;
            w.sport = pref.getInt(getString(R.string.pref_sport), DB.ACTIVITY.SPORT_RUNNING);
            w.setWorkoutType(Constants.WORKOUT_TYPE.ADVANCED);
        }
        WorkoutBuilder.prepareWorkout(getResources(), pref, w,
                TAB_BASIC.contentEquals(tabHost.getCurrentTabTag()));
        WorkoutBuilder.addAudioCuesToWorkout(getResources(), w, audioPref);
        return w;
    }

    private final OnClickListener startButtonClick = new OnClickListener() {
        public void onClick(View v) {
            if (mTracker.getState() == TrackerState.CONNECTED) {
                mGpsStatus.stop(StartActivity.this);

                /**
                 * unregister receivers
                 */
                unregisterStartEventListener();

                /**
                 * This will start the advancedWorkoutSpinner!
                 */
                mTracker.setWorkout(prepareWorkout());
                mTracker.start();

                skipStopGps = true;
                Intent intent = new Intent(StartActivity.this,
                        RunActivity.class);
                StartActivity.this.startActivityForResult(intent, 112);
                notificationStateManager.cancelNotification(); // will be added by RunActivity
                return;
            }
            updateView();
        }
    };

    private final OnClickListener gpsEnableClick = new OnClickListener() {
        public void onClick(View v) {
            if (!mGpsStatus.isEnabled()) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else if (mTracker.getState() != TrackerState.CONNECTED) {
                startGps();
            }
            updateView();
        }
    };

    private void updateView() {
        if (!mGpsStatus.isEnabled() || !mGpsStatus.isLogging()) {
            gpsIndicator.setText(getString(R.string.GPS_indicator_off));
        } else {
            int cnt0 = mGpsStatus.getSatellitesFixed();
            int cnt1 = mGpsStatus.getSatellitesAvailable();
            gpsIndicator.setText(String.format(getString(R.string.GPS_indicator), cnt0, cnt1));
        }

        if (!mGpsStatus.isEnabled()) {
            startButton.setVisibility(View.GONE);
            gpsLayout.setVisibility(View.VISIBLE);
            gpsEnable.setVisibility(View.VISIBLE);

            gpsMessage.setText(getString(R.string.GPS_is_required));
            gpsEnable.setText(getString(R.string.Enable_GPS));
        } else if (!mGpsStatus.isLogging()) {
            startButton.setVisibility(View.GONE);
            gpsLayout.setVisibility(View.VISIBLE);
            gpsEnable.setVisibility(View.VISIBLE);

            gpsMessage.setText(getString(R.string.GPS_is_required));
            gpsEnable.setText(getString(R.string.Start_GPS));
        } else if (!mGpsStatus.isFixed()) {
            startButton.setVisibility(View.GONE);
            gpsLayout.setVisibility(View.VISIBLE);
            gpsEnable.setVisibility(View.GONE);

            gpsMessage.setText(getString(R.string.Waiting_for_GPS));
            notificationStateManager.displayNotificationState(gpsSearchingState);
        } else {
            if (tabHost.getCurrentTabTag().contentEquals(TAB_ADVANCED) && advancedWorkout == null) {
                startButton.setVisibility(View.GONE);
            } else {
                startButton.setVisibility(View.VISIBLE);
            }
            gpsLayout.setVisibility(View.GONE);

            notificationStateManager.displayNotificationState(gpsBoundState);
        }

        boolean hideHR = true;
        boolean hideWatch = true;
        if (mTracker != null) {
            if (mTracker.isComponentConfigured(TrackerHRM.NAME)) {
                hideHR = false;
                Integer hrVal = null;
                if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
                    hrVal = mTracker.getCurrentHRValue();
                }
                if (hrVal != null) {
                    if (!batteryLevelMessageShown) {
                        batteryLevelMessageShown = true;
                        notificationBatteryLevel(mTracker.getCurrentBatteryLevel());
                    }
                }
            }

            if (mTracker.isComponentConfigured(TrackerWear.NAME)) {
                hideWatch = false;
            }
        }

        if (hideHR)
            hrIndicator.setVisibility(View.GONE);
        else
            hrIndicator.setVisibility(View.VISIBLE);

        if (hideWatch)
            watchIndicator.setVisibility(View.GONE);
        else
            watchIndicator.setVisibility(View.VISIBLE);
    }

    @Override
    public String getGpsAccuracy() {
        if (mTracker != null) {
            Location l = mTracker.getLastKnownLocation();

            if (l != null && l.getAccuracy() > 0) {
                if (mTracker.getCurrentElevation() != null) {
                    return String.format(Locale.getDefault(), getString(R.string.GPS_accuracy_elevation),
                            l.getAccuracy(), mTracker.getCurrentElevation());
                } else {
                    return String.format(Locale.getDefault(), getString(R.string.GPS_accuracy_no_elevation),
                            l.getAccuracy());
                }
            }
        }

        return "";
    }

    private String getHRDetailString() {
        StringBuilder str = new StringBuilder();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(StartActivity.this);
        final String btDeviceName = prefs.getString(getString(R.string.pref_bt_name), null);

        if (btDeviceName != null) {
            str.append(btDeviceName);
        } else if (MockHRProvider.NAME.contentEquals(prefs.getString(getString(R.string.pref_bt_provider), ""))) {
            str.append("mock: ").append(prefs.getString(getString(R.string.pref_bt_address), "???"));
        }

        if (mTracker.isComponentConnected(TrackerHRM.NAME)) {
            Integer hrVal = mTracker.getCurrentHRValue();
            if (hrVal != null)
                str.append(" ").append(hrVal);
        }
        return str.toString();
    }

    private boolean mIsBound = false;
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mTracker = ((Tracker.LocalBinder) service).getService();
            // Tell the user about this for our demo.
            StartActivity.this.onGpsTrackerBound();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mTracker = null;
        }
    };

    private void bindGpsTracker() {
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        getApplicationContext().bindService(new Intent(this, Tracker.class),
                mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void unbindGpsTracker() {
        if (mIsBound) {
            // Detach our existing connection.
            getApplicationContext().unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        registerStartEventListener();

        if (data != null) {
            if (data.getStringExtra("url") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"url\") => " + data.getStringExtra("url"));
            if (data.getStringExtra("ex") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"ex\") => " + data.getStringExtra("ex"));
            if (data.getStringExtra("obj") != null)
                Log.e(getClass().getName(), "data.getStringExtra(\"obj\") => " + data.getStringExtra("obj"));
        }
        if (requestCode == 112) {
            skipStopGps = false;
            if (!mIsBound || mTracker == null) {
                bindGpsTracker();
            } else {
                onGpsTrackerBound();
            }
        } else {
            advancedWorkoutListAdapter.reload();
        }
        updateView();
    }

    @Override
    public void onTick() {
        updateView();
    }

    private final OnCloseDialogListener simpleTargetTypeClick = new OnCloseDialogListener() {

        @Override
        public void onClose(SpinnerInterface spinner, boolean ok) {
            if (ok) {
                updateTargetView();
            }
        }
    };

    private void updateTargetView() {
        Dimension dim = Dimension.valueOf(simpleTargetType.getValueInt());
        if (dim == null) {
            simpleTargetPaceValue.setEnabled(false);
            simpleTargetHrz.setEnabled(false);
        } else {
            switch (dim) {
                case PACE:
                    simpleTargetPaceValue.setEnabled(true);
                    simpleTargetPaceValue.setVisibility(View.VISIBLE);
                    simpleTargetHrz.setVisibility(View.GONE);
                    break;
                case HRZ:
                    simpleTargetPaceValue.setVisibility(View.GONE);
                    simpleTargetHrz.setEnabled(true);
                    simpleTargetHrz.setVisibility(View.VISIBLE);
            }
        }
    }

    private final OnSetValueListener intervalTypeSetValue = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            boolean time = (newValue == 0);
            intervalTime.setVisibility(time ? View.VISIBLE : View.GONE);
            intervalDistance.setVisibility(time ? View.GONE : View.VISIBLE);
            return newValue;
        }
    };

    private final OnSetValueListener intervalRestTypeSetValue = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {
            return newValue;
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            boolean time = (newValue == 0);
            intervalRestTime.setVisibility(time ? View.VISIBLE : View.GONE);
            intervalRestDistance.setVisibility(time ? View.GONE : View.VISIBLE);
            return newValue;
        }
    };

    private void loadAdvanced(String name) {
        Context ctx = getApplicationContext();
        if (name == null) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
            name = pref.getString(getResources().getString(R.string.pref_advanced_workout), "");
        }
        advancedWorkout = null;
        if ("".contentEquals(name))
            return;
        try {
            advancedWorkout = WorkoutSerializer.readFile(ctx, name + ".json");
            advancedWorkoutStepsAdapter.steps = advancedWorkout.getStepList();
            advancedWorkoutStepsAdapter.notifyDataSetChanged();
            advancedDownloadWorkoutButton.setVisibility(View.GONE);
        } catch (Exception ex) {
            ex.printStackTrace();
            AlertDialog.Builder builder = new AlertDialog.Builder(StartActivity.this);
            builder.setTitle(getString(R.string.Failed_to_load_workout));
            builder.setMessage("" + ex.toString());
            builder.setPositiveButton(getString(R.string.OK),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.show();
        }
    }

    @Override
    public int getSatellitesAvailable() {
        return mGpsStatus.getSatellitesAvailable();
    }

    @Override
    public int getSatellitesFixed() {
        return mGpsStatus.getSatellitesFixed();
    }

    final class WorkoutStepsAdapter extends BaseAdapter {

        List<StepListEntry> steps = new ArrayList<>();

        @Override
        public int getCount() {
            return steps.size();
        }

        @Override
        public Object getItem(int position) {
            return steps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            StepListEntry entry = steps.get(position);
            StepButton button =
                    (convertView != null && convertView instanceof StepButton) ?
                            (StepButton) convertView : new StepButton(StartActivity.this, null);
            button.setStep(entry.step);

            float pxToDp = getResources().getDisplayMetrics().density;
            button.setPadding((int) (entry.level * 8 * pxToDp + 0.5f), 0, 0, 0);
            button.setOnChangedListener(onWorkoutChanged);
            return button;
        }

    }

    private final Runnable onWorkoutChanged = new Runnable() {
        @Override
        public void run() {
            String name = advancedWorkoutSpinner.getValue().toString();
            if (advancedWorkout != null) {
                Context ctx = getApplicationContext();
                try {
                    WorkoutSerializer.writeFile(ctx, name, advancedWorkout);
                } catch (Exception ex) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(StartActivity.this);
                    builder.setTitle(getString(R.string.Failed_to_load_workout));
                    builder.setMessage("" + ex.toString());
                    builder.setPositiveButton(getString(R.string.OK),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    builder.show();
                }
            }
        }
    };

    private final OnSetValueListener onSetTimeValidator = new OnSetValueListener() {

        @Override
        public String preSetValue(String newValue)
                throws IllegalArgumentException {

            if (WorkoutBuilder.validateSeconds(newValue))
                return newValue;

            throw new IllegalArgumentException("Unable to parse time value: " + newValue);
        }

        @Override
        public int preSetValue(int newValue) throws IllegalArgumentException {
            return newValue;
        }

    };
}
