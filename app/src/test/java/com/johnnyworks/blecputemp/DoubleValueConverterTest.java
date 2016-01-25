package com.johnnyworks.blecputemp;

import com.johnnyworks.blecputemp.utils.DoubleValueConverter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class DoubleValueConverterTest {
	@Test
	public void convertTest() throws Exception {
		double temperature = 98.5;
		int expectExponent = -1;
		int expectMantissa = 985;

		DoubleValueConverter doubleValue = new DoubleValueConverter();
		doubleValue.setValue(temperature);

		assertEquals(expectExponent, doubleValue.getExponent());
		assertEquals(expectMantissa, doubleValue.getMantissa());
	}
}