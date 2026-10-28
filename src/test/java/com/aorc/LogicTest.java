package com.aorc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class LogicTest
{
	@Test
	public void raimentsPercent()
	{
		assertEquals(0, BonusCalculator.raimentsPercent(0));
		assertEquals(10, BonusCalculator.raimentsPercent(1));
		assertEquals(30, BonusCalculator.raimentsPercent(3));
		assertEquals(60, BonusCalculator.raimentsPercent(4));
	}

	@Test
	public void fullBloodSetup()
	{
		// Raiments 60 + blisterwood 20 + blood essence 50 + elite diary 10 = 140
		List<BonusCalculator.Bonus> b = BonusCalculator.activeBonuses(true, 4,
			BonusCalculator.Lantern.BLISTERWOOD, true, true);
		assertEquals(140, BonusCalculator.totalPercent(b));
	}

	@Test
	public void bloodOnlyBonusesSkipOtherRunes()
	{
		// Soul runes: only raiments count (blisterwood, essence, diary are blood only)
		List<BonusCalculator.Bonus> b = BonusCalculator.activeBonuses(false, 4,
			BonusCalculator.Lantern.BLISTERWOOD, true, true);
		assertEquals(60, BonusCalculator.totalPercent(b));
	}

	@Test
	public void matchesWikiNoDiaryNumbers()
	{
		// Wiki no-diary guide: 1,470 base blood runes/hr + raiments 882 + essence 735 + lantern 294
		int total = BonusCalculator.totalPercent(BonusCalculator.activeBonuses(true, 4,
			BonusCalculator.Lantern.BLISTERWOOD, true, false));
		assertEquals(130, total);
		assertEquals(1470 + 882 + 735 + 294, (int) Math.round(BonusCalculator.expectedRunes(1470, total)));
		assertEquals(882 + 735 + 294, BonusCalculator.estimatedBonusRunes(3381, total));
	}

	@Test
	public void gpFormat()
	{
		assertEquals("1m", GpFormat.shortGp(1_000_000));
		assertEquals("600k", GpFormat.shortGp(600_000));
		assertEquals("2.79m", GpFormat.shortGp(2_791_216));
		assertEquals("1.5m", GpFormat.shortGp(1_500_000));
		assertEquals("999k", GpFormat.shortGp(999_999));
		assertEquals("950", GpFormat.shortGp(950));
		assertEquals("0", GpFormat.shortGp(0));
	}

	@Test
	public void perHour()
	{
		assertEquals(0, RuneSession.perHour(100, 30_000)); // under 1 minute: hidden
		assertEquals(6000, RuneSession.perHour(100, 60_000));
		assertEquals(3381, RuneSession.perHour(3381, 3_600_000));
	}

	@Test
	public void sessionAndIdle()
	{
		RuneSession s = new RuneSession();
		assertFalse(s.isStarted());
		s.addRunes(RuneIds.BLOOD_RUNE, 204, 0);
		s.addCraft(0);
		s.addRunes(RuneIds.BLOOD_RUNE, 210, 480_000);
		s.addCraft(480_000);
		s.addBlocksImbued(26, 400_000);
		assertTrue(s.isStarted());
		assertEquals(414, s.getRunes(RuneIds.BLOOD_RUNE));
		assertEquals(2, s.getCrafts());
		assertEquals(26, s.getBlocksImbued());

		long timeout = 300_000;
		assertFalse(s.isIdle(600_000, timeout));
		assertEquals(600_000, s.elapsedMillis(600_000, timeout));
		// idle: timer stops at last action (480s)
		assertTrue(s.isIdle(900_000, timeout));
		assertEquals(480_000, s.elapsedMillis(900_000, timeout));

		s.reset();
		assertFalse(s.isStarted());
		assertEquals(0, s.getTotalRunes());
	}
}
