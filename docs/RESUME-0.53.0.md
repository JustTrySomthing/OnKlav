# 0.53.0 — restored reference behavior and media preparation

Reference: Library Inner-Circle-Source.zip version 235, read-only outside this
source tree. Examined StartupBranding/LaunchSession, ReactionEffects/ReactionMotion,
ReadAloudButton, WalkieDialog, the attachment chooser, media budget/transcoder
behavior and import flow. This implementation remains Kotlin/Compose with the
existing Rust storage/SDK; no reference Java or old application binary is shipped.

Important root cause: AppSdk.bootstrap installed missing bundled apps but never
updated existing ones. It now upgrades installed bundled packages to a newer
included version with the same publisher and the intersection of existing grants
and declared capabilities. Manual uninstall tombstones and revoked permissions
remain respected. Walkie guest is version 10; an older signed v8 fixture tests the
actual upgrade path and preservation of preferences, publisher and permissions.

Files opens Notes as a separate overlay above the still-composed Files screen;
this avoids switching the retained route and its launch epoch underneath an
in-flight file service/text field. SDK content is keyed by app identity, note id
participates in the opening effect, and opening exceptions become visible errors
instead of uncaught launch-coroutine failures. Back closes Notes to Files.

SwipeConversation uses one Animatable position. Releasing a drag first transfers
its exact position, then settles to closed or a complete reveal. Dragging back
closes after a small intentional movement; tapping the revealed card closes in
one tap. The menu stays anchored. Swipes still do not mutate stored data.

Speech activity is a centered circular arrow drawn in Compose Canvas. Reactions
use a 4.6-second upward floating plan: one large glyph and ten delayed smaller
ones, gentle side drift/tilt, fading and a persistent corner badge. There is no
orbital particle circle. Feed/Gallery/Starred uses folder-tab height/insets,
selected gradient/outline, label size and theme colors.

Walkie uses the same SDK panel for chat/contacts/apps. Its parent route is
transparent so the actual prior screen remains behind the scrim. The smaller
outlined dial, centered Quit, opposite-side checkbox alignment and restrained
main controls follow the supplied screenshot; extras remain in More. These
presentation hints are generic shared renderer properties, not new privileges.

On Android 12+ splash exit has no delay: the white glyph subtly grows to 1.22x
with the original decelerating motion while the background fades in 300ms, as
requested. App label is now OnKlav; identity/signature continuity remains intact.

MessageAttachments is shared by Messages and Groups. It restores photo/video
capture, microphone recording, up to three picked images/video/audio/contact
cards, and Android sharing for large files/Drive. CarrierMedia reads the selected
SIM limit (300KB fallback), subtracts draft text, existing parts and PDU overhead,
and automatically prepares media that can fit. Oversized selections get a trim
range and a duration recommendation; originals are untouched. Encoding uses Android
MediaExtractor/MediaCodec/AAC/AVC/MediaMuxer, not a Java module or server. Video
retains audio when present, orientation metadata, and checks duration and final
bytes. Cancellation, 15s codec-stall guards, a 180s operation timeout and temporary
output cleanup are included. Actual Samsung decoder/encoder/camera/provider and
carrier acceptance remains open. Unsupported codec layouts can still return an
explicit error; Android sharing does not create an automatic public Drive link.

Exact checks and APK hashes: source-verification.json / apk-verification.json.
Tests are host checks, not proof of phone visuals, hardware codec support or
carrier delivery. Games/native libraries unchanged. Full migration incomplete.
