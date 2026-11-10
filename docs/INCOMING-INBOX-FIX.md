# New arrivals return deleted conversations to Inbox

The prior Archive fix deliberately excluded Trash. The owner now requires every new unblocked SMS/MMS to appear in Inbox even after the old conversation was deleted. ChatStore.incomingThread now restores both folders and preserves pin/mute settings. New blocked arrivals in Inbox/default are retained in Archive; blocked Archive/Trash remain unchanged. Invalid thread IDs do not create metadata. Unblocking allows the next arrival to return normally.

MessageNotifications.picturePending now checks the block list too, removes a preceding alert when a sender is blocked, and avoids a new download/retry alert. Conversation alerts already check the same block state.

21 unique production host regressions passed: 9 receiving cases, 1 blocked-picture alert case and 11 MMS recovery cases. The separate exact ChatStore/Rust harness passed the same 9 receiving cases and is not counted twice. Final production Kotlin/test compilation and runtime byte matching passed. No native source, signer, shared voice or game change; no APK created.

This fixes normal receiving. A separate crash/storage-failure window between provider insertion and durable folder filing was found in the deep review and remains open (A02). See APP-DEEP-REVIEW-2026-10-30.md and app-audit-verification.json. Physical phone/carrier acceptance is not claimed.
