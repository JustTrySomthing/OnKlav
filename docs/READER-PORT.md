# Read Aloud

Read Aloud is a signed internal SDK app using the public `speech` and `speech_settings` widgets and `speech.output` capability. Messages and eBooks use the same host speech engine, included model and shared reader preferences. No app bundles its own engine or voice pack.

The included Kitten Nano v0.8 FP32 choices are Male (speaker 2, default) and Female (speaker 0), using the owner-requested labels. Both upstream speakers are male. No system TTS setup, voice download or network synthesis is required. See [shared speech](NEURAL-SPEECH.md) for implementation, lifecycle and licensing.

The editor supports clear/paste, position following, previous/next, seek, pause/resume, voice/rate settings and preview. Position feedback is estimated from played PCM. Built-in Read Aloud/eBooks use foreground media controls for screen-off reading; external SDK apps remain foreground-scoped. Revocation, disposal and audio-owner changes cancel speech.

Earlier port milestones are retained under docs/history/pre-0.100.24/docs-READER-PORT.md. Historical phone-TTS requirements no longer apply. Physical Samsung/Bluetooth, long-reading latency, battery and endurance acceptance remain open.
