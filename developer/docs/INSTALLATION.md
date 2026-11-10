# Developer workstation setup

External apps require Python3.11+, Rust1.90+ with rustup/Cargo, wasm32-unknown-unknown and a native linker. They do not require Android Studio, Java, Gradle, NDK, the platform's signing keys, or a host APK rebuild. Initial dependency downloads require network access. Use your normal trusted Python/Rust installers; the kit does not execute a downloaded installer for you.

| System | Native prerequisites | Commands |
| --- | --- | --- |
| Linux | C compiler/linker and normal system build libraries | `rustup target add wasm32-unknown-unknown`, then `python3 developer/ci_smoke.py` |
| macOS | Xcode command-line build tools and Rust | `rustup target add wasm32-unknown-unknown`, then `python3 developer/ci_smoke.py` |
| Windows | Rust MSVC toolchain, Visual Studio C++ build tools with Windows SDK, Python | `rustup target add wasm32-unknown-unknown`, then `python developer/ci_smoke.py` in a terminal with the native build environment available |

Open a new terminal after installing tools. Keep the whole extracted starter together. Spaces and Unicode in project paths are supported. Portable projects carry frozen source dependencies in vendor; preserve vendor, source and Cargo.lock in version control. Keep author keys outside both SDK and project directories.

`ci_smoke.py` performs doctor, creates a disposable portable form in a moved path, and runs build/sign/install/scenario verification with a temporary key. It changes neither a phone nor your existing app. Its exit status is nonzero on failure, so the same command can be used by a CI provider after that provider has installed the prerequisites. It is a real local runtime check, not a simulation of camera, carrier, notifications or account-provider success.

This refresh records a fresh Linux run only. Run this command on Windows/macOS before claiming those environments have passed. Troubleshooting and bounded-command deadlines are in TESTING.md. Optional native PNG/device preview has extra Android prerequisites; ordinary app authoring does not.
