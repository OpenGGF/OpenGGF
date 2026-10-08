# Measure a creator catalog

`CreatorCatalogProbe` generates original code mods and owned zero-filled assets,
then uses the production scanner, catalog validator, exact scanned-hash local trust,
effective catalog, classloader validator and registration transaction once per owner.
It closes loaders/snapshots and deletes its generated work directory afterwards.
It reads no ROMs or installed mod state.

Run the opt-in probe with a Java 21 **JDK** and matching engine/SDK artifacts. Use
a new absolute work directory outside the engine checkout. On Windows use `;`
as the Java classpath separator.

```sh
java -Xmx512m -cp /absolute/engine.jar:/absolute/sdk.jar com.openggf.tools.modsdk.CreatorCatalogProbe /absolute/new-catalog-probe 1,32,128 1048576
java -Xmx512m -cp /absolute/engine.jar:/absolute/sdk.jar com.openggf.tools.modsdk.CreatorCatalogProbe /absolute/new-large-asset-probe 1,8 33554432
java -Xmx512m -cp /absolute/engine.jar:/absolute/sdk.jar com.openggf.tools.modsdk.CreatorCatalogProbe /absolute/new-budget-edge-probe 1024 65536
```

These shapes cover one mod, a modest collection, a large collection, sizeable
32 MiB assets, and the production 1,024-jar repository ceiling. Effective activation
also has an existing 128-pattern-window process budget, with one window allocated
by default per enabled owner, including scenes and data-only music packs. Thus the 1,024-jar shape validates the complete
catalog, registers its first 128 effective owners, and requires the remaining 896
to block deterministically with `PATTERN_WINDOW_BUDGET_EXCEEDED`. Discovery capacity
does not promise that all discovered mods can be active together. They are chosen to exercise
the owning boundaries; they are not recommendations to install that many mods.
The code fixture consumes its asset during registration, then contributes a complete
startup scene. This measures catalog/IO/registration work, not gameplay/rendering
performance or realistic compressed audio/image decoder cost.

The JSON report records build identity, Java/vendor/OS, processors, heap ceiling,
discovered/effective owner counts, blocked reason counts, phase elapsed times, sum of heap-pool peak usage and Linux process peak resident
bytes (`-1` where unavailable). Pool peaks can occur at different moments; their
sum is not a sampled simultaneous heap maximum. Timing excludes fixture generation
and the repeated diagnostic check. Run each shape in a fresh JVM for comparable
memory observations. Measurements have no latency threshold: they establish the
actual cost on the recorded environment, rather than a product performance promise.

Acceptance is behavioral: all effective owners load/register once; no admitted owner
is rejected or faults; only the existing pattern-window cap blocks excess enabled
owners; repeated scans and effective ordering/eligibility return identical results; excess production
jar count and an asset one byte beyond the 64 MiB limit are rejected before
activation. The bounded ordinary correctness test uses only two small owners.
Large catalogs run only by explicitly invoking this tool. Transaction collection
limits and decoder/audio/cache budgets have separate focused regressions; this
probe does not certify arbitrary creator code or all combinations of those limits.
