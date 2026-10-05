# Executable-mod trust

Data-only music and reskin jars do not execute creator code. A jar with classes needs
a manifest entrypoint, structural validation, explicit user trust for its exact jar
hash, and an API-compatible range before the engine creates its owner classloader.
Changing any byte changes the hash and requires a new grant.

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
Java 21 enum constants with final primitive/String instance fields and compiler-generated
enum switch tables. Enum parameters must be literals, and constructors may only call
`Enum(String, int)` and directly assign those fields; helper calls, constant-specific
subclasses, implemented interfaces, arbitrary initialization, and mutable enum fields
are rejected. The generated backing array factory and cloning `values()` method are
checked too. Switch tables must reference enums validated in the same jar, use the
compiler's literal ordinal assignments and `NoSuchFieldError` guards, and have no
additional methods or runtime writes. Flags or synthetic-looking names alone never
make a class eligible. Validation reads classfiles without defining or executing
creator classes; it does not grant general static array, collection, or object state.

Runtime registration is transactional. A callback failure disables the owner for the
session and routes through the engine fault boundary; it must not publish a partial
registry. Use the [finding catalog](../troubleshooting.md) before asking users to grant
trust.
