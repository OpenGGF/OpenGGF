# Portable SMPS song package, schema 4

OpenGGF reads the exploded files from a tool's portable `*.smps.zip` under
`audio/smps/<id>/` in a mod JAR. Preserve the names and exact bytes of each file.
The host rejects undeclared files, unknown manifest fields and enum values,
missing fields, out-of-range values and hash mismatches. The mod's audio
manifest declares the track and maps it through
[`audioOverrides`](manifest.md).

## Files and limits

| File | Content and limits |
| --- | --- |
| `manifest.json` | Strict UTF-8 JSON, at most 1 MiB |
| `track.bin` | Embedded native song bank, at most 32 KiB |
| `track.delta` | S3K retail bank delta, at most 98,306 bytes. Rebuilt bank is at most 32 KiB |
| `voices/global.bin` | Optional ordered native voice table, 25 bytes per voice |
| `psg/asset-NNN.bin`, `mod/asset-NNN.bin` | Embedded envelope bytes, 1–256 bytes per envelope. Filenames use the one-based asset ordinal |
| `dac/asset-NNN.pcm` | Embedded unsigned 8-bit mono PCM with midpoint 128. Zero-based asset ordinal, 1–65,535 bytes per sample, 95 samples and 512 KiB total |
| `source.smps.json` | Optional authoring source, hashed as opaque bytes, at most 16 MiB |
| `LICENSE.txt` | Optional package license text when `package.license` is declared |

The whole exploded package is limited to 2 MiB without source and 20 MiB with
source, including metadata. The entry limit is 482 files. Asset IDs and display
names do not supply any part of the filenames.

## Manifest

The root requires `schemaVersion: 4`, `profile: "portable"`, `package`,
`generator`, `provenance`, `requires`, `driverOptions`, `bank`, `assets`,
`envelopeBindings`, `dacBindings`, `globalVoices`, and `source`.
`driver` defaults to `s3k`. The host requires a patch with a matching
`baseGame`. Each driver uses its own native sequencer and DAC timing.

| Driver | Package features | `driverOptions` |
| --- | --- | --- |
| `s1` | Big-endian relative bank, local FM voices, PSG hold envelopes and PCM DAC at 301 base cycles | `{"speedUpTempo": <0–255 or null>}` |
| `s2` | Little-endian Z80 bank, local FM voices, PSG hold envelopes and PCM DAC at 295 base cycles | `{"speedUpTempo": <0–255 or null>}` |
| `s3k` | Little-endian Z80 bank, local and global FM voices, PSG and modulation envelopes, and PCM DAC at 297 base cycles | `{}` |

