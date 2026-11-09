# PebbleScan

**Open. Scan. Done.**

A lightweight Android scanner for QR codes and product barcodes. Scans run on the device, and the app supports English and Simplified Chinese.

- **Camera and photos:** Scan live, choose an image, or share a screenshot to PebbleScan.
- **Multiple codes:** Freeze the frame and tap the dot over the code you want.
- **One-handed zoom:** Hold the zoom button and slide along the arc, or pinch the preview.
- **Readable results:** Preview link destinations, inspect Wi-Fi details, and copy or share decoded content.

[Download the APK](https://github.com/winnerhash/pebblescan/releases/latest)

## Build

Use JDK 21 and Android SDK 36 (Build Tools 36.1.0):

```sh
./gradlew :sample:assembleBundledDebug
```

On Windows, use `gradlew.bat` in place of `./gradlew`. The bundled APKs in `sample/build/outputs/apk/bundled/debug/` scan offline without a model download.

## Credits

PebbleScan uses the open-source scanning component from [quickie](https://github.com/G00fY2/quickie), licensed under the [MIT License](LICENSE).

[Third-party licenses and notices](sample/src/main/assets/licenses/)
