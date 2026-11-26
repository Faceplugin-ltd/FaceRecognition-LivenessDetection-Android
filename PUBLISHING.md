# Publishing

Three artifacts go to Maven Central under the `com.faceplugin` group:

| Artifact | What it is | Size |
| -------- | ----------- | ---- |
| `com.faceplugin:face-recognition-sdk` | The engine itself (native libraries and models, arm64-v8a and armeabi-v7a) | ~78 MB |
| `com.faceplugin:face-recognition-kit` | Engine lifecycle, serialized calls, camera frames, face gallery, 1:N identify | small |
| `com.faceplugin:face-recognition-capture` | The capture coach screen | small |

Each one ships the AAR, a sources jar and a Dokka javadoc jar, which is what Maven Central validation requires.

`face-recognition-kit` declares `face-recognition-sdk` as a runtime dependency, and
`face-recognition-capture` depends on the kit, so one dependency line pulls what a customer needs.

> **The engine is published publicly.** Anyone can download `face-recognition-sdk` from Maven
> Central once it is released. Only the license key gates its use. If that is not what you want,
> drop `:face-recognition-engine` from `settings.gradle` and keep the engine on Google Drive; the
> kit's POM entry then points at a coordinate you publish elsewhere.

## One-time setup

1. **A namespace.** Register `com.faceplugin` at [central.sonatype.com](https://central.sonatype.com)
   and verify the domain. You get a username and password token.
2. **A signing key.** Maven Central requires every file to be signed:

   ```powershell
   gpg --full-generate-key
   ```

   ```powershell
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```

   ```powershell
   gpg --armor --export-secret-keys YOUR_KEY_ID | Set-Content -Encoding ascii signing-key.asc
   ```

3. **Credentials**, in `~/.gradle/gradle.properties` — never in this repository:

   ```properties
   centralUsername=<token username>
   centralPassword=<token password>
   signingKey=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
   signingPassword=<key passphrase>
   ```

   `signingKey` is the whole armoured block with `\n` for line breaks. CI reads the same names
   from the environment as `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD`.

## Releasing

1. Set the version in `gradle.properties`:

   ```properties
   faceplugin.version=1.0.0
   ```

2. Make sure `libs/facerecognitionsdk.aar` is the build you want to ship.

3. Check what would be published, without uploading anything:

   ```powershell
   .\gradlew publishReleasePublicationToLocalStagingRepository
   ```

   The files appear under each module's `build/staging-deploy/`.

4. Upload:

   ```powershell
   .\gradlew publishReleasePublicationToCentralRepository
   ```

5. Release the staged deployment at [central.sonatype.com](https://central.sonatype.com).
   It reaches `repo1.maven.org` within a few hours.

6. Tag the release so CI and customers can match the source to the artifacts:

   ```powershell
   git tag v1.0.0; git push origin v1.0.0
   ```

Pushing a `v*` tag runs `.github/workflows/publish.yml`, which does steps 3 and 4 for you when the
repository secrets are set.

## Checklist before a release

- [ ] `.\gradlew build` passes, including the unit tests.
- [ ] The demo runs on a phone against the libraries, not against stale copies.
- [ ] `faceplugin.version` is bumped and is not a version already on Maven Central — releases are permanent.
- [ ] The engine AAR in `libs/` matches that version.
- [ ] `CHANGELOG` in the parent repository has the entry.
