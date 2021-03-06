package com.linhnguyen.rccar.service;

/**
 * Created by linhn on 3/28/17.
 */

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.linhnguyen.rccar.core.RCCarAttribute;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    private boolean mGattReady = false;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.linhnguyen.rccar.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.linhnguyen.rccar.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.linhnguyen.rccar.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.linhnguyen.rccar.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.linhnguyen.rccar.le.EXTRA_DATA";
    public final static String RCCAR_MOVE_DATA =
            "com.linhnguyen.rccar.le.MOVE_DATA";
    public final static String RCCAR_SOUND_DATA =
            "com.linhnguyen.rccar.le.SOUND_DATA";

    public final static UUID UUID_RCCAR_CONTROL =
            UUID.fromString(RCCarAttribute.CAR_MOVE_CHARACTERISTIC_CONFIG);
    public final static UUID UUID_RCCAR_SOUND =
            UUID.fromString(RCCarAttribute.CAR_SOUND_CHARACTERISTIC_CONFIG);

    private LocalBroadcastManager localBroadcastManager;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
                mGattReady = false;

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                mGattReady = false;
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                mGattReady = true;
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

            if (getMoveChar() == null) {
                Log.e(TAG, "This BLE device is not compatible");
                mGattReady = false;
                return;
            }
        }
    };

    private BluetoothGattCharacteristic getMoveChar() {
        BluetoothGattCharacteristic res = null;

        BluetoothGattService mGattService = mBluetoothGatt.getService(UUID.fromString(RCCarAttribute.CAR_SERVICE_UUID));
        if (mGattService == null) {
            Log.e(TAG, "get move char: RC CAR service not found!!!");
            return res;
        }

        res = mGattService.getCharacteristic(UUID.fromString(RCCarAttribute.CAR_MOVE_CHARACTERISTIC_CONFIG));
        if (res == null) {
            Log.e(TAG, "get move char: RC CAR Move Characteristic not found!!!");
        }

        return res;
    }

    private BluetoothGattCharacteristic getSoundChar() {
        BluetoothGattCharacteristic res = null;

        BluetoothGattService mGattService = mBluetoothGatt.getService(UUID.fromString(RCCarAttribute.CAR_SERVICE_UUID));
        if (mGattService == null) {
            Log.e(TAG, "get sound char: RC CAR service not found!!!");
            return res;
        }

        res = mGattService.getCharacteristic(UUID.fromString(RCCarAttribute.CAR_SOUND_CHARACTERISTIC_CONFIG));
        if (res == null) {
            Log.e(TAG, "get sound char: RC CAR Sound Characteristic not found!!!");
        }

        return res;
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        localBroadcastManager.sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        mGattReady = false;
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        mGattReady = false;
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        localBroadcastManager = LocalBroadcastManager.getInstance(this);

        localBroadcastManager.registerReceiver(mGattCarReceiver, makeGattRccarIntentFilter());
    }

    private static IntentFilter makeGattRccarIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.RCCAR_SOUND_DATA);
        intentFilter.addAction(BluetoothLeService.RCCAR_MOVE_DATA);
        return intentFilter;
    }

    private final BroadcastReceiver mGattCarReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "Service received message: " + action);
            if (!mGattReady) {
                Log.i(TAG, "Gatt is not discovered. Ignore.");
                return;
            }
            if (BluetoothLeService.RCCAR_MOVE_DATA.equals(action)) {
                BluetoothGattCharacteristic mGattChar = getMoveChar();
                if (mGattChar == null) {
                    disconnect();
                }
                Log.i(TAG, "GATT CAR MOVE");

                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "car move data: " + value.toString());
                mGattChar.setValue(value);
                boolean res = mBluetoothGatt.writeCharacteristic(mGattChar);
                if (!res) {
                    Log.e(TAG, "Write move data " + value.toString() + " failed");
                }
            } else if (BluetoothLeService.RCCAR_SOUND_DATA.equals(action)) {
                BluetoothGattCharacteristic mGattChar = getSoundChar();
                if (mGattChar == null) {
                    disconnect();
                }
                Log.i(TAG, "GATT CAR SOUND");
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                mGattChar.setValue(value);
                boolean res = mBluetoothGatt.writeCharacteristic(mGattChar);
                if (!res) {
                    Log.e(TAG, "Write sound data " + value.toString() + " failed");
                }
            }
        }
    };
}

