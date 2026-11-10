# 0.50.0 — conversation controls and message actions

References are the two supplied original-app screenshots (inbox and amber chat).
No original application executable/source is included or copied.

- Inbox rows have a trailing outline/filled themed pin button on every row.
  Durable Rust thread flags order pinned conversations first. One-line snippets
  share their row with today's local time or short month/day; older years show
  the year. Phone 12/24-hour preference is respected.
- Conversation/group header pin and mute controls use filled pin/slashed bell
  states with active paint. Actions refresh the durable state. Group flags are
  transferred by the existing Rust group binding path to the phone thread;
  notifications continue reading its muted flag via setSilent.
- Smile artwork has a proper lower smiling curve. Each message's smile opens
  the searchable Emoji window directly. Selection uses the existing SMS text
  reaction or Rust group reaction transport path, with arbitrary supported
  Unicode emoji. The long-press message menu retains its quick choices.
- Search opens a themed dialog, searches provider SMS bodies and MMS text parts
  in the selected thread and jumps to a date-bounded history window. It does
  not decode every attachment or load all messages into the feed. Search limits
  are explicit (200 matches, 20,000 MMS envelopes); narrow a query when reached.
  The down arrow restores the current feed. Group wire headers are removed from
  displayed previews. Provider-external text blobs are not indexed by this path.
- Message audio uses the same SdkSpeechController, offline Android backend and
  saved Reader voice/rate. First-tap speech waits for asynchronous voice startup;
  stop/background cancellation clears pending speech. Message identity separates
  identical texts; each selected message starts at the beginning. Tapping its
  active speaker stops playback, and blank messages cannot start speech.

Focused selection: MessageControlsTest, SdkSpeechTest, MessageLoadingTest and
shared tests. No emulator loops or broad UI reruns. Final preview text/contrast
adjustments are compiled into the APK; visual and notification/audio/carrier
behavior still needs a real phone. The engine, signed SDK packages and games
are reused unchanged. No live phone/carrier acceptance is implied.

Assembly also invalidates generated linked-resource and packaging zip caches
after resource/manifest inputs change. The verifier caught a stale 0.49 binary
manifest while source/merged XML was already 0.50; no mismatched APK is accepted.
