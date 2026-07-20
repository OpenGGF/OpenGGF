# `ggfmod validate` finding catalog

`ggfmod validate <mod.jar>` prints sorted numbered findings and exits nonzero for
errors. Packaging invokes the same validator on its staging jar and refuses to
publish when errors are present;
`NON_API_ENGINE_REFERENCE` is a warning but means the referenced internal type has no
compatibility promise. The authoritative producers are
[`ModJarValidator`](../../src/main/java/com/openggf/tools/modsdk/ModJarValidator.java),
[`ModCatalogValidator`](../../src/main/java/com/openggf/mods/ModCatalogValidator.java),
[`ModValidator`](../../src/main/java/com/openggf/mods/validation/ModValidator.java),
and, for multi-mod eligibility,
[`EffectiveCatalogBuilder`](../../src/main/java/com/openggf/mods/EffectiveCatalogBuilder.java).

## Jar, manifest, dependency, and budget findings

The single-jar CLI emits the direct jar, manifest, API, anchor, and pattern-budget
findings below. `DUPLICATE_MOD_ID`, `DISABLED`, `CODE_TRUST_REQUIRED`,
`DESCRIPTOR_INVALID`, and the `DEPENDENCY_*` findings are effective-catalog findings:
they arise when the engine compares multiple discovered mods, not from validating one
jar in isolation. The `REPOSITORY_*` findings likewise come from bounded multi-jar
repository preflight rather than the single-jar CLI.

| Code | What to fix |
|---|---|
| `MOD_JAR_INVALID` | Rebuild a regular non-symlink jar with safe, unique, bounded entry names/content. |
| `JAR_SIZE_LIMIT` / `JAR_READ_FAILED` / `MALFORMED_JAR` | Reduce/rebuild the archive and verify it can be read completely. |
| `MANIFEST_MISSING` / `MANIFEST_INVALID` | Add strict `META-INF/openggf-mod.yaml`; remove unknown/duplicate/alternate fields. |
| `DUPLICATE_MOD_ID` | Give each discovered jar a unique manifest id. |
| `ENGINE_API_INCOMPATIBLE` | Compile against a supported API and correct `engineApiRange`. |
| `DISABLED` | Enable the mod in the manager and restart. |
| `CODE_TRUST_REQUIRED` | Review the source/findings, grant trust for this exact jar hash, and restart. |
| `DEPENDENCY_MISSING` / `DEPENDENCY_DISABLED` / `DEPENDENCY_BLOCKED` | Install/enable/fix the named dependency first. |
| `DEPENDENCY_VERSION_INCOMPATIBLE` / `DEPENDENCY_BASE_GAME_MISMATCH` | Align dependency version range and patch base game. |
| `DEPENDENCY_CYCLE` | Remove a cycle from manifest dependencies. |
| `DESCRIPTOR_INVALID` | Fix the underlying manifest/jar descriptor error reported with it. |
| `INSERT_AFTER_STOCK_ANCHOR_INVALID` | Use a supported stock progression anchor for the manifest base game. |
| `PATTERN_WINDOW_BUDGET_EXCEEDED` | Lower `patternWindows` or reduce the effective enabled-mod window total. |
| `REPOSITORY_JAR_LIMIT_EXCEEDED` / `REPOSITORY_VALIDATION_BYTES_EXCEEDED` / `REPOSITORY_PREFLIGHT_FAILED` | Reduce/fix the `mods/` repository before retrying. |
| `MOD_JAR_CHANGED` | Stop mutating the jar during validation; rebuild and validate an immutable artifact. |

## Asset, level, and audio findings

`AUDIO_OVERRIDE_CONFLICT` also requires an effective catalog with at least two owners;
the remaining rows can be produced while validating one packed jar.

