# Group filing, incoming discovery and unread status

2026-10-30 source fix. Latest delivered APK remains0.100.26-rc1/code135; no new APK built.

## Problems and fixes

- Group saving reused Android's existing thread for the recipient set. A newly created group could therefore inherit Archive/Trash. New explicit group creation now sets Inbox in the same Rust state transaction, preserving other thread flags. Renaming/editing an existing group does not reset its folder. Creating while viewing Archive/Trash returns the list to Inbox.
- Only locally saved recipient sets were excluded from Messages and listed in Groups. Incoming group MMS created by somebody else could stay in Messages. The phone thread's canonical participants now determine discovery: two or more other people means a group. Exclude the configured own number and normalize duplicates. Register a stable thread-derived identity, retain a matching unsent group's identity, and never overwrite a saved name. Use a shared identity lock to prevent parallel discovery from replacing user changes.
- Discover received groups during arrival filing, while loading Messages/Groups and when following a notification. Already-present provider history is reconciled too. Discovery itself never moves archived/deleted history into Inbox. Only a new unblocked arrival does that.
- Direct-message provider paging excludes registered group threads before applying its page size, so a page of group conversations cannot hide older direct conversations. Group Inbox ordering uses recent provider/arrival activity after pinned rows.
- Real SMS and MMS filing now uses the existing durable Rust arrival receipt. A fresh receipt sets unread as well as Inbox; an identical replay cannot override a later read/archive action. MMS persists its local filing receive timestamp before the state action. Notification-only refreshes do not rewrite unread status. Blocked arrivals remain outside Inbox, including Trash where applicable; mute/pin and blocked notification handling remain.
- Group rows and single-conversation reads now use the same metadata/provider unread precedence as Messages, including last arrival time. This prevents a stale read flag from masking a new MMS with an old/same-second sender timestamp. Existing bold/background/dot indicators and manual Mark read/unread controls now reflect that resolved state.
- Opening a conversation reacts to message identity and arrival receipt, not only date; messages sharing an MMS timestamp can still be marked read while being viewed. Saving unrelated group settings does not rewrite unread unless the user actually changes that control. Explicit read/unread changes use the same repository/provider path as Messages.
- When an unsent group binds to the first incoming provider thread, preserve its custom name, pin and mute settings alongside the new arrival's Inbox/unread state.

## Bounds and compatibility

Received identities support up to256 normalized phone participants in state/metadata backup. Explicitly creating a new recipient set retains the existing2–10 member rule; existing larger received sets can be renamed without changing membership. Outbound carrier limits and the existing MMS decoder limits are unchanged. This is SMS/MMS phone-provider routing, not support for proprietary RCS groups.

Discovery is bounded to500 group threads, matching the existing state cap; it uses a filtered provider query and a bounded OEM fallback scan. No Android messages are deleted, copied or silently marked read during discovery. Original folders remain for historical discovery; receipt handling is what resurfaces new arrivals. Existing group aliases are retained rather than destructively merged. Changes preserve all previous editor, speech, Compress, Format Swap, Power/setup, Photo and messaging work.

## Verification

See incoming-groups-verification.json and incoming-groups-checks/. 23 Rust messaging checks and27 distinct host checks passed (main26, then final6 including the added binding case;21 unchanged SMS/MMS regressions retained from the main pass). Production/test Kotlin compilation passed. The Rust messaging checks passed and both Linux host JNI and ARM64 working JNI were rebuilt from the changed source, with16KB Android segment alignment. Released JNI/provenance and signing identities remain unchanged. Current-source host checks cover discovery versus direct/own-number conversations, idempotency/name retention, Inbox creation, unread/read precedence, Archive/Trash restoration, duplicate replay, blocked/mute/pin behavior and first-arrival binding. Existing SMS receipt and MMS recovery/notification regressions were also run. These checks use a synthetic phone-provider fixture and real Rust state; physical carrier/device acceptance remains separate.
