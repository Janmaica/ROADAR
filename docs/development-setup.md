# Development setup

## Requirements

- JDK 17. Android Studio's bundled JBR works on Windows.
- Android SDK Platform 36 and platform tools (`adb`).
- The repository's Gradle Wrapper (Gradle 8.11.1).
- VS Code with Kotlin/Android editing support, or Android Studio.

The app uses `compileSdk = 36`, `targetSdk = 35`, and `minSdk = 26`. Keep the application ID `com.roadar.app` unchanged unless a deliberate package migration is planned.

## Windows and VS Code

1. Clone the repository and open its root folder in VS Code.
2. Install Android SDK Platform 36 and Platform-Tools through Android Studio's SDK Manager, or configure the SDK path in a local, untracked `local.properties`.
3. Select JDK 17. For a standard Android Studio installation, PowerShell can use:

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   java -version
   ```

4. For local pothole inference, obtain the approved model from the project's authorized private model source. Follow [model asset instructions](model-assets.md) to place and checksum-verify it. The model is intentionally excluded from Git; without it, the app builds and camera preview works, but detection and detection-based reporting are disabled with an explicit status.

5. To render the online basemap, create a MapTiler API key for the MapTiler Maps API. In the MapTiler key settings, restrict the mobile key with the allowed User-Agent substring `ROADAR-Android`. Add the key to the existing root `local.properties` file (do not replace its `sdk.dir` entry):

   ```properties
   MAPTILER_API_KEY=YOUR_RESTRICTED_MAPTILER_KEY
   ```

   MapTiler keys are public client credentials when shipped in an Android app, not secrets. Restrict usage, monitor the account's plan/quota, and rotate a compromised key. ROADAR injects the value into BuildConfig and sets a matching ROADAR User-Agent on map requests. `local.properties` is ignored by Git; never commit or paste the key into source, screenshots, or issue reports. Without the key, the app clearly indicates that the online basemap is not configured. Map tiles require an internet connection; offline basemap downloads are not implemented or permitted by ROADAR's current provider configuration.

6. Build the debug app from the repository root:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

7. Run local unit tests:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```

8. To run instrumented UI tests, start an emulator or connect and authorize an Android device, then run:

   ```powershell
   .\gradlew.bat :app:connectedDebugAndroidTest
   ```

Do not commit `local.properties`; its SDK path is specific to a workstation. The debug APK is under `app/build/outputs/apk/debug/`.

## macOS and Linux

Install JDK 17, Android SDK Platform 36, and Platform-Tools, then use:

```shell
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Make the wrapper executable if needed with `chmod +x gradlew`.
