
Monza Radio - UNISOC build (TECNO Spark Go 2 / UMS9230E)

This project targets UNISOC devices (e.g., TECNO KM4). It:
- Uses Unisoc FM classes via reflection (com.unisoc.fmradio.FmNative) when available.
- If hardware not present or reflection fails, runs a local audio simulation (AudioTrack) so you can listen via speaker or Bluetooth WITHOUT headphones.
- Implements RDS polling (3s), favorites, volume/mute, scan, speaker toggle, streaming fallback.
- Includes a foreground service for persistent playback and MediaSession skeleton.

Important:
- I cannot build APK here. This ZIP contains the full Android Studio project. Open in Android Studio, build and install on your phone.
- If your phone exposes different Unisoc class names, let me know exact available FM classes (e.g., via adb or logs) and I will adapt reflection calls.
