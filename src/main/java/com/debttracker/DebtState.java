package com.debttracker;

/**
 * Per-account progress, saved to the RuneLite profile as JSON.
 */
class DebtState
{
	String debtItem = "";
	long marketPrice;
	boolean paidNotified;
	WealthLedger ledger = new WealthLedger();

	void startDebt(String item)
	{
		debtItem = item;
		marketPrice = 0;
		paidNotified = false;
		ledger.rebaseline();
	}
}
