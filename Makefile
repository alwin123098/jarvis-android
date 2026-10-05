# Convenience targets. `make start` does everything.
.PHONY: start setup build build-ui install clean

start:            ## clone-to-running: fetch engines, build, install
	bash scripts/bootstrap.sh

setup:            ## vendor the native engines only
	bash scripts/fetch_native.sh

build:            ## full APK with the native engine
	./gradlew assembleDebug -Pjarvis.buildNative=true

build-ui:         ## fast UI-only APK (no native engine)
	./gradlew assembleDebug

install:          ## install the built APK to a connected device
	adb install -r app/build/outputs/apk/debug/app-debug.apk

clean:
	./gradlew clean
