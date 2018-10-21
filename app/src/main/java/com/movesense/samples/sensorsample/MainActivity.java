package com.movesense.samples.sensorsample;

import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.data.Entry;
import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsConnectionListener;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsSubscription;
import com.movesense.samples.sensorsample.model.ChartViewModel;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.scan.ScanSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rx.Subscription;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemLongClickListener, AdapterView.OnItemClickListener {


    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;

    // MDS
    private Mds mMds;
    public static final String URI_CONNECTEDDEVICES = "suunto://MDS/ConnectedDevices";
    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";

    // BleClient singleton
    static private RxBleClient mBleClient;

    // UI
    private ListView mScanResultListView;
    private ArrayList<MyScanResult> mScanResArrayList = new ArrayList<>();
    ArrayAdapter<MyScanResult> mScanResArrayAdapter;

    // Sensor subscription
    static private String URI_MEAS_ACC_13 = "/Meas/Acc/13";
    private MdsSubscription mdsSubscription, mdsSubscription1;
    private String subscribedDeviceSerial;

    public String username;
    private ChartViewModel chartViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chartViewModel = ViewModelProviders.of(this).get(ChartViewModel.class);

        // Init Scan UI
        mScanResultListView = (ListView) findViewById(R.id.listScanResult);
        mScanResArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mScanResArrayList);
        mScanResultListView.setAdapter(mScanResArrayAdapter);
        mScanResultListView.setOnItemLongClickListener(this);
        mScanResultListView.setOnItemClickListener(this);

        // Make sure we have all the permissions this app needs
        requestNeededPermissions();

        // Initialize Movesense MDS library
        initMds();

        // init user
        username = "Your Name";
    }

    private RxBleClient getBleClient() {
        // Init RxAndroidBle (Ble helper library) if not yet initialized
        if (mBleClient == null) {
            mBleClient = RxBleClient.create(this);
        }

        return mBleClient;
    }

    private void initMds() {
        mMds = Mds.builder().build(this);
    }

    void requestNeededPermissions() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);

        }
    }

    Subscription mScanSubscription;

    public void onScanClicked(View view) {
        String text = ((EditText) findViewById(R.id.nameText)).getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, ChartActivity.class);
            startActivity(intent);

            return;
        } else {
            username = text;
        }

        findViewById(R.id.buttonScan).setVisibility(View.GONE);
        findViewById(R.id.buttonScanStop).setVisibility(View.VISIBLE);

        // Start with empty list
        mScanResArrayList.clear();
        mScanResArrayAdapter.notifyDataSetChanged();

        mScanSubscription = getBleClient().scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )
                .subscribe(
                        scanResult -> {
                            Log.d(LOG_TAG, "scanResult: " + scanResult);

                            // Process scan result here. filter movesense devices.
                            if (scanResult.getBleDevice() != null &&
                                    scanResult.getBleDevice().getName() != null &&
                                    scanResult.getBleDevice().getName().startsWith("Movesense")) {

                                // replace if exists already, add otherwise
                                MyScanResult msr = new MyScanResult(scanResult);
                                if (mScanResArrayList.contains(msr))
                                    mScanResArrayList.set(mScanResArrayList.indexOf(msr), msr);
                                else
                                    mScanResArrayList.add(0, msr);

                                mScanResArrayAdapter.notifyDataSetChanged();
                            }
                        },
                        throwable -> {
                            Log.e(LOG_TAG, "scan error: " + throwable);
                            // Handle an error here.

                            // Re-enable scan buttons, just like with ScanStop
                            onScanStopClicked(null);
                        }
                );
    }

    public void onScanStopClicked(View view) {
        if (mScanSubscription != null) {
            mScanSubscription.unsubscribe();
            mScanSubscription = null;
        }

        findViewById(R.id.buttonScan).setVisibility(View.VISIBLE);
        findViewById(R.id.buttonScanStop).setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return;

        MyScanResult device = mScanResArrayList.get(position);
        if (!device.isConnected()) {
            // Stop scanning
            onScanStopClicked(null);

            // And connect to the device
            connectBLEDevice(device);
        } else {
            // Device is connected, trigger showing /Info
            subscribeToSensor(device.connectedSerial);
        }
    }

    String rmsAcc = null;
    double rms = 0;
    int n = 0;
    // standard deviation
    double std = 0;

    private void subscribeToSensor(String connectedSerial) {
        // Clean up existing subscription (if there is one)
        if (mdsSubscription != null) {
            unsubscribe();
        }

        // Build JSON doc that describes what resource and device to subscribe
        // Here we subscribe to 13 hertz accelerometer data
        StringBuilder sb = new StringBuilder();
        String strContract = sb.append("{\"Uri\": \"").append(connectedSerial).append(URI_MEAS_ACC_13).append("\"}").toString();
        Log.d(LOG_TAG, strContract);
        final View sensorUI = findViewById(R.id.sensorUI);

        subscribedDeviceSerial = connectedSerial;

        // acceleration data

        mdsSubscription = mMds.builder().build(this).subscribe(URI_EVENTLISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);
//                        System.out.println(data);
                        rmsAcc = data;

                        // If UI not enabled, do it now
                        if (sensorUI.getVisibility() == View.GONE)
                            sensorUI.setVisibility(View.VISIBLE);

                        AccDataResponse accResponse = new Gson().fromJson(data, AccDataResponse.class);
                        if (accResponse != null && accResponse.body.array.length > 0) {

                            // compute rms of acceleration
                            rms = rms + Math.pow(Math.sqrt(accResponse.body.array[0].x * accResponse.body.array[0].x
                                    + accResponse.body.array[0].y * accResponse.body.array[0].y
                                    + accResponse.body.array[0].z * accResponse.body.array[0].z) - 9.81, 2);
                            n++;

                            // standard deviation
                            std = std + Math.abs(accResponse.body.array[0].x * accResponse.body.array[0].x
                                    + accResponse.body.array[0].y * accResponse.body.array[0].y
                                    + accResponse.body.array[0].z * accResponse.body.array[0].z - 96.2);
//                            System.out.println("*******test: " + std);


                            String accStr =
                                    String.format("%.02f, %.02f, %.02f", accResponse.body.array[0].x, accResponse.body.array[0].y, accResponse.body.array[0].z);

                            System.out.println("Acc raw value: " + accStr);

                            ((TextView) findViewById(R.id.sensorMsg)).setText(accStr);

//                            ((TextView)findViewById(R.id.accTextView)).setText(accStr);
                        }
                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "subscription onError(): ", error);
                        unsubscribe();
                    }
                });

        sb = new StringBuilder();
        strContract = sb.append("{\"Uri\": \"").append(connectedSerial).append("/Meas/Hr").append("\"}").toString();
        Log.d(LOG_TAG, strContract);

        // bpm data
        mdsSubscription1 = Mds.builder().build(this).subscribe(URI_EVENTLISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        System.out.println(data);

                        if (n == 0) return;

                        // finalize rms value to output
                        double rmsOut = Math.sqrt(rms / n);
                        rms = 0;

//                        System.out.println("*******test before publish std: " + std);
                        // finalize std value to output
                        double stdOut = Math.sqrt(std) / n;
//                        System.out.println("*******test before publish n: " + n);
                        std = 0;
                        n = 0;

                        // construct the message to send to PubNub
                        String message = "{\"bpm\": " + data + ",\n\"acc\": { \"avg\": " + rmsOut + ", \"std\": " + stdOut + "}," +
                                "\"id\": " + connectedSerial + ", \"name\": \"" + username + "\"}";
                        // test
//                        System.out.println(message);

                        Log.d(LOG_TAG, "Heart rate onNotification() : " + data);

                        System.out.println("Bpm raw value: " + data);

                        Matcher matcher = Pattern.compile("\\d+").matcher(data);
                        matcher.find();
                        int bpm = Integer.valueOf(matcher.group());
                        System.out.println("BPM: " + bpm);
                        ((TextView) findViewById(R.id.accTextView)).setText("Acc: " + Math.round(rmsOut) + "    Bpm: " + bpm);
                        chartViewModel.getLiveData().setValue(new Entry(Math.round(rmsOut), bpm));

                        // If UI not enabled, do it now
                        if (sensorUI.getVisibility() == View.GONE)
                            sensorUI.setVisibility(View.VISIBLE);


                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "Heart rate error", error);
                    }
                });

        getSupportFragmentManager().beginTransaction().add(R.id.frameContainer, ChartActivity.newInstance(),
                ChartActivity.class.getSimpleName()).commit();

    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return false;

        MyScanResult device = mScanResArrayList.get(position);

        // unsubscribe if there
        Log.d(LOG_TAG, "onItemLongClick, " + device.connectedSerial + " vs " + subscribedDeviceSerial);
        if (device.connectedSerial.equals(subscribedDeviceSerial))
            unsubscribe();

        Log.i(LOG_TAG, "Disconnecting from BLE device: " + device.macAddress);
        mMds.disconnect(device.macAddress);