S1 and S2 use the supplied speed-shoes tempo byte while the effect is active
and restore normal tempo when it ends. A null value leaves tempo unchanged.
S3K uses the native speed-shoes frame multiplier. See the complete bank and
asset contract in [OpenSMPSDeck's portable package specification](https://github.com/raiscan/open-smps-deck/blob/multi-driver-portable-songs/docs/architecture/portable-package.md)
and the [driver feature matrix](https://github.com/raiscan/open-smps-deck/blob/multi-driver-portable-songs/docs/architecture/driver-support.md).

- `package` carries `id` (lower-case slug, at most 64 characters), `name`,
  strict `version` (major.minor.patch), `authors` (at most 32), nullable
  `license` and nullable author-supplied `rights` (`composition`, `notes`).
- `generator` carries informational `tool`, `version` and `openggfAudio`.
- `provenance` carries boolean `containsRomDerivedData` and
  `containsRetailSongData`, nullable `derivedFromRetailSong`
  (`game`, `songId`, `bankSha256`) and sorted unique `warnings`.
  Recognized codes are `RETAIL_SONG_DATA`, `ROM_DERIVED_DATA`,
  `DERIVED_FROM_RETAIL_SONG`, `UNDECLARED_STOCK_CONVERTED`, `SOURCE_AUDIO`,
  `THIRD_PARTY`, `UNKNOWN_ROM_IMAGE` and `DPCM_INEXACT`. By policy,
  provenance warnings do not disable playback. Packages embedding retail
  songs or ROM-derived bytes are for personal use only.
- `requires` carries `stockAssets` and sorted unique `games` derived from the
  stock references and the base game for a patch bank. The player's ROM
  supplies the referenced bytes and the patch base.
- `source` is null or an object with `path: "source.smps.json"`, `sha256` and
  boolean `redacted`.

## Bank and resources

Use `bank.form: "embedded"`, `path: "track.bin"`, the stored `sha256`
and the `assembledSha256` after all fills. `base` is 0 for normalized pointers or 32768 for the
absolute Z80 window. S1 requires base 0. `headerOffset` is the offset in the
bank and must be 0 for S1 and S2. `localVoiceCount` gives the number of
local voices. S1 and S2 put their finite tracks before the local voice table.
`previewFrames` is informational. `relocations` lists the sorted unique
offsets of 16-bit pointers, with `relocationsComplete` indicating whether the
authoring tool proved full coverage. Words are big-endian on S1 and little-endian
on S2 and S3K.

An asset declares `key` (zero-based `fm/`, `psg/`, `mod/` or `dac/`
ordinal in declaration order), `kind` (`FM_VOICE`, `PSG_VOLUME`,
`MODULATION` or `DAC_SAMPLE`), `id`, `name` and an `origin` object.
Origin types are `original`, `source-audio`, `third-party` (requires
`license` and `attribution`), and `stock`. Each asset declares one
payload: `path` and `sha256`, `bankOffset` for an embedded bank voice,
or a stock `ref` plus `transforms`. The native FM voice byte order is
preserved. An embedded voice whose path is `voices/global.bin` hashes its
25-byte slice in the listed global slot. Embedded stock assets also trigger the
ROM-derived provenance warning.

- `bank.fills` lists sorted, nonoverlapping `{offset, length, asset}`
  mappings for 25-byte zero holes. The asset must be a referenced FM voice.
- `globalVoices` is null for S1 and S2. S3K accepts null or `{path, sha256, voices}`. The path is
  `voices/global.bin`, the hash covers the stored file and `voices` is the
  ordered list of FM asset keys. Referenced voices occupy zero holes.
- `envelopeBindings` lists `{asset, slot}` mappings. PSG slots are 1–255
  and S3K modulation slots 1–127. S1 and S2 PSG envelopes are attenuation
  bytes below `80` hex followed by one `80` hold terminator. S3K envelopes
  must stay within their declared bytes and yield between control cycles.
- `dacBindings` lists sorted `{note, asset, rate}` mappings, one per note
  (129–223). Multiple notes can use the same sample. Rate is 0–255, with zero
  representing 256 iterations of the native delay loop.

## S3K retail patch banks

Use `bank.form: "patch"` with `path: "track.delta"`. Its `sha256` covers the
delta, and `assembledSha256` covers the rebuilt bank. The active cartridge
profile maps the locked-on `provenance.derivedFromRetailSong.songId`. OpenGGF
locates the song and verifies the locator's `bankSha256` against the full
32 KiB ROM bank containing it.

The delta starts with a big-endian unsigned 16-bit target length (10–32,768).
Each run has a big-endian unsigned 16-bit offset and positive length followed
by its literal bytes. The target starts as a copy of the base, truncated if
needed. Runs must fit the target, leave at least one omitted byte between runs,
and change every byte they replace. Any extension must be supplied in full.

Patch banks have empty `fills`, no `bankOffset` assets and `source: null`.
Set `requires.stockAssets: true` and `requires.games: ["s3k"]`. The provenance
must give the S3K base song and bank hash, set `containsRetailSongData: false`,
and include `DERIVED_FROM_RETAIL_SONG` without `RETAIL_SONG_DATA`. A missing
or mismatched bank disables the track and leaves the normal fallback in place.

## Stock references

Every `ref` carries the matching driver in `game`, a `table` and the raw
asset `sha256`.

| Drivers | Table | Locator fields and payload |
| --- | --- | --- |
| S3K | `global-fm` | `slot` selects a 25-byte voice from the universal table |
| All | `local-fm` | Engine `songId` and zero-based `slot` select a raw 25-byte voice through the native song header |
| S1 | `psg` | `index` 1–9 selects the ROM envelope including its terminator |
| S2 | `psg` | `index` 1–13 selects the envelope including its terminator from the ROM's decompressed Z80 `zPSG_EnvTbl` |
| S3K | `psg`, `mod` | `index` selects the loader's PSG or resolved playback modulation envelope |
| All | `dac` | `note` selects decoded unsigned PCM. S1 accepts hex 81–83 and 88–8B, S2 accepts 81–91, and S3K accepts 81–C4 |

Use OpenGGF's engine music IDs, including S2's sparse numbering. Both REV00
and REV01 can supply S1 and S2 assets. The cartridge profile selects the
available S3 and S&K tables. An unknown valid ROM image emits a warning and
each asset's SHA-256 determines acceptance. Missing assets or mismatched
hashes disable the track.

Apply `transforms` in order and provide `resultSha256` when the list is
nonempty. DAC supports `gain` with a `Double.toHexString` factor
(`clamp(round((u - 128) * factor) + 128, 0, 255)`) and `trim` with a
half-open `[start, end)` byte range. FM and envelopes support `patch`
with sorted unique `{offset, value}` entries in `bytes`. Final payloads
must satisfy the kind's bounds. The host verifies the assembled bank
after filling its holes. To bound repeated transforms on untrusted input,
the host allows up to 32 MiB of cumulative payload bytes visited by
transforms per package.
