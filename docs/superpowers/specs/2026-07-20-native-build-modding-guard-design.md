# Native-build modding guard — design

**Date:** 2026-07-20
**Branch:** `feature/ai-native-mod-guard` (worktree, based off `next`)
**Status:** Design (brainstorming), revised after spec review round 1

## Problem

OpenGGF ships two runtime artifacts:

- the **JVM jar** (`OpenGGF-<ver>-jar-with-dependencies.jar` / universal jar), and
- the **GraalVM native-image binary** (`OpenGGF.exe` / `OpenGGF` / `OpenGGF.bin`,
  built with `-Pnative`).

Code-bearing mods are loaded at runtime by building a `URLClassLoader`-backed
`ModDependencyClassLoader` over the mod jar and loading the creator's entrypoint
classes (`ModClassLoaderFactory.create(...)`). GraalVM native-image is a
closed-world ahead-of-time compile: it cannot define new classes from an external
jar at runtime. Therefore **code-bearing mods cannot run under the native binary**.
Data-only mods (music packs, reskins / art overrides) are pure resource-byte reads
and are unaffected.

Today the engine has no signal for this. On a native build a code mod is silently
skipped (it is untrusted, so `ModClassLoaderFactory` `continue`s past it), the
rejection is never surfaced (`ModRuntime.rejectedOwners()` has no boot consumer),
the Mod Manager will still let a user "enable" a code mod that can never load, and
a standalone code mod still renders as a playable master-title entry that crashes
on launch. This is confusing and looks like a bug.

## Goals

1. **Boot notice.** On a native build, when enabled mods are unsupported
   (code-bearing), show an in-engine notice listing them, plus an equivalent
   console warning.
2. **Mod Manager gate.** In the Mod Manager, unsupported (code-bearing) mods
   render greyed/not-loaded and cannot be enabled on a native build.
3. **No crashing entry points.** A standalone code mod must not appear as a
   launchable master-title entry on a native build.
4. **Docs.** The modding docs state plainly that code-bearing mods require the JVM
   jar and are unsupported on native builds; data-only music/reskin packs are
   unaffected.

## Non-goals

- No change to data-only mods (music packs, reskins): they continue to load on
  native builds.
- No rewrite of persisted mod enabled-state. Suppression is **runtime-only** (see
  Decision 1).
- No hot-reload, no attempt to make code mods work under native-image.
- No new general-purpose modding GameMode/notice framework; reuse the existing
  boot-screen chain.

## Key decisions

### Decision 1 — runtime suppression, not config mutation
On a native build, unsupported enabled mods are **not loaded** for the current
process, but their persisted `enabled` state in the mod state store is left
untouched. Rationale: the same on-disk install may later be launched with the JVM
jar, where the mods are valid; silently flipping saved config would be surprising
and lossy.

### Decision 2 — one capability boolean, threaded to the consumers that need it
Introduce a single capability, `compiledModsSupported` (`false` when
`Engine.isNativeImage()` returns true). It is resolved once at boot and threaded to
the consumers below. The `mods` package must not call `Engine` directly (keeps it
unit-testable and respects package boundaries). `ExternalContentPolicy` is **not**
overloaded — that record models scan/session determinism, a separate axis.

Consumers:
1. **Boot mod-runtime build** — `ModClassLoaderFactory.create(...)`: load-time
   backstop so code mods never load on native (see Decision 3).
2. **Boot notice + console log** — computed via the catalog helper (Decision 4).
3. **Master-title enumeration + standalone launch** — suppress standalone code-mod
   entries (Goal 3).
4. **Mod Manager screen** — grey/not-loaded rendering + cascade-aware enable guard.

### Decision 3 — factory gate is a load-time backstop, placed before the untrusted-code skip
`ModClassLoaderFactory.create(...)` gains a `compiledModsSupported` parameter. The
new check is inserted **at the very top of the per-descriptor loop, before the
existing `if (descriptor.containsCode() && !trusted.contains(owner)) continue;` at
`ModClassLoaderFactory.java:70`**:

```java
if (descriptor.containsCode() && !compiledModsSupported) {
    rejections.put(owner, rejection(RejectionReason.NATIVE_UNSUPPORTED,
            "code-bearing mods are unsupported on OpenGGF native builds"));
    continue;
}
```

Placement is at loop top and defensive: in practice only **trusted (EFFECTIVE)**
code mods reach this loop at all — untrusted enabled code mods are marked `BLOCKED
(CODE_TRUST_REQUIRED)` by the eligibility freeze and excluded from
`orderedEnabled()` before the factory runs — so the pre-existing untrusted-code
`continue` at `:70` fires only for the rare already-in-plan case. Putting the
`NATIVE_UNSUPPORTED` check ahead of it guarantees the rejection is recorded for any
code descriptor that does reach the loop, rather than being silently swallowed by
`:70`. The `detail` string is non-blank (required by `Rejection`'s constructor,
`ModRuntime.java:339-342`).

