package com.debttracker;

final class GpFormat
{
	private static final long THOUSAND = 1_000L;
	private static final long MILLION = 1_000_000L;
	private static final long BILLION = 1_000_000_000L;

	private GpFormat()
	{
	}

	/**
	 * Formats gp compactly, rounding toward zero so 2,799,999 never reads as a finished 2.8M.
	 */
	static String format(long gp)
	{
		String sign = gp < 0 ? "-" : "";
		long abs = Math.abs(gp);
		if (abs >= BILLION)
		{
			return sign + scaled(abs, BILLION, 100) + "B";
		}
		if (abs >= MILLION)
		{
			return sign + scaled(abs, MILLION, 100) + "M";
		}
		if (abs >= THOUSAND)
		{
			return sign + scaled(abs, THOUSAND, 10) + "K";
		}
		return sign + abs;
	}

	private static String scaled(long value, long unit, int precision)
	{
		long whole = value / unit;
		long fraction = value % unit * precision / unit;
		if (fraction == 0)
		{
			return Long.toString(whole);
		}
		String digits = String.format(precision == 100 ? "%02d" : "%d", fraction);
		return whole + "." + digits.replaceAll("0+$", "");
	}
}
