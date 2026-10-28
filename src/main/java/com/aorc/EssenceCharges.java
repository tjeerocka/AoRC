package com.aorc;

/**
 * Charges left on the active blood essence (1,000 when activated, 1 per extra blood rune).
 * Exact after "Check" or activation; after that it is an estimate (50% chance per essence).
 */
public class EssenceCharges
{
	public static final int FULL = 1000;

	/** Data written to disk between RuneLite sessions. */
	public static class Saved
	{
		int charges = -1;
		double used;
	}

	private int charges = -1;   // last exact value, -1 = unknown
	private double used;        // estimated charges used since then

	/** From the "Check" message. */
	public void setExact(int value)
	{
		charges = Math.max(0, value);
		used = 0;
	}

	/** A new blood essence was activated. */
	public void activated()
	{
		setExact(FULL);
	}

	/** The essence is gone (all charges used). */
	public void empty()
	{
		setExact(0);
	}

	/** Expected extra blood runes from the essence in one craft. */
	public void use(double extraRunes)
	{
		if (charges >= 0 && extraRunes > 0)
		{
			used += extraRunes;
		}
	}

	/** -1 when unknown. */
	public int remaining()
	{
		if (charges < 0)
		{
			return -1;
		}
		return (int) Math.max(0, Math.round(charges - used));
	}

	public boolean isEstimate()
	{
		return used > 0;
	}

	public Saved save()
	{
		Saved s = new Saved();
		s.charges = charges;
		s.used = used;
		return s;
	}

	public void load(Saved s)
	{
		charges = s == null ? -1 : s.charges;
		used = s == null ? 0 : Math.max(0, s.used);
	}

	/**
	 * Expected essence runes in one blood craft.
	 * runes = what you got, bonusPercent = all bonuses incl. the essence's 50%.
	 */
	public static double expectedEssenceRunes(int runes, int bonusPercent)
	{
		double base = runes / (1.0 + bonusPercent / 100.0);
		return base * 0.5;
	}
}
