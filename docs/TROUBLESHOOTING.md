# Troubleshooting

Environment problems people hit building Jarvis, and how to fix each.

## Quick answer: just use Android Studio

Android Studio bundles a JDK, the Android SDK, and the NDK, and configures them
for you. If you open this folder in Android Studio and press ▶ Run, none of the
issues below apply. The rest of this page is for command-line builds.

---

## `JAVA_HOME is not set and no 'java' command could be found`

Gradle needs **JDK 17**. Install it and point `JAVA_HOME` at it.

### Linux (Debian / Ubuntu / WSL)
```bash
sudo apt update && sudo apt install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```

### macOS
```bash
brew install openjdk@17
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
```

### Windows (PowerShell)
```powershell
winget install EclipseAdoptium.Temurin.17.JDK
# then set JAVA_HOME (adjust the path to your install):
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot"
```
Open a new terminal afterwards.

### Or reuse Android Studio's bundled JDK
Android Studio ships a JDK ("jbr") — point at it instead of installing one:

```bash
# macOS
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
# Linux (adjust to where you unpacked it)
export JAVA_HOME="$HOME/android-studio/jbr"
# Windows
#   C:\Program Files\Android\Android Studio\jbr
```

### Verify
```bash
java -version   # should report 17.x
```

To make it permanent, add the `export` lines to `~/.bashrc` (Linux) or
`~/.zshrc` (macOS).

---

## `SDK location not found` / `ANDROID_HOME`

Gradle also needs the Android SDK. Either set an environment variable:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # Linux
export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS
```

or create `local.properties` in the repo root (this file is git-ignored):

```properties
sdk.dir=/home/you/Android/Sdk
```

If you have no SDK yet, install the command-line tools:
https://developer.android.com/studio#command-tools

---

## NDK not installed (needed only for the native build)

`-Pjarvis.buildNative=true` compiles llama.cpp and whisper.cpp and needs the
NDK (this project pins `26.1.10909125`). Install it via Android Studio's SDK
Manager (SDK Tools → NDK), or:

```bash
sdkmanager "ndk;26.1.10909125"
```

Without the NDK you can still do a UI-only build:
```bash
./gradlew assembleDebug
```

---

## `Permission denied` running `fetch_native.sh`

The executable bit is not always preserved on checkout:

```bash
chmod +x scripts/fetch_native.sh
# or just run it through bash:
bash scripts/fetch_native.sh
```

## `./gradlew: No such file or directory`

The wrapper ships with the repo. If it is missing, pull the latest, or generate
it with a local Gradle:

```bash
git pull
# or, if you have Gradle installed:
gradle wrapper --gradle-version 8.9
```

---

## `adb: no devices/emulators found`

`adb install` needs a target device.

- **Physical phone:** enable Developer options (tap *Build number* 7×), turn on
  **USB debugging**, plug in, and accept the prompt on the phone. Check with
  `adb devices`.
- **Emulator:** create one in Android Studio's Device Manager (API 34+), or via
  the CLI with the `avdmanager` tool, then run the app from Android Studio.

---

## The app builds but chat says "Native engine missing"

That means the APK was built without `-Pjarvis.buildNative=true`, or
`scripts/fetch_native.sh` was not run first. Run both:

```bash
bash scripts/fetch_native.sh
./gradlew assembleDebug -Pjarvis.buildNative=true
```
