# OnKlav 0.101.52-rc1

Further repairs for carrier connections and game startup recovery:

- Walkie-Talkie recognizes incoming +1 sender formatting for an existing
  ten-digit US recipient on the same SIM, while retaining native session checks.
- One verification reply advances all pending game invitations for that friend.
- Contact numbers containing dots work for game and Real Social invitations.
- Real Social recognizes carrier-converted protocol separators for requests,
  posts and media, with existing sender and friendship-token validation.
- A stalled included-game loader can be closed or replaced by another game
  before session initialization, preserving previously saved progress.

Both participants should update before retrying. Automated host tests and APK
verification do not replace physical-device and two-phone carrier acceptance.
