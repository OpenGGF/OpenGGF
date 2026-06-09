OpenGGF `0.6.prerelease` is an alpha snapshot focused on release hardening, runtime ownership,
trace replay visibility, and the playable Sonic 3 & Knuckles vertical slice. The engine remains a
ROM-backed preservation project: no copyrighted assets are included, and users must provide their
own supported Sonic 1, Sonic 2, and Sonic 3 & Knuckles ROMs.

- **S3K vertical-slice progress:** S3K continues to expand beyond Angel Island into Hydrocity,
  Carnival Night, Mushroom Hill, Marble Garden, and IceCap coverage. The current priority remains
  AIZ -> HCZ route blockers first, then CNZ/MGZ/ICZ traversal, events, palette, animated tile,
  object, and boss parity.
- **Runtime-owned zone frameworks:** shared registries now carry more zone behavior through
  `ZoneRuntimeRegistry`, `PaletteOwnershipRegistry`, `AnimatedTileChannelGraph`,
  `ZoneLayoutMutationPipeline`, `ScrollEffectComposer`, `SpecialRenderEffectRegistry`, and
  `AdvancedRenderModeController`. New zone work should prefer these runtime-owned surfaces over
  zone-local state and direct manager writes.
- **Data select and save system:** S3K data select includes save slots, team selection, host-owned
  progress, and cross-game donation for S1/S2. Save writes are now published through a temp-file
  plus atomic move path to reduce the chance of corrupting a user slot on interruption.
- **Configuration:** runtime configuration lives in `config.yaml`. A legacy `config.json` is
  automatically migrated to YAML on first run, and current user-facing docs now point at the nested
  YAML keys.
- **Release hardening:** `@RequiresRom` is inherited by abstract trace bases, release CI runs the
  trace-replay profile and asserts that at least one ROM-backed trace test executed, and publishing
  the GitHub release is a manual `workflow_dispatch` action while the prerelease version tag is
  static.
- **Known release risks:** the architecture review tracker in
  `docs/release-architecture-review-issues.md` records remaining parity/framework issues. The
  June 9 local release review reproduced the S3K AIZ trace replay as an active blocker; any other
  parity or framework risks should remain explicit deferrals in the tracker before publishing.

See `CHANGELOG.md` for the running list of branch-level changes and
`docs/TRACE_FRONTIER_LOG.md` for trace frontier movement.
