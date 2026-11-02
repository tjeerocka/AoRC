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
		assertEquals("30,8k", GpFormat.shortRate(30_895));
		assertEquals("9,999", GpFormat.shortRate(9_999));
		assertEquals("10k", GpFormat.shortRate(10_000));
		assertEquals("2,356", GpFormat.shortRate(2_356));
		assertEquals("999,9k", GpFormat.shortRate(999_999));
		assertEquals("1,23m", GpFormat.shortRate(1_234_567));
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
	public void storageWritesAndReadsPerAccount() throws Exception
	{
		java.io.File base = java.nio.file.Files.createTempDirectory("aorc-test").toFile();
		AorcStorage st = new AorcStorage(base);
		assertEquals(null, st.read(123L));                 // no file yet
		st.write(123L, "{\"a\":1}");
		st.write(123L, "{\"a\":2}");                       // replaces, does not add
		assertEquals("{\"a\":2}", st.read(123L));
		st.write(456L, "{\"b\":1}");                       // other account, own file
		assertEquals("{\"a\":2}", st.read(123L));
		assertEquals("{\"b\":1}", st.read(456L));
		assertTrue(new java.io.File(base, "aorc/123.json").exists());
		assertFalse(new java.io.File(base, "aorc/123.json.tmp").exists());
	}

	@Test
	public void hypeBuddyMessages()
	{
		HypeBuddy b = new HypeBuddy();
		PanelData d = new PanelData();
		d.currentLevel = 86;
		d.goalLevel = 99;
		d.goalLevelMillis = 3_600_000L * 200;
		d.todayRunes = 500;
		d.bestRunes = 500;
		d.days = 1;

		d.accountType = AccountType.IRONMAN;
		// first: hello for an ironman
		assertEquals("An ironman! Respect. Let's craft!", b.message(d, 0));
		// after 10 s: a tip about the goal / runes
		String tip = b.message(d, 10_000);
		assertFalse(tip.startsWith("Hi!"));

		// a trip finished: event message
		d.trips = 1;
		d.lastTripMillis = 267_000;
		d.fastestTripMillis = 267_000;
		assertEquals("Trip done in 4m 27s. Iron efficiency!", b.message(d, 11_000));
		// second trip, new fastest
		d.trips = 2;
		d.lastTripMillis = 250_000;
		d.fastestTripMillis = 250_000;
		assertEquals("NEW FASTEST TRIP! 4m 10s! You're on fire!", b.message(d, 12_000));
		// event stays for 15 s, then tips again
		assertTrue(b.message(d, 20_000).startsWith("NEW FASTEST TRIP!"));
		assertFalse(b.message(d, 40_000).startsWith("NEW FASTEST TRIP!"));

		// essence almost empty is in the tips
		d.essenceCharges = 50;
		assertTrue(b.tips(d).get(0).contains("almost empty"));

		// a main gets trash talk (with spitting)
		HypeBuddy m = new HypeBuddy();
		PanelData md = new PanelData();
		md.accountType = AccountType.MAIN;
		assertTrue(m.message(md, 0).contains("*spits*"));
		assertTrue(HypeBuddy.tripDone(AccountType.MAIN, "4m 00s").contains("*spits*"));
		assertTrue(HypeBuddy.hello(AccountType.HARDCORE).contains("NOT die"));
		assertTrue(HypeBuddy.personality(AccountType.ULTIMATE).get(0).contains("No bank"));
	}

	@Test
	public void accountTypeFromVarbit()
	{
		assertEquals(AccountType.MAIN, AccountType.fromVarbit(0));
		assertEquals(AccountType.IRONMAN, AccountType.fromVarbit(1));
		assertEquals(AccountType.ULTIMATE, AccountType.fromVarbit(2));
		assertEquals(AccountType.HARDCORE, AccountType.fromVarbit(3));
		assertEquals(AccountType.GROUP, AccountType.fromVarbit(4));
		assertEquals(AccountType.HARDCORE_GROUP, AccountType.fromVarbit(5));
		assertEquals(AccountType.UNRANKED_GROUP, AccountType.fromVarbit(6));
	}

	@Test
	public void activityStartsTimer()
	{
		RuneSession s = new RuneSession();
		s.setIdleTimeoutMillis(300_000);
		s.addActivity(0);               // mining dense essence
		s.addActivity(120_000);         // chiseling
		assertTrue(s.isStarted());
		assertEquals(120_000, s.elapsedMillis(120_000, 300_000));
		s.addXp(500, 200_000);          // imbue xp keeps it going
		assertEquals(200_000, s.elapsedMillis(200_000, 300_000));
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
		assertEquals(900, log.get("2026-10-28").runesOf(RuneIds.BLOOD_RUNE));
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
		// Started mid-trip: counts as a trip with its runes, but without a time
		t.onCraft(150);
		t.onEssenceEmpty(0);
		assertEquals(0, t.getTrips());          // waits 60 s for pouch crafts
		t.tick(60_000);
		assertEquals(1, t.getTrips());
		assertEquals(-1, t.getLastTripMillis());
		assertEquals(-1, t.getAvgTripMillis());

		// Trip 2 (8 min): fragments craft + blocks craft
		t.onCraft(100);
		t.onCraft(104);
		t.onEssenceEmpty(480_000);
		t.tick(540_000);
		// Trip 3: inventory crafted at 15:00, pouch emptied and crafted again at 15:20
		t.onCraft(210);
		t.onEssenceEmpty(900_000);
		t.tick(910_000);                       // still waiting
		t.onCraft(10);
		t.onEssenceEmpty(920_000);             // same trip, it now ends at 15:20
		t.tick(979_000);
		assertEquals(2, t.getTrips());
		t.tick(980_000);

		assertEquals(3, t.getTrips());
		assertEquals((150 + 204 + 220) / 3, t.getAvgRunes());
		assertEquals((480_000 + 440_000) / 2, t.getAvgTripMillis());   // only the timed trips
		assertEquals(440_000, t.getFastestMillis());
		assertEquals(440_000, t.getLastTripMillis());

		// Trip 4 after a 1 hour break: counted, but its time is not used
		t.onCraft(10);
		t.onEssenceEmpty(920_000 + 3_600_000);
		t.tick(920_000 + 3_660_000);
		assertEquals(4, t.getTrips());
		assertEquals(460_000, t.getAvgTripMillis());
		assertEquals(-1, t.getLastTripMillis());

		// Essence deposited (no runes): not a trip
		t.onEssenceEmpty(920_000 + 3_800_000);
		t.tick(920_000 + 3_900_000);
		assertEquals(4, t.getTrips());

		t.reset();
		assertEquals(0, t.getTrips());
		assertEquals(-1, t.getAvgTripMillis());

		// Login with an empty inventory: the first trip is timed
		TripTracker f = new TripTracker();
		f.onStartEmpty(0);
		f.onCraft(204);
		f.onEssenceEmpty(480_000);
		// still at the altar: the trip timer keeps running
		assertEquals(510_000, f.getCurrentMillis(510_000));
		assertEquals(204, f.getCurrentRunes());
		f.tick(540_000);                       // stayed 60 s: ends at the last craft
		assertEquals(1, f.getTrips());
		assertEquals(480_000, f.getLastTripMillis());
		assertEquals(90_000, f.getCurrentMillis(570_000));
		assertEquals(0, f.getCurrentRunes());

		// pouches, then leaving the altar (teleport): the trip ends when you leave
		TripTracker l = new TripTracker();
		l.onStartEmpty(0);
		l.onCraft(100);
		l.onEssenceEmpty(400_000);             // inventory crafted
		l.onCraft(30);
		l.onEssenceEmpty(410_000);             // pouch crafted
		assertEquals(415_000, l.getCurrentMillis(415_000));
		l.leaveAltar(420_000);                 // teleported out
		assertEquals(1, l.getTrips());
		assertEquals(420_000, l.getLastTripMillis());
		assertEquals(130, l.getAvgRunes());
		assertEquals(5_000, l.getCurrentMillis(425_000));   // new trip from leaving
		assertEquals(-1, new TripTracker().getCurrentMillis(570_000));
	}

	@Test
	public void tripContinuesAfterRestart()
	{
		TripTracker t = new TripTracker();
		t.onStartEmpty(0);
		t.onCraft(100);                               // fragments crafted at 3 min
		TripTracker.Saved saved = t.save(180_000);    // client closed at 3 min into the trip

		// opened again 1 hour later: the trip continues at 3 min
		TripTracker c = new TripTracker();
		c.load(saved, 3_780_000);
		assertEquals(180_000, c.getCurrentMillis(3_780_000));
		assertEquals(100, c.getCurrentRunes());
		c.onCraft(104);
		c.onEssenceEmpty(3_780_000 + 120_000);        // 2 more minutes
		c.tick(3_780_000 + 180_000);
		assertEquals(1, c.getTrips());
		assertEquals(300_000, c.getLastTripMillis()); // 3 + 2 min, the hour closed is not counted
		assertEquals(204, c.getAvgRunes());

		// a trip with runes still open, login with empty inventory: that trip ends
		TripTracker d = new TripTracker();
		d.onStartEmpty(0);
		d.onCraft(150);
		TripTracker e = new TripTracker();
		e.load(d.save(200_000), 9_000_000);
		e.onStartEmpty(9_000_000);
		e.tick(9_060_000);
		assertEquals(1, e.getTrips());

		// closed right after the last craft (trip waiting for pouches): counted after the restart
		TripTracker g = new TripTracker();
		g.onStartEmpty(0);
		g.onCraft(200);
		g.onEssenceEmpty(400_000);
		TripTracker h = new TripTracker();
		h.load(g.save(410_000), 5_000_000);
		h.tick(5_000_600);
		assertEquals(1, h.getTrips());
		assertEquals(400_000, h.getLastTripMillis());
		assertEquals(10_600, h.getCurrentMillis(5_000_600));   // new trip: 10 s + 0.6 s
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

	@Test
	public void pauseStopsSessionAndTripClock()
	{
		RuneSession s = new RuneSession();
		s.addRunes(RuneIds.BLOOD_RUNE, 100, 0);
		s.addRunes(RuneIds.BLOOD_RUNE, 100, 60_000);
		s.pause(120_000);
		// 10 minutes paused: time stays at 2 minutes
		assertEquals(120_000, s.elapsedMillis(720_000, 5 * 60_000L));
		s.addRunes(RuneIds.BLOOD_RUNE, 100, 720_000);
		assertEquals(150_000, s.elapsedMillis(750_000, 5 * 60_000L));

		TripTracker t = new TripTracker();
		t.onStartEmpty(0);
		t.pause(30_000);
		assertEquals(30_000, t.getCurrentMillis(500_000));
		t.resume(500_000);
		assertEquals(40_000, t.getCurrentMillis(510_000));
		t.onCraft(200);
		t.onEssenceEmpty(600_000);
		t.tick(660_000);
		assertEquals(130_000, t.getLastTripMillis());
	}

	@Test
	public void goalRuneNames()
	{
		assertEquals("blood", GoalRune.BLOOD.shortName());
		assertEquals("runes", GoalRune.ANY.shortName());
		assertEquals(555, GoalRune.WATER.getItemId());
		// every goal rune is a rune the plugin counts
		for (GoalRune g : GoalRune.values())
		{
			if (g != GoalRune.ANY)
			{
				assertTrue(g.name(), RuneIds.isRune(g.getItemId()));
			}
		}
	}

	@Test
	public void newerRunesAndEssence()
	{
		assertTrue(RuneIds.isRune(28929));   // sunfire
		assertTrue(RuneIds.isRune(30843));   // aether
		assertTrue(RuneIds.isTripEssence(RuneIds.GUARDIAN_ESSENCE));
		assertEquals("Aether", RuneIds.RUNES.get(30843));
	}

	@Test
	public void historyKeepsRuneTypes()
	{
		DayLog h = new DayLog();
		h.addRunes("2026-11-01", RuneIds.BLOOD_RUNE, 300);
		h.addRunes("2026-11-01", 555, 100);
		h.addRunes("2026-11-02", 555, 50);
		assertEquals(150, (int) h.total().getTypes().get(555));
		assertEquals(300, (int) h.total().getTypes().get(RuneIds.BLOOD_RUNE));

		// older save without types: blood is known, the rest becomes "other"
		DayLog.Saved old = new DayLog.Saved();
		DayLog.Day d = new DayLog.Day();
		d.runes = 500;
		d.bloodRunes = 400;
		d.types = null;
		old.days.put("2026-10-01", d);
		DayLog h2 = new DayLog();
		h2.load(old);
		assertEquals(400, (int) h2.total().getTypes().get(RuneIds.BLOOD_RUNE));
		assertEquals(100, (int) h2.total().getTypes().get(DayLog.OTHER));
	}

	@Test
	public void historyTypesFilledForOldAndHalfDays()
	{
		// saved by the old version: types empty (not null)
		DayLog.Day old = new DayLog.Day();
		old.runes = 8309;
		old.bloodRunes = 8309;
		DayLog.fillMissingTypes(old);
		assertEquals(8309, (int) old.types.get(RuneIds.BLOOD_RUNE));

		// update in the middle of a day: 5,779 blood before, 190 after
		DayLog.Day half = new DayLog.Day();
		half.runes = 5969 + 100;
		half.bloodRunes = 5969;
		half.types.put(RuneIds.BLOOD_RUNE, 190);
		DayLog.fillMissingTypes(half);
		assertEquals(5969, (int) half.types.get(RuneIds.BLOOD_RUNE));
		assertEquals(100, (int) half.types.get(DayLog.OTHER));
	}

	@Test
	public void levelGoalBarStartsAtYourLevel()
	{
		// level 82 -> 85 with 800k xp left: about 4% along, not 75% (that would be from level 1)
		int start = Goals.xpForLevel(82);
		int goal = Goals.xpForLevel(85);
		double p = Goals.progress(goal - 800_000, start, goal);
		assertTrue(p > 0.03 && p < 0.06);
		assertEquals(1.0, Goals.progress(goal + 5, start, goal), 0.0);
		assertEquals(0.0, Goals.progress(start, start, goal), 0.0);
	}

	@Test
	public void tripsPerPlace()
	{
		assertEquals("Blood - Zeah", TripLocations.placeOf(RuneIds.BLOOD_RUNE, 1, true, false, 6715));
		assertEquals("Blood - Mory", TripLocations.placeOf(RuneIds.BLOOD_RUNE, 1, false, false, 12875));
		assertEquals("Lava - Fire altar", TripLocations.placeOf(4699, 1, false, false, 10315));
		assertEquals("Lava - Earth altar", TripLocations.placeOf(4699, 1, false, false, 10571));
		assertEquals("Nature", TripLocations.placeOf(561, 1, false, false, 1));
		assertEquals("Fire - GOTR", TripLocations.placeOf(554, 1, false, true, 14484));
		assertEquals("ZMI (Ourania)", TripLocations.placeOf(554, 4, false, false, 1));

		TripLocations p = new TripLocations();
		p.onCraft("Blood - Mory");
		p.onTripDone(250, 120_000);
		p.onCraft("Blood - Zeah");
		p.onTripDone(300, 420_000);
		p.onCraft("Blood - Mory");
		p.onTripDone(260, 100_000);
		assertEquals(2, p.get("Blood - Mory").getTrips());
		assertEquals(100_000, p.get("Blood - Mory").getFastestMillis());
		assertEquals(420_000, p.get("Blood - Zeah").getFastestMillis());
		assertEquals("Blood - Mory", p.getLast());
		// a trip without a craft place is not added anywhere
		p.onTripDone(10, 5_000);
		assertEquals(2, p.get("Blood - Mory").getTrips());
	}

	@Test
	public void pouchWear()
	{
		PouchTracker.Pouch c = PouchTracker.of(26784);
		assertEquals(PouchTracker.Pouch.COLOSSAL, c);
		assertEquals(c, PouchTracker.of(26786));
		assertTrue(PouchTracker.isDegraded(26786));
		assertEquals(null, PouchTracker.of(5509));   // small pouch never decays

		// colossal at level 82 holds 27: just repaired = 12 (320 / 27)
		assertEquals(12, PouchTracker.fullFills(c, 82));
		assertEquals(12, PouchTracker.fillsLeft(c, 0, 82));
		assertEquals(11, PouchTracker.fillsLeft(c, 27, 82));
		// 81 wear left before 320 = 3 fills: repair warning
		assertEquals(3, PouchTracker.fillsLeft(c, 320 - 81, 82));
		assertTrue(PouchTracker.needsRepair(3, false));
		assertFalse(PouchTracker.needsRepair(4, false));
		assertTrue(PouchTracker.needsRepair(9, true));     // decayed item
		// level 85: holds 40, 8 fills
		assertEquals(8, PouchTracker.fullFills(c, 85));
		// the others use the game's own breakpoints too
		assertTrue(PouchTracker.fullFills(PouchTracker.Pouch.GIANT, 99) > 0);
	}

	@Test
	public void teleportItemsMatchByName()
	{
		assertTrue(TeleportItem.SAILORS_AMULET.matches("Sailors' amulet"));
		assertTrue(TeleportItem.QUEST_CAPE.matches("Quest point cape (t)"));
		assertTrue(TeleportItem.RING_OF_DUELING.matches("Ring of dueling(8)"));
		assertFalse(TeleportItem.QUEST_CAPE.matches("Quest point hood"));
		assertFalse(TeleportItem.NONE.matches("Sailors' amulet"));
	}

	@Test
	public void moryRouteStepsMatchTheExactSpot()
	{
		AltarRoute r = AltarRoute.of(12875);
		assertEquals("Blood - Mory", r.name);
		assertEquals(5, r.steps.size());
		// step 2: cave entrance 5046 at (3467, 9820)
		assertEquals(1, r.findSpot(3468, 9819, 0));
		// the ruins: found by the spot, also when you click the other ruins object (43477)
		assertEquals(4, r.findSpot(3560, 9780, 0));
		// the cave on the other side (far away) or another floor is not a step
		assertEquals(-1, r.findSpot(3480, 9820, 0));
		assertEquals(-1, r.findSpot(3467, 9820, 1));
		// the way back through step 4 is never highlighted
		assertTrue(r.hidden.contains(43763));
		assertEquals(Integer.valueOf(3), r.hiddenAt.get(43763));   // only while step 4 is next
		// an altar without a route
		assertEquals(0, AltarRoute.of(1).steps.size());
	}

	@Test
	public void stepPassedWhenYouCrawlThroughIt()
	{
		AltarRoute r = AltarRoute.of(12875);
		java.util.Set<Integer> passed = new java.util.HashSet<>();
		passed.add(0);
		// step 2 (3467, 9820), step 3 (3500, 9803): in front of step 2, then through it, out east
		assertEquals(-1, r.passedNow(passed, 3462, 9820, 0, 3464, 9820, 0, 2));
		assertEquals(-1, r.passedNow(passed, 3467, 9820, 0, 3469, 9820, 0, 2));
		assertEquals(1, r.passedNow(passed, 3469, 9820, 0, 3473, 9819, 0, 2));
	}

	@Test
	public void stepNotPassedWhenYouWalkBack()
	{
		AltarRoute r = AltarRoute.of(12875);
		java.util.Set<Integer> passed = new java.util.HashSet<>();
		passed.add(0);
		assertEquals(-1, r.passedNow(passed, 3462, 9820, 0, 3464, 9820, 0, 2));
		assertEquals(-1, r.passedNow(passed, 3464, 9820, 0, 3455, 9821, 0, 2));
	}

	@Test
	public void stepPassedWhenItTeleportsYou()
	{
		AltarRoute r = AltarRoute.of(12875);
		java.util.Set<Integer> passed = new java.util.HashSet<>(java.util.Arrays.asList(0, 1, 2, 3));
		// next to the ruins (3560, 9780), then far away inside the altar
		assertEquals(4, r.passedNow(passed, 3561, 9781, 0, 2460, 4890, 0, 99));
	}

	@Test
	public void afterLoginBetweenStepsTheEarlierOnesAreDone()
	{
		AltarRoute r = AltarRoute.of(12875);
		java.util.Set<Integer> passed = new java.util.HashSet<>();
		// logged in next to step 2 (nothing remembered): step 1 is done
		assertEquals(0, r.passedNow(passed, 3466, 9821, 0, 3466, 9821, 0, 0));
		// logged in between step 3 and step 4: steps 1-3 are done
		passed.clear();
		assertEquals(2, r.passedNow(passed, 3525, 9785, 0, 3525, 9785, 0, 0));
		// nothing new to report the next tick
		passed.addAll(java.util.Arrays.asList(0, 1, 2));
		assertEquals(-1, r.passedNow(passed, 3525, 9785, 0, 3525, 9785, 0, 0));
	}

	@Test
	public void sameStepIsOneSpot()
	{
		assertTrue(AltarRoute.same(new int[]{7, 100, 100, 0}, new int[]{7, 102, 98, 0}));
		assertFalse(AltarRoute.same(new int[]{7, 100, 100, 0}, new int[]{8, 100, 100, 0}));
		assertFalse(AltarRoute.same(new int[]{7, 100, 100, 0}, new int[]{7, 103, 100, 0}));
	}
}
