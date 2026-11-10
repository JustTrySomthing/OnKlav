# OnKlav

**A Virtual Operating System and Game Console for Android**

*Pronounced “Enclave,” a protected, self-contained territory.*

OnKlav is a free, open-source project that runs inside Android. It gives you a
customizable desktop, built-in apps and games, and two SDKs for building your own.
Its design priorities are user control, direct communication, and a lightweight
environment that keeps apps and games together.

[Download OnKlav for Android](https://github.com/JustTrySomthing/OnKlav/releases/tag/v0.101.49-rc1)
from the release page. The signed prerelease APK supports ARM64 devices running
Android 10 or newer. The App SDK and Game SDK are available on the same page.

## What you can do

- **Make it yours.** Arrange your desktop, change colors, fonts, and backgrounds,
  and save or share [theme packages](docs/THEME-PACKAGES.md).
- **Run apps and games inside OnKlav.** Install signed `.onklav-a` apps and
  `.onklav-g` games in a single Android app. OnKlav runs them in sandboxed
  environments and mediates access to supported device capabilities.
- **Play offline.** Play bundled single-player games such as Shroom, Last Light
  Express, Crash Test Panda, and Air Hockey without an internet connection.
- **Play a friend by text.** Supported games such as Naval Battle, Cats vs Dogs,
  and Blackjack use [SMS for one-on-one turns](developer/games/SMS-TURNS.md)
  without a game server or an internet connection. Both players need compatible
  OnKlav installs, default messaging setup, and working cellular SMS service.
  Carrier charges may apply.
- **Stay in touch.** Use built-in Messages and Groups for SMS and MMS.
  [Real Social](docs/REAL-SOCIAL.md) shares posts and replies directly with
  accepted friends over SMS, and uses MMS for media attachments.
- **Build and share your own.** Use the [App SDK](developer/README.md)
  for apps and utilities, and the [Game SDK](developer/games/README.md)
  for games. Developers can share signed packages for users to install.

## How it fits together

One APK contains the Kotlin/Compose host, shared Rust services, signed
internal apps, and the shared Godot Web game engine. The repository contains
the source for OnKlav 0.101.49-rc1 and both SDKs.

Start with [START-HERE.md](START-HERE.md), [current tasks](TODO.md), or the
[SDK guide](sdk/README.md).

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
