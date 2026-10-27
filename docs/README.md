# OpenGGF documentation

OpenGGF's documentation is organized by audience and purpose. Start with the
[user guide](guide/index.md) for playing, creating, contributing or cross-referencing.
Live guides follow the `0.7.prerelease` development line; dated engineering and
release records describe their recorded commits, not current verification.

Level authors can start with the [level editor guide](guide/creating/level-editor.md)
for project creation, safe saves, playtesting, and all-game exports.
Film and cutscene authors can use [OpenGGF Stageplay](guide/creating/stageplay.md)
in the engine or the external editor: start with
[your first film](guide/creating/stageplay-first-film.md), then the
[directing guide](guide/creating/stageplay-directing.md) and
[reference](guide/creating/stageplay-reference.md).

Level implementation and validation: [zone and act test standard](guide/contributing/level-test-standard.md),
[coverage backlog](status/level-test-coverage.md), and
[standardisation plan](architecture/plans/2026-09-13-level-test-standardisation.md).

Maintainers publishing a release should start with
[Publishing a GitHub release](project/release-publishing.md): a push to
`master` automatically publishes after validation and builds succeed.

- [Agent workflow](agent-workflow/) contains agent procedures, runbooks, and
  reusable prompts.
- [Architecture](architecture/) contains current architecture references and
  dated engineering artifacts.
- [Assets](assets/) contains presentation and documentation assets.
- [Changelog](changelog/) contains detailed release history.
- [Guide](guide/) contains player, level-author, contributor, and ROM cross-referencing
  documentation.
- [Modding](modding/index.md) contains the unpublished candidate API handbook,
  formats, quickstarts and maintained samples.
- [Project](project/) contains project history, roadmaps, and process
  direction.
- [Status](status/) contains active bug, discrepancy, trace-frontier, and
  release-state ledgers.

Architecture artifacts are classified by the question they answer:

- [Designs](architecture/designs/) — what should be built?
- [Plans](architecture/plans/) — how will it be built?
- [Research](architecture/research/) — how does the system or source material
  work?
  The [multiplayer Time Attack reference](architecture/research/multiplayer-time-attack.md)
  maps components, room and attempt lifecycles, connection routes, verification
  outcomes and cleanup, with separate integration diagrams.
- [Audits](architecture/audits/) — what state is the project currently in?
  The [interactive source atlas](architecture/audits/project-source-atlas.html)
  maps components, classes, historical file presence and Git activity;
  [regenerate it on demand](../tools/project_atlas/README.md).
  See the [test value and resource cost audit](architecture/audits/testing/2026-10-23-test-value-cost.md)
  for test classifications, measured improvements and ranked redesigns.
- [Validation](architecture/validation/) — did an implementation meet its
  contract?
  The [Sonic TAS trick verdicts](architecture/validation/2026-10-26-tas-trick-verdicts.md)
  link published author notes to executed support and limitation checks.

External trace producers and analysis utilities are documented in the pinned
[`OpenGGF/TraceChaser`](https://github.com/OpenGGF/TraceChaser) submodule under
`tools/tracechaser/docs/`. OpenGGF's dated extraction design, plan, inventory,
and validation records remain here as historical engineering evidence.

Domain names such as audio, testing, trace, performance, or S3K zones may
appear as subfolders inside one of those categories. They do not form a
parallel top-level taxonomy.

Audio: the sound-driver reverse-engineering artifacts (driver maps, gap
analysis, behaviour specs, and oracle records) are indexed in
[architecture/designs/audio/2026-08-30-sound-driver-re-index.md](architecture/designs/audio/2026-08-30-sound-driver-re-index.md).
