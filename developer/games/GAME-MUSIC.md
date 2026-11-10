# Add music to an OnKlav game

Create your track in an audio editor or use a track you have permission to distribute. Import the finished audio into your Godot game. Use the **Music** bus for soundtracks and **Effects** for gameplay sounds so players can adjust them separately.

## Play an imported track

For a custom scene, set your AudioStreamPlayer's stream to the imported audio and its bus to Music. Enable looping in the audio import settings when appropriate. Start playback after attaching the game session. Use the SDK's GameChrome to manage the soundtrack when you need its pause handling and optional track fades.

## Use the SDK's bundled-loop helper

1. Put your audio and a file named `music.json` in a directory in your project, such as `res://soundtrack`. Name the audio `music.ogg`, `music.mp3` or `music.wav`. Use 44.1 kHz audio for this bundle format.
2. For the SDK's v1 loop manifest, use 4/4 time, an integer tempo from 60 to 180 BPM, and 4 to 32 complete bars. Start the loop at frame zero. Calculate the end frame as `floor(bars × 10,584,000 / bpm)`. Export the audio to that duration.
3. Include `music.json` in the Godot export preset's filter for non-resource files. Call `set_music_bundle` on your OnKlavGame helper before attaching the session, or call it during play with an optional fade in seconds. A false result means the bundle could not be loaded.
4. Test looping, pause and the Music volume in the exported game.

An eight-bar Ogg loop at 120 BPM uses this manifest.

```json
{
  "version": 1,
  "audio": "music.ogg",
  "sampleRate": 44100,
  "beatsPerBar": 4,
  "bpm": 120,
  "loopStartFrame": 0,
  "loopEndFrame": 705600
}
```

```gdscript
if not onklav_game.set_music_bundle("res://soundtrack"):
    push_warning("The bundled soundtrack could not be loaded.")
```
