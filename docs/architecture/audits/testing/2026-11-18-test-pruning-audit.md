# Test pruning audit outside the rewind suites

Audited source at `79a6afac50`. This pass continued the first two pruning
commits in PR #372. JDK 25's parser counted test methods annotated with
`@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate` or
`@TestFactory`. This is a source census. JUnit expands parameter cases and
dynamic factories when it runs.

| Source family | Methods screened |
| --- | ---: |
| Engine Java outside dedicated rewind packages | 31,056 |
| Racing server and time-attack Java | 795 |
| Creator example Java | 612 |
| Python test modules tracked by Git | 1,737 |

The Java census separately counted 1,550 methods in dedicated rewind
packages. Two PowerShell suites, six shell test entrypoints, and two Lua contract tests
were inspected separately.

The Java screen compared parsed method bodies and annotations, located
literal self-comparisons, and flagged short mocked tests and short methods
without a visible assertion or an obviously named oracle helper. It returned
51 exact-body groups, 364 short methods without a direct oracle, 159 short
mock-bearing methods and one self-comparison. Those predicates overlap.
Selected candidates were reviewed against fixture, capture and trace helpers
before any removal. One exact-body group from the
Python AST scan exercised two different authored YAML-to-Java table pairs.

## Decisions

| Family | Pruned or strengthened | Evidence that remains |
| --- | --- | --- |
| Gameplay and routes | Removed the duplicate 320/off HCZ1 matrix case and the assertion-free EHZ1 idle-only run. Replaced the WFZ write-and-read and inactive-only collision checks with a production dispatch and hit test. | The unchanged HCZ1 pilot owns 320/off. The other 14 width/donor rows remain. EHZ1 headless movement, object windowing and co-located placement assert actual state. WFZ tests still check entry, spawn, bounds, art frame, the transition to collision flags $06, and a real hit. |
| Special stages and physics | Removed an assertion-free 3×3 sphere conversion, merged direction length checks into exact array checks, and folded collision-queue acceptance into its snapshot test. Removed the generated collision-event record, enum and string checks. | The Blue Sphere convex and concave loop tests assert grid conversion and the open-loop control. Collision trace tests still assert event data, order, and tolerances. The normalized Mod API pin checks the exported enum and record shape. |
| Audio and trace tooling | Dropped a JDK-only GZIP vector and a SHA-256 output-length assertion. Replaced an old unused golden vector and a serializer-to-itself comparison with independent canonical digests for the published capture and manifest. | Actual store tests still exercise zero-mtime GZIP output, fixed chunking, round trips, transactional rejection and tamper detection. Independent S1 and S2 audio/trace fixtures remain separate even where the test bodies match. |
| Creator examples | Deleted three copy-pasted Flappy persistence methods from the root suite. | The ordinary Flappy example test invokes the portable Jupiter suite, including all three methods. The root package-validation and unique lifecycle cases remain. Other portable persistence copies have a separate package-loaded root exercise and lack demonstrated identical root invocation and were retained in this pass. |
| Level, graphics, editor and capture | Removed a visitor identity self-comparison. Representative apparent duplicates were checked for their fixture inputs and helpers. | Object visitor tests still check order, object identity and size. Render comparisons in the same zone use different observation points (SAT limits, picture purity, or interpolation). Editor and capture wrappers delegate to live scene, pixel or publication checks. |
| Network, racing, configuration and ROM tools | Reviewed candidates from the static screen. | Racing bind errors use distinct fault paths. Registry, configuration key and ROM layout tests retain distinct input and compatibility boundaries. Fixture generation and diagnostic capture entrypoints are useful independently of an automated regression assertion. |
| Shell, Lua, Python and PowerShell | Removed historical inventory totals from the PowerShell comparator suite. The old inventory path had already moved. | The live comparator still runs 11 cases against generated XML. The paired Python table-sync checks have different source tables. The shell and Lua harnesses check exit status, exporter payloads or record ordering. |

The standard and exhaustive profiles serve different callers. For example,
the FBZ squeeze suite's representative rows also appear in an explicit
exhaustive profile. That profile selects only its tagged tests, so deleting
the shared rows from either lane would change that lane's coverage. Those
rows were kept. Matching method bodies for independent games, payloads or
ROM routes were also kept. These are source-screening results and reviewed decisions. The audit did not
run mutations or establish the individual regression sensitivity of every method.

## Validation

The focused Java selection ran 234 cases across 20 Surefire reports. It had
231 passes, three HCZ1 route watchdog failures, and zero skips. The three
failures reproduced on the isolated `79a6afac50` baseline against the same
ROM inputs and route class:

| HCZ1 row | Watchdog stage at frame 20,000 |
| --- | --- |
| 320 / S1 donor | LOWER_TUNNEL |
| 512 / S1 donor | FINAL_CURVE |
| 640 / S1 donor | FINAL_CURVE |

Comparison by width and donor found identical outcomes and assertion
messages for all 14 retained matrix cases. The baseline's redundant
320/off matrix case and both runs of the retained native pilot passed.
The separate creator Jupiter launch passed all 24 Flappy cases.

The PowerShell red-set comparator passed all 11 live CLI cases. The
Surefire inventory PowerShell suite passed all 39 fixture cases. Python
route-obligation and authored-table sync selections passed 44 tests.

The focused and baseline Maven jobs used Java 25, verified ROMs,
`-Dmse=off` and surfaceless EGL. The focused build took 3m 06s after 107s
in queue. The baseline took 2m 39s after 8s in queue. The focused selection
is recorded in the PR.

The Maven `-Pguards` profile passed 856 cases without failures or skips.
The build took 3m 36s after 273s in queue.
