# Install, update and remove mods

A mod is a `.jar`; keep it intact. Music packs and object reskins work on native
and JVM builds. Objects, characters, zones, scenes and standalone games contain
Java code and need the **JVM engine** with Java 21. Use matching candidate engine
builds named by the author: the unpublished API can change without a version bump.

## Install

1. Close OpenGGF. Use its distribution directory as the **working directory**.
   After copying mods, launch the JVM jar with `cd /path/to/OpenGGF` followed by
   `java -jar /path/to/OpenGGF/engine.jar` (or the supplied universal jar).
2. Create `mods/` in that **working directory** and copy the mod jar into it.
   Mods are discovered only at process start. Nested directories and exploded
   source projects are not installed mods.
3. Start OpenGGF and open **MODS** on the master title. Select the mod and accept
   to enable it. Code mods ask you to review and trust the exact jar hash; accept
   again to grant trust. Validation is structural; code runs with full permissions.
4. Install the named compatible dependencies first and trust each code dependency
   individually. Enable cascades never grant unseen dependencies code trust.
5. Use Order to arrange independent mods. Dependencies load before their dependents;
   later applicable art/music/startup-scene overrides win. Details/Notices explain
   blocked entries and conflicts. Apply saves pending settings; restart to activate.

If no mod appears, check working directory, `.jar` extension, JVM/native selection
and whether deterministic test/trace/time-attack mode excludes external mods.
If a mod is blocked, read Details and the [finding catalogue](troubleshooting.md).
An incompatible or missing dependency is not fixed by changing the load order.

## Update or roll back

Close the engine and replace the previous jar with the new one. **Do not leave both
versions in `mods/`**: duplicate manifest IDs block all copies, regardless of filename
or which version you intended to enable. Keep a rollback copy outside `mods/`.

Restart, select the updated code mod, and accept twice to renew trust for the new
hash. Its existing enablement/order is preserved; Apply and restart complete the
update. Check dependencies again because their required ranges may have changed.
Data-only updates need no trust grant. The running catalog never hot-reloads files.
To decline an updated code mod, open Details and accept **Disable without trust**,
then Apply/restart. Native builds can disable unsupported code entries directly.

For rollback, close the engine, replace the jar with the previous version, restart
and renew code trust. Back up saves before changing versions when the author warns
of a save-format migration. Restoring an old mod does not reverse a save migration.

## Disable or uninstall

Disable in the manager, confirm any dependent cascade, Apply and restart. To
uninstall, close OpenGGF and remove only the selected jar. Keep `mods/modstate.json` and
saves unless you specifically want to reset your settings/progress.

Missing-owner mod-zone saves are preserved and fall back to a safe stock destination.
Reinstalling/re-enabling the owner can resolve their tagged destination again.
Standalone game saves live under `saves/<mod-id>/`; scene storage is under
`saves/mods/<mod-id>/`. A missing or incompatible standalone game has no playable
Continue route. Keep those files for reinstalls; do not assume a different mod with
the same display name shares its identity or save format.
