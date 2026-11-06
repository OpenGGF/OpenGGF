---
name: mod-authoring
description: Choose and implement supported OpenGGF creator workflows for objects, scoped movement edits, gameplay menus, persistent upgrades, native enemy recipes, and original acts or campaigns. Use for authoring mods against the candidate API, not engine parity fixes or installing someone else's mod.
---

# Mod authoring

Find the smallest supported path for the user's requested behavior while retaining
expert Java/runtime overrides. Start from [canonical setup](../../../docs/modding/getting-started.md)
for a new project or artifact mismatch. The mutable candidate requires engine,
SDK and optional testkit with matching embedded build/source identity; a shared
version or filename is insufficient. See [test setup](../../../docs/modding/testing.md)
only when adding verification or checking the exact packed artifact.

## Route by task

Read the matching section of the [task decision guide](../../../docs/modding/task-recipes.md),
then its minimal source links. Do not load every example or handbook chapter.

| User intent | Read first | Key decision |
|---|---|---|
| Enemy in an existing stock act | [Badnik](../../../docs/modding/task-recipes.md#badnik-in-a-stock-act) | Bind an existing slot through owned registration; true additions need explicit expert level assembly |
| Tune one character | [Movement](../../../docs/modding/task-recipes.md#one-characters-movement) | Scoped `PhysicsProvider.transform`, preserving modifiers, rules and optional init profile |
| Pause world simulation for a creator menu | [Gameplay hold](../../../docs/modding/task-recipes.md#hold-gameplay-for-a-menu) | One captured `GameplayFrameController`; HOLD versus native or controlled input |
| Keep an earned upgrade after restart | [Persistence](../../../docs/modding/task-recipes.md#persistent-upgrade) | Owned `ModSaveSlot` plus separate reversible effects and durable settlement |
| Reuse native AI with supported parameters | [Native recipe](../../../docs/modding/task-recipes.md#parameterized-native-enemy) | Existing facade/finite recipe; verify donor art and linked-child lifecycle |
| Original level, sequence or no-ROM game | [Act/campaign](../../../docs/modding/task-recipes.md#original-act-or-campaign) | Editor export first, runtime providers for custom behavior, standalone for no ROM |

For an original whole-screen game or menu, route directly to
[mod scenes](../../../docs/modding/guides/mod-scenes.md); do not force it into a
native object, a gameplay frame controller, or ROM behavior research. For music,
reskins or other creator tasks outside the table, use the
[handbook's existing quickstarts](../../../docs/modding/index.md).

## Apply only the necessary contracts

- Read actual supported API and maintained source before implementing. A class on
  the engine classpath is not automatically a creator contract. If the helper
  cannot express the request, name the gap and choose a documented expert seam
  or a scoped API extension; do not invent support or silently narrow the request.
- Let `ModContext` derive ownership and publish the registration transaction.
  Use injected object services after construction, bounded assets and owner
  storage. A creator-reported owner or numeric id never grants authority.
- Keep gameplay state on the object/session. Use recreation and captured state
  for objects, a shared captured service/controller graph for managers, and
  fresh runtime providers on loads. Durable writes are not rewound; define
  settlement and retry semantics before awarding a persistent reward.
- For **native ROM behavior** changes, use the matching `s1-implement-object`,
  `s2-implement-object` or `s3k-implement-object` skill (boss counterpart for a
  boss), and matching disassembly guide when researching routines/offsets.
  These skills live beside this one. Original scene rules and authored campaign
  progression need no disassembly unless they claim native parity.
- For overlays use the sibling `ui-design-language` skill; for offscreen pixels
  use `headless-rendering`; for complete S3K native zone bring-up use
  `s3k-zone-bring-up`. Load only the specialist needed for the actual work.

## Verify the promised outcome

Choose focused checks from the recipe and changed behavior. Repository tests use
the Maven queue and current AGENTS guidance; artifact tests use matching testkit
and the exact packed jar. A registration-only pass does not prove an encounter;
module-state capture is not full gameplay rewind; recorded draw operations do not
prove pixels; positioned checks do not certify cold routes. Inspect ROM skips,
findings and disabled owners. Add route/load/respawn and full-session
capture/restore/replay checks where promised behavior depends on them.

Report supported behavior, changed files/artifact identity, exact checks run,
skips and remaining limits. Follow the task's authorized delivery scope; this
skill adds no approval gate, push authority, or requirement to replace expert APIs.
