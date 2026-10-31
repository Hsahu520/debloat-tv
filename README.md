# debloat-tv

Make an Android TV / Google TV feel like an Apple TV: remove the ads and tracking apps, and replace the
ad-filled home screen with a clean launcher. No root, nothing permanent: one script puts everything back.

Built and tested on a **Xiaomi MiTV-MOOR0 (Google TV 14)**. Other Android TV / Google TV sets work too; only the
debloat list is model-specific (see step 5).

## What you get

- **Clean home screen**: big clock and greeting, your favourite apps in a frosted-glass dock, round glass buttons.
- **✦ For You**: a cinematic poster wall picked from your taste (Hindi + English, six genres). OK opens the title
  in Netflix, Prime Video, JioHotstar or SonyLIV; hold OK to like it.
- **Swipe & rate**: Tinder for shows. ◀ nope, ▶ love. Every swipe re-ranks For You.
- **🎙 Jarvis**: talk through the remote's mic: "Panchayat chala do", "kuch funny dikhao", "open Netflix".
  Works offline with the TV's own speech service.
- **🎮 Games**: Poster Memory and Synthwave Run, played with the remote.
- **Bluetooth** and **Power** (restart / power off) buttons on Home.
- **Screensaver**: "GOOD EVENING", your name, clock, date and weather over a photo of your choice.
- **No ads**: Xiaomi / Google ad rows, analytics and promo apps switched off.

## What you need

- A Mac or Linux computer on the **same Wi-Fi** as the TV (Windows: use WSL).
- **adb** (Android platform-tools), **Java 17 or newer**, and the **Android SDK** command-line tools (platform 34).
  On a Mac with Homebrew:
  ```sh
  brew install android-platform-tools openjdk@17
  brew install --cask android-commandlinetools
  sdkmanager "platforms;android-34" "build-tools;34.0.0"
  ```
- **Python 3** (only for downloading posters).

## Step by step

### 1. Turn on debugging on the TV
Settings → System (or Device Preferences) → About → press OK on **Android TV OS build** 7 times.
Go back → **Developer options** → turn on **USB debugging** (and **Network debugging** / **Wireless debugging**
if your TV has it). Note the TV's IP address: Settings → Network & Internet → your Wi-Fi.

### 2. Get the code and fill in your details
```sh
git clone https://github.com/Hsahu520/debloat-tv.git
cd debloat-tv
cp tv.conf.example tv.conf
```
Open `tv.conf` and set `TV_HOST` (the TV's IP), and optionally `GREETING_NAME` and `WEATHER_CITY`.
`tv.conf` stays on your computer; it is never uploaded.

### 3. Connect
```sh
./scripts/connect.sh
```
The TV asks **"Allow USB debugging?"** the first time: tick *Always allow* and press OK, then run it again.
It prints your TV's model and Android version.

### 4. Back up first (important)
```sh
./scripts/backup.sh
```
Saves the installed apps, disabled apps, Home app and all settings into `backup/`. The **first** backup is kept
as `backup/original`; that is what step 9 restores.

### 5. Remove the ads and bloat (optional)
```sh
./scripts/debloat.sh lists/xiaomi-mitv-moor0-googletv14.txt
```
Apps are **disabled, not uninstalled**. The script refuses any list that would switch off something the TV needs
to boot or to reach Home. For another TV, copy the list, keep only packages your TV has
(`adb shell pm list packages`), and check a package with `adb shell dumpsys package <name>` before adding it.
Ready-made lists for 300+ TV models: [CleanYourTV examples](https://github.com/Gathin23/cleanyourtv/tree/main/examples).

### 6. Posters and screensaver photo (optional)
```sh
python3 tools/fetch_posters.py
```
Downloads a poster for each title in `launcher/app/src/main/assets/catalog.json` (no API key). Without posters,
the titles show as lettered cards. For the screensaver, put any landscape photo at
`launcher/app/src/main/assets/home2/screensaver.jpg` (1920×1080 is ideal); without one it draws a rainy window.
Both stay on your computer: they are copyrighted and listed in `.gitignore`.

### 7. Install the launcher
```sh
./install.sh
```
Builds the launcher, installs it, makes it Home, switches on its Home-button helper and its screensaver.
Press **Home** on the remote. Run `./install.sh` again after any change; it updates in place and keeps your
settings.

### 8. Use it
- **Top-right buttons**: ✦ For You · 🎙 Jarvis · 🎮 Games · Inputs · Wi-Fi · Bluetooth · Power · Settings.
- **Hold OK on ⚙ Settings** → Launcher settings (apps per row, rows, dock, wallpaper) → **Save**.
  Also *Edit dock* and *Reorder apps* in the same menu.
- **Swipe & rate**: ▲ undo, ▼ skip, OK watch now, MENU resets your taste.

### 9. Undo everything
```sh
./scripts/restore-original.sh
```
Puts the TV back to `backup/original`: re-enables every app that was on, restores the Home, accessibility and
screensaver settings, and removes the launcher if it wasn't there before. Apps you *uninstalled* yourself are listed
so you can reinstall them from the Play Store. Restart the TV afterwards.

To undo only the debloat: `./scripts/restore.sh lists/<the list you used>.txt`.

## Customise

| What | Where |
|---|---|
| Your name, weather city, TV address | `tv.conf` |
| Dock apps and their order | `launcher/app/src/main/res/values/config.xml` → `dock_apps` (or *Edit dock* on the TV) |
| Titles for For You, Swipe, Jarvis | `launcher/app/src/main/assets/catalog.json` (then `python3 tools/fetch_posters.py`) |
| Home wallpaper theme | `./scripts/theme.sh themes/aurora-dynamic` (morning / evening / night wallpapers) |
| Screensaver photo | `launcher/app/src/main/assets/home2/screensaver.jpg` |

## Troubleshooting

- **Black screen after a restart**: never disable the stock Google TV home (`launcherx`) or the setup wizard.
  The scripts refuse to. If it happens, from the computer:
  `adb shell pm enable com.google.android.apps.tv.launcherx`, then run `./install.sh`.
- **Home button opens the old Google home**: the Home-button helper got switched off (Android does this after
  an update). Run `./install.sh` again.
- **The Google home flashes for a second at power-on**: normal. Many Google TV firmwares re-enable it at every boot;
  the helper switches to the new home within seconds.
- **Can't connect**: TV on, same Wi-Fi, USB debugging on, correct `TV_HOST`. Some TVs turn network debugging off
  when Wi-Fi drops: switch it on again.
- **Jarvis doesn't hear you**: hold the remote close and press OK on the Jarvis screen to start listening.

## Credits

Debloat setup, launcher customisations and the new features by Hemant Sahu. The launcher in `launcher/` started
from [CleanYourTV](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT, see `LICENSE`). Movie posters and photos
belong to their owners and are downloaded on your own computer for personal use only. Weather by
[Open-Meteo](https://open-meteo.com).