Why the backstop matters even though the notice is computed elsewhere (Decision 4):
a code mod **trusted in a prior JVM run** (trust persists by jar sha256) is
EFFECTIVE, reaches this loop, and would otherwise be loaded on native and crash.
The factory gate guarantees no code mod loads on native regardless of trust state.
The notice source does **not** depend on these rejections.

Add `NATIVE_UNSUPPORTED` to `ModRuntime.RejectionReason` (additive; the enum has 5
constants today at `ModRuntime.java:330-336`).

### Decision 4 — the notice/suppression list is computed from the catalog, not from runtime rejections
`ModRuntime.rejectedOwners()` filtered to `NATIVE_UNSUPPORTED` is **insufficient**
as the notice source, because two classes of unsupported-but-enabled mods never
reach the factory with that reason:

- **Untrusted / trust-revoked enabled code mods** are marked `BLOCKED
  (CODE_TRUST_REQUIRED)` by `EffectiveCatalogBuilder` and excluded from
  `orderedEnabled()`, so they never reach the factory. (Trust auto-revokes on jar
  hash change via `ModSubsystem.reconcileBootTrust`, so this is reachable.)
- **Data-only mods depending on a code mod** are rejected `DEPENDENCY_UNAVAILABLE`,
  a different reason.

Instead, introduce a small **pure helper** (proposed:
`com.openggf.mods.NativeUnsupportedMods`) that returns the descriptors that are (a)
enabled by user intent and (b) `containsCode()`. This is the single source of truth
for the **boot notice + console warning**.

**Input contract — the helper needs the persisted `ModState`, not just the
catalog.** `ModCatalog` (`ModCatalog.java:12-13`) carries only `scanned`,
`effective` (`orderedEnabled()`), and `eligibility`; it does **not** retain the
per-mod enabled bit. The `eligibility` map cannot substitute: in
`EffectiveCatalogBuilder.evaluate`, `ownBlock` runs before the `enabled()` test and
returns `BLOCKED (CODE_TRUST_REQUIRED)` for *any* untrusted code mod
(`EffectiveCatalogBuilder.java:79-80,143-147`), so an enabled-untrusted code mod is
indistinguishable from a disabled-untrusted one by status alone — deriving the list
from eligibility would list every untrusted code jar in `mods/`, even ones the user
never enabled. The helper signature is therefore:

```
NativeUnsupportedMods.compute(List<ModCatalogEntry> scanned,
                              ModState startupEnabledIntent,
                              boolean compiledModsSupported)
    → List<ModDescriptor>   // startupEnabledIntent.enabled(id) && descriptor.containsCode()
```

`startupEnabledIntent` is the **startup** `ModState` the process booted with (not
`PendingModStateEditor.pendingState()`, which reflects unsaved manager edits).
`ModSubsystem` already holds the startup state and the editor at the boot site
(`ModSubsystem.java:387-397`), so `Engine` passes it in. The helper stays pure (it
receives one more value object; it makes no `Engine`/`ModSubsystem` calls) and is
directly unit-testable, capturing Decision 4 case (a) — enabled-but-untrusted /
trust-revoked code mods — unambiguously.

Master-title standalone suppression (Goal 3) does **not** route through this helper:
`masterTitleEntries()` iterates `effective().orderedEnabled()` (only EFFECTIVE
descriptors), so a direct `descriptor.containsCode() && !compiledModsSupported` skip
there is sufficient and unambiguous (see Architecture).

The factory's `NATIVE_UNSUPPORTED` rejections (Decision 3) remain for correctness
and diagnostics but are **not** the notice source.

### Decision 5 — reuse the boot-screen chain for the notice
Model a minimal `NativeModNoticeScreen` on `LegalDisclaimerScreen` (text screen,
dismiss-on-confirm). Add a `GameMode.NATIVE_MOD_NOTICE` constant and slot it into
the boot chain **before `MASTER_TITLE_SCREEN`**, shown **only when** the helper's
list is non-empty. Because it precedes the master title, it must be inserted on
**both** boot paths:

- disclaimer-on: after `exitLegalDisclaimer` (`Engine.java:1167-1183`), before
  building the master title; and
- disclaimer-off direct boot (`Engine.java:537-544`), where no `exitLegalDisclaimer`
  runs.

This mirrors the legal-disclaimer wiring (supplier + exit handler) and reuses
`BootScreenModeController` dispatch.

## Architecture & data flow

