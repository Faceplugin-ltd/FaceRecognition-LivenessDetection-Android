<div align="center">
<img alt="FacePlugin" src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/brand/logo.png" width="400"/>
</div>

#### 🌐 Company Site - [Here](https://faceplugin.com)
#### 🤗 Hugging Face - [Here](https://huggingface.co/FacePlugin-Ltd)
#### 🛟 Help Center - [Here](https://doc.faceplugin.com)
#### 🐳 Docker Hub - [Here](https://hub.docker.com/r/faceplugin/face-recognition)

# FacePlugin Face Recognition + 1:N Identification + Liveness Detection — Android (Fully On-Premise)

Jump: [Quick start](#quick-start) · [Get the library](#get-the-library-facerecognitionsdkaar) · [Run the demo](#run-the-demo) · [SDK License](#sdk-license) · [Setup on your own app](#setup-on-your-own-app) · [About SDK](#about-sdk)

## Quick start

- [ ] Clone [FaceRecognition-LivenessDetection-Android](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Android)
- [ ] Download `facerecognitionsdk.aar` from [Releases](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Android/releases/tag/latest) into `libs/` — [Get the library](#get-the-library-facerecognitionsdkaar)
- [ ] Open the folder in Android Studio and run `:app` on a **physical** phone
- [ ] The demo ships with a license key for `com.faceplugin.facerecognitionsdk`; [contact us](#contact) for your own `applicationId`

## Introduction

FacePlugin **Face Recognition + Liveness Detection for Android** runs face recognition and passive liveness entirely on the device. Nothing is sent to a FacePlugin server, which is what banking, access control, and privacy-first identity apps need. Its headline capability is **1:N identification**: enroll your people once, then ask *who is this?* of a camera frame or a photo and get a ranked candidate list back — searched against a gallery that lives on the phone, so no face ever leaves it.

This repository is the whole product: the **engine** (`libs/facerecognitionsdk.aar`), two **helper libraries** you add to your project, and a **demo app** built from those same libraries — so what you read here is what the demo runs.

### Main Functionalities

| Feature | API |
| ------- | --- |
| **1:N identification** against the on-device gallery | `FaceGallery.identify` |
| Face detection (bounding box, landmarks, pose, attributes) | `FaceDetector.detect` |
| Passive liveness (anti-spoofing) | `face.liveness` · `FaceLimits.isLive` |
| Face image quality | `face.face_quality` |
| Face template extraction and similarity | `FaceDetector.template` · `FaceDetector.similarity` |
| Enrollment with duplicate check | `FaceGallery.enroll` |
| Guided camera capture (oval overlay, live coaching) | `FaceCaptureActivity` |
| License and engine lifecycle | `FaceEngine.start` · `FaceEngine.licenseStatus` · `FaceEngine.machineCode` |

### Product List

| Platform | Repository |
| -------- | ---------- |
| **Android** | **[FaceRecognition-LivenessDetection-Android](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Android)** **(this repo)** |
| iOS | [FaceRecognition-LivenessDetection-iOS](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-iOS) |
| React Native | [FaceRecognition-LivenessDetection-ReactNative](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-ReactNative) |
| Flutter | [FaceRecognition-LivenessDetection-Flutter](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Flutter) |
| .NET MAUI | [FaceRecognition-LivenessDetection-DotNet-MAUI](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-DotNet-MAUI) |
| Ionic Cordova | [FaceRecognition-LivenessDetection-Ionic-Cordova](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Ionic-Cordova) |
| Ionic Capacitor | [FaceRecognition-LivenessDetection-Ionic-Capacitor](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Ionic-Capacitor) |

### Related products

- **Face Liveness Detection — Android** — [FaceLivenessDetection-Android](https://github.com/Faceplugin-ltd/FaceLivenessDetection-Android) — liveness only
- **Face Liveness Detection — iOS** — [FaceLivenessDetection-iOS](https://github.com/Faceplugin-ltd/FaceLivenessDetection-iOS) — liveness only
- **Face Recognition — Windows** — [FaceRecognition-Windows](https://github.com/Faceplugin-ltd/FaceRecognition-Windows) — on-premise server
- **Face Recognition — Docker** — [FaceRecognition-Docker](https://github.com/Faceplugin-ltd/FaceRecognition-Docker) — on-premise server

## Before you start

| Step | What you need |
| ---- | ------------- |
| 1 | Android Studio (Hedgehog or newer) and Java 17 |
| 2 | `libs/facerecognitionsdk.aar` from the release page — see [Get the library](#get-the-library-facerecognitionsdkaar) |
| 3 | A **physical** phone. The emulator has no usable camera for liveness |
| 4 | A license key for your `applicationId` ([contact us](#contact)). The demo already has one for `com.faceplugin.facerecognitionsdk` |

### System requirements

| Item | Minimum | Recommended |
| ---- | ------- | ----------- |
| Android | `minSdk 24` (Android 7.0) | Android 10 or newer |
| ABI | `arm64-v8a` or `armeabi-v7a` | `arm64-v8a` |
| RAM | 4 GB | 6 GB or more |
| CPU | 4+ cores | Mid-range SoC from ~2019 or newer |
| Camera | Front camera | 1080p, autofocus |
| Device | Physical device | Same; the emulator is not for camera work |

## Get the library (`facerecognitionsdk.aar`)

`libs/facerecognitionsdk.aar` is empty on GitHub because the binary is too large. Gradle fails fast when the file is missing.

**[Download the engine from Releases](https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Android/releases/tag/latest)**

```bash
git clone https://github.com/Faceplugin-ltd/FaceRecognition-LivenessDetection-Android.git
cd FaceRecognition-LivenessDetection-Android
```

Put the file **here** — not in a nested folder:

```text
FaceRecognition-LivenessDetection-Android/
└── libs/
    └── facerecognitionsdk.aar
```

The engine is about 80 MB, so use [ABI splits or an app bundle](https://developer.android.com/studio/build/configure-apk-splits) for release builds.

## Run the demo

1. Open **this** folder in Android Studio.
2. Select a physical device and Run. Keep `applicationId` as `com.faceplugin.facerecognitionsdk` — the bundled license key is tied to it.
3. Wait for the engine to report **Ready** before using the camera.

The demo is the `:app` module, with five tabs:

| Tab | What it shows |
| --- | ------------- |
| **Identify** | Live 1:N against everyone enrolled, with liveness, then a result screen with the ranked matches |
| **People** | Add one person with the capture coach, add many from photos or a folder, search, edit, delete |
| **Attribute** | Every face in a photo: liveness, quality, detection score, pose, expression, age, gender, landmarks, closest match |
| **Liveness** | Live anti-spoofing check on the camera feed |
| **Settings** | Match, liveness, and pose thresholds; license status |

### Screenshots

<p align="center">
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/face-recognition/mobile/identify.gif" width="240" alt="FacePlugin Face Recognition — live 1:N identification with liveness"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/face-recognition/mobile/people.gif" width="240" alt="FacePlugin Face Recognition — enroll people with the guided capture coach"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/face-recognition/mobile/attribute.gif" width="240" alt="FacePlugin Face Recognition — face attributes, quality and landmarks from a photo"/>
</p>

<p align="center">
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/face-recognition/mobile/liveness.gif" width="240" alt="FacePlugin Face Recognition — passive liveness check on the camera feed"/>
&nbsp;
<img src="https://raw.githubusercontent.com/Faceplugin-ltd/faceplugin-assets/main/screenshots/face-recognition/mobile/settings.png" width="240" alt="FacePlugin Face Recognition — match, liveness and pose thresholds in Settings"/>
</p>

## SDK License

The demo runs as it is: a FacePlugin key for `com.faceplugin.facerecognitionsdk` is already in the code. Licenses are **offline** — once activated, nothing phones home — and each one is bound to your `applicationId`, so your own app needs its own key.

### Set your SDK license

| What | File | What to find |
| ---- | ---- | ------------ |
| The key itself | [`BuiltInLicense.kt`](app/src/main/java/com/faceplugin/facerecognitionsdk/shell/engine/BuiltInLicense.kt) | `const val KEY` |
| Where it is used | [`ShellApp.kt`](app/src/main/java/com/faceplugin/facerecognitionsdk/shell/ShellApp.kt) | `FaceEngine.start(this, BuiltInLicense.KEY)` |
| The id it is bound to | [`app/build.gradle`](app/build.gradle) | `applicationId` |

Find this, and put your key in place of the demo one:

```kotlin
const val KEY =
    "FP1.RlBMMQMAAQDJx5NBpyjuxrTSuz8MAgAA…"
```

Rebuild and run. The Settings tab shows the license state, and the engine reports `NOT_ACTIVATED`, `LICENSE_INVALID` or `LICENSE_EXPIRED` when a key does not fit the app.

### How to get a key

[Contact us](#contact) with the id you will ship. If we ask for a machine code, `FaceEngine.machineCode(context)` returns it. After activation, `FaceEngine.licenseStatus()` reports what the key unlocks.

### License capabilities (Recognition + Liveness)

| Capability | Meaning |
| ---------- | ------- |
| **Recognition** | Detection, attributes, quality, templates, 1:1 similarity, and 1:N identification |
| **Liveness** | Passive anti-spoofing on camera frames and stills |

## Setup on your own app

You integrate from **this repository** — there is no package server, no Maven coordinate, and no remote dependency to add.

1. Clone this repository and put `facerecognitionsdk.aar` in `libs/` ([above](#get-the-library-facerecognitionsdkaar)).
2. Copy the two library modules into your project (or point at the clone):

   ```text
   face-recognition-kit/        FaceEngine, FaceGallery, FaceDetector, FaceLimits, image and camera helpers
   face-recognition-capture/    FaceCaptureActivity, CaptureView, FaceCaptureState (optional capture coach)
   ```

3. `settings.gradle`:

   ```groovy
   include ':face-recognition-kit'
   include ':face-recognition-capture'   // only if you want the capture coach
   ```

4. `app/build.gradle` — the app supplies the engine, because an Android library cannot package a local `.aar`:

   ```groovy
   android {
       defaultConfig {
           minSdk 24
           ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }
       }
       compileOptions {
           sourceCompatibility JavaVersion.VERSION_17
           targetCompatibility JavaVersion.VERSION_17
       }
   }

   dependencies {
       implementation project(':face-recognition-kit')
       implementation project(':face-recognition-capture')
       implementation files(rootProject.file('libs/facerecognitionsdk.aar'))
   }
   ```

5. Start the engine once per process, with **your** license key.

You do **not** need the demo's `:app` module. Full wiring, permissions, and result JSON: [https://doc.faceplugin.com](https://doc.faceplugin.com)

## About SDK

Activate once at start-up, then call the engine. The first start loads the on-device models, so it takes a few seconds. Call it from a background thread.

**1. Activate and initialize**

```kotlin
// Application.onCreate
FaceEngine.start(context, "YOUR_LICENSE_KEY") { code ->
    // code == IdentityEngine.SUCCESS when the engine is ready
}

FaceEngine.addListener { state -> /* LOADING, READY, NOT_ACTIVATED, LICENSE_EXPIRED, … */ }
FaceEngine.isReady
FaceEngine.licenseStatus()
FaceEngine.machineCode(context)
```

**2. Read a face**

```kotlin
val faces = FaceDetector.detect(bitmap, FaceDetectionParam.allAttributes())
val face = faces.first()
face.liveness; face.face_quality; face.yaw; face.roll; face.pitch
face.age; face.genderLabel; face.smile; face.mouth_opened

val template = FaceDetector.template(bitmap, face)      // 1040 bytes
val score = FaceDetector.similarity(templateA, templateB)
```

**3. Enroll and identify (1:N)**

```kotlin
val gallery = FaceGallery.get(context)

when (val outcome = gallery.enroll("Mina Ortiz", "Front desk", photo, limits)) {
    is EnrollOutcome.Enrolled -> outcome.record.id       // and outcome.duplicate, outcome.report
    is EnrollOutcome.Rejected -> outcome.reason
}

when (val outcome = gallery.identify(probe, limits, livenessOn = true)) {
    is IdentifyOutcome.Done -> outcome.result.primary.best   // name, id, similarity
    is IdentifyOutcome.Rejected -> outcome.reason            // no face, liveness failed, no template
}
```

Enrolled people live in SQLite, with their photos in app-private storage. Ids are never reused.

**4. The capture coach (optional)**

Subclass `FaceCaptureActivity` for the oval guide, live coaching, and a hold-to-capture that only fires on a good frame:

```kotlin
class AddPersonActivity : FaceCaptureActivity() {
    override fun resultPanelLayout() = R.layout.view_enroll_panel
    override fun limits() = myLimits
    override fun onCaptureAnalyzed(face: FaceBox?, faceCount: Int) { /* capturedPhoto(), retakeCapture() */ }
}
```

**5. Thresholds**

`FaceLimits` holds every threshold the checks use, so the library never reads your settings:

```kotlin
val limits = FaceLimits(
    match = 0.80f,      // lowest similarity that counts as the same person
    liveness = 0.50f,   // lowest liveness score that counts as a live face
    yaw = 20f, roll = 20f, pitch = 20f,
)
```

`FaceLimits.DEFAULT` is the engine's operating points. The helpers `isLive`, `poseOk`, and `matches` apply the same rule everywhere, so the capture coach, identify, and the attribute screen agree.

**6. What each action did**

`enroll`, `identify`, and `attributes` return a `CallReport`: the engine calls they made, what each returned, whether it passed, and how long it took.

```kotlin
report.success
report.totalMs
report.steps            // faceDetection, templateExtraction, similarity, duplicateCheck, save…
report.steps[0].detail  // "1 face"
```

## Contact

<div align="left">
<a target="_blank" href="mailto:info@faceplugin.com"><img src="https://img.shields.io/badge/email-info@faceplugin.com-blue.svg?logo=gmail" alt="faceplugin.com"></a>&emsp;
<a target="_blank" href="https://t.me/FacePluginSupport"><img src="https://img.shields.io/badge/telegram-@FacePluginSupport-blue.svg?logo=telegram" alt="Telegram @FacePluginSupport"></a>&emsp;
<a target="_blank" href="https://wa.me/+14692784822"><img src="https://img.shields.io/badge/whatsapp-faceplugin-blue.svg?logo=whatsapp" alt="faceplugin.com"></a>
</div>
