# AudioBooks phone acceptance

Use the updated OnKlav APK. AudioBooks is an app inside OnKlav alongside Radio and
Podcasts. Its declared OnKlav capabilities are granted on first installation;
Android's file picker still asks which files/folder to open.

1. Add an MP3 and an M4B, then add a folder containing multiple chapter files.
   Check chapter order, import title/author, rename, bookmarks and reorder.
   Original files should stay untouched.
2. Listen partway through multiple books. Pause, close/reopen OnKlav and restart
   the phone. Resume should retain each book's chapter, position and speed, with
   a five-second rewind. A save failure must be visible, not silently ignored.
3. Check seek/skip, embedded and file chapter changes, sleep timers and end of
   chapter sleep. Check automatic advance and final finished state.
4. Browse/search LibriVox by title/author, open a recording, stream its chapters,
   then download it. Cancel partway through the next chapter and resume. Completed
   chapters should not download again. Switch to airplane mode and seek/play.
5. Check audible playback with screen off and OnKlav in the background, Bluetooth
   and wired headset buttons, unplug handling, notifications and audio focus
   during another app's playback. AudioBooks shares the host's single player.
6. Remove a book; its copies/progress/bookmarks should disappear, while originals
   stay untouched. Remove app data and add a new book; its position must save.
7. Revoke AudioBooks capabilities in OnKlav; active playback/download/source reads
   must stop. Restore them and reopen; retained books/progress should remain.

Development evidence is in audiobooks-app-verification.json. Host fixtures and
screen checks passed; they do not certify actual Android decoders, audible output,
Bluetooth behavior or live LibriVox service availability. LibriVox GET returned403
in the build environment. No physical phone or live-provider acceptance claimed.
