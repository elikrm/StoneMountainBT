package com.sarmale.StoneMountainbtexample_v3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Set;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "FrugalLogs";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_CONNECT = 1001;

    // Bluetooth related variables
    BluetoothDevice StoneMountainBTModule = null;

    // Define the 3 UUIDs you're interested in
    private static final UUID[] stoneMountainUUIDs = new UUID[] {
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"), // SPP
            UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb"), // SCO Audio
            UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb")  // Handsfree
    };
    UUID StoneMountainUUID = stoneMountainUUIDs[0]; // Default to handsfree UUID

    public static Handler handler;
    private final static int ERROR_READ = 0; // used in bluetooth handler to identify message update

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        TextView btReadings = findViewById(R.id.btReadings);
        TextView btDevices = findViewById(R.id.btDevices);
        Button connectToDevice = findViewById(R.id.connectToDevice);
        Button seachDevices = findViewById(R.id.seachDevices);
        Button clearValues = findViewById(R.id.refresh);

        Log.d(TAG, "Begin Execution");

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case ERROR_READ:
                        String StoneMountainMsg = msg.obj.toString(); // Read message from Stone Mountain
                        btReadings.setText(StoneMountainMsg);
                        break;
                }
            }
        };

        clearValues.setOnClickListener(view -> {
            btDevices.setText("");
            btReadings.setText("");
        });

        final Observable<String> connectToBTObservable = Observable.create(emitter -> {
            Log.d(TAG, "Calling connectThread class");
            ConnectThread connectThread = new ConnectThread(StoneMountainBTModule, StoneMountainUUID, handler);
            connectThread.run();
            if (connectThread.getMmSocket().isConnected()) {
                Log.d(TAG, "Calling ConnectedThread class");
                ConnectedThread connectedThread = new ConnectedThread(connectThread.getMmSocket());
                connectedThread.run();
                if (connectedThread.getValueRead() != null) {
                    emitter.onNext(connectedThread.getValueRead());
                }
                connectedThread.cancel();
            }
            connectThread.cancel();
            emitter.onComplete();
        });

        connectToDevice.setOnClickListener(view -> {
            btReadings.setText("");
            if (StoneMountainBTModule != null) {
                connectToBTObservable
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(valueRead -> btReadings.setText(valueRead));
            }
        });

        seachDevices.setOnClickListener(view -> {
            if (bluetoothAdapter == null) {
                Log.d(TAG, "Device doesn't support Bluetooth");
                Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "Device supports Bluetooth");

            if (!bluetoothAdapter.isEnabled()) {
                Log.d(TAG, "Bluetooth is disabled");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
                    return;
                }
            }

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            StringBuilder btDevicesString = new StringBuilder();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress();
                    Log.d(TAG, "deviceName:" + deviceName);
                    Log.d(TAG, "deviceHardwareAddress:" + deviceHardwareAddress);
                    btDevicesString.append(deviceName).append(" || ").append(deviceHardwareAddress).append("\n");

                    if ("Stone Mountain BluSkye PTT".equals(deviceName)) {
                        Log.d(TAG, "Stone Mountain BluSkye PTT found");

                        boolean matchFound = false;
                        if (device.getUuids() != null) {
                            for (android.os.ParcelUuid parcelUuid : device.getUuids()) {
                                UUID deviceUuid = parcelUuid.getUuid();
                                for (UUID uuidToMatch : stoneMountainUUIDs) {
                                    if (deviceUuid.equals(uuidToMatch)) {
                                        matchFound = true;
                                        StoneMountainUUID = deviceUuid; // Save matching UUID
                                        break;
                                    }
                                }
                                if (matchFound) break;
                            }
                        }

                        if (matchFound) {
                            StoneMountainBTModule = device;
                            connectToDevice.setEnabled(true);
                            Log.d(TAG, "Matched UUID: " + StoneMountainUUID.toString());
                        }
                    }
                }
            } else {
                btDevicesString.append("No paired devices found");
            }

            btDevices.setText(btDevicesString.toString());
            Log.d(TAG, "Button Pressed");
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "BLUETOOTH_CONNECT permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "BLUETOOTH_CONNECT permission denied", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
