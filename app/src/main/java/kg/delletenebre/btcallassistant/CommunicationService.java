package kg.delletenebre.btcallassistant;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import xdroid.toaster.Toaster;

import static kg.delletenebre.btcallassistant.JsonUtils.mapToJson;

public class CommunicationService extends Service {
    private static final String TAG = "CommunicationService";
    private static final String TOAST_PREFIX = "BTCA: ";
    private static boolean DEBUG;
    public static final String MY_BROADCAST_INTENT = "kg.delletenebre.btcallassistant.NEW_EVENT";

    protected static Service service;
    private EventsReceiver receiver;

    private SharedPreferences settings;

    private static String btDeviceAddress;
    private static BluetoothSPP bt;
    private static BluetoothAdapter bluetoothAdapter;
    private static String btMessage = "";
    private static String connectedBluetoothName = "";
    private static String connectedBluetoothAddress = "";



    BluetoothSPP.BluetoothConnectionListener bluetoothConnectionListener =
            new BluetoothSPP.BluetoothConnectionListener() {
        public void onDeviceConnected(String name, String address) {
            if (!btMessage.isEmpty()) {
                bt.send(btMessage, true);
                btMessage = "";

                restartService();
            } else {
                //Скорее всего входящее соединение
                connectedBluetoothName = name;
                connectedBluetoothAddress = address;
            }

            if (DEBUG) {
                Toaster.toast(TOAST_PREFIX + "Connected to " + name);
                Log.i(TAG, "Connected to " + name);
            }
        }

        public void onDeviceDisconnected() {
            if (DEBUG) {
                Log.i(TAG, "Connection lost");
            }
        }

        public void onDeviceConnectionFailed() {
            if (DEBUG) {
                Toaster.toast(TOAST_PREFIX + "Unable to connect");
                Log.i(TAG, "Unable to connect");
            }
        }
    };

    BluetoothSPP.OnDataReceivedListener onDataReceivedListener =
            new BluetoothSPP.OnDataReceivedListener() {
        public void onDataReceived(byte[] data, String message) {
            if (DEBUG) {
                Toaster.toast(TOAST_PREFIX + message);
                Log.d(TAG, "Received: " + message);
            }

            try {
                JSONObject dataJsonObj = new JSONObject(message);

                File photoFile = null;
                String photoBase64 = dataJsonObj.getString("photo");

                if (!photoBase64.isEmpty()) {
                    try {
                        photoFile = new File(Environment.getExternalStorageDirectory() + "/download/btca_contact_photo.png");

                        if (!photoFile.exists()) {
                            photoFile.getParentFile().mkdirs();
                            photoFile.createNewFile();
                        }

                        //write the bytes in file
                        FileOutputStream fos = new FileOutputStream(photoFile);
                        fos.write(Base64.decode(photoBase64, Base64.DEFAULT));
                        fos.flush();
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.getLocalizedMessage());
                    }
                }

                if (DEBUG) {
                    Log.d(TAG, "======== Received data ========");
                    Log.d(TAG, "event: " + dataJsonObj.getString("event"));
                    Log.d(TAG, "type: " + dataJsonObj.getString("type"));
                    Log.d(TAG, "state: " + dataJsonObj.getString("state"));
                    Log.d(TAG, "number: " + dataJsonObj.getString("number"));
                    Log.d(TAG, "contact: " + dataJsonObj.getString("contact"));
                    Log.d(TAG, "photo: " + photoBase64);
                    Log.d(TAG, "message: " + dataJsonObj.getString("message"));
                    Log.d(TAG, "btname: " + connectedBluetoothName);
                    Log.d(TAG, "btaddress: " + connectedBluetoothAddress);
                    Log.d(TAG, "===============================");

                    if (MainActivity.activity != null) {
                        byte[] decodedString = Base64.decode(dataJsonObj.getString("photo"), Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        if (decodedByte != null && photoFile != null) {
                            MainActivity.imageView.setImageURI(Uri.fromFile(photoFile));
                        }
                    }
                }



                Intent i = new Intent(MY_BROADCAST_INTENT);
                i.putExtra("event", dataJsonObj.getString("event"));
                i.putExtra("type", dataJsonObj.getString("type"));
                i.putExtra("state", dataJsonObj.getString("state"));
                i.putExtra("number", dataJsonObj.getString("number"));
                i.putExtra("contact", dataJsonObj.getString("contact"));
                if (photoFile != null) {
                    Log.d(TAG, photoFile.getAbsolutePath());
                    i.putExtra("photo", photoFile.getAbsolutePath());
                    i.putExtra("photouri", Uri.fromFile(photoFile));
                } else {
                    i.putExtra("photo", "");
                }
                i.putExtra("message", dataJsonObj.getString("message"));
                i.putExtra("btname", connectedBluetoothName);
                i.putExtra("btaddress", connectedBluetoothAddress);
                service.sendBroadcast(i);

            } catch (Exception e) {
                if (DEBUG) {
                    Toaster.toast("Creating broadcast intent ERROR!");
                }
                Log.e(TAG, e.getLocalizedMessage());
            }

            restartService();
        }
    };



