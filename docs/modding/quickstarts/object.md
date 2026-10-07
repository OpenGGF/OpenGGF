# Quickstart: object or badnik

Read [candidate setup and first build](../getting-started.md) first: Java 21, Maven, and matching absolute engine/SDK jars from one commit. Generate a complete starter with `ggfmod init /absolute/my-object --id my-object --kind object --package example.myobject`. Build with the jar properties shown there, edit source, and repeat the same package command.

The object/zone surface is part of the first Mod API 0.7 contract. Build against
the current API, declare `engineApiRange: ">=0.7.0 <0.8.0"`, then grant explicit
code trust.

1. Run `ggfmod init <dir> --id <id> --kind object --package <java.package>`.
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
