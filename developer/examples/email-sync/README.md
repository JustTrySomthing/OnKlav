# Bounded email sync fixture

Run `python3 developer/onklav.py verify developer/examples/email-sync` and use
`--scenario developer/examples/email-sync/scenario-denied.json` for denial.
The example saves/reopens synthetic headers through real private collections,
then reaches pending host account connection. No fake OAuth or HTTP reply is
provided. Replace example provider configuration and use an actual host opaque
account handle before live requests. See [Email](../../docs/EMAIL.md).
