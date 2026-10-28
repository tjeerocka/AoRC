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
		assertEquals("x1.90", GpFormat.multiplier(90));
		assertEquals("x2.40", GpFormat.multiplier(140));
		assertEquals("x1.00", GpFormat.multiplier(0));
	}

	@Test
	public void perHour()
	{
		assertEquals(0, RuneSession.perHour(100, 0)); // no time yet
		assertEquals(12000, RuneSession.perHour(100, 30_000)); // shows from the start
		assertEquals(6000, RuneSession.perHour(100, 60_000));
		assertEquals(3381, RuneSession.perHour(3381, 3_600_000));
	}

	@Test
	public void breakDoesNotCount()
	{
		RuneSession s = new RuneSession();
		s.setIdleTimeoutMillis(300_000);
		// 10 min active (actions every 4 min or less)
		for (long t : new long[]{0, 240_000, 480_000, 600_000})
		{
			s.addRunes(RuneIds.BLOOD_RUNE, 100, t);
		}
		// 1 hour break, then 10 more minutes
		for (long t : new long[]{4_200_000, 4_440_000, 4_680_000, 4_800_000})
		{
			s.addRunes(RuneIds.BLOOD_RUNE, 100, t);
		}
		assertEquals(1_200_000, s.elapsedMillis(4_800_000, 300_000)); // 20 min, not 80
	}

	@Test
	public void saveAndLoad()
	{
		RuneSession a = new RuneSession();
		a.addRunes(RuneIds.BLOOD_RUNE, 400, 0);
		a.addCraft(0);
		a.addXp(5000, 0);
		a.addBlocksImbued(52, 240_000);
		a.addXp(10, 480_000);
		a.addXp(10, 720_000);
		a.addRunes(RuneIds.BLOOD_RUNE, 400, 900_000);
		a.addCraft(900_000);
		RuneSession.Saved saved = a.save(900_000);

		RuneSession b = new RuneSession();
		b.load(saved);
		assertTrue(b.isStarted());
		assertEquals(800, b.getRunes(RuneIds.BLOOD_RUNE));
		assertEquals(2, b.getCrafts());
		assertEquals(5020, b.getXpGained());
		assertEquals(52, b.getBlocksImbued());
		// before playing again: time is frozen at the saved 15 min
		assertEquals(900_000, b.elapsedMillis(99_999_999, 300_000));
		// play 5 more minutes after loading (next day): 15 + 5 = 20 min
		b.addRunes(RuneIds.BLOOD_RUNE, 100, 50_000_000);
		b.addRunes(RuneIds.BLOOD_RUNE, 100, 50_300_000);
		assertEquals(1_200_000, b.elapsedMillis(50_300_000, 300_000));
		assertEquals(1000, b.getRunes(RuneIds.BLOOD_RUNE));
	}

	@Test
	public void totalsSeparateResets()
	{
		RuneTotals t = new RuneTotals();
		t.addCraft(RuneIds.BLOOD_RUNE, 190, 90);   // 100 base + 90 bonus
		t.addCraft(RuneIds.BLOOD_RUNE, 160, 60);   // 100 base + 60 bonus (gear swapped)
		assertEquals(350, t.getRunes(RuneIds.BLOOD_RUNE));
		assertEquals(150, t.getBonusRunes());

		t.resetBonus();
		assertEquals(0, t.getBonusRunes());
		assertEquals(350, t.getRunes(RuneIds.BLOOD_RUNE)); // runes kept

		t.addCraft(RuneIds.BLOOD_RUNE, 190, 90);
		t.resetRunes();
		assertEquals(0, t.getRunes(RuneIds.BLOOD_RUNE));
		assertEquals(90, t.getBonusRunes());               // bonus kept

		RuneTotals u = new RuneTotals();
		u.load(t.save());
		assertEquals(90, u.getBonusRunes());
	}

	@Test
	public void panelSortAndTime()
	{
		java.util.List<PanelData.Row> rows = new java.util.ArrayList<>();
		rows.add(new PanelData.Row(556, "Air", 50, 0));
		rows.add(new PanelData.Row(560, "Death", 300, 0));
		rows.add(new PanelData.Row(RuneIds.BLOOD_RUNE, "Blood", 10, 0));
		PanelData.sortRows(rows);
		assertEquals("Blood", rows.get(0).name);   // blood always first
		assertEquals("Death", rows.get(1).name);   // then most crafted
		assertEquals("Air", rows.get(2).name);

		assertEquals("4m 12s", PanelData.formatTime(252_000));
		assertEquals("1h 05m", PanelData.formatTime(3_900_000));
		assertEquals("28 Oct 2026", PanelData.formatDate("2026-10-28"));
		assertEquals("-", PanelData.formatDate(null));
	}

	@Test
	public void dayLogBestDayAndTime()
	{
		DayLog log = new DayLog();
		long t = 300_000; // idle timeout 5 min

		// Day 1: 3 actions, 2 min apart = 4 min, 400 blood runes
		log.activity("2026-10-27", 0, t);
		log.activity("2026-10-27", 120_000, t);
		log.addRunes("2026-10-27", RuneIds.BLOOD_RUNE, 400);
		log.activity("2026-10-27", 240_000, t);

		// 1 hour break does not count
		log.activity("2026-10-28", 3_840_000, t);
		log.activity("2026-10-28", 4_080_000, t);           // +4 min
		log.addRunes("2026-10-28", RuneIds.BLOOD_RUNE, 900);
		log.addRunes("2026-10-28", 560, 100);               // death runes

		assertEquals(240_000, log.get("2026-10-27").getActiveMillis());
		assertEquals(240_000, log.get("2026-10-28").getActiveMillis());
		assertEquals("2026-10-28", log.bestDate());
		assertEquals(1000, log.get("2026-10-28").getRunes());
		assertEquals(900, log.get("2026-10-28").getBloodRunes());
		assertEquals(1400, log.total().getRunes());
		assertEquals(480_000, log.total().getActiveMillis());

		// live timer: 1 min after the last action
		assertEquals(60_000, log.liveMillis(4_140_000, t));
		assertEquals(0, log.liveMillis(4_080_000 + 400_000, t)); // idle: no live time

		// pause (logout): next action does not add the gap
		log.pause();
		assertEquals(0, log.liveMillis(4_090_000, t));
		log.activity("2026-10-28", 4_100_000, t);
		assertEquals(240_000, log.get("2026-10-28").getActiveMillis());

		DayLog copy = new DayLog();
		copy.load(log.save());
		assertEquals("2026-10-28", copy.bestDate());
		assertEquals(1400, copy.total().getRunes());
		assertEquals(0, new DayLog().total().getRunes());
		assertEquals(null, new DayLog().bestDate());
	}

	@Test
	public void tripEssenceTypes()
	{
		assertTrue(RuneIds.isTripEssence(1436));   // rune essence
		assertTrue(RuneIds.isTripEssence(7936));   // pure essence
		assertTrue(RuneIds.isTripEssence(24704));  // daeyalt essence
		assertTrue(RuneIds.isTripEssence(13445));  // dense essence block
		assertTrue(RuneIds.isTripEssence(13446));  // dark essence block
		assertTrue(RuneIds.isTripEssence(7938));   // dark essence fragments
		assertFalse(RuneIds.isTripEssence(565));   // blood rune
	}

	@Test
	public void tripStats()
	{
		TripTracker t = new TripTracker();
		// Started mid-trip: these runes are a half trip, not counted
		t.onCraft(150);
		t.onEssenceEmpty(0);
		assertEquals(0, t.getTrips());

		// Trip 1 (8 min): fragments craft + blocks craft
		t.onCraft(100);
		t.onCraft(104);
		t.onEssenceEmpty(480_000);
		// Trip 2 (7 min)
		t.onCraft(210);
		t.onEssenceEmpty(900_000);
		// Emptied again 20s later (pouch refill): same trip, nothing changes
		t.onCraft(10);
		t.onEssenceEmpty(920_000);

		assertEquals(2, t.getTrips());
		assertEquals((204 + 210) / 2, t.getAvgRunes());
		assertEquals(450_000, t.getAvgTripMillis());
		assertEquals(420_000, t.getFastestMillis());
		assertEquals(420_000, t.getLastTripMillis());

		// Trip 3 after a 1 hour break: counted, but its time is not used
		t.onEssenceEmpty(900_000 + 3_600_000);  // includes the 10 runes from above
		assertEquals(3, t.getTrips());
		assertEquals(450_000, t.getAvgTripMillis());
		assertEquals(-1, t.getLastTripMillis());

		// Essence deposited (no runes): not a trip
		t.onEssenceEmpty(900_000 + 3_600_000 + 120_000);
		assertEquals(3, t.getTrips());

		// After loading, the first empty moment only starts a new trip
		TripTracker c = new TripTracker();
		c.load(t.save());
		assertEquals(3, c.getTrips());
		c.onCraft(200);
		c.onEssenceEmpty(99_000_000);
		assertEquals(3, c.getTrips());

		t.reset();
		assertEquals(0, t.getTrips());
		assertEquals(-1, t.getAvgTripMillis());

		// Login with an empty inventory: the first trip counts
		TripTracker f = new TripTracker();
		f.onStartEmpty(0);
		f.onCraft(204);
		f.onEssenceEmpty(480_000);
		assertEquals(1, f.getTrips());
		assertEquals(480_000, f.getLastTripMillis());
	}

	@Test
	public void essenceCharges()
	{
		EssenceCharges e = new EssenceCharges();
		assertEquals(-1, e.remaining());          // unknown
		e.use(50);
		assertEquals(-1, e.remaining());          // still unknown
		e.activated();
		assertEquals(1000, e.remaining());
		assertFalse(e.isEstimate());
		// 190 runes with +90% (incl. essence 50%): base 100, essence ~50
		e.use(EssenceCharges.expectedEssenceRunes(190, 90));
		assertEquals(950, e.remaining());
		assertTrue(e.isEstimate());
		e.setExact(940);                          // "Check" message
		assertEquals(940, e.remaining());
		assertFalse(e.isEstimate());
		e.use(5000);
		assertEquals(0, e.remaining());           // never below 0

		EssenceCharges c = new EssenceCharges();
		c.load(e.save());
		assertEquals(0, c.remaining());
	}

	@Test
	public void goals()
	{
		assertEquals(0, Goals.xpForLevel(1));
		assertEquals(83, Goals.xpForLevel(2));
		assertEquals(1_210_421, Goals.xpForLevel(75));
		assertEquals(1_475_581, Goals.xpForLevel(77));
		assertEquals(13_034_431, Goals.xpForLevel(99));
		assertEquals(0, Goals.millisToGoal(0, 100));
		assertEquals(-1, Goals.millisToGoal(100, 0));
		assertEquals(7_200_000, Goals.millisToGoal(100_000, 50_000)); // 2 hours
	}

	@Test
	public void sessionAndIdle()
	{
		RuneSession s = new RuneSession();
		assertFalse(s.isStarted());
		s.addRunes(RuneIds.BLOOD_RUNE, 204, 0);
		s.addCraft(0);
		s.addBlocksImbued(26, 240_000);
		s.addRunes(RuneIds.BLOOD_RUNE, 210, 480_000);
		s.addCraft(480_000);
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
