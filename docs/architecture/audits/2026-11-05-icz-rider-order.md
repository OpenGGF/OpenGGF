# ICZ tension-platform rider order

The native-frontiers validation exposed two ICZ2 reports: 1,035 grouped errors
in the reused baseline and 1,724 in the combined candidate. Their first error
remains row483 Y. A focused control on unchanged develop `eeabd00a7` also
produces the complete 1,724 report, so the increase cannot be attributed to the
new initial camera solely from those totals.

## Native owner and observed modes

`sub_8BA1C` explicitly calls P1 before P2; `sub_8BA7E` adds the platform's
current Y velocity to an upward departing rider and damps/stores that shared
velocity before the next call (sonic3k.asm:190449-190485). There is no alternate
FixBugs branch here. The Java owner instead consumed an identity map. Its blob
is identical at historical `911513142`, reused `93b2514b`, develop `eeabd00a7`
and the original combined candidate `a89b85806`.

Actual rows951/952 place both riders on slot14, object `$BA`, the tension
platform near `$3540/$0994`. Their first differing shared comparison is953:
the 1,035 mode swaps native P1/P2 Y velocities `-$800` and `-$3E0`, whereas
the 1,724 mode has both native velocities. The allocation-sensitive batch
iteration supplies a concrete mechanism; no identity traversal was logged in
the original profile itself, so its exact allocation history remains inference.

Preserved historical actual rows reproduce the entire 1,035 report. The new
develop observer reproduces the entire 1,724 report. These are distinct source
epochs, not a newly executed pristine current-source raw pair. Their 6,282 IDs,
288,065 fields and expected values match, and every shared field before953 is
identical. Raw ERROR observations fall144,314→102,597, with130,008 value tuples
removed and88,291 added. First added954 Y fraction is already wrong in both
modes; no addition occurs on483 baseline-clean rows. Higher grouped totals
therefore do not mean more raw errors. Missing rows remain unknown.

## Bounded correction and verification

Only the tension owner now consumes the existing semantic player query: native
P1, P2, then established engine-sidekick extensions. It validates the fresh
checkpoint's identity set before changing spring state. The commutative rider
count remains unchanged. No shared map/API, camera, fixture, gameplay hydration,
new state, rewind layout or fitted row constant changes.

The real defensive identity-map fixture assigns semantic P1 last in its actual
traversal. Two- and three-rider upward departures distinguish ordered impulses;
a mismatched roster must fail before changing the spring. All three fail on
unchanged production, with four existing cases passing. Corrected production
passes all seven cases. No allocation search or randomized test is used.

Queued Java25 Maven with `-Dmse=off -Ptrace-segments`, one alphabetical fork,
verified original ROM properties and surfaceless EGL selects the object tests,
three actual ICZ replays, architecture/invariant guards and four S3K keep-green
classes. It completes168 cases:165 pass, three inherited strict replay failures,
zero errors/skips. The entire ICZ2 report matches the canonical 1,724 control;
ICZ1 remains1,824/first3836 animation, and ZoneSlice2,430/first11989 X. An
intended Tails selector was misspelled and did not execute; the lead owns its
corrected final-composition control and raw observation before delivery.

Commands, full reports, source hashes and actual mode analysis remain under
`/tmp/trace-native-frontiers-20261105/`: `icz-order-red`, `icz-order-green`,
`icz2-qualification` and `icz2-context-*`. The lead records final composition
qualification and delivery separately. The route is still red; these component
checks add no full-act, broader roster/donor/viewport or native-pixel certificate.


## Final composed control

Final source `64df7449b` executes the corrected actual Tails ICZ selector and
Sonic+Tails ICZ2 in its explicit201-case composed selection:199 pass, two
inherited trace assertions, zero errors/skips. All seven object cases pass.
The complete Tails report remains1,157/first0 Y speed and matches original
r7; ICZ2 remains1,724/first483 Y and matches original segments. Actual final
ICZ2 rows exactly equal the current-develop native-order control across6,282
rows/288,065 fields, with0 added or removed ERROR tuples, no expected-value or
field-coverage change. This is a real current-base/final-source pair; the older
wrong-order1,035 stream retains its separate historical provenance. The late
read-only observer source is restored exactly and its compiled marker removed.
