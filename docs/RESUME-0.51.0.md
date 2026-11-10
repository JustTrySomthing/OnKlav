# 0.51.0 — retained loading, paged media and screenshot polish

User screenshots: original Walkie offline panel, reaction animation/badges and
black wand/stars with white tip. The original vector asset is unavailable;
the wand was recreated from the supplied screenshot, with its circle themed.

Production paths:
- MessengerMemory is activity-owned. Messages/Groups retain rows and snapshots
  across tab composition. Observers remain in viewModelScope. Phone changes
  invalidate provider data; SMS changes no longer clear the contact directory.
- Inbox reads 40 threads per page, with extra pinned rows resolved explicitly;
  its continuation cursor follows the unpinned provider page. Groups query only
  their saved phone threads instead of scanning 1,000 conversations.
- Chats initially query 20 messages. Older pages append on reaching the top or
  tapping Load Earlier; timestamp/id boundaries handle equal-date SMS/MMS.
  Newest refreshes merge with retained older rows. Raw group envelopes remain
  separate from displayed text so reaction targets survive incremental paging.
  Warm windows retain up to 200 messages for 12 threads. Recovery runs once per
  retained screen; it is not repeated for every tab visit.
- Gallery reads at most 48 MMS envelopes or 24 media messages per page, retains
  existing content and offers/automatically requests older pages near its end.
  Viewer pages use an immutable snapshot, preventing gallery refresh/shrink from
  changing pager indices. Only the active video/audio page creates a player.
  Images decode at 1,280px, GIFs at 768px (thumbnails 256px), with two concurrent
  decoders; released views clear drawables. Player callbacks detach on release.
  No device crash log was available; the exact handset crash is not reproduced.
- Media viewer actions move into More, with full-screen/exit controls. System
  bars and viewer chrome hide in full-screen mode; navigation remains available
  after exiting. Phone decoder/player/rotation acceptance remains open.
- Speech uses a rotating circular-arrow indicator while starting/playing and
  stops on the same message button. Reader voice/speed preferences are retained.
- Standard textual SMS reactions and existing Rust group reactions show corner
  badges. Newly observed reactions animate once, then leave the badge. Unmatched
  text remains visible. Native wire/carrier acceptance is not inferred from this.
- Contacts retain their list during refresh and use photo_uri or contact photo
  fallback when thumbnail metadata is absent. New groups return to their list.
- Shared popup easing is retained with shorter 230ms entrance/180ms exit.
- Walkie signed SDK package v8 hides recipient-entry fields after selection,
  centers the dial and moves offline recording behind its own toggle. Existing
  recording, clip history, auto-play/send and connection operations are retained.
  The shared renderer supports the optional primary button visual hint and
  contacts icon; this adds no capability or permission.

Checks: 27 focused paging/reaction/speech/media/shared checks, then one signed
Walkie guest presentation check during APK assembly (28 total). No full UI suite
or emulator loop. The existing Groups UI fixture now clicks the saved group
explicitly; it was not rerun as a handset visual check. Native JNI and games are
source-matched/reused; only the changed Walkie Wasm package is rebuilt.

Handset work remains: measure cold/warm tab/chat entry; repeated gallery/GIF/
video swiping, full-screen/audio lifecycle; Samsung contact photos; animation/
wand comparison; incoming reactions and two-phone Walkie/carrier acceptance.
Games remain deferred and the full migration remains incomplete.
