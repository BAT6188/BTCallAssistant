package kg.delletenebre.btcallassistant;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class EventsReceiver extends BroadcastReceiver {
    private static final String TAG = "EventsReceiver";
    private static boolean incomingCall = false;
    private static String lastCallState = TelephonyManager.EXTRA_STATE_IDLE;
    private static SharedPreferences settings;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (settings == null) {
            settings = PreferenceManager.getDefaultSharedPreferences(context);
        }
        final boolean DEBUG = settings.getBoolean("debug", false);
        final boolean ENABLE_SERVICE = settings.getBoolean("enableService", true);
        final boolean STOP_SERVICE_SCREEN_OFF = settings.getBoolean("stopServiceScreenOff", false);

        Map<String,String> data = new HashMap<>();
        data.put("event", "");
        data.put("type", "");
        data.put("state", "");
        data.put("number", "");
        data.put("contact", "");
        data.put("photo", "");
        data.put("message", "");

        if (action.equals(Intent.ACTION_USER_PRESENT)) {
            if (DEBUG) {
                Log.i(TAG, "**** ACTION_USER_PRESENT ****");
            }

            if (CommunicationService.service == null) {
                context.startService(new Intent(context, CommunicationService.class));
            }

        } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
            if (DEBUG) {
                Log.i(TAG, "****ACTION_SCREEN_OFF****");
            }

            if (STOP_SERVICE_SCREEN_OFF) {
                context.stopService(new Intent(context, CommunicationService.class));
            }

        } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            if (DEBUG) {
                Log.i(TAG, "**** BluetoothAdapter.ACTION_STATE_CHANGED ****");
            }

            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);

            if (state == BluetoothAdapter.STATE_OFF) {
                context.stopService(new Intent(context, CommunicationService.class));

            } else if (state == BluetoothAdapter.STATE_ON && CommunicationService.service == null) {
                context.startService(new Intent(context, CommunicationService.class));

            }




        } else if (action.equals("android.intent.action.PHONE_STATE")) {
            if (DEBUG) {
                Log.d(TAG, "**** ACTION_PHONE_STATE ****");
            }

            data.put("event", "call");

            String callState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if (lastCallState.equals(callState)) {
                return;

            } else if (callState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                // Трубка не поднята, телефон звонит
                String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                Map<String,String> contact = getContactInfo(context, phoneNumber);
                incomingCall = true;

                data.put("type", "incoming");
                data.put("state", "ringing");
                data.put("number", phoneNumber);
                data.put("contact", contact.get("name"));
                data.put("photo", contact.get("photo"));

            } else if (callState.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                // Телефон находится в режиме звонка (набор номера при исходящем звонке / разговор)
                data.put("state", "offhook");
                data.put("type", "incoming");

                if (!lastCallState.equals(TelephonyManager.EXTRA_STATE_RINGING)){//исходящий
                    data.put("type", "outgoing");
                    incomingCall = false;
                }

            } else if (callState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                // Телефон находится в ждущем режиме - это событие наступает по окончанию разговора
                // или в ситуации "отказался поднимать трубку".
                data.put("state", "idle");
                data.put("type", "incoming");

                if (lastCallState.equals(TelephonyManager.EXTRA_STATE_RINGING)){
                    //Ring but no pickup - a miss
                    data.put("state", "missed");
                } else if (!incomingCall) {
                    data.put("type", "outgoing");
                }
            }
            lastCallState = callState;

            if (CommunicationService.service != null) {
                CommunicationService.sendMessage(data);
            }
            if (DEBUG) {
                Log.d(TAG, data.toString());
            }

        } else if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
            if (DEBUG) {
                Log.d(TAG, "**** ACTION_SMS_RECEIVED ****");
            }

            data.put("event", "sms");
            data.put("type", "incoming");

            String phoneNumber = "";
            Map<String,String> contact = null;
            String smsMessage  = "";

            if (Build.VERSION.SDK_INT >= 19) {
                for (SmsMessage message : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    if (message == null) {
                        if (DEBUG) {
                            Log.e(TAG, "SMS is null");
                        }
                        break;
                    }

                    if (contact == null) {
                        phoneNumber = message.getDisplayOriginatingAddress();
                        contact = getContactInfo(context, phoneNumber);
                    }

                    smsMessage += message.getDisplayMessageBody();
                }
            } else {
                Bundle extras = intent.getExtras();
                Object[] smsData = (Object[]) extras.get("pdus");
                if (smsData != null) {
                    for (Object pdu : smsData) {
                        SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
                        if (message == null) {
                            if (DEBUG) {
                                Log.e(TAG, "SMS is null");
                            }
                            break;
                        }

                        if (contact == null) {
                            phoneNumber = message.getDisplayOriginatingAddress();
                            contact = getContactInfo(context, phoneNumber);
                        }

                        smsMessage += message.getDisplayMessageBody();
                    }
                }
            }

            data.put("number", phoneNumber);
            if (contact != null) {
                data.put("contact", contact.get("name"));
                data.put("photo", contact.get("photo"));
            } else {
                data.put("contact", phoneNumber);
                data.put("photo", "");
            }
            data.put("message", smsMessage);

            if (CommunicationService.service != null) {
                CommunicationService.sendMessage(data);
            }

            if (DEBUG) {
                Log.i(TAG, data.toString());
            }
        }


    }

    public static Map<String,String> getContactInfo(Context context, String phoneNumber) {
        Map<String,String> result = new HashMap<>();
        String contactName = phoneNumber;
        String contactPhoto = "";

        result.put("name", contactName);
        result.put("photo", contactPhoto);

        try {
            ContentResolver cr = context.getContentResolver();
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber));
            Cursor cursor = cr.query(uri,
                    new String[]{
                            ContactsContract.Contacts._ID,
                            ContactsContract.PhoneLookup.DISPLAY_NAME},
                    null, null, null);

            if (cursor == null) {
                return result;
            }

            if (cursor.moveToFirst()) {
                contactName = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));

                long contactId = cursor.getLong(
                        cursor.getColumnIndex(ContactsContract.Contacts.Photo._ID));

                Bitmap contactBitmap = loadContactPhoto(cr, contactId);
                if (contactBitmap != null) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    contactBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();

                    contactPhoto = Base64.encodeToString(byteArray, Base64.DEFAULT);
                }
            }

            if (!cursor.isClosed()) {
                cursor.close();
            }

            if (contactName == null || contactName.isEmpty()) {
                contactName = phoneNumber;
            }


        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }

        result.put("name", contactName);
        result.put("photo", contactPhoto);

        return result;
    }


    public static Bitmap loadContactPhoto(ContentResolver contentResolver, long contactId) {
        Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(contactId));
        InputStream photoStream = ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, true);
        BufferedInputStream buf = new BufferedInputStream(photoStream);
        Bitmap bitmap = BitmapFactory.decodeStream(buf);

        try {
            buf.close();
        } catch (IOException e) {
            Log.d(TAG, e.getLocalizedMessage());
        }

        int maxPhotoSize = 96;
        if (settings != null) {
            maxPhotoSize = Integer.parseInt(settings.getString("photo_max_size", "96"));
            if (maxPhotoSize < 32) {
                maxPhotoSize = 32;
            }
        }
        if (bitmap.getWidth() > maxPhotoSize || bitmap.getHeight() > maxPhotoSize) {
            bitmap = resizeBitmap(bitmap, maxPhotoSize, maxPhotoSize);
        }
        return bitmap;
    }

    public static Bitmap resizeBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();

        return resizedBitmap;
    }
}
