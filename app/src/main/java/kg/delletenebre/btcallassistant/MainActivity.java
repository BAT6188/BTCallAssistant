package kg.delletenebre.btcallassistant;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private SharedPreferences settings;
    private boolean DEBUG = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = PreferenceManager.getDefaultSharedPreferences(this);

        if (CommunicationService.service == null) {
            startService(new Intent(this, CommunicationService.class));
        }

        final Spinner spinnerEvent = (Spinner) findViewById(R.id.spinner_event);
        final Spinner spinnerType = (Spinner) findViewById(R.id.spinner_type);
        final Spinner spinnerState = (Spinner) findViewById(R.id.spinner_state);
        final EditText textNumber = (EditText) findViewById(R.id.number);
        final EditText textContact = (EditText) findViewById(R.id.contact);
        final EditText textSMSMessage = (EditText) findViewById(R.id.message);

        if (textNumber != null) {
            textNumber.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (!hasFocus) {
                        if (textContact != null) {
                            textContact.setText(EventsReceiver.getContactName(MainActivity.this,
                                    textNumber.getText().toString()));
                        }
                    }
                }
            });
        }


        Button btnSend = (Button) findViewById(R.id.emulateIntent);
        if (btnSend != null) {
            btnSend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (spinnerEvent != null && spinnerType != null && spinnerState != null
                        && textNumber != null && textContact != null && textSMSMessage != null) {

                        Map<String,String> data = new HashMap<>();
                        data.put("event", spinnerEvent.getSelectedItem().toString());
                        data.put("type", spinnerType.getSelectedItem().toString());
                        data.put("state", spinnerState.getSelectedItem().toString());
                        data.put("number", textNumber.getText().toString());
                        data.put("contact", textContact.getText().toString());
                        data.put("message", textSMSMessage.getText().toString());

                        if (CommunicationService.service != null) {
                            CommunicationService.sendMessage(data);
                        } else if (DEBUG) {
                            Toast.makeText(MainActivity.this,
                                    "Service not started", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (settings != null) {
            DEBUG = settings.getBoolean("debug", false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        }

        return super.onOptionsItemSelected(item);
    }


}
