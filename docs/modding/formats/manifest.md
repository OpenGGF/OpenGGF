# Mod manifest v1

Every jar contains `META-INF/openggf-mod.yaml`. The authoritative accepted field set
is `ModManifestParser.ROOT_FIELDS` in
[`ModManifestParser`](../../../src/main/java/com/openggf/mods/ModManifestParser.java),
and input limits come from
[`ModInputLimits`](../../../src/main/java/com/openggf/io/ModInputLimits.java).

Required fields are `formatVersion`, `id`, `name`, `version`, `authors`,
`description`, `engineApiRange`, `type`, `dependencies`, `audioOverrides`, and
`artOverrides`. `baseGame` is required only for `patch`; it is forbidden for
`standalone`. `entrypoint` is syntactically optional, but validation requires it when
the jar contains classes. `insertAfter` is valid only with a supported patch-game
stock anchor. `patternWindows` is optional.
Unknown, duplicate, null, alias/merge, shorthand dependency, and alternate-union
shapes are errors.

Use the checked [music](../samples/phase4-gallery-music-pack/META-INF/openggf-mod.yaml),
[reskin](../../../src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml),
[badnik/zone](../../../src/test/resources/mods/sample-mod-src/project/src/main/resources/META-INF/openggf-mod.yaml),
[character](../../../src/test/resources/mods/sample-character-src/project/src/main/resources/META-INF/openggf-mod.yaml),
and [standalone](../../../src/test/resources/mods/sample-standalone-src/project/src/main/resources/META-INF/openggf-mod.yaml)
manifests. They exercise the Mod API 0.7 range across patch/standalone,
music/art maps, entrypoints, and progression.

Manifest format version `1` is independent of the engine Mod API version. Current
code publishes Mod API `0.7.0`; maintained manifests use
`>=0.7.0 <0.8.0`.
