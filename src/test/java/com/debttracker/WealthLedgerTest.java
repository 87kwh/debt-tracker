package com.debttracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.util.QuantityFormatter;
import org.junit.Test;

public class WealthLedgerTest
{
	private static final int COINS = 995;
	private static final int RING = 6737;
	private static final int LOBSTER = 379;
	private static final int BONES = 526;

	private long ringPrice = 2_800_000;

	private long price(int id)
	{
		switch (id)
		{
			case COINS:
				return 1;
			case RING:
				return ringPrice;
			case LOBSTER:
				return 150;
			default:
				return 100;
		}
	}

	private static Map<Integer, Long> items(long... idThenQuantity)
	{
		Map<Integer, Long> items = new HashMap<>();
		for (int i = 0; i < idThenQuantity.length; i += 2)
		{
			items.put((int) idThenQuantity[i], idThenQuantity[i + 1]);
		}
		return items;
	}

	private WealthLedger startedLedger()
	{
		WealthLedger ledger = new WealthLedger();
		ledger.observe(WealthLedger.INVENTORY, items(COINS, 1_000, LOBSTER, 10));
		ledger.observe(WealthLedger.EQUIPMENT, items(RING, 1));
		ledger.observe(WealthLedger.BANK, items(COINS, 5_000_000));
		ledger.observe(WealthLedger.GRAND_EXCHANGE, items());
		ledger.rebaseline();
		return ledger;
	}

	@Test
	public void nothingChangedIsZeroProfit()
	{
		assertEquals(0, startedLedger().profit(this::price));
	}

	@Test
	public void lootCountsAsProfitAndConsumablesAsCost()
	{
		WealthLedger ledger = startedLedger();
		ledger.observe(WealthLedger.INVENTORY, items(COINS, 11_000, LOBSTER, 8));
		assertEquals(10_000 - 300, ledger.profit(this::price));
	}

	@Test
	public void bankingIsNeutral()
	{
		WealthLedger ledger = startedLedger();
		ledger.observe(WealthLedger.INVENTORY, items(LOBSTER, 10));
		ledger.observe(WealthLedger.BANK, items(COINS, 5_001_000));
		assertEquals(0, ledger.profit(this::price));
	}

	@Test
	public void priceSwingOnHeldItemIsNotProfit()
	{
		WealthLedger ledger = startedLedger();
		ringPrice = 3_500_000;
		assertEquals(0, ledger.profit(this::price));
	}

	@Test
	public void firstSightOfBankIsNotProfit()
	{
		WealthLedger ledger = new WealthLedger();
		ledger.observe(WealthLedger.INVENTORY, items(COINS, 1_000));
		ledger.rebaseline();
		assertFalse(ledger.hasObserved(WealthLedger.BANK));

		ledger.observe(WealthLedger.BANK, items(COINS, 50_000_000));
		assertTrue(ledger.hasObserved(WealthLedger.BANK));
		assertEquals(0, ledger.profit(this::price));

		ledger.observe(WealthLedger.BANK, items(COINS, 50_000_500));
		assertEquals(500, ledger.profit(this::price));
	}

	@Test
	public void depositBoxLossCorrectsWhenBankIsNextOpened()
	{
		WealthLedger ledger = startedLedger();
		ledger.observe(WealthLedger.INVENTORY, items(LOBSTER, 10));
		assertEquals(-1_000, ledger.profit(this::price));
		ledger.observe(WealthLedger.BANK, items(COINS, 5_001_000));
		assertEquals(0, ledger.profit(this::price));
	}

