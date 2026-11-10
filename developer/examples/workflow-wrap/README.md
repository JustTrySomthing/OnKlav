# Workflow broker fixture

Synthetic MIME bytes are created through actual private blob services and parsed
by the production MIME broker. The primary scenario stops at authenticated HTTP
upload pending with a nonexistent account; scenario-export.json stops at native
export pending. Neither supplies fake Android/provider success or sends email.
Disposable signing keys/packages are discarded by `onklav.py verify`.
