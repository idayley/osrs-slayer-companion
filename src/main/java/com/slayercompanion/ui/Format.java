package com.slayercompanion.ui;

import java.text.NumberFormat;
import java.util.Locale;

/** Number and time formatting shared by the panel view models. */
final class Format
{
	private Format()
	{
	}

	/** 1,840 / 12.4k / 101k / 1.2m. */
	static String compact(double value)
	{
		double v = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (v < 10_000)
		{
			return sign + NumberFormat.getIntegerInstance(Locale.US).format(Math.round(v));
		}
		if (v < 100_000)
		{
			return sign + oneDecimal(v / 1000) + "k";
		}
		if (v < 1_000_000)
		{
			return sign + Math.round(v / 1000) + "k";
		}
		return sign + oneDecimal(v / 1_000_000) + "m";
	}

	private static String oneDecimal(double v)
	{
		String s = String.format(Locale.US, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	/** ~1m 20s / ~45s. */
	static String travel(double seconds)
	{
		long s = Math.round(seconds);
		return s < 60 ? "~" + s + "s" : "~" + (s / 60) + "m " + (s % 60) + "s";
	}

	static String ordinal(int n)
	{
		int mod100 = n % 100;
		if (mod100 >= 11 && mod100 <= 13)
		{
			return n + "th";
		}
		switch (n % 10)
		{
			case 1:
				return n + "st";
			case 2:
				return n + "nd";
			case 3:
				return n + "rd";
			default:
				return n + "th";
		}
	}

	static String capitalize(String s)
	{
		return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