```
Engine.isNativeImage()  ──► compiledModsSupported (boolean, resolved once at boot)
        │
        ├─► ModSubsystem.installAtBoot(..., compiledModsSupported)   [carry + getter]
        │
        ├─► boot mod-runtime build (Engine.initializeExternalContentAtBoot, ~line 1405)
        │        ModClassLoaderFactory.create(effectiveMods, trusted, compiledModsSupported)
        │           → code mods rejected NATIVE_UNSUPPORTED (load-time backstop)
        │
        ├─► NativeUnsupportedMods.compute(scanned, startupModState, compiledModsSupported)
        │        → List<ModDescriptor> unsupportedEnabled
        │        ├─ LOGGER.warning(header + comma-joined ids)   [console]
        │        └─ if non-empty: build NativeModNoticeScreen, insert NATIVE_MOD_NOTICE
        │                          into the boot chain (both boot paths)
        │
        ├─► masterTitleEntries() (Engine.java:1456-1470) and exitStandaloneMasterTitle
        │        → skip STANDALONE descriptors where containsCode() && !compiledModsSupported
        │          (direct check over orderedEnabled(); does not use the helper)
        │
        └─► ModSubsystem.createManager(font)
                 → new ModManagerScreen(..., compiledModsSupported)
                     render: code mod on native → not-loaded ([--]/greyed) + "UNSUPPORTED" badge
                     toggleSelected(): enable refused if ANY descriptor in the
                                       computed enable-cascade containsCode()
```

### Component responsibilities

| Unit | Responsibility | Depends on |
|------|----------------|------------|
| `Engine` (boot) | Resolve `compiledModsSupported` from `isNativeImage()`; thread into `ModSubsystem.installAtBoot`, factory, master-title enumeration; compute the unsupported list via the helper (passing the startup `ModState`); log it; wire the notice screen (both boot paths) when non-empty | `ModSubsystem`, `NativeUnsupportedMods`, `NativeModNoticeScreen` |
| `NativeUnsupportedMods` (new, pure) | From `scanned` entries + startup `ModState` + flag → enabled code-bearing descriptors; no engine deps | `ModCatalogEntry`/`ModDescriptor`, `ModState` |
| `ModRuntime.RejectionReason` | New constant `NATIVE_UNSUPPORTED` | — |
| `ModClassLoaderFactory` | Accept `compiledModsSupported`; reject enabled code descriptors `NATIVE_UNSUPPORTED` at loop top, before the untrusted-code skip | `ModRuntime` |
| `ModSubsystem` | Carry the flag from boot through its constructors (default `true` elsewhere); expose getter; pass into factory build + `createManager` | `ModClassLoaderFactory` |
| `NativeModNoticeScreen` (new) | Render truncated unsupported list; dismiss-on-confirm | boot-screen infra (mirror `LegalDisclaimerScreen`) |
| `GameMode` / `GameLoop` / `EngineRenderDispatcher` / `BootScreenModeController` | Add `NATIVE_MOD_NOTICE` mode: enum constant, supplier+exit-handler dispatch, background-clear + foreground-draw arm, `handles()` parity | — |
| `ModManagerScreen` | Accept `compiledModsSupported`; not-loaded/greyed render + `UNSUPPORTED` badge for code mods on native; cascade-aware enable refusal | — |

## Boot notice content

Header line (per requirement wording):

> The following enabled mods are not supported on OpenGGF native builds and have
> been disabled:

Then one line per unsupported mod (manifest display name if available, else id;
Engine cross-references `processCatalog()` descriptors by id since the helper
returns descriptors that already carry the name). Truncation uses a named constant
`NativeModNoticeScreen.MAX_VISIBLE_MOD_LINES` (initial value 12):

- if `count <= MAX`: list all `count` lines;
- if `count > MAX`: list the first `MAX - 1` and a final line `…and N more`, where
  `N = count - (MAX - 1)`.

Boundary tests: `count == MAX` → all shown, no "…more" line; `count == MAX + 1` →
`MAX - 1` lines + `…and 2 more`.

The console emits the same header + comma-joined id list at `WARNING`.

## Mod Manager behavior

- **Render (native + `containsCode()`):** the row shows a not-loaded state (`[--]`)
  rather than `[ON]`/`[OFF]`, greyed, with an `UNSUPPORTED` badge added to the
  existing badge list (near `ModManagerScreen` row rendering, alongside `BLOCKED` /
  `TRUST REQUIRED`). This avoids the self-contradiction of a greyed `[ON]` row and
  reflects that the mod is not loaded this session even though config still marks it
  enabled. **Implementation note:** the `[--]` marker is currently driven by
  `!row.valid()` (`ModManagerScreen.java:162`), and `valid()==false` also makes a
  row inert/non-toggleable. Since a code mod on native must still be *disable-able*,
  do **not** reuse `valid()`; add a distinct `notLoaded` flag to the row view and
  widen the marker expression to `(!valid() || notLoaded)` while leaving toggle
  eligibility on `valid()`.
