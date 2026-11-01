# AoRC – Amount of Runes Crafted

A RuneLite plugin that counts every rune you craft and shows it per hour:
all runes, combination runes, ZMI, Guardians of the Rift, sunfire and aether runes.

## Overlay

- Runes crafted and runes per hour (blood runes only show when you craft blood)
- Runecraft XP per hour (includes the 2.5 XP per block at the Dark Altar)
- Blocks imbued (only at Zeah), crafts, GP per hour, profit per hour
- Bonus multiplier, bonus runes, blood essence charges, time to your level goal
- Right-click: reset per hour, rune count, bonus runes or all

## Side panel

- **Top card**: status (crafting / idle / paused), runes per hour of the rune you craft now, XP / GP / bonus
- **Pause / Resume** (resumes by itself when you gain Runecraft XP)
- **Session**: profit, all runes per hour, crafts, bonus runes, blood essence, essence pouch wear
- **Trips** per place (Blood - Zeah, Blood - Mory, Lava - Fire altar, ...): current trip, trips, average, fastest, last.
  A trip ends when you leave the altar (or 60 s after your last craft), so pouch crafts belong to the same trip.
- **Goals** with progress bars: Runecraft level and a rune goal (any rune type)
- **History**: today, best day, all time, per rune type (dropdown and table)
- **Runes crafted**: every rune type with count and per hour
- **Reset ▾** menu and **Clear goal** (resets never touch goals or history)
- **Runey**, the hype buddy (headband in your account type's colour)

Sections can be folded; the plugin remembers which.

## Warnings

- Blisterwood lantern not lit, blood essence not active, Raiments piece missing (checked at the Dark Altar)
- Blood essence almost empty
- Essence pouch about to decay (25% left): the pouch and Astral Contact are highlighted,
  and while you talk to the Dark Mage the text to click is coloured (colour in the settings)

## How counting works

Runes count when they land in your inventory on a tick where you also got Runecraft XP,
so all bonuses are already included and bank withdrawals never count.
The elite Kourend diary bonus only counts at the Arceuus blood altar (dark essence fragments used).

| Bonus (OSRS Wiki) | | Runes |
|---|---|---|
| Raiments of the Eye | 1/2/3 pieces +10/20/30%, full set +60% | all |
| Abyssal lantern willow / redwood / magic | +5% / +5% / +10% | all |
| Abyssal lantern blisterwood | +20% | blood |
| Blood essence (active) | +50% | blood |
| Elite Kourend & Kebos diary | +10% | blood (Arceuus) |

Data is saved per account in `.runelite/aorc/<account>.json`.

## Build and run

Needs Java 11 or newer. Open the folder in IntelliJ IDEA and run the **AoRC** run configuration
(or `src/test/java/com/aorc/AorcPluginTest.java`). RuneLite starts with the plugin loaded.