	@Test
	public void sellOfferKeepsValueThroughItsLifecycle()
	{
		WealthLedger ledger = startedLedger();
		ledger.observe(WealthLedger.INVENTORY, items(COINS, 1_000, LOBSTER, 10, BONES, 100));
		long start = ledger.profit(this::price);

		// Listed: items move out of the inventory into the offer
		Map<Integer, Long> offer = new HashMap<>();
		WealthLedger.addOffer(offer, COINS, BONES, false, 100, 0, 100, 0);
		ledger.observe(WealthLedger.INVENTORY, items(COINS, 1_000, LOBSTER, 10));
		ledger.observe(WealthLedger.GRAND_EXCHANGE, offer);
		assertEquals(start, ledger.profit(this::price));

		// Half sold, uncollected
		offer = new HashMap<>();
		WealthLedger.addOffer(offer, COINS, BONES, false, 100, 50, 100, 5_000);
		ledger.observe(WealthLedger.GRAND_EXCHANGE, offer);
		assertEquals(start, ledger.profit(this::price));

		// Fully sold and collected after 2% tax: the tax lands as a real cost
		ledger.observe(WealthLedger.GRAND_EXCHANGE, new HashMap<>());
		ledger.observe(WealthLedger.INVENTORY, items(COINS, 1_000 + 9_800, LOBSTER, 10));
		assertEquals(start - 200, ledger.profit(this::price));
	}

	@Test
	public void buyOfferKeepsValueThroughItsLifecycle()
	{
		WealthLedger ledger = startedLedger();

		Map<Integer, Long> offer = new HashMap<>();
		WealthLedger.addOffer(offer, COINS, BONES, true, 10, 0, 100, 0);
		ledger.observe(WealthLedger.INVENTORY, items(LOBSTER, 10));
		ledger.observe(WealthLedger.GRAND_EXCHANGE, offer);
		assertEquals(0, ledger.profit(this::price));

		// Filled at the listed price, uncollected
		offer = new HashMap<>();
		WealthLedger.addOffer(offer, COINS, BONES, true, 10, 10, 100, 1_000);
		ledger.observe(WealthLedger.GRAND_EXCHANGE, offer);
		assertEquals(0, ledger.profit(this::price));
	}

	@Test
	public void newDebtStartsFromZero()
	{
		WealthLedger ledger = startedLedger();
		ledger.observe(WealthLedger.INVENTORY, items(COINS, 501_000, LOBSTER, 10));
		assertEquals(500_000, ledger.profit(this::price));

		DebtState state = new DebtState();
		state.ledger = ledger;
		state.paidNotified = true;
		state.startDebt("Berserker ring");
		assertEquals(0, ledger.profit(this::price));
		assertFalse(state.paidNotified);
	}

	@Test
	public void stateSurvivesJsonRoundTrip()
	{
		DebtState state = new DebtState();
		state.debtItem = "Berserker ring";
		state.ledger = startedLedger();
		state.ledger.observe(WealthLedger.INVENTORY, items(COINS, 11_000, LOBSTER, 10));

		Gson gson = new Gson();
		DebtState restored = gson.fromJson(gson.toJson(state), DebtState.class);

		assertEquals("Berserker ring", restored.debtItem);
		assertEquals(10_000, restored.ledger.profit(this::price));
		assertFalse(restored.ledger.observe(WealthLedger.BANK, items(COINS, 5_000_000)));
	}

	@Test
	public void debtAmountFormatsParse() throws ParseException
	{
		assertEquals(2_800_000, QuantityFormatter.parseQuantity("2.8m"));
		assertEquals(2_800_000, QuantityFormatter.parseQuantity("2.8M"));
		assertEquals(2_800_000, QuantityFormatter.parseQuantity("2,800,000"));
		assertEquals(500_000, QuantityFormatter.parseQuantity("500k"));
		assertEquals(1_200_000_000, QuantityFormatter.parseQuantity("1.2b"));
	}

	@Test
	public void gpFormatting()
	{
		assertEquals("950", GpFormat.format(950));
		assertEquals("135.4K", GpFormat.format(135_499));
		assertEquals("2.8M", GpFormat.format(2_800_000));
		assertEquals("2.79M", GpFormat.format(2_799_999));
		assertEquals("2.85M", GpFormat.format(2_850_000));
		assertEquals("3M", GpFormat.format(3_000_000));
		assertEquals("1.2B", GpFormat.format(1_200_000_000));
		assertEquals("-120K", GpFormat.format(-120_000));
		assertEquals("0", GpFormat.format(0));
	}
}
