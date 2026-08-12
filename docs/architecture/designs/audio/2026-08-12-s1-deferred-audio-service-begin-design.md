# S1 Deferred Audio Service Begin Design

## Status and scope

This design is approved for the Sonic 1 complete-run reference observer. It
addresses the exact row-8775 ordering proved on the REV01 ROM and the all-
emeralds BK2. It does not generalize the observer to overlapping service sets,
does not grant child ownership to the Z80 sample-setup service, and does not
hydrate engine state from the trace.

The physical ordering is fixed evidence:

1. M68K kind-4 `UpdateMusic` ends at native ordinal 12, PC `$71C4C`.
2. Z80 kind-6 `zCheckForSamples` begins as a root at ordinal 13, PC `$003A`.
3. M68K reaches `$71B4C` three times with the same A7 `$FFFDB2` and return PC
   `$000B64` while kind 6 remains the root.
4. Kind 6 tails into DPCM and ends at ordinal 20.
5. Exactly one new semantic kind-4 service must begin at ordinal 21.

The three M68K callbacks are genuine loop re-entries, not callback duplicates.
The previous kind-4 token is already closed, so neither direct-parent retry nor
recently-closed ancestry is truthful.

## Chosen model

Add one bounded deferred-begin reservation to the native observer. A new ABI-v3
hook action, `ACTION_DEFER_BEGIN_UNTIL_TOP_END`, is valid only for an exact M68K
hook whose expected active kind is a non-child-bearing blocker. For S1 the hook
is `$71B4C`, expected kind 6, target service kind 4.

The first matching callback reserves the future service begin. Every matching
callback emits `EVENT_HOOK_MARKER` value 4 and leaves the active stack unchanged.
The reservation is keyed by blocker token, hook token, CPU, PC, opcode proof,
target service kind, and managed A7/return identity. Subsequent callbacks must
match every key and are coalesced into the same reservation.

When the exact blocker token ends, native capacity is reserved atomically for
the blocker's normal snapshots/end and one service begin. Native emits the
blocker END and then immediately emits one ordinary `EVENT_SERVICE_BEGIN` for
the deferred target at the next ordinal. The new service is a root with a new
token, parent zero, depth zero, and the original `$71B4C` hook/PC/source proof.
There is no release marker: the adjacent BEGIN consumes the outstanding raw
marker reservation. This preserves the required semantic begin at ordinal 21.

## Native invariants

- Capacity is exactly one deferred reservation per observer session.
- The expected blocker kind must not have `ALLOW_CHILDREN`; otherwise configure
  rejects the action because an ordinary nested begin would be source-valid.
- The action has no snapshot range, predicate slice, reserved payload, or stack
  mutation at observation time.
- While reserved, another matching callback is permitted; a different M68K
  hook, a second reservation, a reset, or an unowned M68K chip write fails
  closed. Z80 events owned by the blocker remain valid.
- Only the exact reserved blocker token may release the reservation. The END
  and deferred BEGIN are adjacent and capacity-atomic.
- Abort preserves first-fault diagnostics. Disable clears the reservation.
- A frame may end with the blocker and reservation still open. Continuation
  aging applies only to the real blocker service, not the unbegun service.
- Cutoff diagnostics retain the reservation; semantic cutoff state does not
  invent an active service before release.

## Managed correlation

`S1CompleteRunAudioReferenceCapture` buffers every `$71B4C` callback. Marker
value 4 is a terminal raw correlation event for each callback. The first marker
stores A7 and return PC; later markers must match them exactly. Marker ownership
must match the active blocker token/kind/parent/depth, while its manifest hook
names target kind 4.

The released native SERVICE_BEGIN has no contemporaneous managed callback. The
observer accepts it only when it immediately follows the exact blocker END and
consumes one pending deferred reservation. The emitted managed evidence retains
all physical callbacks plus the single released service token. Consumer
rejection and later validation failure must roll back both active service state
and the reservation transactionally.

## Raw and canonical schema

Raw diagnostics gain a bounded `NativeDeferredServiceBegin` record containing:

- blocker token/kind/parent/depth;
- target service kind and hook token;
- CPU, instruction-start PC, first frame/ordinal, latest frame/ordinal;
- observation count; and
- whether it is still pending or was consumed by a specific released token.

Each frame retains its marker-value-4 events in the existing native correlation
chains. A buffered-native cutoff must include the pending reservation; OpenGGF
and callback producers must not emit this native sidecar. Raw/storage roots are
sensitive to every field. Semantic serialization and comparison exclude the
physical markers and reservation. After release, the ordinary canonical
DriverService begins at the release coordinate and participates in comparison
normally.

The stream validator retains at most one reservation across frames. It requires
all marker proofs in global native order, exact blocker continuity, adjacent
blocker END to released BEGIN, and exact cutoff carry/discharge. Missing,
duplicated, forged, or dangling evidence is capture failure.

## Reset, failure, and cutoff behavior

A reset or power boundary while a deferred begin is pending is rejected. There
is no source evidence at row 8775 for translating the unbegun service into reset
state, so silently dropping or synthesizing it would be lossy. Capacity failure
before blocker completion emits none of the blocker snapshots, END, or released
BEGIN. A movie cutoff may retain the raw pending reservation, but cleanup may
discard it only after its immutable cutoff diagnostics and digest are captured.

## Rejected alternatives

- Overlapping root services replace the single-owner model across native,
  managed, cutoff, and comparator layers for one bounded case.
- Observation-only suppression loses the new `UpdateMusic` lifetime.
- Recently-closed ancestry falsifies the proven physical order.
- Granting kind 6 `ALLOW_CHILDREN` permits source-invalid nested services.
- Fitting the observed retry count of three would fail another valid bus-wait
  duration; the bound is one reservation, not three observations.

## Acceptance

Acceptance requires native RED/GREEN matrices, managed transactional/correlation
tests, raw-schema round trips and adversarial stream validation, the exact real
row-8775 gate with ordinals 12/13/20/21, a bounded next-frontier probe, unchanged
legacy S2/S3 semantic vectors, deterministic paired builds/installs, identity-
bound performance gates, and independent review. No capture is published merely
because row 8775 clears.
