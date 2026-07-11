# Mod Support Format + Hostile-Input Contracts

**Date:** 2026-07-10
**Status:** authoritative cross-phase contract
**Branch baseline:** `next`
**Applies to:** mod-support Phases 0–4

## 1. Asset roots and paths

`com.openggf.io.ModAssetRoot` is a sealed, closeable abstraction with `JarRoot` and `DirectoryRoot`
implementations. It opens bounded streams by normalized forward-slash entry name.
Absolute paths, backslashes, empty segments, `.`/`..`, NUL, duplicate normalized
names, and case-fold collisions are rejected. Directory roots resolve real paths and
must remain under the declared project root after symlink resolution. Jar roots never
extract entries to disk. Limit-bearing factories are
`jar(Path declaredRoot, Path jar, ModInputLimits limits)` and
`directory(Path declaredRoot, Path directory, ModInputLimits limits, DirectoryAccess access)`.
The jar two-argument convenience overload delegates with `ModInputLimits.production()`;
there is no unrestricted directory convenience overload. `DirectoryAccess.PRODUCTION`
is rejected, while only explicit `DEVELOPMENT` and `TEST` access may construct a
directory root. Both root types resolve the
target real path beneath the declared root before opening. Each root retains its
limits, rejects a `readBounded` request above `limits.maxAssetBytes()`, and enforces
the lower of the requested and injected caps. The factory caller owns `close()`.

Threat/lifecycle boundary: production discovery accepts packed jar roots only. Jar
roots copy the contained, non-symlink jar into an engine-owned private temporary file,
then validate/read only that immutable snapshot. Directory roots are available only
to explicit `ggfmod run` development and tests; the creator-controlled source tree is
trusted during snapshot construction, copied with `NOFOLLOW_LINKS` plus before/after
identity/size checks into an engine-owned private temporary directory, and never read
again afterward. Concurrent malicious mutation of that explicitly trusted dev tree
during snapshot construction is outside the threat model; mutation after construction
cannot affect the session. `close()` closes archives and removes only the verified
engine-created snapshot tree/file. This preserves Windows dev mode without pretending
portable Java offers handle-relative race-proof traversal on every provider.

Directory snapshot construction enforces entry count, per-name UTF-8 bytes, aggregate
entry-name bytes, per-file bytes, and actual aggregate validation bytes incrementally
inside the copy loop. Each limit is checked before the corresponding temporary-file
write or additional snapshot growth; validation is not deferred until after the source
tree has already been copied.

Manifest ids match `[a-z0-9][a-z0-9-]{0,63}`. Registry-local object/art/track/SFX/
animation names match `[a-z0-9][a-z0-9._/-]{0,127}`, with no empty, `.`, or `..`
segment. Persisted keys are tagged `(modId, localName)` values; display form is exactly
`modId:localName`. No Unicode/case normalization occurs, exactly one display colon is
allowed, UTF-8 length is capped at 192 bytes, and owner mod id must equal the declaring
manifest id. Constructors and all parsers share one validator. Tests cover malformed,
case mismatch, owner mismatch, colon ambiguity, and binary/JSON round trips.

Production limits are immutable constants in one `com.openggf.io.ModInputLimits`
type. Tests do not mutate globals: bounded readers already accept an explicit maximum,
and parsers/converters accept an immutable limits value whose production default is
the constants below; tests may construct only equal-or-lower values.

