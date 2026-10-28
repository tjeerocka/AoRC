package com.aorc;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Saves AoRC data in its own file per account: .runelite/aorc/&lt;account&gt;.json
 * Written straight to disk (no RuneLite config sync), so nothing gets lost or overwritten.
 */
public class AorcStorage
{
	/** Everything we save for one account. */
	public static class Data
	{
		RuneSession.Saved session;
		RuneTotals.Saved totals;
		DayLog.Saved history;
		TripTracker.Saved trips;
		EssenceCharges.Saved essence;
	}

	private final File dir;

	public AorcStorage(File baseDir)
	{
		this.dir = new File(baseDir, "aorc");
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
