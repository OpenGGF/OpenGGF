# Mod API compatibility surface

OpenGGF's compiled-mod contract is marked by the runtime-visible, type-only
`com.openggf.game.ModApi` annotation. The supported inventory is not limited to
the initially curated roots: every engine type reachable through their public or
protected constructors, methods, fields, generic bounds, annotations, nested
types, supertypes, interfaces, record components, and sealed permits clauses is
part of the same contract and must also be annotated.

At Mod API 1.1.0 the recursive surface contains **783 engine types** and
**15,901 canonical signature entries**. The breadth is intentional. In
particular, the legacy-wide signatures of `GameModule`, `ObjectServices`, and
the object base classes expose substantial runtime infrastructure; silently
treating those transitive types as unsupported would make creator binaries depend
on an undocumented, unstable ABI.

The checked-in baseline is
`src/test/resources/mods/mod-api-signatures-1.1.txt`. The guard requires the
baseline to remain a subset of the current canonical surface, so removals and
changes fail while reviewed compatible additions can be published with an
appropriate semantic API version increase. Before updating the baseline:

1. run `TestModApiSignatureSurface` and inspect every added line;
2. annotate every newly reachable engine type;
3. narrow any third-party signature to a JDK or engine-owned contract instead of
   allowlisting the dependency;
4. add a JDK type only to the explicit platform allowlist after compatibility
   review; package-prefix exemptions are forbidden;
5. regenerate the sorted LF baseline and re-run the Javadoc/SDK packaging tests.

Release packaging generates exact-inventory Javadoc and attaches
`openggf-mod-sdk` and `openggf-mod-sdk-javadoc` classifier jars beside the
engine artifact. Architecture guards ignore only the `@ModApi` marker edge and
the release tool's exact inventory lookup; the annotation does not establish a
runtime ownership dependency.

New creator APIs should use narrow engine-owned facades and value types so this
closure decays rather than expands. Existing 1.x supported signatures cannot be
removed, narrowed, or unannotated merely to reduce the inventory; that requires
a deliberate breaking-version transition and migration guidance.
