package com.sarmale.StoneMountainbtexample_v3;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class ConnectThread extends Thread {
    private final BluetoothSocket mmSocket;
    private static final String TAG = "FrugalLogs";
    public static Handler handler;
    private final static int ERROR_READ = 0;
    private InputStream inputStream;
    private volatile boolean isRunning = true;

    @SuppressLint("MissingPermission")
    public ConnectThread(BluetoothDevice device, UUID MY_UUID, Handler handler) {
        BluetoothSocket tmp = null;
        this.handler = handler;

        try {
            Log.d(TAG, "Creating RFCOMM socket with UUID: " + MY_UUID);
            tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's create() method failed", e);
        }
        mmSocket = tmp;
    }

    @SuppressLint("MissingPermission")
    public void run() {
        try {
            mmSocket.connect();
            InputStream inputStream = mmSocket.getInputStream();
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                bytes = inputStream.read(buffer);
                if (bytes == -1) {
                    Log.d(TAG, "End of stream reached, disconnecting");
                    break;
                }
                String readMessage = new String(buffer, 0, bytes);
                Log.d(TAG, "Read " + bytes + " bytes: " + readMessage);
                handler.obtainMessage(ERROR_READ, readMessage).sendToTarget();
            }
        } catch (IOException e) {
            Log.e(TAG, "Connection or read failed", e);
            handler.obtainMessage(ERROR_READ, "Connection lost or failed").sendToTarget();
        } finally {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }
        }
    }
//    public void run() {
//        try {
//            Log.d(TAG, "Attempting to connect socket...");
//            mmSocket.connect();
//            Log.d(TAG, "Socket connected.");
//            inputStream = mmSocket.getInputStream();
//            Log.d(TAG, "InputStream obtained.");
//        } catch (IOException connectException) {
//            handler.obtainMessage(ERROR_READ, "Unable to connect to the BT device").sendToTarget();
//            Log.e(TAG, "connectException: " + connectException);
//            try {
//                mmSocket.close();
//            } catch (IOException closeException) {
//                Log.e(TAG, "Could not close the client socket", closeException);
//            }
//            return;
//        }
//
//        byte[] buffer = new byte[1024];
//        int bytes;
//
//        while (isRunning) {
//            try {
//                Log.d(TAG, "Waiting to read from InputStream...");
//                bytes = inputStream.read(buffer);
//                if (bytes > 0) {
//                    String readMessage = new String(buffer, 0, bytes);
//                    Log.d(TAG, "Read " + bytes + " bytes: " + readMessage);
//                    handler.obtainMessage(ERROR_READ, readMessage).sendToTarget();
//                } else {
//                    Log.d(TAG, "Read 0 bytes from InputStream");
//                }
//            } catch (IOException e) {
//                Log.e(TAG, "Input stream was disconnected or error occurred", e);
//                break;
//            }
//        }
//        Log.d(TAG, "ConnectThread reading loop ended.");
//    }

    public void cancel() {
        isRunning = false;
        try {
            Log.d(TAG, "Closing socket...");
            mmSocket.close();
            Log.d(TAG, "Socket closed.");
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }

    public BluetoothSocket getMmSocket() {
        return mmSocket;
    }
}
