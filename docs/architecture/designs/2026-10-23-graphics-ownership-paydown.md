# Graphics ownership paydown

## Grounding

At base `b97991680`, `ScriptFramesApplyStrategy` bridges the existing
`AniPlcScriptState` into the runtime-owned animated-tile graph. Its sole production
factory is `Sonic2AnimatedTileChannels.fromScripts`, called by
`Sonic2PatternAnimator` after ROM decoding and initial art priming. The graph
passes a channel-specific `ChannelContext`; the strategy advances the script
against the level and resolves `GameServices.graphics()` on each application.
Scripts own their timers, frame indices, copied art and rewind state. Graphics
is an engine-wide owner, replaceable through `EngineServices.configure`.

The compatibility strategy constructor validates but discards its graphics
argument. Commit `e627c6758` explicitly added a regression rejecting use of that
installation-time manager. The test configures the current owner before strategy
construction, so a stronger control must rebind after construction and exercise
the production factory. Commit `22912daed` documents the bridge's purpose:
reuse legacy AniPLC application without rewriting its frame semantics.
`ChannelContext` is a pinned public Mod API record; adding a component is a
public-contract change.

Sprite mapping preparation currently lives in `level.render.SpritePieceRenderer`.
Its public nested `PreparedPiece` resolves geometry, art index, raw tile-word
identity, full palette bank, flips, priority and source-tile clipping.
`GraphicsManager` converts submitted pieces into graphics-owned `SpriteSatEntry`
values, adding priority bucket, exact scanline windows, presentation subject and
ghost style. Mask processing consumes native collection order before tile
expansion. Replay reverses entries and buckets for painter order, then calls back
into the level renderer's pure expansion. The CPU presentation, head-composition,
instanced and direct-command routes share that geometry while retaining their
entry-owned presentation state.

Commit `f77af42a9` introduced the piece boundary so CPU sprite-list masking could
precede tile expansion. Priority/replay fixes `34ac16639` and `b530e96e1`, exact
scanline clipping `0590c42b7`, and ghost preservation `c1d668287` establish
constraints that a package move must preserve. `892292047` explicitly keeps
immutable graphics presentation values free of gameplay dependencies and leaves
level conversion in the producer. No inspected historical source justifies the
specific nesting/package of `PreparedPiece`; treating that location as required
architecture would be an inference without evidence.

## Constraints for the sketches

- Keep execution-time graphics rebinding, native AniPLC progression and initial
  priming. A fixed installation-time manager or a replacement global locator
  does not satisfy the ownership cut.
- Retain raw mapping identity separately from relocated atlas indices; retain
  source column-major art numbering, palette bank, flips and effective priority.
- Preserve SAT order, exact clipped scanlines, mask roles, presentation subjects,
  ghost colour/opacity and scratch-buffer lifetimes across all replay sinks.
- Keep native admission before appearance filtering. Do not merge replay into
  an unrelated open batch or convert native masks to shader rectangles.
- The nested `PreparedPiece`, its constructors/helpers and
  `GraphicsManager.submitSpriteSatPiece(PreparedPiece)` are pinned candidate
  public API. Preserve signatures unless a necessary candidate-surface change is
  explicitly designed and coherently updates policy, version and pins. The goal
  is bounded ownership improvement, not public API expansion.
- Prefer one canonical value/expansion owner with no duplicate synchronized
  representations, type-erased bridges or compatibility paths gaining new users.

## Evidence and limits

Grounding used independent read-only how and source-history investigations.
Direct evidence comes from the cited commits, local design documents and
regression assertions. The original production event motivating engine graphics
rebinding is unknown; the regression establishes the required behavior.
External product analytics, chat and observability are not needed to choose
ownership for these local in-process contracts; no such evidence is claimed.

Relevant controls include `TestScriptFramesApplyStrategyGraphicsRebind`,
`TestSonic2PatternAnimatorGraphAdapter`, `TestSpritePieceRendererSatPreparation`,
`TestGraphicsManagerSpriteSatReplay`, `TestSatReplayBatching`,
`TestSpriteSatMaskPostProcessor`, `TestSatGhostLayering`,
`TestPlayerHeadPresentation`, `TestSpritePresentation`,
`TestSpritePreparationSnapshots`, and the Mod API signature/architecture guards.
Most command-list controls inspect CPU state rather than actual rendered pixels;
offscreen coverage must be identified separately. No tests have run at this
grounding checkpoint.

## Synthesis decision