| Code | What to fix |
|---|---|
| `ASSET_MISSING` / `ASSET_FORMAT_INVALID` | Include the declared baked asset and regenerate it with the matching converter. |
| `LEVEL_FORMAT_INVALID` | Re-export/reconvert the exact `ModLevelDefinition` v1 inventory and remove trailing/mismatched bytes. |
| `LEVEL_OWNER_MISMATCH` | Use object/track keys owned by the declaring manifest id. |
| `AUDIO_MANIFEST_MISSING` / `AUDIO_MANIFEST_INVALID` | Add/fix strict `audio/audio-manifest.yaml` and its declared entries. |
| `AUDIO_ASSET_MISSING` / `AUDIO_ASSET_INVALID` | Include a bounded WAV/Ogg with supported rate/channels/duration/PCM size. |
| `AUDIO_LOOP_INVALID` | Express loop points in decoded source frames and keep start/end within the asset. |
| `AUDIO_OVERRIDE_ID_INVALID` / `AUDIO_OVERRIDE_TRACK_MISSING` | Use a valid stock music id and an existing owned track. |
| `AUDIO_OVERRIDE_CONFLICT` | Resolve the intentional later-wins conflict or accept the reported effective owner. |
| `SFX_UNSUPPORTED_PHASE1` | Remove SFX from a Phase 1 data-only/base-game path; standalone SFX uses API 1.2. |
| `STANDALONE_AUDIO_OVERRIDE` | Remove base-game numeric music overrides from a standalone manifest. |

## Compiled-code and rewind findings

| Code | What to fix |
|---|---|
| `ENTRYPOINT_MISSING` / `ENTRYPOINT_CONTRACT` / `ENTRYPOINT_CONSTRUCTOR` | Declare a present public concrete `GgfMod` with a public no-arg constructor. |
| `DUPLICATE_CLASS` / `CLASS_ENTRY_NAME_MISMATCH` / `MALFORMED_CLASSFILE` | Rebuild unique valid class entries at paths matching their binary names. |
| `RESERVED_ENGINE_PACKAGE` | Move creator classes out of `com.openggf.*`. |
| `OBJECT_BASE_CONTRACT` | Extend a supported public mod object base rather than implementing internals directly. |
| `STATIC_STATE_UNSUPPORTED` | Keep gameplay state on instances/session services; only compile-time primitive/String constants may be static. |
| `OBJECT_RECREATE_PATH_MISSING` | Implement the supported rewind recreation contract for every concrete mod object. |
| `FINAL_SCALAR_REWIND_GAP` | Make changing scalar state capturable/restorable instead of uncaptured final instance state. |
| `OBJECT_REFERENCE_REWIND_ID_MISSING` | Capture referenced objects by rewind identity and restore through the supported context. |
| `CONSTRUCTOR_SERVICES_ACCESS` | Move service access out of construction into lifecycle callbacks. |
| `NON_API_ENGINE_REFERENCE` | Prefer an `@ModApi` type; otherwise accept that this internal reference may break. |

## Related runtime/discovery findings

These use the same structured-finding vocabulary but are emitted during repository
discovery, session preparation, registration, or callbacks rather than by the
single-jar `ggfmod validate` command:

| Code | What to fix |
|---|---|
| `MOD_REPOSITORY_INVALID` | Repair the repository root/entries before discovery. |
| `MOD_STATE_SAVE_FAILED` / `MOD_DISABLE_SAVE_FAILED` | Check mod-state directory permissions/ownership; do not replace paths concurrently. |
| `MOD_ART_ASSET_INVALID` / `MOD_LEVEL_ASSET_INVALID` | Fix the owner contribution's bounded path/container before registration. |
| `AUDIO_DEPENDENCY_FAILED` / `AUDIO_PREPARATION_FAILED` | Fix the upstream owner/asset; the stock fallback remains available. |
| `MOD_REGISTRATION_FAILED` / `MOD_CALLBACK_FAILED` | Fix the throwing owner callback; registration is discarded or the owner is disabled for the session. |
| `NATIVE_UNSUPPORTED` | This code-bearing mod cannot load on a native build. Run the JVM jar (`OpenGGF-<ver>-jar-with-dependencies.jar`) to use it. Data-only music/reskin mods are unaffected. |
| `MOD_REGISTRATION_DISABLE_SAVE_FAILED` / `TRUST_REVOCATION_SAVE_FAILED` | Check mod-state storage after a failed registration or trust revocation; the requested persisted disable/revocation did not save. |
| `MOD_PATCH_METADATA_FAILED` / `MOD_PATCH_APPLY_FAILED` | Fix the patch owner's metadata/callback failure; the engine leaves that patch unapplied. |
| `MOD_CHARACTER_DISABLED_FALLBACK` / `MOD_CHARACTER_UNKNOWN_FALLBACK` | Re-enable/install the saved character owner or select an available character; launch used a stock fallback. |

Run validation again after every change. A zero-finding result prints
`Validation passed: 0 findings`; packaging invokes the same validator automatically.
