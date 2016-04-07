package kg.delletenebre.btcallassistant;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class EventsReceiver extends BroadcastReceiver {
    private final String TAG = getClass().getName();
    private static boolean incomingCall = false;
    private static String lastCallState = TelephonyManager.EXTRA_STATE_IDLE;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean DEBUG = settings.getBoolean("debug", false);
        final boolean ENABLE_SERVICE = settings.getBoolean("enableService", true);
        final boolean STOP_SERVICE_SCREEN_OFF = settings.getBoolean("stopServiceScreenOff", false);

        Map<String,String> data = new HashMap<>();
        data.put("event", "");
        data.put("type", "");
        data.put("state", "");
        data.put("number", "");
        data.put("contact", "");
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
                String contactName = getContactName(context, phoneNumber);
                incomingCall = true;

                data.put("type", "incoming");
                data.put("state", "ringing");
                data.put("number", phoneNumber);
                data.put("contact", contactName);

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
            String contactName = "";
            String smsMessage  = "";

            if (Build.VERSION.SDK_INT >= 19) {
                for (SmsMessage message : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    if (message == null) {
                        if (DEBUG) {
                            Log.e(TAG, "SMS is null");
                        }
                        break;
                    }
                    phoneNumber = message.getDisplayOriginatingAddress();
                    contactName = getContactName(context, phoneNumber);
                    smsMessage  += message.getDisplayMessageBody();
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
                        phoneNumber = message.getDisplayOriginatingAddress();
                        contactName = getContactName(context, phoneNumber);
                        smsMessage += message.getDisplayMessageBody();
                    }
                }
            }

            data.put("number", phoneNumber);
            data.put("contact", contactName);
            data.put("message", smsMessage);

            if (CommunicationService.service != null) {
                CommunicationService.sendMessage(data);
            }

            if (DEBUG) {
                Log.i(TAG, data.toString());
            }
        }


    }

    public static String getContactName(Context context, String phoneNumber) {
        try {
            ContentResolver cr = context.getContentResolver();
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phoneNumber));
            Cursor cursor = cr.query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                    null, null, null);
            if (cursor == null) {
                return phoneNumber;
            }
            String contactName = "";
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
            }

            if (!cursor.isClosed()) {
                cursor.close();
            }

            if (contactName == null || contactName.isEmpty()) {
                contactName = phoneNumber;
            }

            return contactName;
        } catch (Exception ex) {
            return phoneNumber;
        }
    }
}
