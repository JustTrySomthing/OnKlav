# New arrivals return deleted conversations to Inbox

Every new unblocked SMS/MMS returns an archived or trashed conversation to Inbox, including after permanent deletion of its old messages. Blocking moves the selected conversations and matching direct threads to Trash in the same state write as the block. New messages from blocked senders go to Trash. Unblocking leaves the thread in Trash until a new unblocked message arrives or the user restores it. Folder changes preserve pin and mute settings. Invalid provider thread IDs do not create metadata.

MessageNotifications.picturePending now checks the block list too, removes a preceding alert when a sender is blocked, and avoids a new download/retry alert. Conversation alerts already check the same block state.

21 unique production host regressions passed: 9 receiving cases, 1 blocked-picture alert case and 11 MMS recovery cases. The separate exact ChatStore/Rust harness passed the same 9 receiving cases and is not counted twice. Final production Kotlin/test compilation and runtime byte matching passed. No native source, signer, shared voice or game change; no APK created.

This fixes normal receiving. A separate crash/storage-failure window between provider insertion and durable folder filing was found in the deep review and remains open (A02). See APP-DEEP-REVIEW-2026-10-30.md and app-audit-verification.json. Physical phone/carrier acceptance is not claimed.
