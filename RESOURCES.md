# RuneLite plugin resources

Reference material gathered for this project, checked on 2026-11-06 against RuneLite 1.13.6.

## Getting started

- [Example plugin template](https://github.com/runelite/example-plugin): the official starting point
  and the base for this project. Its `AGENTS.md`, copied here, is a good summary of the rules.
- [Plugin Hub](https://github.com/runelite/plugin-hub): how to create, submit and update a plugin.
- [Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide): plugins, events,
  overlays, config and panels.
- [Building with IntelliJ IDEA](https://github.com/runelite/runelite/wiki/Building-with-IntelliJ-IDEA):
  IDE setup. Use JDK 11.
- [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts): logging in
  to a development client.
- [Using the client developer tools](https://github.com/runelite/runelite/wiki/Using-the-client-developer-tools):
  inspect widgets, containers and events while the client runs.

## Rules

- [Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features):
  check before adding a feature.
- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [Information about the Plugin Hub](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub)

## API reference

- [RuneLite client Javadoc](https://static.runelite.net/api/runelite-client/)
- [RuneLite API Javadoc](https://static.runelite.net/api/runelite-api/)
- [ItemManager](https://static.runelite.net/api/runelite-client/net/runelite/client/game/ItemManager.html):
  prices, item search and icons.
- [RuneLite source](https://github.com/runelite/runelite): core plugins show correct API use. The
  Grand Exchange plugin is the most relevant here.

The parts this plugin relies on:

| Need | API |
| --- | --- |
| Inventory, worn items, bank, looting bag | `ItemContainerChanged`, `gameval.InventoryID` |
| Grand Exchange slots | `GrandExchangeOfferChanged`, `Client.getGrandExchangeOffers()` |
| Item prices | `ItemManager.getItemPrice`, `canonicalize` for noted items and placeholders |
| Per-account saved data | `ConfigManager.getRSProfileKey`, `RuneScapeProfileChanged` |
| Bar and text | `OverlayPanel`, `ProgressBarComponent`, `LineComponent` |
| Parsing `2.8m` | `QuantityFormatter.parseQuantity` |

## Similar plugins

- [Profit Tracker](https://github.com/nofatigue/runelite-profit-tracker): tracks inventory and
  equipment value tick by tick and skips bank ticks. Its author has stopped working on it.
- [InventoryValue](https://github.com/wikiworm/InventoryValue): the plugin that inspired Profit Tracker.

## Game data

- [OSRS Wiki real-time prices](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices): the
  prices API. RuneLite's `ItemManager` already provides prices, so this plugin does not call it.
- [OSRS Wiki](https://oldschool.runescape.wiki/): item details, including Grand Exchange tax.

## Getting this into the normal launcher

The regular RuneLite launcher only loads plugins from the Plugin Hub. Until this plugin is on the
hub, run it with `./gradlew run`.

To submit it: publish the code in a public GitHub repository, then open a pull request to
`runelite/plugin-hub` adding a file in `plugins/` with `repository=` and `commit=` lines. An optional
`icon.png` of up to 48x72 px can sit in the repository root.
