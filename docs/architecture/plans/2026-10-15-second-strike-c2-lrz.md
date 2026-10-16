# Second Strike C2 — bounded LRZ nozzle packet

Native source/API packet, not a completed LRZ route. Pinned base
`76022570e8233823e1e36419666a0f29c768f4b6`; private branch
`feature/ai-second-strike-c2-lrz`, owned by the campaign lead. Root owns
inventory, genuine compiled exports, SDK consumers and composed integration.

## Contract and native ownership

- `com.openggf.game.sonic3k.difficulty.LavaReefHazardRecipe` is an immutable
  `@ModApi` enum with `STOCK` and `ECHO_JET` only.
- `com.openggf.mods.objects.LavaReefFactory.flameThrower(ObjectSpawn,
  LavaReefHazardRecipe)` is an `@ModApi` static method returning existing
  `AbstractObjectInstance`. Both inputs are required; the exact native object
  ID must be `$29`. No native class or boss base is exported by its return.
- The native owner is `LrzFlameThrowerObjectInstance`, with unchanged stock
  constructor/axis/subtype interpretation. Only authenticated original LRZ2
  records may be bound by later creator content. No content binding is in this
  packet; the factory cannot itself authenticate a placement's level origin.
- ECHO_JET keeps the native first120 firing updates, native emission clock and
  complete subtype idle. It inserts32 warning updates and24 echo firing
  updates before that idle. Native child speeds, fire-shield rules and25-update
  flame lifetime remain authoritative. Missing body art or visibility abandons
  the warning and restores full idle; no warning repeats the emission SFX.
- The cue draws stationary native nozzle frames6/7 with the existing palette
  banks1/2, without a damage-shaped harmless flare or shared palette write.
  Actual rendered visibility and a safe player response are **unproved**.
- Ordinary parent retirement leaves committed flames to expire natively. An
  ECHO_JET owner's native unload cancels pending echo state and marks that old
  owner destroyed. Stock unload remains unchanged. World/quarantine graph
  retirement still needs actual composed-host evidence; the local unload check
  does not certify fault-driven publication/withdrawal.

## Focused evidence

Both commands ran in this private tree with Java25 through its updated queue.
No startup/API/SDK or broad category checks were repeated.

1. Request26137: `maven_queue.py --lean -Dmse=off
   -Dtest=TestLrzEchoJet,TestLrzFlameThrower
   -Dopenggf.headless.gl=surfaceless-egl test -B -q`.
   Terminal0 after1634s admission wait. Fresh XML:4 echo +8 stock tests,
   12 passed, zero failures/errors/skips. This source preceded the ID guard and
   explicit opt-in unload retirement.
2. Build-only test compilation of the follow-up first rejected a wrong test
   import; corrected to the existing same-package native registry, then passed.
3. Request32672, same focused command/flags, terminal0. Fresh XML:
   `TestLrzEchoJet`8 and `TestLrzFlameThrower`8,16 passed, zero failures,
   errors or skips. Includes exact-ID/null rejection, native90-slot exhaustion,
   real-manager recreation/forward replay of the warning and36-child direct
   fixture graph, and native unload with committed-flame expiration. Direct
   cadence fixtures do not step every flame concurrently;36 is their collected
   emission history, **not** an observed production simultaneous-shot budget.

## Open route and acceptance obligations

- Pick authentic original LRZ2 `$29` records by the authored safe response,
  retaining all original fields; do not blanket-bind an ID family as route proof.
- Verify real ROM pixels/readability at production widths320/352/400/528/800,
  zero-ring checkpoint recovery, missed-momentum alternatives and cold routes.
- Demonstrate owner fault/unload through actual composed publication/lifetime
  owners, not only a direct native `onUnload` call.
- LRZ1 traversal, crusher/collapse/switch/elevator combinations, native boss
  sublevel, Knuckles transport and HPZ handoff remain campaign work.
- Opus C2 R1 reviewed the native batch: this remains a bounded native/API dependency packet,
  not the completed C2 wave. No API inventory, descriptor or pin changed here.

Root retains integration. Keep this private tree until its changes are accounted
for; no main merge, push, PR or cleanup is owned by this packet.


## C2 Opus R1 lifetime clarification

Root source audit supports retaining ECHO_JET's pending-warning/echo cancellation
and stale-reference guard. Native committed loc44038..44084 flame remains an
independent hazardous actor (0x98) until its own25-update expiry; no nozzle
teardown deletes it. No STOCK production lifetime change was authorized/made.

34176 ended130 while queued (cause UNKNOWN, no Maven/fresh XML; no owned
execution survived). Root accepted necessary same-source follow-up1450 after
that incomplete terminal, not a duplicate/restart for admission delay.1450:
2cases,1 STOCK retained-call negative/1ECHO_JETpass,0E/skip,55s queue/23.999s
Maven. Actual manager removal removes nozzle/slot; only a deliberately retained
reference then emits30 late stock flames. This does NOT show ordinary manager
scheduling. Committed original30 expire natively under either preset.
Immutable negative source against4f06a5:
`${TASK_HANDOFFS}/lrz-stock-retained-late-dispatch-negative-d958cc48f74e.patch`,
SHA256d958cc48f74efaadf78ade31af17bcfdd7905446f7f6b587c5f5b3eea626dac3.

Next affected2cases retain ECHO_JET no-new-shot/retired-owner assertions and
compare explicit STOCK to absent-recipe native constructor state under the
same real removal/retained calls. They impose no new stock contract. Only
test/docs changed; original335f23/4f06 production remains exact.70592 terminal0:2cases/0F/E/skip,0.551s fresh XML,76s queue/28.530s Maven.
Root owns compiled export/SDK/combined checks; no creator activation/route proof.


## Authenticated opening creator ingredient

LavaReefCampaign.register(ModContext) binds ONLY canonical LRZ2 original0 at
1F7C9C=08D207702910 to ECHO_JET. Every original field is matched; subtype10's
native horizontal axis/idle and all51other nozzles/all rings/terrain remain
unchanged. LRZ1 has no29 and stays OPEN. No common provider call/activation.
54546 sole affected creator registration class:736s queue/26.151s Maven,
22:58:03BST,3PASS/0F/E/skip (0.964s freshXML): exact record/factory, all-field and
foreign-owner rejection, unchanged original inventory/rings, reload idempotence,
LRZ1 stock and other-zone lookalike rejection. No native/API suite repeated.
Actual immutable50133 engineffad06c7+SDK6190e722 paired javac25 and strict CLI
package passed zero findings; LRZ-only proof jar65bf077d3486d98fc0bd859494d06d1e32400a215f973620fee13a13f6c440ec.
This is compiler/admission-source evidence, not runtime safe-pocket/cue/echo
pixels, zero-ring checkpoint, ordinary crossing, real owner-fault or cold-route
proof. Existing native32672/70592 bounded outcomes retain their original scope.
