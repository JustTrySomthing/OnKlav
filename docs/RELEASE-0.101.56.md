# OnKlav 0.101.56-rc1

Games, Walkie-Talkie, and Real Social send `? Do you have the OnKlav App ?`
when a number has yet to be verified. If no reply arrives before the timeout,
the attempt ends without sending another SMS.

- **Walkie-Talkie.** Call invitations distinguish declines from timeouts.
  When a call goes unanswered, the caller can leave an audio message by MMS.
  Duplicate carrier replies and delayed call controls no longer restart a call
  or show up as ordinary texts.
- **SMS friend games.** Incoming invites show who wants to play and offer Play
  and Decline. Expired invites allow a fresh attempt. Cancelled invites stay
  cancelled even if carrier texts arrive out of order.
- **Real Social.** Friend invitations have explicit Accept and Decline actions.
  Posts and reactions from connected friends route to Real Social. Delayed
  invites and reactions no longer undo newer actions.
- **Messages.** Known contacts display their first names in conversation
  headers. Reactions attach to the original bubble, with animated reactions
  between OnKlav users. Blocking moves the conversation to Trash. New messages
  restore archived or deleted conversations to Inbox when the sender is not
  blocked.
- **Game audio.** The game sound setting now controls playback on supported
  Android WebView versions. The volume buttons adjust media volume in games.

Update from **Settings → Check for updates** with release candidates enabled,
or install the APK from this release. The APK uses the same signing certificate
as the previous release and installs as an update.
