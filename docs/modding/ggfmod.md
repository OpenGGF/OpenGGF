# `ggfmod` creator CLI

The creator CLI is published separately from the engine APIs. Put both artifacts on
the classpath: the `OpenGGF-0.6.prerelease-jar-with-dependencies.jar` supplies the public mod API
and runtime dependencies; `OpenGGF-0.6.prerelease-openggf-mod-sdk.jar` supplies only
the CLI, converters, packager, and project templates. The SDK classifier is not a
standalone jar.

Run `docs/modding/ggfmod.ps1` on Windows or `docs/modding/ggfmod` on macOS/Linux,
passing the two jar paths followed by a command. For example:

```text
ggfmod.ps1 OpenGGF-0.6.prerelease-jar-with-dependencies.jar OpenGGF-0.6.prerelease-openggf-mod-sdk.jar init my-mod --id my-mod --package example.mymod
```

The generated project contains a canonical manifest, a compilable namespaced sample
badnik, a Genesis-exact sample sheet, and a minimal level source in the editor's exact
JSON/binary export format. `convert level` accepts only that export format. `convert
audio` validates and copies WAV/OGG bytes without transcoding. `package` creates a
deterministic jar and validates it before atomic publication.

`run <build-output>` is the only development-directory entry point. It launches the
engine with `-Dggfmod.dev.modDir=<absolute-build-output>`. The engine snapshots that
directory once into engine-owned immutable storage and never rereads the creator tree
during the session. Merely enabling test mode does not enable directory loading.
