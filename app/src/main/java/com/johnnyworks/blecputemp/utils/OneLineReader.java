package com.johnnyworks.blecputemp.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

/**
 * Created by johnnysung on 15/10/10.
 */
public class OneLineReader {
	private static final String TAG = OneLineReader.class.getSimpleName();

	public static Double getValue(String path) throws FileNotFoundException {
		String str;
		try {
			File file = new File(path);
			FileInputStream inputStream = new FileInputStream(file);
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			str = bufferedReader.readLine();
			bufferedReader.close();
			inputStreamReader.close();
			inputStream.close();
			Double value;
			if (str == null) {
				return null;
			}
			try {
				value = Double.parseDouble(str);
			} catch (NumberFormatException e) {
				e.printStackTrace();
				value = null;
			}
			return value;
		} catch (FileNotFoundException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
