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

- **Top card**: status (runecrafting / idle / paused), runes per hour of the rune you craft now, XP / GP / bonus
- **Pause / Resume** (resumes by itself when you gain Runecraft XP)
- **Session**: profit, all runes per hour, crafts, bonus runes, blood essence, essence pouch wear
- **Trips** per place (Blood - Zeah, Blood - Mory, Lava - Fire altar, ...): current trip, trips, average, fastest, last.
  A trip ends when you leave the altar (or 60 s after your last craft), so pouch crafts belong to the same trip.
  "All trips" adds up every place. A trip under 20 s is counted but not timed.
- **Goals** with progress bars: Runecraft level and a rune goal (any rune type)
- **History**: today, best day, all time, per rune type (dropdown and table)
- **Runes crafted**: every rune type with count and per hour
- **Reset ▾** menu and **Clear goal** (resets never touch goals or history)
- **Runey**, the hype buddy (headband in your account type's colour)

Sections can be folded; the plugin remembers which.

## Warnings

- Blisterwood lantern not lit, blood essence not active, Raiments piece missing (checked at the Dark Altar)
- Blood essence almost empty
- Essence pouch at durability 3 (read from the game): the pouch and NPC Contact are highlighted,
  and while you talk to the Dark Mage the text to click is coloured (colour in the settings)

## Highlights (setting group "Teleport highlights")

All helpers below (highlights, the line and arrow, the menu changes) only start once you gain
Runecraft XP, and stop again after 10 minutes without any. Log in wearing full Raiments of the Eye and
a lantern with an essence pouch in your inventory, and they start right away (off again after 5 minutes
without Runecraft XP). The overlay box only shows after Runecraft XP; counting and the panel always run.

- **Teleports**: the bank teleport (when you are done at the altar) and the altar teleport (when you are
  full) light up in your inventory / equipment. A teleport counts as used when you land where it takes you,
  so a misclicked other teleport keeps the highlight.
- **Routes**, built in, the next thing to click lights up (outline, the game's yellow arrow and a line
  along recorded paths):
  - **Blood - Mory**: fairy ring, the caves (the 93 or 74 Agility shortcuts, or the long way) and the ruins.
    The cave back the wrong way loses its Enter option while its step is next.
  - **Blood - Zeah**: runestone, the 69 / 52 shortcut, dark altar, chisel + dark block, blood altar
    (bind, chisel all, bind again), the 73 climb back. Boosted Agility counts for the shortcuts.
- **Bank**: the bank chests / bankers and the essence you used last, until inventory and pouches are full.
  In the bank the pouches have no Empty; Fill is the left-click. A full pouch, or any pouch while you
  have no essence in your inventory, only has Examine (no Fill, no Deposit).
- **Altar**: the pouch to empty next; near an altar the pouches have no Fill / Check and Empty is the
  left-click (not at ZMI / Guardians of the Rift).

## Developer (folded settings group)

- **Route debug**: a box with your position, the route or Zeah state and what happened last, labels above
  the route objects and the object ID + spot in the right-click menu.
- **Record path**: record a walk (pick what you record in "Recording"); the file goes to
  `.runelite/aorc/paths`, its corners can be built in so the line follows the real way.

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