Two independent candidates (GPT-6-Astra and GPT-6.1-Sol), followed by a
GPT-6.1-Sol cross-judge, converged on a graphics-owned immutable `SpritePiece`
and execution-time animation injection. Both candidates scored 21/25 across
semantics, ownership, compatibility/cost, interface depth and validation. These
are design judgments, not test results; both models share a family.

The user selected **preserve existing signatures**. Adopt candidate B: keep
`GraphicsManager.submitSpriteSatPiece(SpritePieceRenderer.PreparedPiece)` as the
single legacy submission ingress. It copies all 17 geometry/native-word fields
into `graphics.SpritePiece`, then attaches current subject/ghost identity and
submits the `SpriteSatEntry` envelope. The envelope owns bucket, visible scanline
window and presentation identity; its `piece()` owns geometry. Replay consumes
that value directly and calls its sole tile-expansion implementation. No replay
callback into `level.render` or reconstruction of a legacy `PreparedPiece` remains.

Existing producer usage stays:

```java
graphics.submitSpriteSatPiece(preparedPiece); // exact candidate API retained
```

Internal replay becomes value-oriented:

```java
SpritePiece piece = entry.piece();
piece.forEachTile((tile, hFlip, vFlip, palette, x, y) -> {
    // existing sink, clip and batching behavior
});
```

`SpritePieceRenderer.renderPreparedPiece` performs the other explicit conversion
for direct rendering, then uses the same expansion owner. The two boundary copies
are intentional; there must be no second expansion loop, forwarding accessor
facade, synchronized geometry cache, public overload or new API pin. Share
source-window validation without allocating a value merely to validate another.
New graphics consumers must use the canonical value, not expand legacy ingress.

For animation, `ScriptFramesApplyStrategy` receives a
`Supplier<GraphicsManager>` at construction and resolves it exactly once per
`apply`. The S2 composition owner supplies current-runtime graphics selection.
It must not capture the install-time manager, and the public `ChannelContext`
record stays unchanged. Initial script priming, timers and rewind behavior remain
at their existing owners. A production-factory regression changes graphics after
installation and again between applications.

### Accepted guard migration

One retained class direction is not one frozen record. Before this change,
`GraphicsManager` has four sprite-seam records (two expansion calls,
`withDebugSource`, and the submission parameter); `SpriteSatEntry` has twenty
(17 accessors, a parameter, constructor and return type). Moving the conversion
creates new accessor-call identities in `GraphicsManager` while removing old
identities. Review and commit that exact bounded relocation explicitly. Do not
call it prune-only, globally refreeze, rename the rule, or enable automatic store
updates. Publish actual final counts after implementation. The unrelated
`BackgroundRenderer` dependency remains outside this cut.

### Rejected alternatives and checks

Candidate A moved native producers and submission to a public canonical value.
It would remove the whole sprite ingress dependency but change the pinned
submission descriptor and expose a new record/consumer while retaining the old
record. The user chose compatibility, so this candidate is rejected for this
campaign despite the unpublished API permitting deliberate evolution.

Both sketches considered producer-owned executable geometry/expansion ports.
Those split admission metadata from emitted geometry and add callback lifetime
and protocol coordination. An immutable value with one expansion algorithm is
the smaller contract. Avoid recovering the rejected protocol through adapters.

Graft A's explicit no-new-legacy-callers boundary onto B, and use allocation-free
shared validation. Tests must map all 17 fields with distinct values, exercise
2x3 geometry, all flips, clipped/empty windows, raw tile words versus atlas IDs,
palette banks above three and priority OR. Pair CPU SAT collection/replay controls
with identified offscreen pixel coverage: `TestPresentationSpritePixels` alone
does not traverse SAT collection/replay and cannot establish GPU SAT parity.
Implementation follows candidate B in `bac8446dc65c77210d58e00ec9ca5486bd815226`;
menu/injection landed in `b2170f274c0106040298bf5fcb4d81ddc082cc7d`.
The low-level store moves 187 → 181 by removing 23 exact identities and adding
17 ingress accessor calls; frozen rule descriptions/settings and API pins are
unchanged. Isolated graphics validation passed 126 tests across 13 classes
(including signatures and existing EGL pixels); menu/injection passed 18 tests.
All had zero skips. A new actual SAT replay pixel control also passed on the
original base before composition. Combined checks and delivery evidence are
recorded in the [plan](../plans/2026-10-23-architecture-ratchet-paydown.md).
