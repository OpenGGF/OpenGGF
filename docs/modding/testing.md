# Testing a mod

Use the engine, SDK and `mod-testkit.jar` from the same creator kit. Their embedded
build metadata must match, including the source commit; a shared filename or API
version alone does not identify a matching mutable candidate. The testkit is a
separate test dependency. Ship your mod's main classes and resources, not testkit
classes, JUnit or test classes.

## Portable integration test

The exported `hello-scene` project includes
[`HelloSceneIntegrationTest`](../../examples/hello-scene/src/test/java/hello/HelloSceneIntegrationTest.java).
It packages `target/classes` through the SDK, scans the packed repository, validates
the jar, loads its immutable snapshot through the production dependency loader,
registers through the creator transaction, and resolves its launch through the
normal module resolver. It opens the real scene host, feeds input, records drawing,
and checks runtime findings. No checkout-only test harness is required.

From an unpacked creator kit, verify the artifacts before installing the test
dependency locally:

```sh
cd /absolute/kit
KIT_VERSION=$(python3 -c 'import json; print(json.load(open("creator-kit.json"))["engineVersion"])')
python3 tools/build_project.py examples/hello-scene --testkit /absolute/kit/mod-testkit.jar --verify-only
mvn org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file -Dfile=/absolute/kit/mod-testkit.jar -DgroupId=com.openggf -DartifactId=OpenGGF "-Dversion=$KIT_VERSION" -Dclassifier=mod-testkit -Dpackaging=jar -DgeneratePom=true
mvn -f examples/hello-scene/pom.xml package -Pcreator-tests "-Dopenggf.testkit.version=$KIT_VERSION" -Dopenggf.engine.jar=/absolute/kit/engine.jar -Dopenggf.sdk.jar=/absolute/kit/sdk.jar
```

The version comes from the kit's metadata. This installs the supplied artifact in your local Maven cache; it
does not publish an API or require an OpenGGF Maven repository. The exported POM
pins compiler/Surefire plugins and Jupiter 5.10.3. Its `creator-tests` profile
activates test sources and the separate classifier. These commands test the kit's
exported Hello Scene; `ggfmod init` projects do not already include that profile.
Ordinary starter builds do not require the testkit.

To add production-backed tests to your generated project, use the exported
`examples/hello-scene/pom.xml` as the configuration reference. Copy its pinned
Jupiter 5.10.3 and JUnit Platform Console 1.10.3 test dependencies, Surefire 3.2.5,
`creator-tests` profile and test-source properties/directory into your existing POM.
Keep your project's converters and production package execution. Add tests under
`src/test/java`; adapt `HelloSceneIntegrationTest`'s base module and launch request
to your manifest's game. Use the same matching artifact/profile properties above,
with `-f` pointing to your project's POM. Keep testkit/JUnit dependencies at test
scope and package only `target/classes`, so test classes stay out of the mod jar.

Jupiter runs discovery, lifecycle hooks, extensions, parameterized tests and
dynamic tests. Repository checks use the same Jupiter engine through the pinned
[JUnit Platform Console 1.10.3](https://docs.junit.org/5.10.3/user-guide/#running-tests-console-launcher),
including classes named `*Checks`. A failing test or empty discovery produces a
nonzero result; tests are never manually invoked by reflection.

## Testing packed mods

`ModTestKit.openTrusted(repository, storageRoot)` opens packed jars with exact,
locally granted scanned-hash trust. It does not alter the player's enabled-mod
state, remembered trust, configuration or saves. Supply fresh test directories,
and close the kit with try-with-resources. `packageAndOpen(classes, repository,
storageRoot)` first runs the matching SDK's package command. It requires an empty
dedicated repository so it cannot overwrite an artifact or silently load another
project's jar; the matching SDK must be on the test classpath.

The default logical-ROM resolver has no available ROM. Tests needing stock
content pass `LogicalRomResolver.fromRomManager(...)`, the appropriate
configuration and any required built-in patch registrations to the full
`openTrusted` overload. Required ROMs remain required; this path never pretends
that prerequisites are available. Keep user ROM paths explicit and outside the
distributed project.

Use `launch(root, request)` for a patch mod and `standalone(owner)` for a standalone
module. `plan(owner)` exposes the actual successful registration from the latest
production registration pass. It neither calls the creator entry point again
nor manually applies explicit patches. Failed or disabled owners have no
successful current plan. `loadOwned(owner, name)` uses the actual owner loader,
so dependency tests exercise real visibility and class identity.

## Input, drawing, faults and rewind

`kit.input()` controls physical keys, gamepad samples and a monotonic timestamp
source. `tick()` samples the engine's real input handler, updates the scene and
advances edges; it defaults to a deterministic 60 Hz clock. Test taps, held
alternatives, remapping, disconnects and scene transitions, rather than just a
single held key. Dropped physical events remain observable through normal input
diagnostics.
The full `openTrusted` overload uses its supplied configuration as the same live
input-binding source. Change that test configuration to exercise a remap on the
next sample. Standalone input checks can construct `DeterministicInput` with a
configuration or `Supplier<InputBindings>`; the no-argument constructor uses
isolated default bindings.

`draw()` records the production canvas without a GL context and returns immutable
draw operations. This checks layout and image selection. Pixel/rendering checks
still need the appropriate renderer or gameplay capture. A no-ROM scene test
does not verify the appearance or correctness of its ROM art.
The default scene host is silent. Catalog validation checks audio declarations;
prepared music playback, process atlas allocation and complete engine-session
activation need supplied production services or the corresponding engine session
tests.

Callbacks run through the engine's owner fault boundary. Inspect `findings()` and
`disabledOwners()` after the triggering callback; test both the owner's outcome
and required-dependent disable. A registration/load rejection fails opening with
diagnostics, while a later callback failure follows the normal runtime abort
behavior. Do not swallow a callback abort and describe the mod as healthy.
The runtime's save/load warning sink writes to the same `findings()` store, so
missing-owner recovery can be asserted together with its normal save outcome.

`moduleState(module)` registers the module's production-owned rewind adapters.
Capture, change state, restore, then replay the same input and compare the result.
This is **module-state coverage only**. Full gameplay rewind uses the active
session's real `RewindRegistry`, including objects, character physics, camera,
events and managers; pass that registry to the capture/restore helpers. The
isolated kit also supplies `rewindClassResolver()` backed by its actual private
owner loaders. When constructing a gameplay session for a packed-mod test, set
`session.getLevelManager().setRewindClassResolver(kit.rewindClassResolver())`
before loading its level. This lets captured mod objects resolve their original
class and verified owner without installing the test runtime into the player's
process-wide mod subsystem. It does not activate other engine-session services.
The
[zone and act testing standard](../guide/contributing/level-test-standard.md)
defines the wider matrix required for authored playable routes. A scene draw or
successful level load alone does not certify a gameplay route.

Keep domain rules in ordinary small Jupiter tests. Use production-backed tests
for registration, assets, dependencies, services, callbacks, save/load and rewind
boundaries, and use route/capture tests for complete playable behavior.
