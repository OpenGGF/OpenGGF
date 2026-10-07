# Quickstart: music pack

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-music --id my-music --kind music --package example.mymusic`. Build with the jar properties shown there, edit source, and repeat the same package command.

Music packs are the smallest mod: no Java and no trust grant.

1. Copy the [music sample](../samples/phase4-gallery-music-pack/README.md).
2. Give the manifest a unique lower-case id and target one stock game.
3. Put WAV or Ogg assets under `audio/` and describe them in
   `audio/audio-manifest.yaml`.
4. Map stock music ids to local track ids in `audioOverrides`.
5. Run `ggfmod package --input <exploded-dir> --out <mod.jar>`; packaging validates.
6. Copy the jar to `mods/`, enable it, save the pending state, and restart.

Use the [full music guide](../music-packs.md) for loop frames, gain, tempo effects,
codec limits, and per-game stock-id isolation. MP3 and base-game SFX replacement are
not accepted by the current format.
