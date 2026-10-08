# Quickstart: music pack

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-music --id my-music --kind music --package example.mymusic`. Build with the jar properties shown there, edit source, and repeat the same package command.

Music packs are the smallest mod: no Java and no trust grant.

1. Open the generated `my-music` project; it includes an original WAV and audio manifest.
2. Keep its unique lower-case id and choose the target stock game in the mod manifest.
3. Put WAV or Ogg assets under `src/main/resources/audio/` and describe them in
   that directory's `audio-manifest.yaml`.
4. Map stock music ids to local track ids in `audioOverrides`.
5. Repeat the Maven package command and validate `target/my-music-mod.jar`.
6. Copy that jar to `mods/`, enable it, Apply the pending state, and restart.

The [music sample](../samples/phase4-gallery-music-pack/README.md) is an alternative
checkout reference; follow its asset-generator instructions before manual packaging.

Use the [full music guide](../music-packs.md) for loop frames, gain, tempo effects,
codec limits, and per-game stock-id isolation. MP3 and base-game SFX replacement are
not accepted by the current format.