| Item | Limit |
|---|---:|
| manifest or metadata file | 1 MiB |
| jar file on disk | 1 GiB |
| entries per jar | 16,384 |
| one entry name / aggregate entry-name bytes | 512 bytes / 4 MiB |
| one compressed asset entry after inflation | 64 MiB |
| total bytes read from one mod during validation | 512 MiB |
| decoded audio per track | 10 minutes / 256 MiB PCM, whichever comes first |
| decoded one-shot SFX | 30 seconds / 32 MiB PCM each; 16 simultaneous voices |
| decoded-audio cache per process | 512 MiB |
| accepted audio sample rate / channels / gain | 8,000–192,000 Hz / 1 or 2 / 0.0–4.0 |
| mod jars / repository bytes inspected per boot | 1,024 / 8 GiB |
| image dimensions / pixels | 8192 × 8192 / 33,554,432 |
| patterns / frames / pieces per sheet | 65,536 / 4,096 / 65,536 |
| level map dimensions / cells | 4096 × 4096 / 4,194,304 |
| objects / rings in one level | 65,535 each |
| XML nesting / attributes per element | 64 / 128 |
| YAML/JSON nesting / collection entries | 32 / 10,000 |
| YAML aliases / document code points | 0 / 1,048,576 |
| metadata string / numeric token | 65,536 chars / 20 digits |

The jar file itself resolves by real path beneath the declared mod root (a symlinked
jar escaping the root is rejected). Before any manifest is read, the complete central
directory is validated for entry count, names, normalized/case collisions, declared
sizes, and aggregate budgets. Declared zip sizes are checked before reads and a counting stream enforces the same
limit when sizes are absent or dishonest. Aggregate budgets are reserved before
large allocations. Limit failures are structured catalog/CLI errors.

### Manifest v1

`META-INF/openggf-mod.yaml` is strict YAML with this canonical v1 shape (ordering is
writer-canonical, not reader-significant):

```yaml
formatVersion: 1
id: example-music
name: Example Music Pack
version: 1.0.0
authors:
  - Example Author
description: Replaces one stock track.
engineApiRange: ">=1.0.0 <2.0.0"
type: patch
baseGame: s2
dependencies:
  - id: shared-library
    versionRange: ">=1.2.0 <2.0.0"
audioOverrides:
  12: boss-remix
artOverrides: {}
# Optional Phase-2 fields, omitted by Phase-1 data-only mods:
# entrypoint: com.example.ExampleMod
# insertAfter: cpz2
# patternWindows: 1
```

Required fields are `formatVersion`, `id`, `name`, `version`, `authors`,
`description`, `engineApiRange`, `type`, `dependencies`, `audioOverrides`, and
`artOverrides`. Names, author strings, and description are nonblank; `authors` has
1–32 entries. `type` is `patch` or `standalone`. Singular `baseGame` is required for
`patch` and forbidden for `standalone`; one patch jar targets exactly one of `s1`,
`s2`, or `s3k`. Optional `entrypoint`, when present, is a nonblank fully-qualified
binary class name; absence means data-only. Optional `insertAfter` is a lower-case
stock level-flow entry key exposed by the target base game's `ZoneProgressionPlan`
and matches `[a-z0-9][a-z0-9-]{0,31}`; Phase 2 rejects an unknown or non-stock key.
Optional `patternWindows` is an integer from 1 through 16 inclusive and defaults to
1. Each window contains `0x8000` pattern ids; a process may allocate at most 128 mod
windows total in effective order, otherwise the contributing owner is ineligible.
`dependencies` is only a
sequence of `{id, versionRange}` mappings—no string shorthand. `audioOverrides` is
only a mapping of nonnegative integer stock music ids to owned local track names;
SFX overrides are deferred. `artOverrides` is only a mapping of stock art-key strings
to normalized mod asset paths. Empty collections are written explicitly. Nulls,
duplicate keys/fields, aliases, merge keys, alternate union shapes, and unknown
fields are errors. Phase 1 parses `entrypoint`, `artOverrides`, `insertAfter`, and
`patternWindows` but eligibility refuses any explicitly declared future field or
nonempty art map; later phases consume them without changing manifest v1.

### Audio manifest v1

`audio/audio-manifest.yaml` is absent when a mod declares no streamed audio. When
present it is strict YAML with canonical writer order:

```yaml
formatVersion: 1
tracks:
  - id: boss-remix
    assetPath: audio/boss-remix.ogg
    loop: true
    loopStartFrame: 44100
    loopEndFrame: 176400
    gain: 1.0
    tempoEffects: true
sfx: []
```

