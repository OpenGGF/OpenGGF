# Quickstart: object or badnik

The object/zone surface is part of the first Mod API 0.7 contract. Build against
the current API, declare `engineApiRange: ">=0.7.0 <0.8.0"`, then grant explicit
code trust.

1. Run `ggfmod init <dir> --id <id> --package <java.package>`.
2. Extend the supported object/badnik base in the generated project and use injected
   `ObjectServices`; never fetch manager singletons from object code.
3. Register an owned local key from the mod entrypoint and reference it as
   `<mod-id>:<local-name>`.
4. Keep mutable gameplay state on instances/session services, implement the supported
   rewind recreate path, and bake art with `convert art`.
5. Build, run `ggfmod package`, then run `ggfmod validate` on the jar; fix every error
   and understand any warning before granting trust.

[`sample-mod-src`](../../../src/test/resources/mods/sample-mod-src/README.md) is the
maintained badnik+zone project. The [trust](../concepts/trust.md),
[identity](../concepts/id-semantics.md), and [finding catalog](../troubleshooting.md)
explain the validator boundary.
