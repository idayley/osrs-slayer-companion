package com.slayercompanion.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatTest
{
	@Test
	public void compactNumbers()
	{
		assertEquals("0", Format.compact(0));
		assertEquals("1,840", Format.compact(1840));
		assertEquals("12.4k", Format.compact(12_400));
		assertEquals("31k", Format.compact(31_000));
		assertEquals("101k", Format.compact(101_250));
		assertEquals("1.2m", Format.compact(1_234_567));
		assertEquals("12m", Format.compact(12_000_000));
	}

	@Test
	public void travel()
	{
		assertEquals("~1m 20s", Format.travel(80));
		assertEquals("~45s", Format.travel(45));
	}

	@Test
	public void ordinals()
	{
		assertEquals("1st", Format.ordinal(1));
		assertEquals("12th", Format.ordinal(12));
		assertEquals("22nd", Format.ordinal(22));
		assertEquals("50th", Format.ordinal(50));
		assertEquals("113th", Format.ordinal(113));
	}
}
