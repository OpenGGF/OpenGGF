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
| `track.bin` | Native song bank, at most 32 KiB |
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
`baseGame`. Only S3K is implemented and its `driverOptions` is an empty
object. `s1` and `s2` fail closed.

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
  stock references. The player's ROM supplies the referenced bytes.
- `source` is null or an object with `path: "source.smps.json"`, `sha256` and
  boolean `redacted`.

## Bank and resources

Use `bank.form: "embedded"`, `path: "track.bin"`, the stored `sha256`
and the `assembledSha256` after all fills. The reserved patch form is
unsupported. `base` is 0 for normalized pointers or 32768 for the
absolute Z80 window. `headerOffset` is the offset in the bank.
`localVoiceCount` gives the number of local voices.
`previewFrames` is informational. `relocations` lists the sorted unique
offsets of 16-bit little-endian pointers, with `relocationsComplete` indicating
whether the authoring tool proved full coverage.

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
- `globalVoices` is null or `{path, sha256, voices}`. The path is
  `voices/global.bin`, the hash covers the stored file and `voices` is the
  ordered list of FM asset keys. Referenced voices occupy zero holes.
- `envelopeBindings` lists `{asset, slot}` mappings. PSG slots are 1–255
  and modulation slots 1–127. Envelopes must stay within their declared bytes
  and yield between control cycles.
- `dacBindings` lists sorted `{note, asset, rate}` mappings, one per note
  (129–223). Multiple notes can use the same sample. Rate is 0–255, with zero
  representing 256 iterations of the native delay loop.

## Stock references

Every `ref` carries `game: "s3k"`, `table` and the raw asset `sha256`.

| Table | Locator fields | Bytes |
| --- | --- | --- |
| `global-fm` | `slot` | One 25-byte voice from the universal table |
| `local-fm` | `songId`, `slot` | One 25-byte voice through the native song header's voice pointer |
| `psg` | `index` | The loader's PSG envelope |
| `mod` | `index` | The playback envelope with native operands resolved |
| `dac` | `note` (129–196) | Decoded unsigned PCM for the native note |

The cartridge profile selects the available S3 and S&K tables. An unknown
valid ROM image emits a warning and each asset's SHA-256 determines acceptance.
Missing assets or mismatched hashes disable the track.

Apply `transforms` in order and provide `resultSha256` when the list is
nonempty. DAC supports `gain` with a `Double.toHexString` factor
(`clamp(round((u - 128) * factor) + 128, 0, 255)`) and `trim` with a
half-open `[start, end)` byte range. FM and envelopes support `patch`
with sorted unique `{offset, value}` entries in `bytes`. Final payloads
must satisfy the kind's bounds. The host verifies the assembled bank
after filling its holes. To bound repeated transforms on untrusted input,
the host allows up to 32 MiB of cumulative payload bytes visited by
transforms per package.
