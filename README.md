# PatchBoy

A Game Boy, Game Boy Color and Game Boy Advance emulator for everyday Android phones, with
on-screen touch controls.

PatchBoy is a fork of **[Titan Slot](https://github.com/sp3lllz/titan-slot)**, which was built
for the Unihertz Titan 2 Elite and its QWERTY keyboard. PatchBoy keeps the same cartridge
shelf and adds what a normal touchscreen phone needs: a gamepad drawn on screen, portrait and
landscape play, and menus you can tap. Both are ports of
**[slot](https://github.com/BrandonKowalski/slot)** by Brandon Kowalski, a Game Boy-centric
frontend for the Anbernic RG SP. The shelf of carts, the slot they go into, the cart shapes and
colours, the sounds and the controls are all slot's. If you have an RG SP, go use slot.

| Shelf | Core |
| --- | --- |
| Game Boy | Gambatte (or mGBA, swap the chip in the cart sheet) |
| Game Boy Color | Gambatte (or mGBA) |
| Game Boy Advance | mGBA |

The cores are the official [libretro](https://www.libretro.com) builds, run through
[LibretroDroid](https://github.com/Swordfish90/LibretroDroid). They aren't in the base APK: the
setup wizard installs them on first launch.

**Requirements:** Android 11 or newer on a 64-bit ARM (arm64) phone, which covers almost every
phone from the last several years. A Bluetooth or USB gamepad or keyboard is optional.

## Install

1. On your phone, download **`PatchBoy-<version>.apk`** from the
   [latest release](https://github.com/sp3lllz/patchboy/releases/latest).
2. Open it, and let your browser or file manager install apps when Android asks. Or, from a
   computer with USB debugging on:
   ```bash
   adb install PatchBoy-0.1.0.apk
   ```
3. Open PatchBoy. Setup downloads the two emulator cores (a few megabytes), so the first launch
   needs an internet connection.

PatchBoy's package is `dev.patchboy`, so it installs alongside Titan Slot rather than over it.

Release APKs are the `direct` flavour and are signed with PatchBoy's release key, whose SHA-256
certificate fingerprint is:

```
E9:46:31:BD:EB:FC:9A:9D:B3:FB:88:EA:4F:35:D9:88:7B:A6:12:0E:09:CD:76:BD:F0:21:88:AF:CA:4E:E6:A4
```

Android only updates an app with an APK signed by the same key, so a build you made yourself
(debug builds use your own debug key) can't be installed over a release, or the other way
round, without uninstalling first. Each release lists its APK's SHA-256 checksum.

## First launch

A short setup walks through everything, and any step can be run again from Settings:

1. **Where your games live:** the phone's storage or the microSD card. Battery saves and save
   states always stay on the phone, so swapping cards never loses progress. Settings > Game
   Storage moves the whole shelf later.
2. **The emulators:** Gambatte and mGBA download (a few megabytes) with a progress bar.
3. **Your games:** pick a folder (sub-folders are searched) or single files in the system file
   picker. `.gb`, `.gbc` and `.gba` ROMs and zips of them are copied in; a zip holding a whole
   collection is unpacked into one cart per game. Settings > Add Games does this any time.
4. **Your saves:** `.sav` or `.srm` battery saves from another emulator or a flash cart, matched
   to your games by name (so `Pokemon - Emerald Version (U).sav` finds
   `Pokemon - Emerald Version (USA, Europe).gba`). A save that replaces one already there keeps
   the old one as `.srm.bak`, and that game's next start skips its old resume state.
5. **Controls:** what the on-screen gamepad's buttons do. With a keyboard connected, it's the
   keyboard instead, drawn with what every key does.
6. **Done:** optionally fetch the real printed cart labels for everything you imported.

No storage permission is asked for: everything goes through the system file picker.

## Build

Requires JDK 17 and the Android SDK (platform 36). There are two flavours, which differ only in
where the cores come from:

| Flavour | For | Cores |
| --- | --- | --- |
| `play` | Google Play | On-demand feature modules (`:core_gambatte`, `:core_mgba`) that Play delivers during setup. Google Play doesn't allow apps to download native code from anywhere else. |
| `direct` | Sideloading, your own testing | Setup downloads them from the libretro buildbot into the app's private storage. |

```bash
# Sideload build: nothing to fetch first.
./gradlew assembleDirectDebug        # -> app/build/outputs/apk/direct/debug/app-direct-debug.apk
adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk

# Play build: put the cores into their feature modules, then build the bundle.
scripts/fetch-cores.sh               # downloads the two arm64 cores from buildbot.libretro.com
./gradlew bundlePlayRelease          # -> app/build/outputs/bundle/playRelease/app-play-release.aab
```

To try the Play flavour's on-demand install before publishing, upload the bundle to Play
Console's internal app sharing or internal testing track, or use bundletool's
`build-apks --local-testing`.

`fetch-cores.sh` warns when a core isn't aligned for 16 KB memory pages, which Google Play
requires of apps targeting Android 15 and later; such a core needs rebuilding with
`-Wl,-z,max-page-size=16384` before the bundle will be accepted.

### Release builds

Release builds are signed when Gradle is given a properties file holding the key, kept outside
the repository:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew assembleDirectRelease -Ppatchboy.signing=/path/to/signing.properties
# -> app/build/outputs/apk/direct/release/app-direct-release.apk
```

A `keystore.properties` in the project root works too (it is git-ignored). Without either,
release builds come out unsigned. Minification stays off: LibretroDroid calls into Kotlin from
native code and finds its lifecycle observers by reflection.

## Your games

PatchBoy keeps everything in its own folders (`Android/data/dev.patchboy/files`): games and
cart art on the volume you chose, everything else on the phone.

```
On the volume you chose (phone or microSD card):
  Games/GB  GBC  GBA            ROMs (.zip works, sub-folders are fine)
  Labels/<same folders>/<rom>   optional cart label art

Always on the phone:
  Saves/                        battery saves
  States/                       save states and their thumbnails
  BIOS/                         optional: gba_bios.bin, gb_bios.bin, gbc_bios.bin
  Config/carts.json             what you changed in each cart sheet
```

Settings > About shows both paths. Copying ROMs into `Games/` over USB and picking Settings >
Rescan Games works too, though Add Games is easier.

Battery saves and cart settings are included in Android's backup; games and save states are
not. Uninstalling asks whether to keep the app's data.

## Controls

### Touch

While a game runs, a Game Boy's buttons are drawn on screen:

- **Portrait:** the picture on top and the controls under it, like a Game Boy. The picture
  keeps its full integer scale (6× for Game Boy, 4× for GBA on a 1080 px wide phone).
- **Landscape:** the picture in the middle, the d-pad on the left and A / B on the right. Turning
  the phone doesn't restart the game. The rotation lock is respected.

The d-pad reads eight directions, and a thumb that lands on it keeps steering even if it slides
off the edge. Fingers can slide from button to button, and a thumb laid between A and B presses
both. L and R appear for Game Boy Advance games only. Above the d-pad are the frontend buttons:

| Button | Tap | Hold | Double-tap |
| --- | --- | --- | --- |
| MENU | Pause menu | Save a resume state and eject | Save state switcher |
| SAVE | Quick save (a new save state) | Load the newest save state | |
| ◀◀ | | Rewind | |
| ▶▶ | | Fast-forward | Lock fast-forward on |

**On the shelf:** tap the middle to play (resuming where you left off), hold it to start fresh,
swipe or tap the sides to browse, tap the shelf names at the top to switch shelves. The **Cart**
and **Settings** buttons along the bottom open the cart sheet and settings. Every menu row is
tappable, and Back closes whatever is open.

Settings, setup and the cart sheet use big touch rows by default: switches for on / off
settings, ‹ › buttons that step through choices either way, and a back arrow at the top. With a
controller, Settings > **Menu Style** > **Classic** brings back slot's compact list, made for
d-pad navigation. (Touch menus work with a controller too; the row you're on lights up while
one is connected.)

Settings has **Touch Controls** (Auto, On or Off), **Touch Size** (Small, Medium, Large),
**Touch Opacity** and **Vibrate on Touch**. In Auto, the on-screen buttons hide while you play
with a keyboard or gamepad, and come back the next time you touch the screen.

### Keyboard and gamepad

A Bluetooth or USB gamepad works without setup. Its d-pad steers, whether it reports as buttons
or as a hat (as Xbox-style pads do); the thumbsticks are ignored. A keyboard uses these defaults, and every key can be
rebound in Settings > Keyboard & Gamepad:

| Key | Button |
| --- | --- |
| W A S D | D-pad |
| K | A |
| L | B |
| I | X |
| O | Y |
| Z | L bumper |
| M | R bumper |
| V | Start |
| B | Select |
| P or Back gesture | Menu |
| N | Fast-forward |
| X | Rewind |
| Q | Quick save (hold: quick load) |

**On the shelf:** A/D flip through carts, W/S jump a letter, Z/M switch shelves, tap K to
resume where you left off, hold K to start fresh, V to open the cart sheet, P for settings.

**In game:** the frontend buttons above work the same from their keys (Q, P, X, N), plus
B + M to save a state and B + Z to load the latest. Select still reaches the game: it is held
back only until it's clear you aren't doing a shortcut.

Loaded the wrong state? **Undo Load** in the pause menu puts the game back as it was.

Select still reaches the game: it is held back only until it's clear you aren't doing a
shortcut.

The clock and battery sit above the picture while you play in portrait, clear of the camera
cutout, along with fast-forward and rewind marks. The screen stays on while a game runs and may sleep when it's paused.

## Cart art

Settings > **Scrape Cart Art** dresses the whole shelf with the real printed cartridge labels,
scanned, from slot's art set (the one slot's Cart Studio uses). Carts are found by the ROM's
CRC32, so file names don't matter. About 7,000 games are covered. Box art, title screens and
screenshots from the libretro thumbnails can be picked per cart in the cart sheet.

It only fills carts without art, and upgrades box art it scraped earlier once a real label
exists. Art you put in `Labels/` yourself, or picked from the phone, is never replaced.

## The cart sheet

Tap **Cart** on the shelf (or press Start). A live preview sits on top, and you can change:

| Row | |
| --- | --- |
| Name | Type a new one (the on-screen keyboard, or a hardware one: Enter saves, Back cancels) |
| Scrape Art | Cart Label, Box Art, Title Screen or Screenshot; A/D picks, K fetches |
| Use Image From Phone | Any picture from the photo picker becomes the label |
| Art Fit | Fill the label panel, or fit the whole image |
| Remove Art | Back to a printed label with the cart's name |
| Colour | Auto (from the ROM, slot's tables) or a preset; select it to type a hex code like `#2F5CC0` |
| Finish | Solid, Clear (shows the board) or Glitter |
| Outline | Game Boy carts: the notched DMG shell or the rounded Color one |
| Chip | GB/GBC: Gambatte or mGBA |
| Reset Cart | Undo all of the above |
| Remove Game | Select it twice: the ROM and its art go, its saves and states stay |

Everything is saved to `Config/carts.json`, which you can edit or copy to another phone.

## Save states

- **Manual:** SAVE, Save State in the pause menu, or Q / B + M on a keyboard. Every one is kept,
  with a thumbnail. Hold SAVE to load the newest; double-tap MENU to flick through them all.
- **Automatic:** a resume state is written when you eject a cart and whenever the app leaves
  the screen: Home, switching apps, swiping it away, or the screen turning off.
- On the shelf, tap a cart to pick up from the newest state (auto or manual), or hold it to
  start the game fresh. Battery saves (`.srm`) are kept either way, and written every 30 seconds
  while they change, so a crash or a flat battery loses little.

States are per core, in `States/<platform>/<rom name>/<core>/`.

## How it runs

The shelf is `MainActivity`. A game runs in `GameActivity` in its own `:game` process, as
Lemuroid does: the emulator view always gets a fresh window and surface, and the process exits
when the cart comes out, so each launch loads its core cleanly. The game turns with the phone
but handles the turn itself, so the core keeps running. On 90 and 120 Hz panels it asks for the
mode nearest 60 Hz while a game is up, so every emulated frame lands on a vsync.

## Settings

Fast-forward speed and sound, rewind, integer or fit scaling, screen filter (Sharp, Smooth,
LCD, CRT), colour correction, original Game Boy palette, touch controls (Auto / On / Off, size,
opacity, vibration), menu style (Touch or Classic), keyboard and gamepad keys, add games, import saves,
scrape cart art, game storage (phone or microSD), emulator cores, rescan, the setup guide, and
about.

## Credits

Full credits and acknowledgements are in **[CREDITS.md](CREDITS.md)**. In short:

- **[Titan Slot](https://github.com/sp3lllz/titan-slot)**: the app PatchBoy is forked from.
- **[slot](https://github.com/BrandonKowalski/slot)** by Brandon Kowalski (GPL-3.0): the
  design, cart outlines, shell colour tables, carousel and insert animation, controls, cart
  sounds, and the real cart label scans from its art set.
- **[LibretroDroid](https://github.com/Swordfish90/LibretroDroid)** by Filippo Scognamiglio
  (GPL-3.0) runs the cores; **[Lemuroid](https://github.com/Swordfish90/Lemuroid)** was the
  reference for running games in their own process and delivering cores as feature modules.
- **Cores:** Gambatte (GPL-2.0) and mGBA (MPL-2.0), from [libretro](https://www.libretro.com).
- **Art:** label scans from [ScreenScraper](https://www.screenscraper.fr) (CC BY-NC-SA 4.0) by
  way of slot's art set; box art from the
  [libretro thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails).
- **Type:** Open Sans, SIL Open Font License.

## License

PatchBoy is licensed under the [GNU General Public License v3.0](LICENSE), the same as Titan
Slot, slot and LibretroDroid. Builds may be sold, but whoever receives one is entitled to its complete
source code under the same license; About links to this repository for that.

Game Boy, Game Boy Color and Game Boy Advance are trademarks of Nintendo. This is a fan project,
not affiliated with Nintendo. Bring your own legally
obtained games; none are included or downloaded.
