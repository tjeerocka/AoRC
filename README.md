# AoRC – Amount of Runes Crafted

A RuneLite plugin that counts the runes you craft and shows them per hour.
Blood runes are shown first. Other rune types can be shown too.

## What it shows

- Blood runes crafted and blood runes per hour
- Other rune types per hour (option), and all runes per hour
- Runecraft XP per hour (includes the 2.5 XP per block at the Dark Altar)
- Dark essence blocks imbued
- Number of crafts (altar clicks that made runes)
- GP per hour (GE price)
- Active bonuses and an estimate of how many runes came from bonuses

## Side panel (sidebar button)

- All rune types with icon, count and per hour (ZMI: every rune type shows up here)
- RC xp/hr, GP/hr, **Profit/hr** (GP minus blood essence used), bonus multiplier
- **Blood essence charges** left (exact after "Check", ~ = estimate)
- **Trips**: trips, average runes per trip, average and fastest trip time
- **Goals**: time to your Runecraft level goal and blood rune goal
- **History**: today, best day and all time (runes + time spent)

## Warnings (checked when you imbue at the Dark Altar)

- Blisterwood lantern not lit / equipped
- Blood essence in inventory but not activated
- Raiments of the Eye piece missing
- Blood essence almost empty (about 100 charges or less)

The elite Kourend diary bonus only counts at the Arceuus blood altar
(detected because dark essence fragments are used up).

## How counting works

The plugin counts the **real runes** that land in your inventory, but only on a tick
where you also got Runecraft XP. So all bonuses (Raiments, lantern, blood essence,
diary) are already inside the number. Bank withdrawals do not count (no XP).

## Bonuses (from the OSRS Wiki)

| Source | Bonus | Runes |
|---|---|---|
| Raiments of the Eye | 1/2/3 pieces +10/20/30%, full set +60% | all |
| Abyssal lantern (willow / redwood) | +5% | all |
| Abyssal lantern (magic) | +10% | all |
| Abyssal lantern (blisterwood) | +20% | blood |
| Blood essence (active, in inventory) | +50% (50% chance of +1 per essence) | blood |
| Elite Kourend & Kebos diary | +10% | blood (Arceuus altar) |

All bonuses add together and use the base runes only.
Gear is detected automatically. The elite diary is a checkbox in the plugin settings.

Overlay: right-click → **Reset** to start a new session.

## Build and run

Needs Java 11 or newer (JDK 17 recommended).

1. Open this folder in IntelliJ IDEA (File → Open → pick the folder). It loads Gradle by itself.
2. Run `src/test/java/com/aorc/AorcPluginTest.java` (the `main` method).
   RuneLite starts with the plugin already loaded.
3. Turn on **AoRC** in the plugin list if it is not on.

Or in a terminal: `gradlew build` (Windows) / `./gradlew build`.

Note: RuneLite will only start in developer mode this way. To use it in the normal
client for everyone, the plugin must be sent to the Plugin Hub (the repo must be public then).
