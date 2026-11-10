# OnKlav

OnKlav is an Android platform and launcher with built-in apps and games.
One APK contains the Kotlin/Compose host, shared Rust services, signed
internal apps and the shared Godot Web game engine. This repository
contains the source for OnKlav 0.101.49-rc1 and the App and Game SDKs.

Start with [START-HERE.md](START-HERE.md), [current tasks](TODO.md) and [engineering instructions](AGENTS.md). The [developer toolkit](developer/README.md) and [SDK guide](sdk/README.md) describe the shared app APIs.

## Speech

One English-only Kitten Nano v0.8 FP32 model with exactly two selected voice rows and host native engine serve Messages, readers and SDK apps. The choices are Male (speaker2, default) and Female (speaker0), using the owner-requested labels; both upstream speakers are male. No Kokoro bank, system TTS setup, per-app model or cloud synthesis is required. See [shared speech](docs/NEURAL-SPEECH.md).

## Build

JDK17, SDK36/build-tools35, Gradle8.11.1 and the original Android signer are required. Reuse source-current native/SDK/game outputs. Changes to native speech need Rust1.90 and NDK27.2.12479018.

```sh
python3 scripts/build.py --plan
python3 scripts/build.py  # optimized release; debug is for development
python3 scripts/check_android.py --tests-only --test app.onklav.home.NeuralSpeechTest
```

Phone/carrier/Bluetooth/endurance and public distribution qualification remain separate from host tests and APK verification. Active backup/document adoption and Notes recovery protect existing user data. Historical milestones are retained in docs/history/pre-0.100.24. Keep third-party licenses/corresponding source and signing identities.

See [size and responsiveness](docs/SIZE-AND-RESPONSIVENESS.md) for measured package changes, preserved features and device-validation limits.

## License

Copyright (C) 2026 OnKlav. OnKlav-owned host, App SDK, and Game SDK
code is licensed under GPL-3.0-or-later. See [LICENSE](LICENSE),
[App SDK licensing](developer/LICENSE.md), and
[Game SDK licensing](developer/games/LICENSE.md). Third-party
materials retain their own notices and terms. See the
[distribution review](docs/PUBLIC-RELEASE-LICENSING.md) for bundled
content and outstanding rights checks.
