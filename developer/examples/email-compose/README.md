# Compose fixture (no actual sending)

Verify with `python3 developer/onklav.py verify developer/examples/email-compose`;
for denial use `--scenario developer/examples/email-compose/scenario-denied.json`.
Uses a nonexistent fixture account/identity and example.org addresses. Tests save,
reopen, review/cancel/confirm, reach pending HTTP, and reopen into a locked state.
They never authorize that pending request or simulate provider success. Do not
connect this fixture to real mail credentials. See [compose guide](../../docs/EMAIL-CONTENT.md).