`formatVersion` and `tracks` are required; `sfx` is optional in the format and
canonical writers emit it explicitly. `tracks` is an ordered sequence of 0–10,000
records. Every track requires exactly `id`, `assetPath`, `loop`, `loopStartFrame`,
`gain`, and `tempoEffects`; `loopEndFrame` is the only optional track field. `id` is
an owned local track name and `assetPath` is a normalized contained `audio/...` WAV or
OGG entry. `loopStartFrame` is a nonnegative integer in source-sample frames. When
`loop` is false it must be 0 and `loopEndFrame` must be absent. When `loop` is true,
an omitted end means decoded EOF; a present end must be greater than the start and no
greater than decoded length. Gain is finite `[0.0,4.0]`; booleans are YAML booleans,
not strings or integers. Track ids are unique.

`sfx` is an ordered sequence using records with exactly `id`, `assetPath`, and `gain`.
Ids use the owned SFX-key grammar; assets are contained WAV/OGG entries; gain follows
the track rule. Phase 1 parses `sfx` but refuses nonempty SFX content. Phase 3 consumes
it for standalone one-shot SFX without changing audio-manifest v1; base-game SFX
override routing remains a separately tracked follow-on. SFX ids are unique and may
share a local spelling with a track because the registries are typed.

Unknown, duplicate, null, or alternate fields; duplicate ids within one typed
registry; aliases/merge keys; out-of-range numbers; invalid loop combinations; and
trailing YAML documents are errors. Golden tests parse the document above, emit it
byte-for-byte in canonical order, and cover an empty manifest, loop-to-EOF, nonlooped
track, Phase 1 SFX refusal, and every invalid union.

## 2. Editor envelopes

Envelope v1 hashing is verified against the **raw canonical payload JSON tree** before
migration. Its exact three-field payload stays hash-stable. V2 adds complete stock
object/ring tables and stable editor placement ids. Missing v2 tables mean empty
replacement, never v1 semantics. V3 adds `objectKey` for mod objects and omits a
meaningful numeric object id when that key is present; v1/v2 readers quarantine v3
rather than spawning the wrong object. Every version has its own DTO and canonical
writer. Versions above the current version quarantine.

Object placements persist the source game encoding needed for lossless round-trip:
stable placement id, centre x/y, object id or object key, subtype, render flags,
respawn flag, and raw placement words. A module-owned `ObjectPlacementEncoding`
validates and constructs S1/S2/S3K records. It never silently masks overflow.

## 3. Baked object-sheet container v1

Binary, big-endian: magic `GGFS`, u16 version `1`, u32 pattern count followed by
32-byte Sega-format patterns, u16 frame count, then frames (`u16 delay`, `u16 piece
count`) and fixed pieces (`s16 x`, `s16 y`, `u8 width`, `u8 height`, `u32 tileIndex`,
`u8 flags`, `u8 paletteIndex`). A trailing `u8 hasPalette`; when set, `u8 paletteLine`
and exactly sixteen u16 Genesis colors follow. Reserved flag bits, trailing bytes,
zero dimensions, out-of-range tile spans, and all limit violations are errors. Writer
order is input frame/piece order and golden fixtures pin bytes.

## 4. Full-level export and ModLevelDefinition v1

The export root contains `level.json`, `patterns.bin`, `chunks.bin`, `blocks.bin`,
`fg-map.bin`, optional `bg-map.bin`, `solid-heights.bin`, `solid-widths.bin`,
`solid-angles.bin`, `collision-primary.bin`, `collision-secondary.bin`, and
`palettes.bin`. All integers are big-endian; readers require exact lengths and reject
trailing bytes.

Binary layouts:

