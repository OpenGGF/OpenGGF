# OpenGGF 0.8.prerelease — Development changelog

Unreleased. Development since the [0.7.20261105](CHANGELOG.0.7.md) release. Add entries
under the matching area heading; see the
[documentation policy](docs/guide/contributing/documentation-policy.md#release-note-destination).

## Gameplay and presentation

- **Sonic 1's Sonic on Sonic 3&K tumble objects:** Sonic 1 has no tumble frames,
  so a donated Sonic 1 Sonic riding a Flying Battery wire cage or another tumble
  object vanished. He now keeps his own walk and run frames there, and a donor's
  own tumble frame base (Sonic 2 Tails') carries into its translated profile.

## Modding and tools

- **Seeded Data Select captures:** `GameplayCaptureTool --save-slots <file.json>`
  writes declared save slots through the production save manager into the
  capture's private save root before a title-first capture, and `--extra-teams`
  sets the Data Select extra team combos for that capture. The Sonic 3&K hero
  reel uses both to show custom-team saves. Title-first headless boots also run the
  donated Data Select warm-up that Engine runs before its startup mode, so a
  Sonic 1 or Sonic 2 capture with S3K donating shows the generated zone previews
  instead of S3K's placeholders.

## Maintenance and release preparation

- **Scripted website hero reels:** `tools/media/hero_reels.py` regenerates
  the Sonic 1, Sonic 2 and Sonic 3&K title-card reels from the current engine
  with one command. It replaces the hand-cut 0.6 Resolve edits. Each game's shot
  list is a committed manifest. Each shot replays a committed trace fixture or
  BK2 headlessly, and shots are joined with hard cuts. The build fails on a
  missing ROM, a short, black or frozen shot, or a death inside a shot. It
  writes a poster, a contact sheet and provenance (engine commit, manifest hash,
  frame ranges) beside each reel. The reels keep the 0.6 cut's feature beats as
  well as its zones: live rewind undoing a hit or a fall, sidekick chains (ten
  Sonics in Scrap Brain, a crowd at the Emerald Hill 2 boss), borrowed art and
  Super Sonic in Sonic 1 and Sonic 3&K, Knuckles in Sonic 2, a Casino Night
  jackpot, custom-team Data Select saves, Sonic 2 saves in Sonic 3&K's donated
  Data Select, and every Sonic 3&K zone with its bonus
  and special stages. `GameplayCaptureTool --rewind FRAME:LENGTH`
  holds the live rewind key over a window and draws the rewind plate,
  `--emeralds` also takes Sonic 1 and Sonic 2's six emeralds, and
  `--title-screen` now accepts `--donor`, since boot activates the donor before
  the title.
