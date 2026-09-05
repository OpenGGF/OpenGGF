# Complete-run audio coverage summary

`CompleteRunAudioCoverageSummary` reports only captures governed by a
`CompleteRunAudioProfile`. It does not aggregate the narrow S1, S2, or S3K
parity-adapter families.

Each canonical comparison layer has two independent dispositions. Authority is
`UNAVAILABLE`, `DIAGNOSTIC_ONLY`, or `COMPARABLE`, derived from the profile's
producer bindings and observation/comparison inventories. Evidence is
`NOT_RUN`, `REFERENCE_LIMITATION`, `KNOWN_MISMATCH`, or `VERIFIED_MATCH`, derived
from one supplied comparison report. A declaration without a report never
becomes verification.

Fixture, profile, observation, and comparison inventories must match both
capture identities. A full `MATCH` additionally requires both pinned runtime
and observer trust roots. Diagnostic mismatch reports retain an unavailable
authority disposition while pinned sides still undergo runtime validation.
Capture failures throw with the original report attached.

Mismatch attribution uses the comparison location as well as its kind. Frame
lag, frame chip events, and cutoff-boundary chip state have distinct owners.
Frame coordinates, terminal counts, terminal digests, metadata, record shape,
and other whole-report failures remain unassigned instead of being credited to
an arbitrary layer. Consequently, only a correlated `MATCH` with every layer
both comparable and verified can set `fullParity`.

Focused verification is in `TestCompleteRunAudioComparator`. Its controls cover
profile, ROM/BK2/run-manifest fixture and runtime mismatches; declarations with
no evidence; the shipped S1/S2/S3K fixed-profile limitations; capture failures;
typed layer attribution; and deterministic output.
