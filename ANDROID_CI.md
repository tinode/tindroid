# APK builds and downloads

The workflow builds APKs and attaches them to [GitHub Releases](https://github.com/tinode/tindroid/releases). Default-branch pushes create prereleases; tag pushes attach APKs to a release. Publishing a release in GitHub's UI attaches APKs to its tag. Manual runs also publish: branch runs create CI prereleases, and tag runs attach APKs to that tag. Pull requests provide Actions artifacts without publishing.

## Maintainer signing

In **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64 of your existing release keystore |
| `ANDROID_RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | Signing key alias |
| `ANDROID_RELEASE_KEY_PASSWORD` | Signing key password |

Encode the keystore locally:

```sh
base64 < release.keystore | tr -d '\n'
```

Use the existing app signing key to preserve update compatibility. Signing secrets are used only outside pull-request builds. Without them, push/manual builds use a temporary debug key; these are test builds and may require uninstalling the previous APK. Debug APKs always use debug signing. PR release APKs remain unsigned.

The workflow uses placeholder Firebase configuration; push notifications require the project's real `app/google-services.json`.