| File | Magic/header after magic | Records |
|---|---|---|
| patterns | `GPTN`, u16 v1, u16 recordSize=32, u32 count≤2048 | 32 raw Sega-pattern bytes |
| chunks | `GCHK`, u16 v1, u16 recordSize=8, u32 count≤1024 | four raw u16 `PatternDesc` values |
| blocks | `GBLK`, u16 v1, u8 gridSide (8 or 16), u8 reserved=0, u32 count≤256 | `gridSide²` raw u16 `ChunkDesc` values |
| fg/bg map | `GMAP`, u16 v1, u16 width, u16 height, u16 layers=1, u32 cellCount | one unsigned-byte block id per cell |
| solid heights | `GSHG`, u16 v1, u16 recordSize=16, u32 count≤256 | 16 signed profile bytes |
| solid widths | `GSWD`, same header/count as heights | 16 signed profile bytes |
| solid angles | `GSAN`, u16 v1, u16 recordSize=1, u32 count matching profiles | one angle byte |
| primary/secondary collision | `GCOL`, u16 v1, u8 path (0/1), u8 recordSize=2, u32 count matching chunks | one unsigned u16 solid-tile index per chunk |
| palettes | `GPAL`, u16 v1, u16 lineCount≤4, u16 colorsPerLine=16, u16 reserved=0 | Genesis u16 colors, line-major |

`level.json` keys, in canonical writer order, are: `formatVersion` (1), `zoneName`,
`zoneIndex`, `levelIndex`, `blockGridSide`, `width`, `height`, `bounds` (`minX`,
`maxX`, `minY`, `maxY`), `start` (`x`,`y`), `music`, `assets`, `objects`, `rings`.
`music` is exactly one of `{ "stockId": <int> }` or
`{ "trackKey": { "modId": <id>, "name": <local-name> } }`. `assets` names every
required file above. Object entries are exactly one of `stockObjectId` or `objectKey`
plus placement id, centre x/y, subtype, render flags, respawn flag, and raw words.
Rings carry placement id and centre x/y. Unknown required keys, both/neither union
arms, count mismatches, out-of-range descriptor indices, and values that would be
masked by engine types are errors. Arrays retain source order. Golden fixtures cover
empty and minimal playable levels.

Spawns are tagged unions: `{stockObjectId,...}` or `{objectKey,...}`. A mod key is
never converted into persistent numeric identity. `ModLevelDefinition` uses this JSON
shape directly; YAML is not a second authoritative format.

## 5. Playable-sheet container v2

Playable v2 uses magic `GGFP`, u16 version=2, u16 sectionCount, followed by sections
`u32 tag, u32 byteLength, payload` in fixed order:

1. `BASE`: u32 embeddedLength + one complete baked-sheet-v1 `GGFS` payload.
2. `META`: u32 basePatternIndex, u32 bankSize, u8 paletteLine, u8 flags=0, u16
   reserved=0. Portrait/HUD payloads are deferred to a future v3; v2 rejects nonzero
   flags.
3. `FRAM`: u16 frameCount exactly matching BASE frameCount; each frame has s16 originX/originY, u16 collisionWidth/
   collisionHeight, u16 runCount; each DPLC run is u16 sourceTile, u16 tileCount,
   u16 bankOffset. Counts are positive and spans stay within pattern/bank bounds.
4. `ANIM`: u16 sequenceCount; sequences sorted by UTF-8 key and encoded as u16 name
   byteLength + bytes, u16 stepCount, then steps `(u16 frameIndex, u16 duration,
   u8 flags, u8 reserved)`; only flag bit0=loop is defined.
5. Optional `APND`: u16 channelCount; channels sorted by namespaced key, then u16 key
   length + bytes, u16 frameCount, and u16 indices into BASE/FRAM frames. ANIM indices
   also target that same frame domain.

Section tags are ASCII packed big-endian. Unknown optional tags with high tag bit set
may be skipped; unknown required tags, duplicate/missing required sections, duplicate
keys, invalid UTF-8, dangling indices, trailing section bytes, and count/size limit
violations are errors. Writer ordering above is canonical and golden fixtures pin the
complete byte stream before any runtime art work begins.

