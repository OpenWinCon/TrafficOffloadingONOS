package sdn.trafficofflodingapp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

	private Intent udpListeningIntent = null;
	private Switch sdnSwitch;
	private ProgressDialog mProgressDialog;
	public TextView resultScan = null;

	private String LOG_TAG = "TrafficOffloading";
	private String SWITCH_SDN = "switchSDN";
	private String logmsg = "";
	UDPReceiver udpReceiver=null;
	IntentFilter filter = null;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		resultScan = (TextView) findViewById(R.id.ScanResultList);

		sdnSwitch = (Switch) findViewById(R.id.switch_sdn);
		sdnSwitch.setChecked(false);
		sdnSwitch.setSwitchTextAppearance(this, R.style.SwitchTextOffAppearance);
	
		// instantiate it within the onCreate method
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setIndeterminate(false);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setCancelable(true);
		
		udpReceiver = new UDPReceiver();
		filter= new IntentFilter(UDPReceiver.ACTION_RESP);
		filter.addCategory(Intent.CATEGORY_DEFAULT);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	public void onSwitchSDNClicked(View view) throws InterruptedException {
		udpListeningIntent = new Intent(this, TrafficOffloadingListener.class);
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean on = ((Switch) view).isChecked();
	
		if (on) {
			Log.d(LOG_TAG, "ONOS connetion switch is on");
			registerReceiver(udpReceiver, filter);
			sdnSwitch.setSwitchTextAppearance(this, R.style.SwitchTextAppearance);
			startService(udpListeningIntent);
		} else {
			logmsg = "";
			resultScan.setText(logmsg);
			Log.d(LOG_TAG, "ONOS connetion switch is off");
			sdnSwitch.setSwitchTextAppearance(this, R.style.SwitchTextOffAppearance);
			stopService(udpListeningIntent);

		}
	}

	@Override
	public void onStart() {
		super.onStart();

		// get previous state of the switch
		sdnSwitch.setChecked(getPreference(SWITCH_SDN, this));
	}

	@Override
	public void onStop() {
		super.onStop();
		// save current state of the switch
		setPreference(SWITCH_SDN, sdnSwitch.isChecked(), this);
	}

	private void setPreference(String key, Boolean value, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(key, value);
		editor.commit();
	}

	private Boolean getPreference(String key, Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		return preferences.getBoolean(key, false);
	}

	// print broadcast msg at textView
	public class UDPReceiver extends BroadcastReceiver {
		public static final String ACTION_RESP = "com.mamlambo.intent.action.MESSAGE_PROCESSED";

		@Override
		public void onReceive(Context context, Intent intent) {
			String extras = intent.getStringExtra("TrafficOffloadingListener");
			//logmsg = logmsg + "\n" + extras;

			resultScan.setText(logmsg);
			resultScan.setTextColor(Color.BLACK);
			Log.d(LOG_TAG, extras + "print broadcast msg at textView ");

		}

	}

}
