package com.debttracker;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.text.ParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Debt Tracker",
	description = "Shows a bar that fills up as your profit pays off an item you are in debt for",
	tags = {"profit", "money", "debt", "overlay", "gp"}
)
public class DebtTrackerPlugin extends Plugin
{
	private static final String STATE_KEY = "state";
	private static final int SAVE_INTERVAL_TICKS = 10;
	private static final int PRICE_REFRESH_TICKS = 100;
	private static final int GE_LOGIN_BURST_TICKS = 3;

	private static final Map<Integer, String> TRACKED_CONTAINERS = Map.of(
		InventoryID.INV, WealthLedger.INVENTORY,
		InventoryID.WORN, WealthLedger.EQUIPMENT,
		InventoryID.BANK, WealthLedger.BANK,
		InventoryID.LOOTING_BAG, WealthLedger.LOOTING_BAG
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private DebtTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DebtTrackerOverlay overlay;

	@Inject
	private Gson gson;

	private final Map<String, Map<Integer, Long>> pending = new HashMap<>();
	private boolean offersChanged;
	private DebtState state;
	private String stateProfile;
	private boolean unsaved;
	private int ticks;
	private int ticksSinceLoad;
	private long configuredAmount;
	private int itemId = -1;

	@Getter
	private long profit;

	@Getter
	private boolean amountInvalid;

	@Getter
	private BufferedImage itemIcon;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				TRACKED_CONTAINERS.forEach((id, location) ->
				{
					ItemContainer container = client.getItemContainer(id);
					if (container != null)
					{
						pending.put(location, readItems(container));
					}
				});
				offersChanged = true;
			}
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		clientThread.invoke(() ->
		{
			saveState();
			state = null;
			stateProfile = null;
			pending.clear();
			offersChanged = false;
			profit = 0;
			itemId = -1;
			itemIcon = null;
		});
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		String location = TRACKED_CONTAINERS.get(event.getContainerId());
		if (location != null)
		{
			// Applied on the next tick, so an item moving between two locations is seen in both at once
			pending.put(location, readItems(event.getItemContainer()));
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		// Slots are cleared while logging in, before the real offers arrive
		if (event.getOffer().getState() != GrandExchangeOfferState.EMPTY || client.getGameState() == GameState.LOGGED_IN)
		{
			offersChanged = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			pending.clear();
			offersChanged = false;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		saveState();
		state = null;
		stateProfile = null;
		profit = 0;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (DebtTrackerConfig.GROUP.equals(event.getGroup()) && event.getProfile() == null)
		{
			clientThread.invoke(this::applyConfig);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (state == null && !loadState())
		{
			return;
		}

		ticks++;
		ticksSinceLoad++;
		boolean changed = applyObservations();
		if (ticks % PRICE_REFRESH_TICKS == 0)
		{
			resolveItem();
			changed = true;
		}
		if (changed)
		{
			refreshProfit();
		}
		if (ticks % SAVE_INTERVAL_TICKS == 0)
		{
			saveState();
		}
	}

	boolean hasDebt()
	{
		return state != null && !(config.debtItem().trim().isEmpty() && config.debtAmount().trim().isEmpty());
	}

	String getDebtName()
	{
		String item = config.debtItem().trim();
		return item.isEmpty() ? "Debt" : item;
	}

	long getDebtAmount()
	{
		return configuredAmount > 0 ? configuredAmount : state == null ? 0 : state.marketPrice;
	}

	boolean isBankSynced()
	{
		return state != null && state.ledger.hasObserved(WealthLedger.BANK);
	}

	private boolean loadState()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return false;
		}

		String json = configManager.getConfiguration(DebtTrackerConfig.GROUP, profile, STATE_KEY);
		if (json != null)
		{
			try
			{
				state = gson.fromJson(json, DebtState.class);
			}
			catch (JsonParseException e)
			{
				log.warn("Discarding unreadable debt state", e);
			}
		}
		if (state == null)
		{
			state = new DebtState();
		}
		stateProfile = profile;
		ticksSinceLoad = 0;

		applyObservations();
		applyConfig();
		return true;
	}

	private void saveState()
	{
		if (state != null && unsaved)
		{
			configManager.setConfiguration(DebtTrackerConfig.GROUP, stateProfile, STATE_KEY, gson.toJson(state));
			unsaved = false;
		}
	}

