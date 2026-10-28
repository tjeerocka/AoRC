package com.aorc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out the expected extra runes from gear and unlocks.
 *
 * Wiki rules used:
 * - Raiments of the Eye: 1/2/3 pieces = +10/20/30%, full set (4) = +60%.
 * - Abyssal lantern: willow +5% all runes, magic +10% all runes,
 *   blisterwood +20% blood runes only, redwood also gives the willow effect (+5%).
 * - Blood essence (active, in inventory): 50% chance of +1 blood rune per essence = +50% of base.
 * - Elite Kourend &amp; Kebos diary: +10% blood runes at the Arceuus blood altar.
 * - All bonuses add together and act on the base runes only (they do not stack on each other).
 *
 * This class has no RuneLite code, so it can be tested on its own.
 */
public final class BonusCalculator
{
	public enum Lantern
	{
		NONE(0, false, "None"),
		WILLOW(5, false, "Willow"),
		MAGIC(10, false, "Magic"),
		REDWOOD(5, false, "Redwood"),
		BLISTERWOOD(20, true, "Blisterwood");

		final int percent;
		final boolean bloodOnly;
		final String label;

		Lantern(int percent, boolean bloodOnly, String label)
		{
			this.percent = percent;
			this.bloodOnly = bloodOnly;
			this.label = label;
		}
	}

	/** One bonus line, for the overlay. */
	public static final class Bonus
	{
		public final String name;
		public final int percent;

		Bonus(String name, int percent)
		{
			this.name = name;
			this.percent = percent;
		}
	}

	private BonusCalculator()
	{
	}

	public static int raimentsPercent(int pieces)
	{
		switch (Math.max(0, Math.min(4, pieces)))
		{
			case 1:
				return 10;
			case 2:
				return 20;
			case 3:
				return 30;
			case 4:
				return 60;
			default:
				return 0;
		}
	}

	/**
	 * List of active bonuses for one rune type.
	 *
	 * @param bloodRunes   true when the rune being crafted is a blood rune
	 * @param raiments     number of Raiments of the Eye pieces worn (0-4)
	 * @param lantern      lit lantern worn
	 * @param bloodEssence active blood essence in inventory
	 * @param eliteDiary   elite Kourend &amp; Kebos diary done
	 */
	public static List<Bonus> activeBonuses(boolean bloodRunes, int raiments, Lantern lantern,
		boolean bloodEssence, boolean eliteDiary)
	{
		List<Bonus> list = new ArrayList<>();
		int r = raimentsPercent(raiments);
		if (r > 0)
		{
			list.add(new Bonus("Raiments " + Math.min(4, raiments) + "/4", r));
		}
		if (lantern != null && lantern != Lantern.NONE && (bloodRunes || !lantern.bloodOnly))
		{
			list.add(new Bonus(lantern.label, lantern.percent));
		}
		if (bloodRunes && bloodEssence)
		{
			list.add(new Bonus("Blood ess.", 50));
		}
		if (bloodRunes && eliteDiary)
		{
			list.add(new Bonus("Elite diary", 10));
		}
		return Collections.unmodifiableList(list);
	}

	public static int totalPercent(List<Bonus> bonuses)
	{
		int sum = 0;
		for (Bonus b : bonuses)
		{
			sum += b.percent;
		}
		return sum;
	}

	/** Expected total runes for a number of base runes. */
	public static double expectedRunes(int baseRunes, int totalPercent)
	{
		return baseRunes * (1.0 + totalPercent / 100.0);
	}

	/** Estimate of how many of the crafted runes came from bonuses. */
	public static int estimatedBonusRunes(int craftedRunes, int totalPercent)
	{
		if (totalPercent <= 0 || craftedRunes <= 0)
		{
			return 0;
		}
		double base = craftedRunes / (1.0 + totalPercent / 100.0);
		return (int) Math.round(craftedRunes - base);
	}
}
