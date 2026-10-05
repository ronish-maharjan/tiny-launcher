# TinyTVLauncher

A minimal, lightweight Android TV launcher designed for older and low-resource Android TV devices.

The launcher focuses on one job:

**show installed applications and launch them quickly.**

## Features

* Displays installed TV applications
* D-pad navigation
* Open applications with the OK/Enter button
* Rearrange applications
* Hide applications
* Unhide hidden applications
* Reset application order
* Minimal launcher settings
* Black, distraction-free interface
* No network communication
* No background service
* No database
* No continuous polling
* No animations in the main launcher

## Compatibility

* Minimum Android version: Android 6.0 (API 23)
* Designed primarily for Android TV
* Java
* Platform Android UI APIs
* No Compose runtime
* No AndroidX runtime dependency
* No database or background service

## Performance goals

TinyTVLauncher is designed around low resource usage.

The launcher tries to:

* keep the view hierarchy small
* avoid unnecessary redraws
* avoid work while idle
* avoid background processing
* keep application metadata small
* keep icon memory bounded
* perform navigation with simple integer calculations
* refresh installed applications only when necessary

The launcher does not attempt to make other applications run faster. Its responsibility is to remain lightweight and responsive while acting as the Android TV home screen.

## Build locally

Use Java 17.

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## License

Licensed based on MIT
