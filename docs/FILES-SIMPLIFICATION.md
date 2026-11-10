# Files and provider simplification — 2026-11-02

Owner-requested source refinement. No APK was requested or produced. This supersedes the GMX retirement/migration design and the individual Android folder connection workflow in earlier notes.

## Email and Cloud Files

Email offers Yahoo, Google and Private domain with password/app-password sign-in and official setup help. GMX presets, detection, resumable retirement, credential/cache purge code and its dedicated test have been removed. Outlook/iCloud presets, Microsoft enrollment, OAuth handlers/account fields, token authentication and the Google authentication dependency are removed from Email. The signed Email package advances to version 4 using the original publisher; obsolete Microsoft/Google OAuth HTTP origins are removed. The host’s existing network-access grant is retained with a valid signed policy for the two current webmail domains; this is required by the SDK manifest contract and adds no OAuth flow. Generic SDK capabilities used by third-party apps remain separate.

Cloud Files offers only pCloud US/EU and Koofr. Google Drive sign-in/sharing code and tests are deleted, and 4shared/legacy provider editor choices are removed. Provider-neutral Backups and document recovery remain active; their helpers no longer use Drive names. The HTTP storage transport remains necessary for pCloud/Koofr and is not presented as a user setup choice.

## Shared media folders

Automatic camera, message, image editor, browser and app media imports use Images, Audio or Video directly according to MIME type. There is no automatic per-app media folder. Generic browser downloads infer media type from the filename when the server reports an unknown type. Explicit user-selected destinations still work.

A one-time index change consolidates existing media from automatic app folders, retaining content IDs, owners and bytes. Duplicate filenames are made unique. User-created folders, protected ancestors and Trash are preserved. Empty automatic media containers are removed. Documents retain their organization.

Image/audio/video attachment requests open the corresponding OnKlav root immediately. Mixed/all-file requests start at Files Home; ordinary document saves start in Documents. Email, Messages and SDK pickers share this behavior.

## Modern Files browser

Files Home uses “OnKlav System Files” and “Android Files.” Both OnKlav and Android listings have a persistent top list/thumbnail toggle, bounded image and video previews, folder cards, selection feedback and pencil menus on mutable entries. The existing signed Files controller continues to enforce its operations, confirmations, paging and capability grants. Writes to read-only selections are rejected before parsing save-only metadata, avoiding a confusing JSON error. Previews also check live permissions and folder locking. Read-only Android preview/open operations do not rewrite the saved directory index, preventing a late thumbnail read from discarding a newer listing.

Android Files immediately shows Photos, Images, Audio, Video, Documents, Downloads, APK and Other. Photos collects camera images; Images collects other images. Downloads and Other provide ordinary folder navigation. Android may require its one-time file-access permission; no individual folder chooser is part of this interface. Android private application directories remain unavailable under Android's own policy.

Android operations use broker-owned opaque tokens, fresh permission checks, canonical shared-storage paths and no symlink traversal. Rename/copy/create/compress do not overwrite existing files. Copy/ZIP work and preview decoding are bounded. Android deletion remains explicitly confirmed and only removes files or empty folders. Folder sharing creates a bounded temporary ZIP. OnKlav's ordinary Trash, move/copy and recovery flows remain available.

## Qualification

See files-simplification-verification.json and the referenced logs for actual results. Verification covers source-matched Linux/ARM64 builds, storage/SDK checks, Android host checks and native Compose renderings. Physical phone, removable storage and live carrier/provider acceptance remain separate; these source changes do not update an installed phone automatically.
