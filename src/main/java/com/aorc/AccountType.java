package com.aorc;

/** Account type from varbit 1777 (RuneLite Varbits.ACCOUNT_TYPE). */
public enum AccountType
{
	MAIN,
	IRONMAN,
	ULTIMATE,
	HARDCORE,
	GROUP,
	HARDCORE_GROUP,
	UNRANKED_GROUP;

	public static final int VARBIT = 1777;

	public static AccountType fromVarbit(int value)
	{
		switch (value)
		{
			case 1:
				return IRONMAN;
			case 2:
				return ULTIMATE;
			case 3:
				return HARDCORE;
			case 4:
				return GROUP;
			case 5:
				return HARDCORE_GROUP;
			case 6:
				return UNRANKED_GROUP;
			default:
				return MAIN;
		}
	}

	public boolean isGroup()
	{
		return this == GROUP || this == HARDCORE_GROUP || this == UNRANKED_GROUP;
	}

	public boolean isHardcore()
	{
		return this == HARDCORE || this == HARDCORE_GROUP;
	}
}
