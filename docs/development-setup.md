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

4. Build the debug app from the repository root:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

5. Run local unit tests:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```

6. To run instrumented UI tests, start an emulator or connect and authorize an Android device, then run:

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
