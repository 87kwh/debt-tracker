package com.debttracker;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Tracks item quantities per storage location against a baseline. Profit is the change in total
 * quantity of each item, valued at its current price. Moving an item between locations nets to
 * zero, and items that were held all along are immune to price swings.
 */
class WealthLedger
{
	static final String INVENTORY = "inventory";
	static final String EQUIPMENT = "equipment";
	static final String BANK = "bank";
	static final String LOOTING_BAG = "lootingBag";
	static final String GRAND_EXCHANGE = "grandExchange";

	private Map<String, Map<Integer, Long>> holdings = new HashMap<>();
	private Map<String, Map<Integer, Long>> baseline = new HashMap<>();

	/**
	 * Records the contents of a location. The first time a location is seen its contents become
	 * part of the baseline, since they were not earned, they just weren't visible until now.
	 *
	 * @return true if the contents differ from what was last recorded
	 */
	boolean observe(String location, Map<Integer, Long> items)
	{
		Map<Integer, Long> previous = holdings.put(location, new HashMap<>(items));
		if (previous == null)
		{
			baseline.put(location, new HashMap<>(items));
			return true;
		}
		return !previous.equals(items);
	}

	boolean hasObserved(String location)
	{
		return holdings.containsKey(location);
	}

	/**
	 * Makes everything currently held the new starting point, so profit returns to zero.
	 */
	void rebaseline()
	{
		baseline = new HashMap<>();
		holdings.forEach((location, items) -> baseline.put(location, new HashMap<>(items)));
	}

	long profit(ToLongFunction<Integer> price)
	{
		Map<Integer, Long> change = new HashMap<>();
		holdings.values().forEach(items -> items.forEach((id, qty) -> change.merge(id, qty, Long::sum)));
		baseline.values().forEach(items -> items.forEach((id, qty) -> change.merge(id, -qty, Long::sum)));

		long profit = 0;
		for (Map.Entry<Integer, Long> entry : change.entrySet())
		{
			if (entry.getValue() != 0)
			{
				profit += entry.getValue() * price.applyAsLong(entry.getKey());
			}
		}
		return profit;
	}

	/**
	 * Adds what a Grand Exchange offer is holding on the player's behalf: unsold items and coins
	 * received for a sell offer, unspent coins and bought items for a buy offer.
	 */
	static void addOffer(Map<Integer, Long> items, int coinsId, int itemId, boolean buying, int total, int sold, long price, long spent)
	{
		long itemsHeld = buying ? sold : total - sold;
		long coinsHeld = buying ? price * total - spent : spent;
		items.merge(itemId, itemsHeld, Long::sum);
		items.merge(coinsId, coinsHeld, Long::sum);
	}
}
