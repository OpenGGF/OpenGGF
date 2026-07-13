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

Runtime registration is transactional. A callback failure disables the owner for the
session and routes through the engine fault boundary; it must not publish a partial
registry. Use the [finding catalog](../troubleshooting.md) before asking users to grant
trust.