## 6. Mod API inspection and class identity

`@ModApi` has RUNTIME retention. The compatibility snapshot includes public API,
protected subclass hooks, constructors, generic signatures, annotations, exceptions,
and all transitive signature types. Every reachable non-JDK signature type must also
be annotated `@ModApi`; an explicit small allowlist covers only stable JDK value
types. This closes provider/handler, factory, rewind, and registration-handle APIs
rather than exposing unsupported types through supported signatures. Direct bytecode
references to other reachable engine internals are allowed with compatibility
warnings, not validation errors, because trusted mods may call unsupported internals.
ASM remains required for constructor/service,
mutable-static, and method-body checks. `ggfmod package` always runs `validate` and
fails on errors. The engine independently recomputes the same structural validation
from jar bytes before creating an owner loader; no embedded author-generated
validation record is trusted.

Rewind class identity is `(ownerModId, binaryClassName)`. A boot-scoped loader
registry resolves any dynamic child through its owner loader; it does not rely on a
registration-time class map and rejects ambiguous/missing owners. Phase 2 code mods
may contain only compile-time-constant static primitive/String fields; every static
non-final field and every final static array/object is a validation error. Custom mod
static adapters are deferred. Engine-owned framework state continues to use normal
session-scoped `RewindSnapshottable` adapters.

## 7. TMX accepted domain

Phase 4 accepts finite orthogonal TMX maps with 16×16 tiles, CSV-encoded tile layers,
exactly one tileset (`firstgid=1`), and an embedded or project-root-contained external
TSX/image. Required layer: `FG`. Optional layers: `BG`, `COLLISION`, and
`COLLISION_ALT` (missing layers are empty; missing ALT copies primary and is documented
as path-neutral). Layers have zero offsets, full map dimensions, and unique
case-insensitive names. One optional `OBJECTS` object group holds point objects/rings/
start; other groups and infinite/chunked maps are errors.

The import root is the real parent directory of `<map.tmx>`. External TSX and image
sources must be relative normalized paths whose resolved real targets remain beneath
that root; absolute paths and escapes are errors. The tileset is exactly 16×16,
`margin=0`, `spacing=0`, with a positive `columns` equal to `imageWidth/16` and
`tilecount == columns * (imageHeight/16)`. Its single PNG image has positive
limit-bounded dimensions divisible by 16; image width equals `columns*16`. Missing or
contradictory geometry, collection-of-images tilesets, tile offsets, transformations,
object alignment overrides, and per-tile images are errors.

Command syntax is `ggfmod convert level --from-tmx <map.tmx> --palette
<palettes.bin> [--solid-tiles <profile-dir>] --out <export-dir>`. `palettes.bin` is
exact `GPAL` v1. Source alpha must be exactly 0 or 255. Alpha 0 maps to palette index
0. An opaque pixel must match an entry at index 1..15; after selecting the lowest
palette line that can represent the whole 8×8 pattern, duplicate colors use the
lowest matching index. Opaque colors available only at index 0, partial alpha, no
matching line, or more than 15 nontransparent colors are errors. `profile-dir`, when
present, contains exact `solid-heights.bin`,
`solid-widths.bin`, and `solid-angles.bin` files with equal counts; otherwise the
two-entry empty/full-solid set is emitted and both collision layers are limited to 0/1.
Profile height/width/angle shaping is outside TMX: creators supply these binaries from
the SDK/profile tooling. A future in-engine profile-shape editor may produce them;
Phase 0's editor only selects existing profile indices and collision modes.

