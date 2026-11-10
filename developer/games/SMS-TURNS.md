# SMS turn games — SDK0.6 / host165

Developers choose single-only games or single + turn_sms games. No platform-owner approval, enrollment or server is required. Users install signed .onklav-g files themselves. Both friends must have a compatible OnKlav host; custom games require the exact same signed file. The native host rechecks installed identity before sending. Package signatures establish update identity, not trusted SMS identity or cheat-proof gameplay.

## User flow

Games → Play a Friend opens contacts/search or a mobile number. Messages magic wand → Games supplies that conversation's peer to the same flow. Existing active games can be reopened there. Cats vs Dogs also offers Cats/Dogs selection. Air Hockey is single only and is excluded from the friend picker.

After the user chooses a contact or uses Enter number → Invite (and chooses a side where needed), the host opens the connection dialog automatically and sends the readable question **? Do you have OnKlav App ?**. A compatible OnKlav automatically answers its versioned capability check. The inviter then sends a game invitation; the recipient explicitly Accepts or Declines in Games. Opening accepted play starts the actual friend session. The compatibility/invitation window expires after65 seconds. Expiry is not acceptance. Each title has its own session; catalog and Messages entries resolve only that title. Different titles can retain active friend matches/invitations. A second live match for the same title is blocked; switching titles does not send Quit. Saves are bound to the match ID; see SAVES.md. Cancel closes a pending invitation durably; Try Again after failure starts a fresh verification window. The contact picker supports hold-to-reverify independently of game invitations. Incoming invitations and new turns have private notifications opening Games.

Setup must make OnKlav the default messaging app with SEND_SMS/RECEIVE_SMS. Blocked numbers are refused through both OnKlav and Android checks. Carrier/SIM charges may apply. No app-owner approval is involved. This recreates the original readable-question → capability → invitation → acceptance sequence; it is a new OnKlav protocol and does not interoperate with the retired Inner Circle wire format.

## Shared GDScript channel

```gdscript
const TurnChannel = preload("res://turn_channel.gd")
var channel = TurnChannel.new()
func _ready():
    add_child(channel)
    channel.received.connect(receive_move)
    channel.sent.connect(apply_own_move)
    channel.changed.connect(refresh_controls)
# channel.enabled indicates an accepted host friend session.
# channel.host chooses player0; the other peer is player1.
# channel.send("F3") stages a bounded application move, not a raw SMS.
# Apply your own move only when sent(data) confirms durable staging.
# Save {"model":..., "network":channel.snapshot()} atomically afterwards.
```

The turn template contains a complete solo/computer and friend example. `received(data)` delivers contiguous peer events; `sent(data)` delivers durably staged own events, even when recovered after reload. `changed` refreshes status/pending/error. Check `enabled`, `status == "active"`, `pending` and `sending` before enabling local moves. `retry()` explicitly retries existing frames; it must never invent a new move. `snapshot()` returns match/rx/tx; validate `restore()` alongside your model before mutation. Match IDs separate independent games. Save after every accepted event. Pause freezes simulation/animation; journaling received turns remains possible while suspended.

Payloads are1–64 ASCII characters matching `[A-Za-z0-9_.,:-]+`. Use compact versioned deterministic messages. Every received move remains untrusted: validate actor, turn, cell/ranges, legal state transitions and duplicate effects before applying. Reject impossible updates visibly, preserve the old save, and let the user return/quit. Never accept executable resources, filesystem paths or URLs as turn commands. Do not put contact information or secrets in payloads.

## Transport behavior and limits

The native broker owns recipients, SIM selection, consent, wire framing, storage and sends. Web game code receives only match ID/host role, bounded events, its own history, pending state and errors. There is no Android JavaScript object, arbitrary phone number/SMS body bridge, internet multiplayer or device/file/account access. Both native and custom game HTML remain sandboxed. A compromised game can submit only bounded updates to its already accepted peer; declarations alone do not guarantee good game behavior.

Frames are one ASCII SMS, at most140 characters: `!OKG1|type|32hex-match|sequence|data`. Types: I invite, A accept, D decline, N missing exact package, Q quit, X move, K acknowledgement. Capability answers use !OKA1. Built-in protocol tokens identify compatible rule revisions; custom tokens identify the exact signed package SHA256. Control frames never become ordinary user message bubbles.

Native state uses an OS-locked AtomicFile shared across main/game processes. Moves are staged before sending, with monotonic per-peer sequence and acknowledgement. Only one local move may await acknowledgement. Up to8 out-of-order frames are buffered; duplicate frames acknowledge without repeating a game event. Unexpected peer/match/sequence is refused. Failed/ambiguous sends require explicit Retry; callbacks cannot regress acknowledged state. This does not promise carrier delivery or ordering. Game-level request/reply messages should tolerate pending acknowledgements.

Bounds:90 move sends/receives per minute per match; at most512 received events per match,32 retained match records,512KiB total journal. Completed records can be removed in Games. Compatibility auto-answers are throttled, capped120 per day. Use this for modest turn-based games, not real-time or unbounded chat. SMS is neither encrypted nor cryptographically authenticated end to end; phone/SIM identity is not a cryptographic player identity. Do not claim anti-cheat or confidential communication.

Return/Android Back checkpoints and pauses without sending Quit. Reopening the current game offers New/Resume. New clears/quits the old match and returns to the original game-specific start dialog; Resume restores it. X uses the standard green Resume/red Quit confirmation. A peer Quit must disable further moves and explain why; delivery failure must not award victory. The Games session card also provides a confirmed Quit recovery route.

## Qualification

Exercise two independent phones and SIMs: accepted/declined/expired invitation, wrong/missing package, blocked peer, airplane mode, failed/duplicate/reordered updates, Retry, system Home/Back, process loss, New/Resume/Quit, invalid payload and peer Quit. Automated broker/model/browser tests supplement these checks; they do not establish carrier delivery, handset audio or physical-device performance.