	private void applyConfig()
	{
		String amount = config.debtAmount().trim();
		configuredAmount = 0;
		amountInvalid = false;
		if (!amount.isEmpty())
		{
			try
			{
				configuredAmount = QuantityFormatter.parseQuantity(amount);
				amountInvalid = configuredAmount <= 0;
			}
			catch (ParseException | NumberFormatException e)
			{
				amountInvalid = true;
			}
		}

		// Without a loaded account this runs again once one loads, so a reset is not lost
		if (state == null)
		{
			return;
		}

		itemId = -1;
		itemIcon = null;
		String item = config.debtItem().trim();
		if (config.startOver() || !item.equalsIgnoreCase(state.debtItem))
		{
			state.startDebt(item);
			unsaved = true;
		}
		resolveItem();
		refreshProfit();

		if (config.startOver())
		{
			configManager.setConfiguration(DebtTrackerConfig.GROUP, DebtTrackerConfig.START_OVER, false);
		}
	}

	private void resolveItem()
	{
		String item = config.debtItem().trim();
		if (item.isEmpty())
		{
			return;
		}

		if (itemId == -1)
		{
			// Prices load in the background after the client starts, so this can come up empty at first
			itemManager.search(item).stream()
				.min(Comparator.<ItemPrice, Boolean>comparing(p -> !p.getName().equalsIgnoreCase(item))
					.thenComparingInt(p -> p.getName().length()))
				.ifPresent(match ->
				{
					itemId = match.getId();
					itemIcon = itemManager.getImage(itemId);
				});
		}

		if (itemId != -1 && state.marketPrice == 0)
		{
			state.marketPrice = itemManager.getItemPrice(itemId);
			unsaved = true;
		}
	}

	private boolean applyObservations()
	{
		boolean changed = false;
		for (Map.Entry<String, Map<Integer, Long>> entry : pending.entrySet())
		{
			changed |= state.ledger.observe(entry.getKey(), entry.getValue());
		}
		pending.clear();

		// The slots must be recorded before the first new offer, or that offer's items would look
		// like they were already there. Waiting out the login burst keeps real offers out of the
		// empty first reading, where they would look like profit.
		boolean offersKnown = state.ledger.hasObserved(WealthLedger.GRAND_EXCHANGE);
		boolean burstOver = ticksSinceLoad > GE_LOGIN_BURST_TICKS;
		if (burstOver ? offersChanged || !offersKnown : offersChanged && offersKnown)
		{
			changed |= state.ledger.observe(WealthLedger.GRAND_EXCHANGE, readOffers());
			offersChanged = false;
		}

		unsaved |= changed;
		return changed;
	}

	private void refreshProfit()
	{
		profit = state.ledger.profit(itemManager::getItemPrice);

		long amount = getDebtAmount();
		if (amount > 0 && profit >= amount && !state.paidNotified && client.getGameState() == GameState.LOGGED_IN)
		{
			state.paidNotified = true;
			unsaved = true;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Debt Tracker: you have paid off your " + getDebtName() + "!", null);
		}
	}

	private Map<Integer, Long> readItems(ItemContainer container)
	{
		Map<Integer, Long> items = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0)
			{
				items.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
			}
		}
		return items;
	}

	private Map<Integer, Long> readOffers()
	{
		Map<Integer, Long> items = new HashMap<>();
		for (GrandExchangeOffer offer : client.getGrandExchangeOffers())
		{
			if (offer != null && offer.getState() != GrandExchangeOfferState.EMPTY)
			{
				GrandExchangeOfferState offerState = offer.getState();
				boolean buying = offerState == GrandExchangeOfferState.BUYING
					|| offerState == GrandExchangeOfferState.BOUGHT
					|| offerState == GrandExchangeOfferState.CANCELLED_BUY;
				WealthLedger.addOffer(items, ItemID.COINS, itemManager.canonicalize(offer.getItemId()), buying,
					offer.getTotalQuantity(), offer.getQuantitySold(), offer.getPrice(), offer.getSpent());
			}
		}
		items.values().removeIf(quantity -> quantity == 0);
		return items;
	}

	@Provides
	DebtTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DebtTrackerConfig.class);
	}
}
