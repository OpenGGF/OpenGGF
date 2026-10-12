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
stock anchor. `patternWindows` and `composition` are optional.
Unknown, duplicate, null, alias/merge, shorthand dependency, and alternate-union
shapes are errors.

Use the checked [music](../samples/phase4-gallery-music-pack/META-INF/openggf-mod.yaml),
[reskin](../../../src/test/resources/mods/sample-reskin-src/META-INF/openggf-mod.yaml),
[badnik/zone](../../../src/test/resources/mods/sample-mod-src/project/src/main/resources/META-INF/openggf-mod.yaml),
[character](../../../src/test/resources/mods/sample-character-src/project/src/main/resources/META-INF/openggf-mod.yaml),
and [standalone](../../../src/test/resources/mods/sample-standalone-src/project/src/main/resources/META-INF/openggf-mod.yaml)
manifests. They exercise the Mod API 0.7 range across patch/standalone,
music/art maps, entrypoints, and progression.

Manifest format version `1` is independent of the engine Mod API version. The
current unpublished candidate is Mod API `0.7.0`; maintained manifests use
`>=0.7.0 <0.8.0`.

`composition` describes optional application order and explicit incompatible or
exclusive contributions. Omit it to preserve ordinary enablement order.

```yaml
composition:
  before: [other-owner]
  after: [shared-art-owner]
  conflictsWith: [incompatible-owner]
  exclusiveContributions: [startup-scene, "art:signpost"]
```

Each field is optional and contains a bounded list of unique strings. Owner lists
use manifest IDs; self references reject. `before` and `after` affect the same
actual data and compiled patch order. Missing, disabled or blocked optional
neighbors add no edge. These edges do not grant class visibility or cause a
neighbor to be disabled when a callback faults. Required `dependencies` retain
those separate hard dependency semantics. Combined ordering cycles produce
`ORDERING_CYCLE`; incompatible enabled owners produce `MOD_CONFLICT`, with sorted
participants.

Exclusive claims accept `startup-scene`, `display-width`, `game-start`,
`art:<stock-key>` or `audio:<nonnegative-stock-id>`. Claims require the owner's
actual corresponding contribution. Unknown/game-inapplicable stock art keys and
unused claims reject. Competing successful contributions with any exclusive
claim reject every participant and required dependents, rather than silently
choosing a winner. Stock data conflicts are rejected before audio preparation;
compiled scene/display/start claims are checked after successful registration
transactions and before publication. Without exclusivity, later application wins
for these singular targets. Owner-qualified zones, objects, character identities,
service bundles and decoded patches compose additively. See
[content composition and runtime services](../content-mods.md) and the
[two-act worked campaign](../guides/two-act-campaign.md).
