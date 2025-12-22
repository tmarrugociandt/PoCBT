# SampleCode-V5 (Android SDK V5) — Overview and Guide

This repository contains samples and modules to work with the Mobile SDK V5 (Android). The README is written in English and explains how to open, build, install, request runtime permissions, and how to diagnose the JNI crash that occurs when requesting camera permissions.

## Repository structure
- `android-sdk-v5-as/` — Top-level build configuration (scripts, `dependencies.gradle`, `gradle.properties`, `gradlew`), repositories and common resources. Contains `msdkkeystore.jks` (example keystore).
- `android-sdk-v5-sample/` — Sample application demonstrating SDK usage and example activities. Main manifest: `android-sdk-v5-sample/src/main/AndroidManifest.xml`. Concrete `Application` class: `android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/DJIAircraftApplication.kt`.
- `android-sdk-v5-uxsdk/` — UI/UX library for the SDK (reusable components). Manifest: `android-sdk-v5-uxsdk/src/main/AndroidManifest.xml`.

## Prerequisites
- Android Studio compatible with the Android Gradle Plugin used (recommended: AGP 8.x+).
- JDK 11 or the version recommended by Android Studio/Gradle (check `gradle.properties`).
- Android SDK with the API levels specified in `dependencies.gradle` / `gradle.properties` (the project uses placeholders `ANDROID_COMPILE_SDK_VERSION`, `ANDROID_MIN_SDK_VERSION`, `ANDROID_TARGET_SDK_VERSION`).
- An Android device with USB debugging enabled or an emulator with camera support (testing on a real device is recommended for hardware connections).

## Open the project
1. Open Android Studio and select "Open" pointing to the root folder that contains `settings.gradle` (for example `android-sdk-v5-as`).
2. Let Gradle sync and download dependencies.

## Build and run
From the repo root (the folder that contains `gradlew`) you can use the Gradle Wrapper:

- Build the sample app (debug):

```bash
./gradlew :android-sdk-v5-sample:assembleDebug
```

- Install the sample app to a connected device:

```bash
./gradlew :android-sdk-v5-sample:installDebug
```

- Clean and rebuild:

```bash
./gradlew clean :android-sdk-v5-sample:assembleDebug
```

You can also install the generated APK manually:

```bash
adb install -r android-sdk-v5-sample/build/outputs/apk/debug/android-sdk-v5-sample-debug.apk
```

Make sure `adb devices` lists your device.

## Permissions (AndroidManifest.xml)
Permissions extracted from the project manifests:

- In `android-sdk-v5-sample/src/main/AndroidManifest.xml` (among others):

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

- In `android-sdk-v5-uxsdk/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

IMPORTANT: Declaring permissions in `AndroidManifest.xml` is required but not sufficient on Android 6.0+ (Marshmallow and newer). For dangerous permissions such as `CAMERA`, `RECORD_AUDIO`, and `ACCESS_FINE_LOCATION`, you must request them at runtime.

## Requesting runtime permissions (Kotlin examples)
Two approaches are recommended: the modern Activity Result API, or the legacy `ActivityCompat.requestPermissions`.

- Activity Result API (Kotlin) — recommended:

```kotlin
// inside an Activity or Fragment
private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) {
        // Initialize what needs the camera (e.g. initSdk())
        initCameraFeatures()
    } else {
        // Show rationale or explain why permission is needed
        showCameraRationale()
    }
}

// To launch the request:
requestCamera.launch(Manifest.permission.CAMERA)
```

- ActivityCompat (legacy compatibility):

```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
} else {
    initCameraFeatures()
}

