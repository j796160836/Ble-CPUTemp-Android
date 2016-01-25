package com.johnnyworks.blecputemp.service;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.johnnyworks.blecputemp.MainActivity;
import com.johnnyworks.blecputemp.R;
import com.johnnyworks.blecputemp.bleprofile.TemperatureProfile;
import com.johnnyworks.blecputemp.utils.CpuTempReader;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Created by johnny on 10/21/15.
 */
public class PeripheralService extends Service {

	public static final String TAG = PeripheralService.class.getSimpleName();

	public final static String ACTION_CLOSE = "PeripheralService.ACTION_CLOSE";
	public final static String ACTION_START_SERVER = "PeripheralService.ACTION_START_SERVER";
	public final static String BROADCAST_CONNECTION_STATUS = "PeripheralService.BROADCAST_CONNECTION_STATUS";
	public final static String BROADCAST_CPU_TEMPERATURE_UPDATE = "PeripheralService.BROADCAST_CPU_TEMPERATURE_UPDATE";
	public final static String BROADCAST_DEVICES_CONNECTED = "PeripheralService.BROADCAST_DEVICES_CONNECTED";
	public final static String BROADCAST_SERVICE_CLOSED = "PeripheralService.BROADCAST_SERVICE_CLOSED";

	private TemperatureProfile mTemperatureProfile;

	private BluetoothGattService mBluetoothGattService;
	private HashSet<BluetoothDevice> mBluetoothDevices;
	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private AdvertiseData mAdvData;
	private AdvertiseSettings mAdvSettings;
	private BluetoothLeAdvertiser mAdvertiser;
	private Handler mHandler;
	private String mAdvertisingStatus = "";

	private final AdvertiseCallback mAdvCallback = new AdvertiseCallback() {
		@Override
		public void onStartFailure(int errorCode) {
			super.onStartFailure(errorCode);
			Log.e(TAG, "Not broadcasting: " + errorCode);
			int statusText;
			switch (errorCode) {
				case ADVERTISE_FAILED_ALREADY_STARTED:
					statusText = R.string.status_advertising;
					Log.w(TAG, "App was already advertising");
					break;
				case ADVERTISE_FAILED_DATA_TOO_LARGE:
					statusText = R.string.status_advDataTooLarge;
					break;
				case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
					statusText = R.string.status_advFeatureUnsupported;
					break;
				case ADVERTISE_FAILED_INTERNAL_ERROR:
					statusText = R.string.status_advInternalError;
					break;
				case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
					statusText = R.string.status_advTooManyAdvertisers;
					break;
				default:
					statusText = R.string.status_notAdvertising;
					Log.wtf(TAG, "Unhandled error: " + errorCode);
			}
			updateConnectionStatus(getString(statusText));
		}

		@Override
		public void onStartSuccess(AdvertiseSettings settingsInEffect) {
			super.onStartSuccess(settingsInEffect);
			Log.v(TAG, "Broadcasting");
			updateConnectionStatus(getString(R.string.status_advertising));
		}
	};