//        Intent intent = new Intent(this, ChartActivity.class);
//        startActivity(intent);

        return true;
    }

    private void connectBLEDevice(MyScanResult device) {
        RxBleDevice bleDevice = getBleClient().getBleDevice(device.macAddress);

        Log.i(LOG_TAG, "Connecting to BLE device: " + bleDevice.getMacAddress());
        mMds.connect(bleDevice.getMacAddress(), new MdsConnectionListener() {

            @Override
            public void onConnect(String s) {
                Log.d(LOG_TAG, "onConnect:" + s);
            }

            @Override
            public void onConnectionComplete(String macAddress, String serial) {
                for (MyScanResult sr : mScanResArrayList) {
                    if (sr.macAddress.equalsIgnoreCase(macAddress)) {
                        sr.markConnected(serial);
                        break;
                    }
                }
                mScanResArrayAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "onError:" + e);

                showConnectionError(e);
            }

            @Override
            public void onDisconnect(String bleAddress) {

                Log.d(LOG_TAG, "onDisconnect: " + bleAddress);
                for (MyScanResult sr : mScanResArrayList) {
                    if (bleAddress.equals(sr.macAddress)) {
                        // unsubscribe if was subscribed
                        if (sr.connectedSerial != null && sr.connectedSerial.equals(subscribedDeviceSerial))
                            unsubscribe();

                        sr.markDisconnected();
                    }
                }
                mScanResArrayAdapter.notifyDataSetChanged();
            }
        });
    }

    private void showConnectionError(MdsException e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Connection Error:")
                .setMessage(e.getMessage());

        builder.create().show();
    }

    private void unsubscribe() {
        if (mdsSubscription != null) {
            mdsSubscription.unsubscribe();
            mdsSubscription = null;
        }
        if (mdsSubscription1 != null) {
            mdsSubscription1.unsubscribe();
            mdsSubscription1 = null;
        }

        subscribedDeviceSerial = null;

        // If UI not invisible, do it now
        final View sensorUI = findViewById(R.id.sensorUI);
        if (sensorUI.getVisibility() != View.GONE)
            sensorUI.setVisibility(View.GONE);

    }

    public void onUnsubscribeClicked(View view) {
        unsubscribe();
    }

    public void showInfo(View view) {
        ((ImageButton) findViewById(R.id.buttonInfo)).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.textView)).setVisibility(View.VISIBLE);
    }

    public void hideInfo(View view) {
        ((TextView) findViewById(R.id.textView)).setVisibility(View.GONE);
        ((ImageButton) findViewById(R.id.buttonInfo)).setVisibility(View.VISIBLE);
    }
}
