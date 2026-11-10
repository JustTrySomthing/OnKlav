# Email setup and Cloud Files sharing

User-requested refinement, 2 November 2026. No APK requested or built.

Email now offers Yahoo, Google and Private domain. The later owner-requested complete GMX removal is described in GMX-REMOVAL.md. Yahoo/Google keep the native secure IMAP/SMTP transport and device-encrypted credentials, using app passwords. Their Setup directions show short steps, official help links and account-settings links. The Google OAuth selector, authorization launcher and Microsoft authorization offer are removed from the form. Historical encrypted records/token transport remain parseable; reconnecting a legacy token account requires a new password, not a silent migration. Custom-server behavior is preserved; GMX presets, UI and existing local accounts are retired.

Cloud Files enrollment offers pCloud US/EU and Koofr, with free-plan signup links and sign-in help. WebDAV remains the transport implementation, but is absent from UI copy. Existing other-provider accounts remain readable/editable. Provider restrictions and encrypted pCloud Crypto folders are described without technical transport terminology.

Messages and Groups share MessengerPane/MessageAttachments. Paperclip → Share via Cloud Files replaces Contact card. The native cloud workspace opens over the existing draft. Select a file already online, or upload one via OnKlav Files then select it. Confirm Create sharing link, review/copy the result, then Add link to message. Neither choosing nor adding a link sends the message. Draft text is read at callback time, links append on a new line, and over-budget drafts retain the link with a copy route. Existing contact-card reception remains supported.

Sharing uses a separate fixed-origin client: pCloud POST getfilepublink at api.pcloud.com or eapi.pcloud.com (selected by account region); Koofr uses its primary mount and POST /api/v2/mounts/{id}/links. Only files under the selected account root are accepted. Credentials use POST fields for pCloud and Basic headers for Koofr, never a query string or returned sharing URL. HTTPS, normal certificate checks, no redirects/cookies/automatic write retries, bounded response sizes, cancellable requests and strict returned-link validation are retained. Koofr-generated link passwords are included with the sharing link, never confused with the account password. Missing protected-link passwords and provider refusal remain visible errors; no sharing policy is weakened. Cancellation/interrupted writes can leave a provider-side link: inspect/remove it in the provider account before retrying. Old SDK/native/game/signing artifacts remain unchanged.

## Provider references checked

- Google: https://support.google.com/accounts/answer/185833 — app passwords require two-step verification; some managed/security-key/Advanced Protection accounts do not offer them.
- Yahoo: https://help.yahoo.com/kb/SLN15241.html — Account Security → External connections → Create app password (wording may also be Generate app password).
- Free plans: https://www.pcloud.com/ and https://app.koofr.net/signup . No fixed storage allowance is promised.
- pCloud sharing/authentication: https://docs.pcloud.com/methods/public_links/getfilepublink.html and https://docs.pcloud.com/methods/intro/authentication.html .
- Koofr primary-mount schema: https://github.com/koofr/go-koofrclient/blob/master/api_scheme.go ; mount endpoint: https://github.com/koofr/go-koofrclient/blob/master/client_mount.go .
- Koofr app-password/HTTP Basic authentication: https://github.com/koofr/java-koofr .
- Koofr-compatible API sharing endpoint/schema: https://storage.rcs-rds.ro/help/developers/api . Existing Koofr app-password/Basic integration is reused for its fixed API origin; live account acceptance remains required.
- Koofr limits: https://koofr.eu/help/share-files-and-folders/i-have-a-free-account-and-cannot-share-certain-files-why/ .

## Validation

All 47 selected/shared host checks passed: 30 app tests executed and 17 unchanged shared checks reused by Gradle. Production and all test-source compilation passed, and the runtime bundle matches compiled Kotlin. Twelve new regression checks cover provider API calls/security/cancellation, email app-password forms/help callbacks and the paperclip choice; enrollment expectations were updated. Cloud welcome and Koofr setup renders were visually inspected. A test-fixture compile omission and an intermediate generated-cache snapshot error were resolved; initial logs are preserved. Final results and source hashes are in email-cloud-sharing-verification.json. Fake protocol fixtures do not establish live provider acceptance or physical Samsung behavior.
