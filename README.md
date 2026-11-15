# SubCase

A powerful wrapper for [Sub-Store](https://github.com/sub-store-org/Sub-Store) on Android.

> This is a fork of [sub-store-org/subcase](https://github.com/sub-store-org/subcase), maintained by [Ruxco](https://github.com/Ruxco).

[![Latest release](https://img.shields.io/github/v/release/Ruxco/subcase?label=Release&logo=github)](https://github.com/Ruxco/subcase/releases/latest)

<img src="https://github.com/user-attachments/assets/76d72cf0-7827-4a0b-b28b-e9937bdf4235" width="250"/>
&nbsp;&nbsp;&nbsp;&nbsp;
<img src="https://github.com/user-attachments/assets/a44deb47-c9c9-4648-b40b-082ed47e4ae9" width="250"/>

## Features

- No root permission required.
- Runs Sub-Store entirely on-device.
- Two switchable backend engines:
  - **Node.js (default)**: a Javet-based Node.js runtime executing the official `sub-store.bundle.js`. Script operators can use `require`, `process`, `Buffer`, and the full Node.js runtime.
  - **WebView**: executes the Loon build with a WebView-based Loon API emulation.
- Automatic Sub-Store frontend and backend updates.
- Automatic data migration when switching backend engines.
- No crash reporting or telemetry.

## Backend engines

SubCase can run the Sub-Store backend in two modes, switchable in **Settings → Backend engine**:

- **Node.js (default)**: runs the official `sub-store.bundle.js` on a Javet-based Node.js runtime. Script operators can use `require`, `process`, `Buffer`, and other Node.js APIs.
- **WebView**: executes the Loon build inside a WebView with Loon API emulation.

Switching engines requires restarting the service. The two engines use different storage formats; SubCase migrates the data automatically when you switch.

## Build

The Node.js engine depends on `com.caoccao.javet:javet-node-android` (bundled Node.js runtime). Building requires JDK 17, Android SDK 37, and NDK r27; the GitHub Actions workflow (`.github/workflows/build-apks.yml`) already provides this environment.

## Notes

This fork removes Firebase Crashlytics and the crash-reporting toggle, changes the in-app GitHub link to this fork, and checks for app updates against this fork's releases. Update checks are skipped silently while this fork has no published release.