	private BluetoothGattServer mGattServer;
	private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
		@Override
		public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
			super.onConnectionStateChange(device, status, newState);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothGatt.STATE_CONNECTED) {
					mBluetoothDevices.add(device);
					updateConnectedDevicesStatus();
					Log.v(TAG, "Connected to device: " + device.getAddress());
				} else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
					mBluetoothDevices.remove(device);
					updateConnectedDevicesStatus();
					Log.v(TAG, "Disconnected from device");
				}
			} else {
				mBluetoothDevices.remove(device);
				updateConnectedDevicesStatus();
				// There are too many gatt errors (some of them not even in the documentation) so we just
				// show the error to the user.
				final String errorMessage = getString(R.string.status_errorWhenConnecting) + ": " + status;
				sendToastMessage(errorMessage);
				Log.e(TAG, "Error when connecting: " + status);
			}
		}

		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
		                                        BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
			Log.d(TAG, "Device tried to read characteristic: " + characteristic.getUuid());
			Log.d(TAG, "Value: " + Arrays.toString(characteristic.getValue()));
			if (offset != 0) {
				mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
			/* value (optional) */ null);
				return;
			}
			mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
					offset, characteristic.getValue());
		}

		@Override
		public void onNotificationSent(BluetoothDevice device, int status) {
			super.onNotificationSent(device, status);
			Log.v(TAG, "Notification sent. Status: " + status);
		}

		@Override
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
		                                         BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
		                                         int offset, byte[] value) {
			super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
					responseNeeded, offset, value);
			Log.v(TAG, "Characteristic Write request: " + Arrays.toString(value));
			int status = mTemperatureProfile.writeCharacteristic(characteristic, offset, value);
			if (responseNeeded) {
				/* No need to respond with an offset */
				/* No need to respond with a value */
				mGattServer.sendResponse(device, requestId, status, 0, null);
			}
		}
	};

	public void sendTemperatureMeasurementUpdate(double temperatureValue) {

		startForeground(1, generateNotification(temperatureValue));

		mTemperatureProfile.setTemperatureMeasurementValue(temperatureValue);
		sendNotificationToDevices(mTemperatureProfile.getTemperatureMeasurementCharacteristic());
	}

	private void sendNotificationToDevices(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothDevices.isEmpty()) {
			Log.i(TAG, "bluetoothDeviceNotConnected");
		} else {
			boolean indicate = (characteristic.getProperties()
					& BluetoothGattCharacteristic.PROPERTY_INDICATE)
					== BluetoothGattCharacteristic.PROPERTY_INDICATE;
			Log.v(TAG, "PROPERTY_INDICATE = " + indicate);
			for (BluetoothDevice device : mBluetoothDevices) {
				// true for indication (acknowledge) and false for notification (unacknowledge).
				mGattServer.notifyCharacteristicChanged(device, characteristic, indicate);
			}
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
		mBluetoothDevices = new HashSet<>();
		mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();

		mTemperatureProfile = new TemperatureProfile();
		mBluetoothGattService = mTemperatureProfile.getBluetoothGattService();

		mAdvSettings = new AdvertiseSettings.Builder()
				.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
				.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
				.setConnectable(true)
				.build();
		mAdvData = new AdvertiseData.Builder()
				.setIncludeDeviceName(true)
				.setIncludeTxPowerLevel(true)
				.addServiceUuid(mTemperatureProfile.getServiceUUID())
				.build();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && !TextUtils.isEmpty(intent.getAction())) {
			String action = intent.getAction();
			if (ACTION_START_SERVER.equals(action)) {
				startServer();
				startCpuMeasurementTimer();
			} else if (action.equals(ACTION_CLOSE)) {
				sendServiceClosedBroadcast();
				stopSelf();
			}
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		stopCpuMeasurementTimer();
		stopServer();
		super.onDestroy();
	}

	public void startCpuMeasurementTimer() {
		mHandler.post(getCpuTempTask);
	}

	public void stopCpuMeasurementTimer() {
		mHandler.removeCallbacks(getCpuTempTask);
	}

	public void startServer() {
		mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
		if (mGattServer == null) {
			if (!mBluetoothAdapter.isEnabled()) {
				updateConnectionStatus(getString(R.string.please_enable_bluetooth));
			}
			return;
		}
		// Add a service for a total of three services (Generic Attribute and Generic Access
		// are present by default).
		mGattServer.addService(mBluetoothGattService);

		if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {
			mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
			mAdvertiser.startAdvertising(mAdvSettings, mAdvData, mAdvCallback);
			Log.d(TAG, "=== startAdvertising ===");

			mAdvertisingStatus = getString(R.string.ble_start_advertising);
			startForeground(1, generateNotification(0));
		} else {
			updateConnectionStatus(getString(R.string.status_noLeAdv));
			Log.d(TAG, "=== status_noLeAdv ===");
			mAdvertisingStatus = getString(R.string.status_noLeAdv);
			startForeground(1, generateNotification(0));
		}
	}

	public void stopServer() {
		if (mGattServer != null) {
			mGattServer.close();
		}
		if (mAdvertiser != null) {
			// If stopAdvertising() gets called before close() a null
			// pointer exception is raised.
			mAdvertiser.stopAdvertising(mAdvCallback);
		}
	}

	// === Binder ===

	public class LocalBinder extends Binder {
		public PeripheralService getService() {
			return PeripheralService.this;
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return super.onUnbind(intent);
	}

	private final IBinder mBinder = new LocalBinder();

	public static boolean isServiceRunning(Context context) {
		ActivityManager manager = (ActivityManager) context
				.getSystemService(ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (PeripheralService.class.getName()
					.equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	// === Binder ===

	private Notification generateNotification(double temperatureValue) {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setSmallIcon(R.drawable.ic_notification);
		builder.setContentTitle(String.format(getString(R.string.cpu_temperature), temperatureValue));
		builder.setContentText(mAdvertisingStatus);

		Intent intent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(PeripheralService.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(pendingIntent);

		Intent iClose = new Intent(this, PeripheralService.class);
		iClose.setAction(ACTION_CLOSE);
		PendingIntent piClose = PendingIntent.getService(this, 0,
				iClose, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.addAction(R.drawable.ic_close, this.getResources().getString(R.string.stop_advertising), piClose);
		builder.setLocalOnly(true);
		builder.setOngoing(true);
		return builder.build();
	}

	private void sendToastMessage(String message) {
		Log.v(TAG, message);
	}

	private void updateConnectionStatus(String message) {
		Intent intent = new Intent(BROADCAST_CONNECTION_STATUS);
		intent.putExtra("message", message);
		LocalBroadcastManager.getInstance(PeripheralService.this).sendBroadcast(intent);
		Log.v(TAG, "ConnectionStatus  " + message);
	}

	private void updateConnectedDevicesStatus() {
		final String message = getString(R.string.status_devicesConnected) + " "
				+ mBluetoothDevices.size();
		Log.v(TAG, "=== " + message);
		Intent intent = new Intent(BROADCAST_DEVICES_CONNECTED);
		intent.putExtra("num", mBluetoothDevices.size());
		LocalBroadcastManager.getInstance(PeripheralService.this).sendBroadcast(intent);
	}

	private void updateCpuTemperature(double temperature) {
		Intent intent = new Intent(BROADCAST_CPU_TEMPERATURE_UPDATE);
		intent.putExtra("temperature", temperature);
		LocalBroadcastManager.getInstance(PeripheralService.this).sendBroadcast(intent);
		sendTemperatureMeasurementUpdate(temperature);
	}

	private void sendServiceClosedBroadcast() {
		Intent intent = new Intent(BROADCAST_SERVICE_CLOSED);
		LocalBroadcastManager.getInstance(PeripheralService.this).sendBroadcast(intent);
	}

	public void measureCpuTemperature() {
		CpuTempReader.getCPUTemp(new CpuTempReader.TemperatureResultCallback() {
			@Override
			public void callbackResult(CpuTempReader.ResultCpuTemperature result) {
				updateCpuTemperature(result.getTemperature());
			}
		});
	}

	private final Runnable getCpuTempTask = new Runnable() {
		@Override
		public void run() {
			measureCpuTemperature();
			mHandler.postDelayed(getCpuTempTask, 1000);
		}
	};
}