    @Override
    public void onCreate() {
        super.onCreate();

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.getBoolean("enableService", true)) {
            stopSelf();
        }

        DEBUG = settings.getBoolean("debug", false);
        boolean BT_POPUPS = settings.getBoolean("btPopups", true);
        btDeviceAddress = settings.getString("btDevice", "");

        service = this;

        receiver = new EventsReceiver();
        registerReceiver(receiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        bt = new BluetoothSPP(this);

        if (!bt.isBluetoothAvailable()) {
            if (DEBUG) {
                Log.e(TAG, getString(R.string.bt_not_available));
            }

            if (BT_POPUPS) {
                Toaster.toast(R.string.bt_not_available);
            }

            stopSelf();
        } else {
            if (!bt.isBluetoothEnabled()) {
                if (DEBUG) {
                    Log.e(TAG, getString(R.string.bt_not_enabled));
                }

                if (BT_POPUPS) {
                    Toaster.toast(R.string.bt_not_enabled);
                }

                stopSelf();
            } else {
                bt.setupService();
                bt.startService(BluetoothState.DEVICE_ANDROID);
                bt.setBluetoothConnectionListener(bluetoothConnectionListener);
                bt.setOnDataReceivedListener(onDataReceivedListener);

                if (DEBUG) {
                    Log.d(TAG, "Service successfully created");
                }
            }
        }


    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bt != null) {
            bt.stopService();
        }

        if (receiver != null) {
            unregisterReceiver(receiver);
        }

        service = null;

        if (DEBUG) {
            Log.d(TAG, "Service destroyed");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }


    public static void setBtDeviceAddress(String address) {
        btDeviceAddress = address;
    }

    public static void setDEBUG(Boolean debug) {
        DEBUG = debug;
    }


    public static void sendMessage(String message) {
        if (bt != null && !btDeviceAddress.isEmpty()) {
            btMessage = message;
            bt.connect(btDeviceAddress);
        } else {
            Toaster.toastLong("Please choose in settings a bluetooth device to connect to");
        }
    }
    public static void sendMessage(Map<String,String> data) {
        //data.put("btnamefromdevice", getLocalBluetoothName());
        sendMessage(mapToJson(data).toString());
    }

    private void restartService() {
        new Handler().postDelayed(new Runnable() {
            public void run() {
                bt.stopService();

                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        bt.startService(BluetoothState.DEVICE_ANDROID);
                    }
                }, 500);
            }
        }, 500);

    }

    public static String getLocalBluetoothName() {
        if (bluetoothAdapter == null){
            bluetoothAdapter = bluetoothAdapter.getDefaultAdapter();
        }
        String name = bluetoothAdapter.getName();
        if (name == null) {
            name = bluetoothAdapter.getAddress();
        }
        return name;
    }

}
