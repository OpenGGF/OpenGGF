# Title-screen mod reload

Installed mods can be changed without restarting OpenGGF. The Mod Manager's Apply
action first saves `modstate.json`, then asks the title host to rescan `mods/` and
build fresh catalog, audio, and classloader owners. The host replaces the process
mod subsystem, installs a fresh registration source, and recreates the master title
so standalone entries and character choices come from the same new launch plan.

The title is the ownership boundary: active gameplay, object instances, rewind
history, and prepared audio are gone before a mod set is replaced. A failed scan
or classloader preparation leaves the previous runtime installed and reports a
retryable reload error in the manager; the saved selection remains on disk. Code
mods still require exact-jar trust, and native builds still reject code mods.

The manager can be opened with an older catalog after jars are copied into
`mods/`. Apply with no draft changes performs a rescan; the new entries then
appear the next time the manager opens. Development-directory runs retain their
single boot snapshot and read-only manager.

Applying changes within a running game was excluded because gameplay objects,
audio leases, and rewind snapshots can retain classes or asset bytes from the
old owner. That requires a separate session-migration contract; returning to
the title already gives a clean ownership boundary.
