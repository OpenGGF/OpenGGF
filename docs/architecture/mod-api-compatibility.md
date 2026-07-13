# Mod API compatibility surface

OpenGGF's compiled-mod contract is marked by the runtime-visible, type-only
`com.openggf.game.ModApi` annotation. The supported inventory is not limited to
the initially curated roots: every engine type reachable through their public or
protected constructors, methods, fields, generic bounds, annotations, nested
types, supertypes, interfaces, record components, and sealed permits clauses is
part of the same contract and must also be annotated.

At the published Mod API 1.2.0 baseline, the recursive surface contains **875
engine types** and **17,178 canonical signature entries**. The breadth is intentional. In
particular, the legacy-wide signatures of `GameModule`, `ObjectServices`, and
the object base classes expose substantial runtime infrastructure; silently
treating those transitive types as unsupported would make creator binaries depend
on an undocumented, unstable ABI.

The Phase 2 zone seam replaces the fixed `LevelData` enum in creator-facing
signatures with `LevelDescriptor`. `LevelData` remains the stock implementation
and every enum constant delegates its unchanged index and start coordinates
through that interface; the enum itself is no longer part of the creator ABI.
This replacement was made while 1.1 remained unpublished, before the final
Phase 2 baseline freeze.

The 1.1 surface also includes the additive loader-aware rewind contract:
`DynamicObjectEntry.ownerModId` identifies the compiled-mod loader that owns a
captured dynamic class, while `RewindClassResolver` lets the engine preserve
that ownership across recreation. Legacy `DynamicObjectEntry` constructors are
retained and produce ownerless engine entries, so existing 1.1 binaries remain
source- and binary-compatible.

The published 1.2 baseline is
`src/test/resources/mods/mod-api-signatures-1.2.txt`. The previous
`src/test/resources/mods/mod-api-signatures-1.1.txt` baseline is retained and still
checked as a subset; it contains 831 engine types and 16,483 canonical entries.
This proves a Phase 2 mod whose range begins at `1.1.0` remains source/binary
compatible, while the semantic-range tests separately retain Phase 1's canonical
`>=1.0.0 <2.0.0` compatibility.

The guards require each published baseline to remain a subset of the current
canonical surface, so removals and changes fail while reviewed compatible additions
can be published with an appropriate semantic API version increase. Before updating
a baseline:

1. run `TestModApiSignatureSurface` and inspect every added line;
2. annotate every newly reachable engine type;
3. narrow any third-party signature to a JDK or engine-owned contract instead of
   allowlisting the dependency;
4. add a JDK type only to the explicit platform allowlist after compatibility
   review; package-prefix exemptions are forbidden;
5. regenerate the sorted LF baseline and re-run the Javadoc/SDK packaging tests.

The 1.2 roots add the character and standalone creator path: owner-tagged
`CharacterKey`/`CharacterDefinition` registration and playable construction, plus
`GameDataSource`, `AbstractStandaloneGameModule`, `ModGame`, and
`StandaloneLevelLoader`. It also publishes `DelegatingGameModule`, the forwarding
decorator used by creator patches that wrap a stock module, plus `GroundSensor` and
`AbstractLevelInitProfile` for the prescribed standalone character and level-lifecycle
implementation. These are additive to the 1.1 object/zone surface; the old baseline is
not overwritten or renamed.

Release packaging generates exact-inventory Javadoc and attaches
`openggf-mod-sdk` and `openggf-mod-sdk-javadoc` classifier jars beside the
engine artifact. Architecture guards ignore only the `@ModApi` marker edge and
the release tool's exact inventory lookup; the annotation does not establish a
runtime ownership dependency.

New creator APIs should use narrow engine-owned facades and value types so this
closure decays rather than expands. Existing 1.x supported signatures cannot be
removed, narrowed, or unannotated merely to reduce the inventory; that requires
a deliberate breaking-version transition and migration guidance.
