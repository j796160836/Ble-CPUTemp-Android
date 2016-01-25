package com.johnnyworks.blecputemp.bleprofile;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.ParcelUuid;
import android.util.Log;

import com.johnnyworks.blecputemp.utils.DoubleValueConverter;

import java.util.Arrays;
import java.util.UUID;

/**
 * Created by johnny on 10/21/15.
 */
public class TemperatureProfile {
	private static final String TAG = TemperatureProfile.class.getSimpleName();

	/**
	 * See https://developer.bluetooth.org/gatt/services/Pages/ServiceViewer.aspx?u=org.bluetooth.service.health_thermometer.xml
	 * for details
	 */
	private static final UUID TEMPERATURE_SERVICE_UUID = UUID
			.fromString("00001809-0000-1000-8000-00805f9b34fb");

	/**
	 * See https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic
	 * .temperature_measurement.xml
	 * for details
	 */
	private static final UUID TEMPERATURE_MEASUREMENT_CHAR_UUID = UUID
			.fromString("00002A1C-0000-1000-8000-00805f9b34fb");

	public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private static final double INITIAL_TEMPERATURE_MEASUREMENT_VALUE = 98.5f;

	private BluetoothGattService mTemperatureService;
	private BluetoothGattCharacteristic mTemperatureMeasurementCharacteristic;

	public TemperatureProfile() {
		mTemperatureMeasurementCharacteristic =
				new BluetoothGattCharacteristic(TEMPERATURE_MEASUREMENT_CHAR_UUID,
						BluetoothGattCharacteristic.PROPERTY_INDICATE,
						BluetoothGattCharacteristic.PERMISSION_READ);

		mTemperatureMeasurementCharacteristic.addDescriptor(
				getClientCharacteristicConfigurationDescriptor());

		mTemperatureService = new BluetoothGattService(TEMPERATURE_SERVICE_UUID,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);
		mTemperatureService.addCharacteristic(mTemperatureMeasurementCharacteristic);

		setInitialValues();
	}

	public static BluetoothGattDescriptor getClientCharacteristicConfigurationDescriptor() {
		return new BluetoothGattDescriptor(CCCD,
				(BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
	}

	public void setInitialValues() {
		setTemperatureMeasurementValue(INITIAL_TEMPERATURE_MEASUREMENT_VALUE);
	}

	public BluetoothGattService getBluetoothGattService() {
		return mTemperatureService;
	}

	public ParcelUuid getServiceUUID() {
		return new ParcelUuid(TEMPERATURE_SERVICE_UUID);
	}

	public void setTemperatureMeasurementValue(double temperatureMeasurementValue) {
		DoubleValueConverter doubleValue = new DoubleValueConverter();
		doubleValue.setValue(temperatureMeasurementValue);

		Log.d(TAG, "mantissa = " + doubleValue.getMantissa() + "  exponent = " + doubleValue.getExponent());
		mTemperatureMeasurementCharacteristic.setValue(new byte[]{0, 0, 0, 0, 0});
		mTemperatureMeasurementCharacteristic.setValue(doubleValue.getMantissa(),
				doubleValue.getExponent(),
				BluetoothGattCharacteristic.FORMAT_FLOAT,
				/* offset */ 1);

		Log.d(TAG, Arrays.toString(mTemperatureMeasurementCharacteristic.getValue()));
	}

	public int writeCharacteristic(BluetoothGattCharacteristic characteristic, int offset, byte[] value) {
		Log.v(TAG, "writeCharacteristic");
		if (offset != 0) {
			return BluetoothGatt.GATT_INVALID_OFFSET;
		}
		if (value.length != 1) {
			return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
		}
		return BluetoothGatt.GATT_SUCCESS;
	}

	public BluetoothGattCharacteristic getTemperatureMeasurementCharacteristic() {
		return mTemperatureMeasurementCharacteristic;
	}
}
