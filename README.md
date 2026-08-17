# NX Auto Clicker

Minimal single point auto clicker for Android, built for Android 10 without root.

## Usage

1. Enable the accessibility service.
2. Grant the display over other apps permission.
3. Start the panel.
4. Drag the round target to the spot that should be tapped.
5. Use the floating column: play/stop on top, settings in the middle, close at the bottom.
   Press and drag any of the three to move the whole column.

Settings cover tap interval, press duration, a live clicks per second readout and an optional
click limit that stops automatically once reached.

While running the target ring becomes non touchable so the injected tap reaches the app underneath.
Keep the control column away from the target ring, otherwise the injected tap lands on a button.

## Google Play Protect

Play Protect blocks sideloaded apps that request an accessibility service in regions where advanced
fraud protection is enabled. This app needs accessibility to dispatch taps, so the warning is
expected. Choose install anyway, or temporarily turn the protection off in Play Store settings.
There is no code level workaround short of distributing through a store.

## Build

GitHub Actions builds signed debug and release APKs on every push to `main`.
