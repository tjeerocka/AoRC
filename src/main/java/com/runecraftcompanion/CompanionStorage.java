package com.runecraftcompanion;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Saves Runecraft Companion data in its own file per account: .runelite/runecraft-companion/&lt;account&gt;.json
 * Written straight to disk (no RuneLite config sync), so nothing gets lost or overwritten.
 */
public class CompanionStorage
{
	/** Everything we save for one account. */
	public static class Data
	{
		RuneSession.Saved session;
		RuneTotals.Saved totals;
		DayLog.Saved history;
		TripTracker.Saved trips;
		EssenceCharges.Saved essence;
		// runes counted for the rune goal, per rune type (own counter, not cleared by the resets)
		java.util.Map<Integer, Integer> goalRunes;
		TripLocations.Saved tripPlaces;
		Integer craftRegion;   // map area of your last craft (the altar)
		// essence used in your last craft
		Integer lastEssence;
		// level goal bar: the goal level and the XP the bar starts from
		Integer goalStartLevel;
		Integer goalStartXp;
	}

	private final File dir;

	/** The data folder in .runelite. */
	static final String FOLDER = "runecraft-companion";
	/** The folder of the plugin's first name: moved to FOLDER once. */
	private static final String OLD_FOLDER = "aorc";

	public CompanionStorage(File baseDir)
	{
		this.dir = new File(baseDir, FOLDER);
		File old = new File(baseDir, OLD_FOLDER);
		if (old.isDirectory() && !dir.exists())
		{
			// the plugin was renamed: keep all saved data
			if (!old.renameTo(dir))
			{
				dir.mkdirs();
			}
		}
	}

	File file(long accountHash)
	{
		return new File(dir, accountHash + ".json");
	}

	/** null when there is no file yet. */
	public String read(long accountHash) throws IOException
	{
		File f = file(accountHash);
		if (!f.exists())
		{
			return null;
		}
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
	}

	/** Write to a temp file first, then replace, so a crash never leaves a broken file. */
	public void write(long accountHash, String json) throws IOException
	{
		if (!dir.exists() && !dir.mkdirs())
		{
			throw new IOException("Cannot create " + dir);
		}
		File target = file(accountHash);
		File tmp = new File(dir, accountHash + ".json.tmp");
		Files.write(tmp.toPath(), json.getBytes(StandardCharsets.UTF_8));
		try
		{
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
