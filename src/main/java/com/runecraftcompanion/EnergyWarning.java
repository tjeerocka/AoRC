package com.runecraftcompanion;

/** How bad your run energy is: 30% or less low, 20% or less very low, 10% or less critical. */
final class EnergyWarning
{
	static final int NONE = 0;
	static final int LOW = 1;
	static final int VERY_LOW = 2;
	static final int CRITICAL = 3;

	private EnergyWarning()
	{
	}

	/** Warning level for run energy in percent (0-100). */
	static int level(int percent)
	{
		return percent <= 10 ? CRITICAL : percent <= 20 ? VERY_LOW : percent <= 30 ? LOW : NONE;
	}
}
