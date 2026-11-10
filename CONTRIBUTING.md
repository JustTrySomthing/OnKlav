# Contributing to OnKlav

You can help with a phone test, a clearer guide, a shareable theme, or a code change.

- **Test on Android.** Try the [latest prerelease](https://github.com/JustTrySomthing/OnKlav/releases) and [report a bug](https://github.com/JustTrySomthing/OnKlav/issues/new/choose). Include your OnKlav version, Android version, device model, and the steps that reproduce it. Use sample content in screenshots.
- **Improve docs or themes.** Pick a [starter issue](https://github.com/JustTrySomthing/OnKlav/labels/good%20first%20issue) or open an issue to describe a gap. For a theme, see the [theme format](docs/THEME-PACKAGES.md) and include permission information for any artwork you want to share.
- **Write code.** Read [START-HERE.md](START-HERE.md) and the [project conventions](AGENTS.md). Use the [App SDK setup](developer/docs/INSTALLATION.md) or [Game SDK setup](developer/games/README.md) for package work. See the [README build section](README.md#build) for the Android host.

## Submit a change

1. Fork the repository and create a branch from `main`.
2. Keep each pull request focused. For a larger change, open an issue first to discuss the scope.
3. Run the relevant checks and record the commands and results in the pull request. Describe any Android device checks you ran and include screenshots for UI changes.
4. Open a pull request against `main`. Link the issue and explain how to verify the change.

Keep signing keys, credentials, device backups, phone numbers, and message contents out of commits and public reports. Use a disposable key when testing package authoring. Read [LICENSE](LICENSE) and preserve the applicable third-party notices.

## Run the SDK checks

The App SDK smoke test needs Python 3.11+, Rust 1.90+, the WebAssembly target,
and a native linker. The Game SDK tooling suite uses Python and the listed
dependencies.

```sh
rustup target add wasm32-unknown-unknown
python3 developer/ci_smoke.py
python3 -m unittest discover -s developer/tests -p test_distribution.py -v
python3 -m pip install -r developer/games/requirements.txt
python3 -m unittest discover -s developer/games/tests -v
```

The checks exercise portable App SDK packaging and authoring and Game SDK tooling.
Android UI, carrier delivery, and gameplay on a handset require separate testing.
