# OpenGGF 0.8.prerelease — Development changelog

Unreleased. Development since the [0.7.20261105](CHANGELOG.0.7.md) release. Add entries
under the matching area heading; see the
[documentation policy](docs/guide/contributing/documentation-policy.md#release-note-destination).

## Gameplay and presentation

- **S3K support notice says beta:** starting Sonic 3 & Knuckles from the hub
  now shows the beta notice, matching the 0.7 release notes. 0.7 shipped with
  the alpha notice. Players who hid the alpha notice see the beta one once.

- **S2 Crawl:** paused proximity includes its zero countdown pass, Super Sonic
  selects ordinary enemy contact, and accepted enemy attacks retire through the
  shared touch owner. The Sonic+Tails chain advances from CNZ2 to HTZ2; later
  route errors and the special-stage return timing differences remain open.


## Modding and tools

## Maintenance and release preparation

- **Contributor readability:** live objects, slots and stable identities share
  population storage; actor reconstruction and contact memory have separate owners
  behind the existing runtime entry points. Retiring multipart solids expires
  owned contact bits and frame history without releasing player state. Contact
  restore discards uncaptured future observations. Production badnik and contact
  witnesses, shared coarse-X arithmetic with explicit native/wider bounds, and
  a typed rewind HUD frame make contribution paths easier to follow. HCZ owns
  its next screen-shake value without using the renderer as scratch state.
  Failed trace replays print their exact test/report/triage route; triage reads
  compressed fixture auxiliaries, preserves pre-trace evidence and distinguishes
  incomplete or unknown completion from a completed comparison. Retained raw-field
  diagnostics follow that storage and are exercised on successful test paths.

- **Scripted website hero reels:** `tools/media/hero_reels.py` regenerates
  the Sonic 1, Sonic 2 and Sonic 3&K title-card reels from the current engine
  with one command. It replaces the hand-cut 0.6 Resolve edits. Each game's shot
  list is a committed manifest. Each shot replays a committed trace fixture or
  BK2 headlessly, and shots are joined with hard cuts. The build fails on a
  missing ROM, a short, black or frozen shot, or a death inside a shot. It
  writes a poster, a contact sheet and provenance (engine commit, manifest hash,
  frame ranges) beside each reel.

- **Trace coverage and canonical fixtures:** independent strict replay covers all
  163 segments of the Knuckles super-emerald, Mega, Sonic+Tails and KiS2 chains,
  plus six previously untested standalone slices. Existing canonical identities
  replace 30 duplicate fixture directories. The S1 complete-emerald chain now
  records native PLC readiness and clears its four queue timing frontiers;
  its ending-load timing remains open. Physics and auxiliary observations
  are preserved exactly. New red frontiers remain visible.
  Special-stage entry honors the recorded team and production lock-on module;
  the single entry-state import consumes S2-family manifest destination rings.
