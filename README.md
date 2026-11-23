# Nimble

A very lightweight, fully on-device assistant harness for Android, built on
[Cactus Needle 3](https://github.com/cactus-compute/cactus) (a 29–121M parameter
tool-calling model) and Whistle (on-device speech to text). It borrows the
shape of [Hermes Agent](https://github.com/NousResearch/hermes-agent) (an agent
loop, toolsets, gating) and reshapes it around what a tiny model can do.

**Status:** first draft (v0.1). See [PLAN.md](PLAN.md) for the design and roadmap.

## What it does

A minimal chat screen: type, or tap the mic on the right of the message box
and speak. Nimble turns the request into a device action:

| Domain | Tools |
|---|---|
| Alarms & timers | `set_alarm`, `set_timer`, `show_alarms` |
| Calls & messages | `call_contact`, `send_text`, `open_app` |
| Notes | `create_note`, `list_notes`, `clear_notes` |
| Phone controls | `set_flashlight`, `set_volume`, `check_battery`, `open_settings` |

Calls and texts open the dialer or messaging app pre-filled; you press call or send.

## Offline by design

- Weights are **downloaded from Hugging Face inside the app** (Settings → Models),
  pinned by revision and verified by SHA-256. That is the only network use.
- Everything else (understanding, speech recognition, actions) runs on the phone.
- CPU thread count is set manually in Settings.

## Get the APK

Every push builds an APK in GitHub Actions: open the **Actions** tab, pick the
latest *Android APK* run, and download the `nimble-apk` artifact.
`nimble-release.apk` is the small, minified build (signed with a throwaway
debug key, so uninstall before installing a build from a different run).
Requires an arm64 Android phone, Android 8.0+.

## Build locally

```sh
./scripts/fetch_engine.sh        # downloads the Cactus engine for android-arm64 into ./engine
./gradlew assembleRelease        # needs the Android SDK + NDK
```

## Layout

```
app/src/main/cpp/            JNI shim over the Cactus C ABI (links the static engine)
app/src/main/java/app/nimble/
  engine/                    CactusEngine: one thread, one Needle session, model loading
  harness/                   Harness loop, router, Tool/Domain types, time parsing
  tools/                     Domain packs (≤5 tools each) and the notes store
  models/                    Weight download + checksum
  settings/  voice/  ui/     Settings, 16 kHz recorder, Compose screens
```

## Adding a tool

1. Write a `Tool` subclass (flat arguments, enums for closed sets, bounded numbers,
   phrases "copied word for word" and parsed in Kotlin).
2. Add it to a `Domain` (keep each domain at 5 tools or fewer), or add a new
   domain to `tools/Domains.kt` with a few keywords for fast routing.

## License

Nimble code: to be decided by the owner. Cactus engine and weights: Apache 2.0.