- **Enable guard (cascade-aware):** in `toggleSelected()` (`:283`), when the action
  is an enable, compute the enable-cascade as today
  (`disabledDependencyClosure(id)`) and refuse if **any** descriptor in that
  cascade `containsCode()` while `!compiledModsSupported`, with status
  `"<id> is not supported on native builds"`, returning before the trust/cascade
  path (`:295-343`). This closes the hole where a data-only mod depending on a
  trusted code mod would cascade-enable the code mod. Disabling a code mod (to clean
  up config from the native build) remains allowed.

## Behaviors explicitly accepted

- **Development source mods** (`ggfmod.dev.modDir` / `DevelopmentModSource`): a code
  dev-mod is force-trusted+enabled and has no Mod Manager (editor is null). On
  native it is caught by the factory backstop and appears in the boot notice; no
  manager interaction applies. Accepted.
- **`containsCode()` = presence of bytecode** (any `.class` in the jar), not
  "requires classloading to function." A data mod bundling an inert stray `.class`
  is conservatively flagged `UNSUPPORTED` on native. This matches the existing
  `CODE_TRUST_REQUIRED` treatment and is safe. Accepted.
- **Dependent data-only mods are not named in the notice.** When an enabled
  data-only mod depends on a code mod, the code mod (the root cause) appears in the
  notice; the dependent — which the factory would reject `DEPENDENCY_UNAVAILABLE` —
  is not, because it is not itself `containsCode()`. Listing the code mod is the
  actionable signal, so the omission is accepted. (The Mod Manager still refuses to
  enable such a dependent via the cascade-aware guard.)

## Testing (JUnit 5 only)

- **`NativeUnsupportedMods`** (unit, pure): given `scanned` + startup `ModState` +
  flag — enabled code mod → included; enabled data-only mod → excluded; disabled
  code mod → excluded (even when untrusted/BLOCKED, to prove eligibility status is
  not the source); with `compiledModsSupported=true` → always empty. An
  enabled-but-untrusted / trust-revoked code mod → still included (Decision 4 case
  a). A disabled-untrusted code mod → excluded (guards the false-positive the
  eligibility-only derivation would have produced).
- **`ModClassLoaderFactory`** (unit): `compiledModsSupported=false` → enabled code
  descriptor reported `NATIVE_UNSUPPORTED`, no loader created; data-only still
  loads; a *trusted* code mod is also rejected (backstop). `true` → unchanged
  (regression).
- **`ModManagerScreen`** (unit, headless `TextSink`): on native, enabling a
  code-bearing mod is refused and the row exposes `UNSUPPORTED` + not-loaded render;
  **enabling a data-only mod whose dependency is a trusted code mod is refused**
  (cascade case); disabling a code mod is still permitted; data-only mods enable
  normally.
- **Master-title suppression** (unit): a standalone code mod is absent from
  `masterTitleEntries()` on native and present on JVM.
- **Notice truncation** (unit): boundary cases above against
  `MAX_VISIBLE_MOD_LINES`.
- **Boot wiring** (unit where feasible): native + only data-only mods enabled →
  **no** `NATIVE_MOD_NOTICE` inserted; JVM (`compiledModsSupported=true`) → notice
  never wired regardless of catalog.

## Documentation

- `docs/modding/index.md` — "Native builds" note: code-bearing mods require the JVM
  jar; data-only music/reskin packs work on native.
- `docs/modding/content-mods.md` — same note near run/enable instructions.
- `docs/modding/troubleshooting.md` — add a `NATIVE_UNSUPPORTED` row (remedy: run
  the JVM jar to use code-bearing mods).

## Branch / policy

- Worktree branch `feature/ai-native-mod-guard` based off `next`.
- Commit-message trailers required on every non-merge commit. Engine changes touch
  `src/main/` → `Changelog: updated` with a staged `CHANGELOG.md` entry.
  `Agent-Docs`, `Guide`, `Known-Discrepancies`, `S3K-Known-Discrepancies`,
  `Configuration-Docs`, `Skills` set `updated`/`n/a` per files actually staged.
- No zone/route/frame carve-outs; this is a runtime-capability gate, not a
  physics/trace change.

## Risks

- **Boot-chain ordering.** The notice slots between the legal disclaimer and master
  title on both boot paths; mis-wiring could skip it or double-show it. Mitigation:
  mirror the legal-disclaimer supplier/exit-handler wiring exactly; activate only
  when the helper list is non-empty; cover with the boot-wiring tests.
- **Capability plumbing reaching many constructors** (`ModSubsystem` has ~7
  construction sites). Mitigation: default the flag to `true` (supported) in
  existing/test constructors so only the production boot path changes behavior.
- **Boot-screen change spans four files** (`GameMode`, `GameLoop`,
  `EngineRenderDispatcher`, `BootScreenModeController`). Mitigation: enumerate the
  exact edits in the plan; reuse `BootScreenModeController.updateLegalDisclaimer`
  shape.
