package com.johnnyworks.blecputemp.utils;

/**
 * Created by johnnysung on 16/01/31.
 */
public class DoubleValueConverter {

	private int mantissa;
	private int exponent;

	public DoubleValueConverter() {
	}

	public DoubleValueConverter(double value) {
		setValue(value);
	}

	public void setValue(double value) {
		String tempStr = String.format("%.1f", value);
		String[] tempStrArr = tempStr.split("\\.");

		mantissa = Integer.valueOf(tempStrArr[0] + tempStrArr[1]);
		exponent = tempStrArr[1].length() * -1;
	}

	public int getMantissa() {
		return mantissa;
	}

	public int getExponent() {
		return exponent;
	}
}
