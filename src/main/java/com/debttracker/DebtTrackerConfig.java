package com.debttracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(DebtTrackerConfig.GROUP)
public interface DebtTrackerConfig extends Config
{
	String GROUP = "debttracker";
	String START_OVER = "startOver";

	@ConfigItem(
		keyName = "debtItem",
		name = "Debt item",
		description = "What you are paying off, e.g. Berserker ring. Changing this starts a new debt from zero.",
		position = 1
	)
	default String debtItem()
	{
		return "";
	}

	@ConfigItem(
		keyName = "debtAmount",
		name = "Debt amount",
		description = "How much you owe, e.g. 2.8m or 500k. Leave blank to use the item's Grand Exchange price.",
		position = 2
	)
	default String debtAmount()
	{
		return "";
	}

	@ConfigItem(
		keyName = START_OVER,
		name = "Start over",
		description = "Tick to reset progress on the current debt to zero.",
		position = 3
	)
	default boolean startOver()
	{
		return false;
	}
}
