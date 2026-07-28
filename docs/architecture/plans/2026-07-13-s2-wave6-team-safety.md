# Sonic 2 Wave 6 Team Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend remaining implemented EHZ/WFZ/DEZ mandatory interactions to arbitrary sidekicks without changing the native P1/P2 prefix or green trace behavior.

**Architecture:** Native ROM scalar slots remain first-class and bind to stable player owners; novelty participants use identity-keyed extension state encoded through `PlayerRefId`. Shared world-coordinate event and boss thresholds remain unchanged for widescreen, and no donor-specific movement branch is added unless a test proves a spin-dash-only blocker.

**Tech Stack:** Java 21, JUnit 5, Maven, compact rewind codecs, Sonic 2 disassembly-backed object logic.

---

### Task 1: EHZ bridge riders

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/BridgeObjectInstance.java`
- Create: `src/test/java/com/openggf/game/sonic2/objects/TestS2BridgeMultiSidekick.java`

- [ ] Write tests that place native P1/P2 and two extension riders on distinct logs, assert native-first depression movement, reorder/omission/death behavior, and compact rewind replacement keys.
- [ ] Run `mvn "-Dtest=com.openggf.game.sonic2.objects.TestS2BridgeMultiSidekick" test` and confirm the extension-rider assertions fail because only `nativeP2OrNull()` is latched.
- [ ] Add an identity-keyed extension log-index map, process native P2 before ordered extensions, replace the map from the live checkpoint batch each frame, and register the map as `CAPTURED` rewind state.
- [ ] Re-run the focused test and keep the existing EHZ bridge regression test green.

### Task 2: WFZ palette switcher identity

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/WFZPalSwitcherObjectInstance.java`
- Create: `src/test/java/com/openggf/game/sonic2/objects/TestWFZPalSwitcherMultiSidekick.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`

- [ ] Write tests where two extensions cross independently, native P2 is promoted/demoted by reorder, an omitted state is pruned, and compact restore resolves replacement player identities.
- [ ] Run the focused class and confirm the tests fail under `NATIVE_P1_P2` and index-owned scalar state.
- [ ] Bind native scalar flags to captured player owners, transfer state through the existing identity map, extend participation after the native prefix, and prune omitted extension keys.
- [ ] Re-run the focused class and rewind coverage guard.

### Task 3: Death Egg ending containment

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/bosses/Sonic2DeathEggRobotInstance.java`
- Create: `src/test/java/com/openggf/game/sonic2/objects/bosses/TestS2DeathEggTeamSafety.java`
- Modify: `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`

- [ ] Write tests that seed defeat phases 4/6 and assert every valid extension is locked, forced right, and ground-contained while main-player progression remains authoritative; omitted/dead/unloaded players are released, and unrelated pre-existing control is untouched.
- [ ] Run the focused test and confirm extra sidekicks remain uncontrolled before implementation.
- [ ] Add a PlayerRef-keyed set of boss-owned extension controls, apply team containment after the unchanged native-main code, release invalid/omitted owners immediately, clear ownership on unload/fade, and capture the set for rewind.
- [ ] Add compact replacement-player rewind coverage and re-run the boss plus graph-rewind suites.

### Task 4: Remaining mandatory native-only traversal audit

**Files:**
- Modify: `src/main/java/com/openggf/game/sonic2/objects/MTZLongPlatformObjectInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic2/objects/LauncherSpringObjectInstance.java`
- Modify: focused tests under `src/test/java/com/openggf/game/sonic2/objects/`

- [ ] Add RED tests proving an extension participant can arm MTZ subtype-3 proximity and can independently enter/release Obj85 without aliasing native P2.
- [ ] Replace only the two traversal participation policies with `MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED`; retain native list order and existing identity maps.
- [ ] Re-run the focused tests. Leave badnik target-selection policies unchanged because they are not mandatory traversal ownership.

### Task 5: Compatibility audit and verification

**Files:**
- Modify: `CHANGELOG.md`
- Create: `docs/compatibility/2026-07-13-s2-ehz-wfz-dez-remediation.md`

- [ ] Record that event/boss thresholds are world coordinates at 320/352/400/528/800 widths, that tested interactions are contact/position driven, and that no S1 spin-dash fallback is required.
- [ ] Document the missing EHZ2 trace fixture explicitly.
- [ ] Run focused unit suites, `TestRewindCoverageGuard`, and `TestStaticStateRewindCoverageGuard`.
- [ ] Re-run EHZ1, WFZ, and DEZ-ending traces with the same 2 GB fork; all three must remain green. Never hydrate engine state from trace data.
- [ ] Run `git diff --check`, review Flying Battery is untouched, update the changelog, and commit with policy trailers.