// and handle the response in onRequestPermissionsResult
```

### Practical note
Never initialize or load a native library that tries to access the camera before the `CAMERA` permission is granted and validated by the app — doing so can cause native (JNI) crashes. See Troubleshooting below.

## Diagnosis: JNI crash when requesting camera permissions
Observed symptoms (from the logs provided):
- Native crash with messages like "JNI DETECTED ERROR IN APPLICATION: field operation on NULL object" and stack frames in `libSdkyclx_clx.so` during `JNI_OnLoad` / `System.loadLibrary`.
- Crash occurs when requesting camera permission at runtime.

Probable root cause
- The project calls `com.cySdkyc.clx.Helper.install(this)` from `attachBaseContext` in the `Application` implementation, which runs very early in the app lifecycle. That call likely loads native libraries and/or initializes components that expect access to hardware or Java objects that aren't available or permitted yet. If native code runs and attempts to access camera resources before the user grants permission, it may trigger a JNI crash.

Local evidence
- `android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/DJIAircraftApplication.kt` contains:

```kotlin
override fun attachBaseContext(base: Context?) {
    super.attachBaseContext(base)
    try {
        com.cySdkyc.clx.Helper.install(this)
    } catch (t: Throwable) {
        // ... error handling ...
    }
}
```

Concrete recommendations to avoid the crash
1. Defer the installation / native load until after required permissions (e.g. `CAMERA`) are granted.
   - Move `com.cySdkyc.clx.Helper.install(this)` out of `attachBaseContext` / `Application.onCreate` and call it from an `Activity` (for example from `DJIAircraftMainActivity.onCreate`) after checking/requesting permissions.
2. If the library must be loaded early, wrap the load in a broad try/catch and avoid executing any camera access code until `ContextCompat.checkSelfPermission(..., Manifest.permission.CAMERA)` returns `GRANTED`.
3. Add detailed logs before and after `System.loadLibrary` / `Helper.install` to find the exact failure point.
4. Reproduce and collect native logs with `adb logcat` and, for native crashes, look for tombstones in `/data/tombstones`:

```bash
adb logcat -v threadtime > crash.log
# reproduce the crash
# search crash.log for native traces
adb shell ls /data/tombstones
adb pull /data/tombstones/tombstone_00
```

5. (Optional) If you have native symbols available, use `ndk-stack` to symbolize the native stack and get readable line information.

Suggested snippet to move the installation (quick example)

```kotlin
// In the entry Activity (e.g. DJIAircraftMainActivity)
private fun ensureHelperInstalled() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        try {
            com.cySdkyc.clx.Helper.install(this.applicationContext)
        } catch (t: Throwable) {
            Log.e("DJIAircraft", "Helper.install failed", t)
        }
    } else {
        // request permission and install in the callback
        requestCamera.launch(Manifest.permission.CAMERA)
    }
}
```

Where `requestCamera` is the `ActivityResultLauncher` shown earlier and its callback calls `ensureHelperInstalled()` when `granted == true`.

## Tips to select the rear camera
If you need to force using the rear camera when initializing SDK components, do this after permission is granted. The exact API depends on the SDK you are using (DJI, Camera2, etc.). General approach:
1. Wait for the `CAMERA` permission to be granted.
2. List cameras with `CameraManager.getCameraIdList()`.
3. Choose the camera whose `CameraCharacteristics.LENS_FACING` equals `LENS_FACING_BACK`.

Example (Camera2 sketch):

```kotlin
val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
for (id in cameraManager.cameraIdList) {
    val chars = cameraManager.getCameraCharacteristics(id)
    val facing = chars.get(CameraCharacteristics.LENS_FACING)
    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
        // use this id
        openCamera(id)
        break
    }
}
```

Ensure any SDK initialization that opens the camera uses this id and runs after permissions are granted.

## Where to put AIRCRAFT_API_KEY
The project expects an `AIRCRAFT_API_KEY` Gradle property that is used by the sample app. By default this property is defined in `android-sdk-v5-as/gradle.properties` (this repository currently includes an example value there). The sample's `build.gradle` maps that property into a manifest placeholder:

```groovy
// in android-sdk-v5-sample/build.gradle
manifestPlaceholders["API_KEY"] = project.AIRCRAFT_API_KEY
```

and the manifest reads it into a meta-data entry:

```xml
<meta-data android:name="com.dji.sdk.API_KEY" android:value="${API_KEY}" />
```

Recommended (safer) ways to provide your real API key:

- Put the key in your user Gradle properties (recommended, not committed):

Add to `~/.gradle/gradle.properties`:

```properties
AIRCRAFT_API_KEY=your_real_aircraft_api_key_here
```

- Or pass it at build time (useful for CI or one-off builds):

```bash
./gradlew :android-sdk-v5-sample:assembleDebug -PAIRCRAFT_API_KEY=your_real_aircraft_api_key_here
```

- Or add it to a machine-specific `android-sdk-v5-as/gradle.properties` file if you prefer local project configuration — but do NOT commit production keys to the repository.

CI notes
- In CI pipelines, store the key in a secret manager and write it to the build machine's `~/.gradle/gradle.properties` or inject it into the project `gradle.properties` file at build time. For example, in a pipeline step you can `echo "AIRCRAFT_API_KEY=$AIRCRAFT_API_KEY" >> ~/.gradle/gradle.properties` (using the CI secret variable).

Security note
- Do not commit your production API key to the repo. Remove any real keys from version control and use user-level or CI-level secure storage instead.

## Security and privacy
- Request only the permissions you actually need and provide a user-facing rationale.
- Do not commit production keystores to public repositories. The file `msdkkeystore.jks` exists in the repo — do not upload a production keystore.
- If the app handles user images/videos, document the handling and follow applicable privacy policies.

## Collecting logs and reproducing the crash
1. Connect the device via USB and enable debugging.
2. Clear logs and start `adb logcat`:

```bash
adb logcat -c
adb logcat -v threadtime > crash.log
```

3. Open the app, reproduce the permission request and check `crash.log`.
4. Search `crash.log` for the native stack or "JNI DETECTED ERROR IN APPLICATION".
5. Pull tombstones if a native crash occurred:

```bash
adb shell ls /data/tombstones
adb pull /data/tombstones/tombstone_*
```

6. (Optional) If you have symbol files, run:

```bash
ndk-stack -sym <path_to_symbols> -dump tombstone_XX
```