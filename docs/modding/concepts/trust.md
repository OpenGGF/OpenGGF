# Executable-mod trust

Data-only music and reskin jars do not execute creator code. A jar with classes needs
a manifest entrypoint, structural validation, explicit user trust for its exact jar
hash, and an API-compatible range before the engine creates its owner classloader.
Changing any byte changes the hash and requires a new grant.

## First-party bundled mods: a deliberate narrowing

Mods bundled with an OpenGGF build are the one exception to the explicit-grant rule.
The build packages each first-party mod with `ggfmod package --warnings error` and
writes its id, version, file name, size and SHA-256 into a manifest inside the engine
artifact itself (`META-INF/openggf/bundled-mods.json`). That build-pinned manifest is
the only source of this default trust:

- A bundled jar is trusted, and enabled unless the player disabled it, only when its
  immutable snapshot matches the manifest's hash, size, id and version. Nothing the jar
  declares about itself grants trust; there is no first-party flag, package exception
  or validator exemption. The engine validates and class-loads it exactly like any other
  code mod, through the same structural validator and owner classloader.
- The trust is derived on every boot and never written to `modstate.json`, which keeps
  recording only the player's own grants and choices. A new build trusts only the hash
  its own manifest names; an older grant never carries over to changed bytes.
- A missing, resized, tampered or mislabelled bundled jar is not loaded and is shown as
  an error entry (`BUNDLED_MOD_*` findings). It is never silently trusted, and the
  player-installed copy of the same id does not take its place.
- Manifest ids are reserved: a jar in `mods/` with a bundled id is ignored and reported
  (`BUNDLED_MOD_ID_RESERVED`) rather than blocking both copies as duplicates.
- The player can disable a bundled mod in the Mod Manager; the choice persists across
  upgrades. It cannot be uninstalled from the manager, because the engine artifact owns it.
- Deterministic, certifying and development boots never read, extract or load bundled
  mods. Native builds carry no bundled manifest or jars and keep rejecting code mods.

Anyone able to replace files in the install directory can already replace the engine
jar, so trusting the engine's own manifest adds no new authority. The narrowing is
limited to that manifest; it does not extend to any other directory or to user mods.

`ggfmod package` always validates its staging jar before publication. The separate
`ggfmod validate` command prints sorted findings for an existing jar. The engine
independently repeats validation and does not trust an author-generated report.
Validation rejects reserved engine-package
classes, malformed/duplicate classes, unsupported static state, constructor service
access, missing rewind recreation/identity coverage, and invalid entrypoints. Direct
references to non-`@ModApi` engine internals are compatibility warnings: they may
break without an API migration promise.

Static gameplay state remains unsupported: keep it on instances or session services,
including state that an immutable record happens to contain. Literal compile-time
primitive/String constants are allowed. A bounded ASM check also recognizes simple
Java 21 enum constants with final primitive/String instance fields, compiler-generated
enum switch tables, and javac's synthetic assertion flag. Enum parameters must be literals, and constructors may only call
`Enum(String, int)` and directly assign those fields; helper calls, constant-specific
subclasses, implemented interfaces, arbitrary initialization, and mutable enum fields
are rejected. The generated backing array factory and cloning `values()` method are
checked too. Switch tables must reference enums validated in the same jar or trusted
JDK/public engine API enums, use the
compiler's literal ordinal assignments and `NoSuchFieldError` guards, and have no
additional methods or runtime writes. Assertion initialization must exactly read
the class or its nest host's assertion status and store its inverse; callbacks,
extra reads and changed branches are rejected. Flags or synthetic-looking names alone never
make a class eligible. Validation reads classfiles without defining or executing
creator classes. External enum metadata comes only from platform resources or
allowlisted engine API resources, with no fallback for a rejected creator enum;
it does not grant general static array, collection, or object state.

Runtime registration is transactional. A callback failure disables the owner for the
session and routes through the engine fault boundary; it must not publish a partial
registry. Use the [finding catalog](../troubleshooting.md) before asking users to grant
trust.
