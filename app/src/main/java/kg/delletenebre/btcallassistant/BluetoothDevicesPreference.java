package kg.delletenebre.btcallassistant;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.res.Resources;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.util.Set;

public class BluetoothDevicesPreference extends ListPreference {

    public BluetoothDevicesPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bta.getBondedDevices();
        CharSequence[] entries = new CharSequence[pairedDevices.size() + 1];
        CharSequence[] entryValues = new CharSequence[pairedDevices.size() + 1];

        Resources res = context.getResources();
        entries[0] = res.getString(R.string.pref_bt_device_no_device);
        entryValues[0] = "";
        int i = 1;
        for (BluetoothDevice dev : pairedDevices) {
            entries[i] = String.format(res.getString(R.string.pref_bt_device_template),
                    dev.getName(),dev.getAddress());
            entryValues[i] = dev.getAddress();
            i++;
        }
        setEntries(entries);
        setEntryValues(entryValues);
    }

    public BluetoothDevicesPreference(Context context) {
        this(context, null);
    }

}