The XML factory disables DTDs, external entities, XInclude, and external schema
access. External paths use root containment. FG/BG GIDs strip H/V flags and use
`gid - firstgid` as the zero-based tileset tile index; GID 0 remains empty. Every flip
bit on COLLISION/COLLISION_ALT and diagonal/hex flags everywhere are errors. A
collision cell's solid profile index is its unsigned raw CSV GID after validating
that no flags are present: GID 0 selects empty profile 0 and GID 1 selects profile 1.
For the corresponding block cell, primary GID 0 encodes primary
`NO_COLLISION`; primary GID >0 encodes primary `ALL_SOLID`. Secondary uses the same
rule from COLLISION_ALT; when COLLISION_ALT is absent, it copies both the primary
profile index and primary mode. Descriptor bits are exactly
`chunkId | hFlip<<10 | vFlip<<11 | primaryMode.value<<12 |
secondaryMode.value<<14`. `TOP_SOLID` and `LEFT_RIGHT_BOTTOM_SOLID` authoring remain
in-engine/future work. A visually empty cell with nonzero collision receives a
nonzero derived chunk containing blank patterns and the requested profiles/modes.
Object properties are strict typed values: `objectKey` or `stockObjectId`, integer
`subtype`, boolean `respawnTracked`, and ring/start marker kinds. A marker uses
nonempty modern `class`, otherwise legacy `type`; comparison is ASCII
case-insensitive, and two nonempty values must compare equal. Multiple starts are
errors. Every marker must be a Tiled point object with width=height=rotation=0 and
finite integral x/y pixels. Engine centre coordinates equal those x/y values from map
origin; require `0 <= x < mapWidth*16` and `0 <= y < mapHeight*16`. Tile objects,
ellipses/polygons/polylines/text, fractional values, and out-of-bounds points are
errors. Unknown object properties are errors.

Before row-major dedup, the converter reserves pattern 0 as 32 zero bytes, chunk 0 as
four raw-zero pattern descriptors with both solid indices 0, and block 0 as sixty-four
raw-zero chunk descriptors. GID 0 and an absent BG map through this blank hierarchy;
published counts and limits include the reserved entries. Dedup then traverses
row-major and assigns first-seen pattern/chunk/block identities starting at 1;
collision primary and secondary indices are part of chunk identity. Output is
byte-deterministic. Tests pin exact raw descriptor mode bits, a headless floor/wall
collision, a first-used visual tile that is nonblank plus empty FG/BG cells, blank
render/collision for the reserved hierarchy, a golden SHA-256, and cover orientation, dimensions,
missing/duplicate layers, multiple tilesets, firstgid, flips, offsets, typed
properties, raw collision GIDs 0/1 plus a custom profile GID, duplicate palette
colors, opaque-index-0 rejection, partial alpha, modern class, legacy type,
class/type conflict, import-root/TSX/image escape, tileset geometry, fractional/
rotated/sized/out-of-bounds markers, centre-coordinate mapping, XXE, count limits,
and repeat-run equality.

## 8. Semantic versions and ranges

Versions are strict `MAJOR.MINOR.PATCH` non-negative decimal triples with no leading
zeroes except zero. Phase 1 intentionally rejects prerelease (`-...`) and build
metadata (`+...`) rather than implementing partial SemVer precedence. A range is
either `*`, one exact version, or one-to-four whitespace-separated comparators from
`<`, `<=`, `>`, `>=`, `=`; comparators are ANDed. OR, hyphen, tilde, and caret syntax
are errors. Parsing normalizes comparator order, rejects contradictory/empty ranges,
and caps every numeric component at `Integer.MAX_VALUE`.

Golden examples: `1.2.3` matches only `1.2.3`; `>=1.2.0 <2.0.0` matches `1.2.0` and
`1.9.9` but not `2.0.0`; `*` matches every accepted version; `^1.2.3`, `1.2`,
`1.2.3-beta`, `>=2.0.0 <1.0.0`, and overflow are parse errors. Manifests carry their
own integer `formatVersion: 1` separately from `engineApiRange` and dependency ranges.
The initial published API ladder is `1.0.0` in Phase 1, additive `1.1.0` in Phase 2,
and additive `1.2.0` in Phase 3. Cross-phase tests prove a canonical
`>=1.0.0 <2.0.0` data-only pack remains eligible at every step and a Phase 2 range
beginning at `1.1.0` remains eligible in Phase 3.
