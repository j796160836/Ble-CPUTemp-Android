package com.johnnyworks.blecputemp.utils;

import android.os.AsyncTask;
import android.util.Log;

import java.io.FileNotFoundException;

/**
 * Created by johnnysung on 15/10/10.
 */
public class CpuTempReader {
	public static final String TAG = CpuTempReader.class.getSimpleName();

	public static final String[] CPU_TEMP_FILE_PATHS = new String[]{
			"/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp"
			, "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp"
			, "/sys/class/thermal/thermal_zone0/temp"
			, "/sys/class/i2c-adapter/i2c-4/4-004c/temperature"
			, "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature"
			, "/sys/devices/platform/omap/omap_temp_sensor.0/temperature"
			, "/sys/devices/platform/tegra_tmon/temp1_input"
			, "/sys/kernel/debug/tegra_thermal/temp_tj"
			, "/sys/devices/platform/s5p-tmu/temperature"
			, "/sys/class/thermal/thermal_zone1/temp"
			, "/sys/class/hwmon/hwmon0/device/temp1_input"
			, "/sys/devices/virtual/thermal/thermal_zone1/temp"
			, "/sys/devices/platform/s5p-tmu/curr_temp"
			, "/sys/devices/virtual/thermal/thermal_zone0/temp"
			, "/sys/class/thermal/thermal_zone3/temp"
			, "/sys/class/thermal/thermal_zone4/temp"
	};

	private static boolean checkValueValid(Double temp) {
		return (temp != null) && (temp.doubleValue() >= -30.0) && (temp.doubleValue() <= 250.0);
	}

	public static void getCPUTemp(TemperatureResultCallback callback) {
		TryGetFilesTask task = new TryGetFilesTask();
		task.setCallback(callback);
		task.execute();
	}

	private static class TryGetFilesTask extends AsyncTask<Void, Void, ResultCpuTemperature> {

		private TemperatureResultCallback callback;
		private static ResultCpuTemperature cachedResult;

		public void setCallback(TemperatureResultCallback callback) {
			this.callback = callback;
		}

		@Override
		protected ResultCpuTemperature doInBackground(Void... voids) {
			String cpuFilePath = null;
			boolean needDivide1k = false;
			Double val = null;

			if (cachedResult != null) {
				try {
					val = OneLineReader.getValue(cachedResult.cpuFilePath);
				} catch (FileNotFoundException e1) {
				}
				if (val != null) {
					if (checkValueValid(val)) {
						// Found
					} else if (checkValueValid(val / 1000L)) {
						// Found
						val = val / 1000L;
					}
					cachedResult.temperature = val.doubleValue();
					return cachedResult;
				}
			}

			for (String path : CPU_TEMP_FILE_PATHS) {
				try {
					val = OneLineReader.getValue(path);
				} catch (FileNotFoundException e1) {
					Log.v(TAG, "Not found: " + path);
					continue;
				}
				if (val != null) {
					if (checkValueValid(val)) {
						// Found
						Log.v(TAG, "Found: " + path);
						cpuFilePath = path;
						needDivide1k = false;
						break;
					} else if (checkValueValid(val / 1000L)) {
						// Found
						Log.v(TAG, "Found: " + path);
						cpuFilePath = path;
						needDivide1k = true;
						val = val / 1000L;
						break;
					}
				}
			}
			if (cpuFilePath == null) {
				// Not Supported
				return null;
			}
			ResultCpuTemperature result = new ResultCpuTemperature();
			result.cpuFilePath = cpuFilePath;
			result.needDivide1k = needDivide1k;
			result.temperature = val.doubleValue();
			cachedResult = result;
			return result;
		}

		@Override
		protected void onPostExecute(ResultCpuTemperature resultTryFiles) {
			if (callback != null) {
				callback.callbackResult(resultTryFiles);
			}
		}
	}

	public interface TemperatureResultCallback {
		void callbackResult(ResultCpuTemperature result);
	}

	public static class ResultCpuTemperature {
		String cpuFilePath = null;
		boolean needDivide1k;
		double temperature;

		public String getCpuFilePath() {
			return cpuFilePath;
		}

		public boolean isNeedDivide1k() {
			return needDivide1k;
		}

		public double getTemperature() {
			return temperature;
		}
	}
}
