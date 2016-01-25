package com.johnnyworks.blecputemp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.johnnyworks.blecputemp.service.PeripheralService;

public class MainActivity extends AppCompatActivity {
	public static final String TAG = MainActivity.class.getSimpleName();

	private PeripheralService mService;

	TextView labelMessage;
	TextView labelDevicesNum;
	TextView labelCpuTemp;
	Button buttonUpdate;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		labelMessage = (TextView) findViewById(R.id.label_message);
		labelDevicesNum = (TextView) findViewById(R.id.label_devices_num);
		labelCpuTemp = (TextView) findViewById(R.id.label_cpu_temp);
		buttonUpdate = (Button) findViewById(R.id.button_update);

		buttonUpdate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mService != null) {
					mService.measureCpuTemperature();
				}
			}
		});

		final String message = getString(R.string.status_devicesConnected) + " "
				+ 0;
		labelDevicesNum.setText(message);

		if (!PeripheralService.isServiceRunning(this)) {
			Intent startServiceIntent = new Intent(this, PeripheralService.class);
			startServiceIntent.setAction(PeripheralService.ACTION_START_SERVER);
			startService(startServiceIntent);
		}
		Intent bindServiceIntent = new Intent(this, PeripheralService.class);
		bindService(bindServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

		LocalBroadcastManager.getInstance(this).registerReceiver(mUartStatusChangeReceiver, makeGattUpdateIntentFilter());
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(PeripheralService.BROADCAST_CONNECTION_STATUS);
		intentFilter.addAction(PeripheralService.BROADCAST_CPU_TEMPERATURE_UPDATE);
		intentFilter.addAction(PeripheralService.BROADCAST_DEVICES_CONNECTED);
		intentFilter.addAction(PeripheralService.BROADCAST_SERVICE_CLOSED);
		return intentFilter;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		try {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(mUartStatusChangeReceiver);
		} catch (Exception ignore) {
			Log.e(TAG, ignore.toString());
		}
		unbindService(mServiceConnection);
		mService = null;
	}

	private final BroadcastReceiver mUartStatusChangeReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			final Intent mIntent = intent;
			//*********************//
			if (PeripheralService.BROADCAST_CPU_TEMPERATURE_UPDATE.equals(action)) {
				double temperature = intent.getDoubleExtra("temperature", 0);
				labelCpuTemp.setText(String.format("%.1f°C", temperature));
			} else if (PeripheralService.BROADCAST_CONNECTION_STATUS.equals(action)) {
				String message = intent.getStringExtra("message");
				labelMessage.setText(message);
			} else if (PeripheralService.BROADCAST_DEVICES_CONNECTED.equals(action)) {
				int num = intent.getIntExtra("num", 0);
				final String message = getString(R.string.status_devicesConnected) + " "
						+ num;
				labelDevicesNum.setText(message);
			} else if (PeripheralService.BROADCAST_SERVICE_CLOSED.equals(action)) {
				finish();
			}
		}
	};

	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder rawBinder) {
			mService = ((PeripheralService.LocalBinder) rawBinder).getService();
			Log.d(TAG, "onServiceConnected mService= " + mService);
		}

		public void onServiceDisconnected(ComponentName classname) {
			mService = null;
		}
	};

}
