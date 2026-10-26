# S2 Sideways: safe admission and owned-child lifetime

Status: **physical-storage target superseded by the reconvened council outcome below**.
The earlier design is retained as research, not an implementation instruction.
The runtime implementation and focused qualification are tracked in the
[repair audit](../audits/2026-10-24-rewind-disposition-review.md#sideways-safe-admission-and-owned-child-retirement-2026-10-25).
Historical research source baseline:
`d3fa3d519f6acb7285d538b5f972807036f1019e`.

## Reconvened council: bounded engine repair, October 25

The user rejected memory emulation as contrary to the project's goals and asked
for a safe engine repair without reproducing the Sideways ROM corruption.
Astra, Fable 5.1, GPT-6.1 Sol and Opus 5.5 independently reconsidered the prior
proposal on source `61be1910ac29d5606410be315ec881a5745a75d3`. All four recommend
a narrow, named compatibility exception. The council recorded the implementation handoff; its read-only recommendation
is separate from the later implementation/qualification below.

### Decision and scope

Do not implement the physical SST authority, fixed-tail byte cell, occupant-byte
views or generalized residue model proposed below for this repair. Preserve the
native research and rejected-candidate evidence. Fix the invalid Java ownership
created by Obj7A child-admission failure, deliberately omitting the ROM writes
through its failed-allocation pointer into Tails's fixed storage. This is an
explicit exception to shipped-ROM fidelity, not a global `FixBugs=1` selection.
There is no disassembly fixBugs branch selecting this behavior.

The [TAS submission 10042 author notes](https://tasvideos.org/10042S) describe
object-exhaustion respawn-table desynchronization, duplicated bosses and an art
queue overflow that changes lock-on mode. They illustrate how corruption
compatibility can spread across systems. They do not prove that this BK2 could
never be supported, that Sideways fidelity is impossible, or that ordinary
semantic ROM bugs should be corrected. Failed spawns, slot order, respawn state
and other modeled behavior remain fidelity obligations outside this exception.

### Observed engine defect and required outcome

The private Sideways child constructor publishes reciprocal links before
`addDynamicObjectAfterSlot` succeeds. Exhaustion destroys the candidate before
identity registration, leaving the parent's link pointing at an unregistered
object. Strict rewind correctly rejects that graph. The native failure instead
writes into the 64-byte Tails-tail cell and redirects its parent pointer.

The repair must leave the initialized parent with its existing bounds, direction,
X offset and movement, no rejected-child link, no consumed child slot and no
writes to Tails. Do not retry until an ordinary fresh placement incarnation.
Normal endpoint retirement must still release its remember bit and occupied slot.
Successful pairs retain ordinary movement, collision, initialization, slot order
and later-in-the-same-walk execution. Preserve the landed independent tail
animation/previous-animation and `$FD` return prerequisite.

Prefer reservation before construction, with reciprocal links published only
after successful admission. The council disagreed whether existing anchored
reservation facilities are sufficient or require a bounded adapter. Establish
this from actual callers before choosing an API. A simple post-admission unlink
is acceptable only if construction, service binding and Stageplay admission have
no unacceptable rejected-candidate side effects. A destroyed flag alone does not
distinguish pool exhaustion from Stageplay rejection. Do not substitute ordinary
allocation for anchored allocation or expose an accidental public Mod API.

### Council lifecycle alternatives

Allocation failure does not settle independent removal of an already admitted
pair. Astra recommends parent removal also retires the child; Sol recommends
unlinking either survivor without a new cascade; Opus recommends pair retirement
because the child has no independent offscreen culling. Fable's narrow failure
repair does not resolve all admitted-pair removal paths.

The implementer must examine raw removal, marked-but-unswept destruction,
Stageplay/editor removal, bulk reset and restore before selecting and documenting
bounded semantics. Do not silently adopt either a cascade or an immortal orphan.
Do not target an unrelated replacement occupant through an old Java reference.
Native cleared/reused-slot cross-writes remain observed evidence, not permission
to broaden this repair into memory emulation. Any additional deliberate lifetime
deviation must be named explicitly with its effect on supported content.

### Implemented bounded lifetime contract

The implementation starts from current main `08f0a5e78b4c7aaa11604e6e9eb9f2cb4151daa4`,
with no shared allocator/API changes. Existing
`ObjectLifetimeOps.reserveFindNextFreeChildSlot` reserves strictly after the
parent, preserving the distinction from competition-limited plain allocation.
`ObjectConstructionContext.with` gives the candidate that reserved slot before
construction; construction initializes only its local state. The existing
anchored admission then supplies services, identity and later-slot same-pass
execution. Only registered admission publishes the two links.

On exhaustion no candidate exists. On Stageplay rejection its existing retirement
path releases the reserved slot and clears the candidate's slot index before
callbacks can install replacements. Local cleanup does not release it again.
Exception cleanup removes only an admitted candidate identity, or releases its
still-owned reservation after clearing the saved index; it never frees another
current occupant through an old slot number. A destroyed flag is not an admission
result. Unmanaged/bootstrap, probe and active-restore receivers create no live
companion; ordinary rewind reconstruction restores captured initialized scalars
and exact references without reinitializing or applying offsets twice.

The selected lifetime is asymmetric: parent retirement detaches both edges then
marks its owned child for ordinary sweep; child retirement detaches both edges
and leaves the initialized parent moving and endpoint-culling alone, with no
retry. This applies to raw manager removal, destruction markers before sweep and
Stageplay/editor retirement. Offscreen parent retirement retains normal remember
release and respawn. Repeated retirement is idempotent. Bulk reset discards the
old graph, and retained references can affect only those old Java instances,
never restored or reused-slot replacements. This additional deliberate deviation
omits native cleared/reused partner cross-writes without introducing a generic
cascade across object families.

The [accepted discrepancy](../../status/known-discrepancies.md#s2-sideways-platform-safe-admission-and-owned-child-retirement)
is the durable compatibility contract. The [repair qualification and affected-act
matrix](../audits/2026-10-24-rewind-disposition-review.md#sideways-safe-admission-and-owned-child-retirement-2026-10-25)
distinguish observed mechanics from inherited route/competition/trace gaps. The
historical 17 passing controls/two failures below belong to the rejected storage
candidate and are not evidence for this repair.

### Rewind, verification and documentation handoff

Keep strict identity validation. Capture failed-admission state as an initialized
parent with a legal absent/self partner representation; restore it without
rerunning initialization, retrying allocation or applying the X offset again.
Healthy pairs must reconstruct fresh reciprocal identities. Malformed dangling
references must still fail. Verify whether initialization state is already captured.

Focused controls must cover exhaustion and no retry, unaffected Tails, normal
pair admission and dispatch order, endpoint/remember cleanup, either-member
removal and genuine slot reuse, destructive reconstruction and forward replay.
Check Stageplay rejection and unmanaged/bootstrap callers separately; qualify or
state the two-player gap. Update existing known-gap assertions into positive
repair assertions only when the behavior is implemented and checked. The earlier
17 passing controls and two failed deletion/reuse controls remain counterevidence
to the old candidate, not validation of this new proposal.

Keep trace comparators unchanged. The historical S2 SST recording range excludes
the fixed tail cell, but that does not prove corruption has no downstream compared
effects. A corruption-dependent replay remains visibly divergent and attributed,
never masked, hydrated or declared passing. Natural route frequency is unknown.

At implementation, add a named accepted discrepancy with native consequence,
engine behavior, citations and trace limits; revise the open-bug closure criteria,
active changelog and affected coverage obligations. Mirror the explicit narrow
fidelity exception in AGENTS.md/CLAUDE.md as needed. Existing S3K failed-allocation
and coordinate-link precedents require a separate disposition, not silent removal
or a blanket retrospective policy. Revisit this decision if ordinary supported
routes materially depend on the omitted behavior or a bounded semantic solution
meets the project boundary without memory emulation.

The council was read-only and ran no builds, tests or native probes. Its agreement
is a design recommendation; no vote substitutes for the implementation checks.

### Demonstration and evidence limits

A follow-up capture at source `61be1910a` uses the committed fixed-tail probe
with two temporary diagnostic adaptations: advance every hardware frame through
TraceChaser's published-video API, and position the constructed parent at camera
Y + `$80` instead of the original below-viewport `$200`. Setup still uses the
last dynamic slot, subtype `$06`, camera X + `$A8`, synthetic remember index and
original CPZ2 BK2 inputs. These are declared RAM setup writes, not a natural
route or a production engine repair. Subsequent failure/copy/deletion executes
unmodified S2 REV01 code. The selected case is `live-last`.

Original ROM SHA-1 is `8bca5dcef1af3e00098666fd892dc1c2a76333f9`;
movie SHA-256 is `7e28cf822d5dbbe64646965cd857f264bf51d3349075af94db1a818cac7311e4`.
The host is built from TraceChaser `322851cbfa2e1c1803332142e11ab74b4eb34eea`
against the existing BizHawk 2.11 installation with strict preflight. No desktop
or audio device is used. Preparation and native execution use the existing
build/activity and test-admission leases. The native process exits zero with
361 continuous frame-end observations and no unmet assertions. Setup is BK2
input row 8791; the finite window ends at 9151. The tail parent changes from
`$B040` to `$CFC0`, 153 native tail passes consume the platform parent, and
retirement clears the fixed tail cell at relative sample 153. Queue wait is
19 seconds and native process execution 10.43 seconds. This is new rendered
observation, not a claim that historical measurements were rerun unchanged.

The engine side was independently reproduced on unchanged source `61be1910a`
with Java 25 using `python3 tools/testing/maven_queue.py -Dmse=off
'-Dtest=TestS2SidewaysPformAdmissionQualification#characterizeRealAdmissionAndFreshReplay'
test -B`. All nine parameterized cases passed with zero failures/errors/skips;
these are known-gap characterization assertions, including the expected exact
strict-capture rejection, not a repaired engine. Queue wait was 19 seconds;
Maven reported 1:22 including compilation. The engine panel describes this
observed assertion path, rather than inventing a gameplay crash.

The external task directory is `sideways-demo-20261025`; the delivered
`sideways-rom-and-engine.mp4` contains 361 native frames presented at 30 fps,
about half speed, with state annotations. Its engine section is explicitly an
explanatory panel of the Java graph failure, not rendered engine gameplay or a
before/after repair comparison. Native state readings establish the corruption;
individual sprites may be occluded, missing or visually ambiguous. Do not infer
natural frequency, ordinary-route reachability or pixel parity from this demo.
The durable video and diagnostic adaptation are outside the repository; original
ROMs and raw generated host/build output are not committed.

## Historical physical-storage proposal, superseded

Use one physical SST authority mounted in each ObjectManager, with a canonical
64-byte fixed tail cell and complete contracts for the fields of every supported
occupant. Native links denote coordinates for successful and failed allocation.
Existing logical fields may remain their sole authoritative values when an exact
native-width view and cross-write can be proved; do not add synchronized copies.
Empty/unowned storage retains explicitly recorded bytes. Missing representation
must fail completeness checks before activation, never throw on a supported
runtime reuse path.

This takes Opus's address-based shape, Astra's distinction between storage,
incarnation and authority, Sol's controlled restore composition, and Fable's
literal fixed-cell operations. All four initial proposals had unresolved
completion gates. Their useful components do not constitute a tested repair.
The [known bug](../../status/known-bugs.md) remains open. The
[rewind audit](../audits/2026-10-24-rewind-disposition-review.md) records accepted
native observations, the animation prerequisite, and the rejected candidate's
17 passing controls and two failing raw-deletion/reuse controls.

## What the native program requires

`AllocateObjectAfterCurrent` exhausts the dynamic range with A1 at `$FFFFD000`,
the fixed `Tails_Tails` cell. Obj7A skips child initialization but still writes
links, bounds and X there. A rejected Java child cannot represent that result;
strict rewind correctly rejects its dangling identity.

The long write at tail `$3C` overwrites Obj05's parent word at `$3E`. A parent in
SST127 leaves `$CFC0`, sign-extended by `movea.w`. Word arithmetic wraps at native
width without modifying adjacent fractional bytes. The fixed cell is 64 bytes,
not 80. Native `DeleteObject2` clears all 64; zero then follows an actual write.
Obj02 installation writes ID and parent only and must not clear other bytes.

Obj05 reads thirteen bytes in eight parent fields: art word `$02`, X/Y words
`$08/$0C`, velocity words `$10/$12`, and animation/status/angle bytes
`$1C/$22/$26`. A replacement's aliases must be interpreted as those same bytes,
regardless of its Java class or their meaning in its own routine.

**Successful pairs also need physical links.** The child reads its parent's X
and toggles byte `$36` through the address in `loc_2953E`. If a supported engine
operation retires only the parent, the child subsequently reads/writes the empty
or reused slot. Conversely, parent endpoint cleanup deletes the current occupant
of its child's coordinate. Keeping an admitted-pair Java reference would retain
stale incarnation behavior. Byte `$36` and physical endpoint deletion therefore
belong in the domain alongside the thirteen-byte tail read, even though they
were not needed by the first failed-allocation control.

Pinned reference owners, at S2 disassembly
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`:

- `AllocateObjectAfterCurrent` (s2.asm:33705-33725).
- `Obj7A_SubObjectLoop` (s2.asm:56313-56351).
- `loc_294C4` (s2.asm:56374-56387) and `loc_2953E` (s2.asm:56428-56440).
- `DeleteObject2` (s2.asm:30334-30345).
- `Obj02_Init_Continued` (s2.asm:38934-38946).
- `Obj05_Main` (s2.asm:41723-41764).

Model `FixBugs = 0`. A natural sidekick death does not itself reinstall Obj05;
the accepted reinstallation observation used declared synthetic setup. It still
qualifies the literal write interaction, not every engine installation caller.

## Storage and lifetime authority

The proposed `NativeSstAuthority` is an ObjectManager component and a component
of its existing snapshot. It owns the complete fixed cell, explicit clear/residue
state, coordinate resolution and physical cross-operations. This avoids a new
independently ordered rewind adapter and a wholesale move of player physics and
all object fields into a central byte array.

Its three identities are distinct:

- A physical address is the native coordinate, interpreted within an explicit
  world lineage. It survives occupant reuse.
- `ObjectRefId` identifies a Java incarnation for reconstruction and other strict
  graphs; it cannot decide which occupant a native pointer addresses.
- An engine-issued capability authorizes access for the current lineage,
  manager mount epoch and live owner. Retirement, remount and destructive restore
  revoke old capabilities, even if restored IDs and coordinates are equal.

Manager ownership does **not** mean manager replacement clears RAM. A native
clear is a named operation in the load contract. Same-world preservation carries
one immutable image and remounts fresh capabilities; the retired manager cannot
remain a live writer. Parking preserves the manager/inventory image together.
New-world replacement creates a distinct lineage. No controller resolves an
ambient current manager to regain authority.

```mermaid
flowchart TD
    M[ObjectManager: slots and incarnation identity] --> N[Physical SST authority]
    W[World lineage and mount capability] --> N
    O[Single-owner live native fields] --> N
    R[Explicit clear and unowned residue] --> N
    N --> T[Canonical fixed tail cell]
    N --> L[Obj7A address reads, writes and deletion]
    T --> E[Obj05 fixed-slot execution]
    E --> A[Existing DPLC and presentation owners]
    S[One composed object snapshot] --> N
```

## Complete occupant contracts

A contract identifies the existing canonical field or storage atom for every
native byte this domain can observe or modify. It must preserve big-endian byte
lanes, aliases, signed interpretation, partial writes, initialization order and
read-before-write residue. A getter that returns a plausible class default is
not such a contract. Neither is a list of fields the class sometimes writes:
conditional writes and pre-initialization reads still need exact state.

For a modeled live atom, the native view reads the actual authoritative field,
and an in-domain cross-write mutates that owner. No per-frame export, second live
header, or restore copy-back is allowed. If a needed atom is absent, add it to
its real owner or revise the boundary; an adapter cannot invent it. Unowned
bytes belong to explicit residue storage. At admission the authority transfers
any required residue into the new field owner once, before native initialization;
it does not overwrite unwritten bytes with constructor defaults. Stage 0 must
prove this binding order without a hidden journal of constructor activity.

Vacate clears the represented storage through the modeled operation and revokes
the occupant. A missing live object is not by itself evidence of a clear:
reserved slots, native subobjects and unowned storage must have declared states.
Engine raw retirement currently follows a zero-slot policy; ObjectManager's
removal comments and the S3K MonkeyDudeArm precedent support that **engine policy**.
They do not prove native reachability or exact representation of arbitrary
replacement occupants. Record the clear when it happens, rather than fabricating
zero during a later failed lookup.

Stock registry entries, donor/player roles, reserved-slot participants and
supported creators must all satisfy this contract before activation. Derive the
coverage check from actual registrations, not a parallel exception allowlist.
A runtime unknown-class throw cannot be reclassified as a successful repair.

**Creator policy proposed by this design:** engine-issued own-body semantics
supply position, art priority and native status components; velocity, animation,
angle and other bytes are written only where their semantic owner provides an
explicit operation. Unwritten bytes retain modeled residue. Do not treat a live
creator's position as opaque zero, trust creator-supplied ownership, or infer
unmodeled fields from defaults. Existing getter-overriding creators require an
explicit migration or a proved fault-scoped field binding. This is a candidate
API contract change if current interfaces cannot express it; audit examples,
descriptor and normalized pins together. It is not already supported behavior.

## Caller shape

These are proposed interfaces, not executable code or verified API signatures.
The complete admission operation hides reserve/bind/construct ordering.

```java
NativeAdmission child = objects.admitAfter(parentCapability, childRecipe);
switch (child) {
    case Admitted(var address) -> s2Sideways.initializePair(parentAddress, address);
    case Exhausted(var returnedAddress) ->
            s2Sideways.applyFailedStores(parentAddress, returnedAddress);
}
// Both outcomes retain a physical coordinate, not a Java child handle.

s2Sideways.moveChild(childCapability, linkedAddress); // native X read and $36 XOR
s2Sideways.retireAtEndpoint(parentCapability, linkedAddress); // delete current target

nativeSst.dispatchTail(nativeBoundary, dynamicArtOwner);
// No playable/renderer prerequisite for native execution or DPLC decisions.

registry.restore(snapshot); // one existing public restore entry point
```

Reservation occurs before construction so exhaustion builds no rejected child.
Preserve the established slot order and later-in-the-same-walk execution. Keep
unmanaged standalone objects explicit; a detached pair has no physical allocator
failure. Existing preconstructed admission APIs need a documented compatibility
path before migration, not silent default-header transfer.

Keep byte offsets and native instructions in S2-owned code. Shared admission and
lifecycle code consumes a module-supplied capability rather than a game-name
condition. Creator callers cannot obtain another actor's raw address or writer.

## Fixed execution, ROM reads and presentation

Obj05's animation state lives in the 64-byte cell. Preserve the landed independent
`anim`/`prev_anim` and `$FD` return. Host-native installation is explicit; character
class, renderer creation and donated art do not install a fixed object.
Detached S3K, extra-team, puppet, bonus and standalone tails retain their own
state and can share the animation implementation without sharing RAM ownership.

The cross-review inspected the Obj05 animation paths and found parent reads in
the thirteen-byte domain and self reads within the 64-byte cell. An unsigned
parent animation byte can index beyond `Obj05AniSelection`; the subsequent
signed ROM-table displacement remains a ROM read, not additional parent RAM.
Clamping at `$20` or substituting Blank is unjustified. Use address-faithful reads
from the ROM pipeline. The remaining mapping/DPLC/DMA range and published VRAM
consequences still require the Stage 0 proof; source inspection of the animation
routine is not pixel qualification.

Fixed-slot dispatch follows the native loop and cell ID even when the original
player or renderer is absent. It submits art decisions to the existing
`DynamicArtLifecycleService`. Drawing derives position, flags, mapping and art
from the cell, not its former parent's Java position. Cell deletion does not
clear the separate native DPLC dedup register, cancel accepted DMA work or reset
renderer caches indiscriminately. Art resource rebinding cannot reinstall RAM.

## Clear, carry and restore contract

| Event | Required behavior |
| --- | --- |
| Native level RAM clear | Named clear operation at the verified load boundary; invalidate old cell capabilities. |
| Obj02 install | Write only ID and parent; execute after the last real clear and before the first fixed dispatch. |
| Camera-snap, bootstrap or prelude reset | Classify its actual semantics; do not equate every `reset*` with RAM clear. |
| Same-world manager replacement or exact carry | Carry the represented image and inventory explicitly; retire old authority and issue fresh mount capabilities. |
| New world | New lineage; prior handles cannot address it. |
| Park/editor stash and reopen | Preserve a coherent inventory/storage image; restoration is distinct from starting a fresh level. |
| Art/ending resource binding | Change presentation binding only for a bound native cell; detached mode preserves its existing state contract. |
| Raw/Stageplay retirement | Explicitly clear the retired slot under engine policy; no invented linked-target cascade. |
| Obj7A endpoint retirement | Apply native physical deletion to the current linked occupant, then the parent's native cleanup. |
| Roster change | Preserve or retire/install through the explicit native-role policy; surviving fixed execution does not require a Tails presentation. |
| Destructive rewind | Reconstruct under suspended effects, restore canonical fields/residue/cell once, then publish fresh bindings. |

Art initialization currently precedes ObjectManager construction. Therefore a
controller built there cannot capture a manager cell as an incidental constructor
side effect. Bind through the later explicit native lifecycle and retain a typed
unbound/private/manager-owned state, with validated transitions.

Inside ObjectManager restore, reconstruct identities and ordinary canonical
fields under suspended publication, restore residue and the fixed image, and
then expose fresh capabilities. Assert that reconstruction has not replayed
native initialization stores such as Obj7A's X addition. Resource callbacks must
not write the cell afterward. The image has one restore owner; object fields and
residue have disjoint ownership, not overlapping copies whose ordering happens
to win.

Use an explicit snapshot ownership variant such as `Private(state)` versus
`ManagerOwned(binding)`. `restoreRewindState(null)` currently resets a tail, so
null cannot silently acquire an ignore meaning. Remove bound RAM copies from
player/native-art/ending adapters; preserve detached snapshot semantics.
Direct controller restore in bound mode must fail clearly or be unavailable in
the revised API, rather than become a second writer. This is a deliberate
compatibility obligation, not a reason to hide a signature change.

## Council decisions and rejected approaches

Four independent designs came from GPT-6 Astra, Claude Fable 5.1, GPT-6.1 Sol and
Claude Opus 5.5. A fresh Opus cross-review read all four and checked the pinned
source. Parent synthesis accepts its physical-link finding and narrower target,
with stricter lifecycle provenance and byte-ownership conditions. No ranking or
model vote substitutes for those conditions.

| Candidate | Retained contribution | Rejected or unresolved element |
| --- | --- | --- |
| Astra: world-owned header storage | Coordinates/incarnations/capabilities; single authority; alias and lifecycle gates. | Moving all shared/player fields into one world array is not yet justified; admitted Java pair links miss physical reuse. |
| Sol: canonical header authority | Restore staging, parking and separate art/dedup ownership. | Constructor journals and a second bound animation restore path; broad migration without a complete census. |
| Fable: fixed-cell authority | Literal fixed cell, explicit install and direct native deletion. | Unsupported occupants, lookup-time zero, construct-before-admission and implicit reset assumptions. |
| Opus: coordinates and ports | Physical links for both outcomes, typed exhaustion, cross-delete/XOR residue. | Runtime unknown-owner failure, creator opacity, animation cap, null/no-op restore and renderer-gated DPLC. |

The initial fixed-tail designs were honest partials, not complete repairs.
The selected target requires total contracts before activation. If live field
views cannot satisfy that requirement without synchronized state, revisit a
canonical field migration for the demonstrated atoms; do not quietly add a
fallback. A general CPU/RAM emulator and a blanket rewrite of all private state
remain unjustified.

## Stage 0: required before runtime implementation

Record these proof products in the existing audit/design, not new report types:

1. **Consumers:** finish the address-based ROM mapping/DPLC/DMA proof after the
   animation paths. Identify every mutable read/write and bound each access.
   Published VRAM behavior is a separate qualification obligation.
2. **Writers and aliases:** derive a per-native-ID inventory for the thirteen
   parent bytes plus `$36` and physical deletion. Check it against actual stock,
   donor, reserved-slot and creator admission. Map conditional/partial writes,
   read-before-write residue and cross-write aliases to one canonical owner.
   A generated disassembly write list is a starting aid, not semantic proof.
3. **Lifecycle:** classify construction/reset/rebuild, camera re-reset, teardown,
   trace bootstrap, Stageplay/editor, park/reopen and carry callers as native
   clear, preserve/carry, snapshot restore, engine-retire-zero or presentation.
   Prove native installation follows the last clear and precedes dispatch.
4. **Snapshots and authority:** enumerate all tail/header snapshot writers and
   callbacks; prove reconstruction ordering, disjoint field/residue ownership
   and revocation of stale capabilities through restore and carry.
5. **Creator/API migration:** settle the proposed own-body write contract and
   supported preconstructed callers; enumerate exact interface, descriptor,
   signature and example changes. No silent supported-content narrowing.
6. **Reachability and scope:** explicitly cover supported retirement of either
   admitted pair member. Bound other stale-reference cross-writes that intersect
   this domain. Assess the two-player Obj7A branch separately; the present
   single-camera evidence does not qualify it or justify removing support.

No candidate is ready until this gate passes. An unclassified caller, unsupported
registered occupant or conflicting state owner blocks activation. Thirteen bytes
plus one fixed cell describes an initial consumer footprint, not a proven total
migration size. Natural campaign frequency is still unmeasured.

## Smallest complete implementation after Stage 0

Develop the following together in one isolated branch and qualify the complete
slice before replacing the current known-gap assertions:

1. Canonical fixed cell, explicit empty/residue states, named clear/carry and
   composed snapshot ownership with reconstruction-write assertions.
2. Reservation-before-construction, physical links for both allocation outcomes,
   native failed stores, child X/$36 operations and endpoint deletion.
3. Authority-run Obj05, ROM-addressed animation/mapping behavior, explicit native
   install/bind, independent DPLC execution and typed snapshot ownership.
4. Total occupant contracts and admitted single-member retirement/reuse, including
   supported creator and reserved-slot behavior. Certification is a prerequisite,
   not a runtime unsupported-type branch.

Meaningful controls start with the rejected candidate's two cases: clear without
reuse, and genuine registry-admitted replacement with nonzero fields and aliases.
Use real native occupants covering the required values rather than inventing an
impossible stock object with every byte nonzero. Include animation above `$20`
where the audited native state can produce it, plus explicit raw byte controls
for ROM consumer coverage. Cover parent-only and child-only retirement, a `$36`
write into an empty slot and its subsequent admission, two failed parents,
reinstallation, only-earlier holes, last-slot pressure and admitted same-walk
ordering.

At each boundary, destructively reconstruct and compare forward execution;
check exact bytes, identities and expired external capabilities. Retain the
literal `$FD` prerequisite, detached/S3K behavior and existing tail rendering/
DPLC checks. Deliberately broken controls must expose zero-on-reuse, dropped
residue, replayed X addition, a player/art snapshot overriding the cell, art
refresh clearing it and DPLC suppressed without a renderer.

Reuse the accepted native pressure observations. New native work should target
missing lifecycle, ROM-consumer or presentation facts. Shared admission/lifecycle/
API changes require focused ROM, rewind and candidate-surface checks and one
lead-owned change-based smoke selection; no release-only full suite is prescribed.
The council ran no implementation tests.

Reject the target if a supported occupant needs fabricated bytes, a synchronized
shadow field, a runtime unknown-owner exception, an invented cascade, an
unclassifiable reset or a second restore writer. Preserve the bug and explicit
coverage limits until the full replacement contract is proved.
