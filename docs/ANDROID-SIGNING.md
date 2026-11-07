# Android signing key

Local `debug` and `release` APKs use one persistent PKCS#12 keystore when the
ignored repository-root `keystore.properties` file is present. Copy
`keystore.properties.example`, point `storeFile` to the private keystore outside
the checkout, and keep the properties file and key out of Git.

GitHub Actions builds the release APK unsigned, then signs it in a separate step
using the same key supplied through encrypted repository secrets:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- Repository variable `SIGNING_CERT_SHA256`

The original repository keeps its existing signing fingerprint as the workflow
fallback. A fork can set its own repository variable to its own key's
fingerprint, so fork signing configuration does not replace the original
repository's signer.

Actions artifacts themselves require a GitHub sign-in to download. A fork that
sets repository variable `PUBLIC_CI_PREVIEW=true` also publishes the latest
successful push build as a rolling prerelease at
`/releases/download/ci-preview/DynamicIsland.apk`; GitHub release assets are
publicly downloadable without an account. Stable versioned releases remain
separate.

The fork key and a backup are stored in separate private directories outside the
repository on the build machine. Its password is in the ignored local
`keystore.properties` and in a private external credentials file; the same
values are configured as GitHub Actions secrets.

**Important:** this fork-specific certificate is different from the official
release certificate. Android will not install a fork-signed APK as an update to
an official-signed APK with the same application ID. Switching between those
distributions requires uninstalling the old app first (which removes its data).
Back up both the keystore and its credentials; future updates from this fork
must keep using exactly this key.
