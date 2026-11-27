# Salt signing key (committed, stable)

`salt-debug.keystore` is a self-signed PKCS12 keystore that **every** Salt build signs with —
local and CI alike. It exists so that a new APK can be installed **over** an existing one:
Android refuses to update an app when the new APK is signed with a different key, which is
exactly the "App not installed" error.

| Property | Value |
|---|---|
| Store type | PKCS12 |
| Alias | `saltdebug` |
| Password | `saltdebug` |
| Validity | ~30 years |
| Key | RSA 2048, self-signed (`CN=Salt Debug Key, O=zelvior`) |

This is a debug-grade key, safe to commit — it provides **no Play Store signing authority**.
Before publishing to Google Play, replace it with your private upload key (never commit that
one):

```kotlin
signingConfigs { create("upload") { /* your private config */ } }
```

Regenerate (only if you accept that existing installs must be uninstalled first):

```bash
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout /tmp/key.pem -out /tmp/cert.pem -days 10950 \
  -subj "/CN=Salt Debug Key/O=zelvior/C=PK"
openssl pkcs12 -export -inkey /tmp/key.pem -in /tmp/cert.pem \
  -name saltdebug -passout pass:saltdebug -out salt-debug.keystore
```
