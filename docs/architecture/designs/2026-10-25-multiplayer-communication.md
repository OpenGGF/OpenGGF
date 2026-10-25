# Multiplayer communication

Task base: `159655c8460dac15912dc4326491386a75368666` on `develop`.

## Intent and agreed behavior

Provide a small shared communication framework for multiplayer, first consumed by
Time Attack. Full Chat permits typed messages and Quick Chat. Quick Chat permits
only catalogued messages. Off permits neither. The user confirmed public/dedicated
servers should default to Quick Chat and end-user room creation to Full Chat.

Personal Censor Profanity defaults on. Personal Disable Multiplayer Chat defaults
off, hides typed and quick player messages, and does not suppress connection,
race, vote, roster or LAN-invite information. These preferences belong to the
player's engine settings, not the remote server. Disabling clears visible history
and drops incoming chat so re-enabling does not reveal a hidden backlog.

The user confirmed the public master sets a maximum for the relay rooms it owns;
room owners can restrict further. `chatModeLimit` defaults QUICK_CHAT in server
configuration and can be FULL_CHAT or OFF. Clamp initial choices and later owner
changes to this limit. Direct player-hosted rooms retain their Full Chat default;
the master advertises their actual mode without claiming authority over them.

## Approach

Enforce capabilities at the shared authoritative RoomHost. UI restrictions alone
would let another client bypass Quick Chat or Off. Keep filtering on the client;
server censorship would override personal preferences. Existing trust gates and
the two-second shared per-player chat cooldown apply to both message kinds.

Quick Chat transmits semantic phrase IDs, never client-rendered text. Hosts fill
the current game/zone/act context. Receiving clients render English text for the
MVP; stable IDs and a separate text catalogue permit later community translation.
Initial phrases include Brutal!, You're too slow!, Do you know the way?, This is
the way., That's no good!, This is <Zone Name>!, Good luck!, and Well played!.

A compact local dictionary and exact Unicode token matching provide predictable
CPU and memory cost. Fuzzy substring matching would cause Scunthorpe-style false
positives; remote classification and model inference add disproportionate cost.
Normalize lookup tokens, preserve original nonmatching text, and mask only matched
original spans. Starter language coverage and known limits must be explicit.
No attempt to infer intent or promise comprehensive abuse detection is in scope.

## Shared contracts

- `net.protocol.ChatMode`: FULL_CHAT, QUICK_CHAT, OFF; `parse(String)`,
  `allowsText()`, `allowsQuickChat()`, `label()`, and a restrictive intersection.
- `net.protocol.QuickChatPhrase`: stable enum IDs and `parse(String)`; six
  requested phrases plus positive greetings. `net.chat.EnglishQuickChat.text`
  renders them using a zone name, independently of the wire IDs.
- RoomDescriptor, RoomSummary and RoomHostConfig gain a final `String chatMode`
  field. Existing Java constructors retain Full Chat for source compatibility;
  current callers explicitly communicate their desired mode. Unknown modes fail
  validation. The protocol version rises from 5 to 6 because JSON is strict.
- Control messages: `SetChatMode(String mode)`, `ChatModeState(String mode)`,
  `QuickChat(String phraseId)`, `QuickChatBroadcast(int slot, String displayName,
  String phraseId, String gameId, int zone, int act)`.
- Only a room owner can change mode, between rounds. Join state and later updates
  report the authoritative mode. Host-origin sender identity and zone context
  prevent forged text, names or template arguments.
- ClientRaceSession owns bounded structured history, retaining its `chatLines()`
  presentation API. It supplies `chatMode()`, `chatDisabled()`,
  `setChatPreferences(boolean censorProfanity, boolean disableChat)` and
  `setQuickChatFormatter(Function<QuickChatContent,String>)`. The public content
  record contains a validated phrase, game ID and zone/act; private sealed typed
  and quick entries retain sender slot/name. Mode derives from the descriptor;
  an unjoined session permits no chat.
  Cache rendered lines until messages/preferences/formatter change; no per-frame
  lexicon scanning. Apply censorship to both sender label and message body.
- `net.chat.ProfanityFilter.censor(String)` is pure, bounded by its input, and
  independent of engine configuration and mod code.
- SceneContext exposes read-only `censorMultiplayerProfanity()` (default true)
  and `multiplayerChatDisabled()` (default false). The engine host reads global
  configuration; the Time Attack owner passes snapshots into ClientRaceSession.
  Networking itself never reads global configuration.

## Player controls and persistence

Engine Settings offers Censor Profanity and Disable Multiplayer Chat as immediate
settings. Config defaults, YAML template, metadata, timing and CONFIGURATION.md
must agree. Time Attack stores only its hosted-room mode preference, default Full
Chat, and lets the owner choose/change it. Room browsing and lobby details display
the effective communication mode.

Quick Chat is available alongside typed entry in Full Chat. The chooser offers
the catalogue without arbitrary text. In Quick Chat mode typed entry disappears;
Off disables communication actions. Personal disabling is receive/display only,
as requested. Lobby and race access support keyboard and controller. Neither
race path pauses the simulation or consumes controller movement or jump. Keep
existing solo behavior unchanged.
Typed composition remains the existing lobby composer; the newly requested race
input is Quick Chat. Full to Quick blocks future typed messages while retaining
messages previously accepted under Full. Off clears history.

### Controller race access follow-up

The initial implementation at `c502c600b` exposed the race chooser only through
Q/P and 1–8; its controller menu worked only in the lobby. The follow-up starts
from `94c75d8a8` and reuses the same chooser: right-stick click toggles it,
right-stick up/down selects a phrase, and the right trigger sends and closes.
The HUD and chooser explain these controls. Lobby controls stay the normal menu
navigation, confirm and Back.

Using D-pad/left-stick selection or face-button confirmation during a race was
rejected because those controls also move and jump. Start belongs to pause;
the bumpers already serve rewind and frame advance. The right stick and trigger
are independent physical controls, so no logical gameplay input is rewritten.
`RunInput.physicalInput()` exposes one immutable snapshot per host frame, with a
neutral default for existing hosts. Disabled controllers are removed from this
channel, while keyboard state and logical replay remain independent.

Input reconnaissance also found that the engine's debug/frame-step substitutions
could report a bumper as Q through `RunInput.keyPressed()`. Run-host keyboard
queries now use physical key state and edges; engine-level debug, rewind and
frame-step mappings retain their existing behavior. A real `InputHandler` bridge
test covers both the alias rejection and genuine Q presses. Review found that
pause stops host polling; a trigger pressed while paused could otherwise appear
new on resume. `RunInput.resumedAfterPause()` marks the first callback after an
engine/window pause, and the chooser closes and rebaselines before accepting
controller actions. Held controls during pause never send on resume.

The chooser baselines connected pads and held controls, uses a trigger threshold
and stick hysteresis, and repeats held navigation after a delay. It follows the
first connected pad until that pad disconnects; disconnect closes a controller
chooser and baselines any replacement. No held click can reopen the chooser
after retry or a room-mode change. Controller actions do not suppress a separate
keyboard retry edge. Focused checks cover these boundaries, keyboard/lobby
regressions, continuing race input and the additive candidate API signature.

Follow-up validation on `94c75d8a8` plus this change passed 632 selected cases
with no failures, errors or skips. Queued Maven used `--normal -Dmse=off
-Dopenggf.headless.gl=surfaceless-egl` and `test` with these exact focused groups:

- 121 cases: `-Dtest=TestMultiplayerRaceCoordinator,TestTimeAttackQuickChat,TestHostedRunController,TestGameLoopTitleCardPause,TestScenePhysicalInput,TestInputHandlerLogicalSnapshot,TestModApiSignatureSurface`.
- 74 cases: `-Dtest=TestModApiHookPolicy,TestModApiRuntimePolicy,TestNoProvisionalModApiShims,TestNetIsolationRules,TestModApiSdkPackager,TestModSdkArtifactVerifier,TestModApiJavadocTool`.
- 437 cases: all remaining Time Attack and `com.openggf.control` test classes,
  excluding the four already covered above, plus `TestHostedRunLeadIn`,
  `TestHostedRunActCompletionWiring`, `TestModApiReleasePolicy`,
  `TestModApiPinPolicy`, `TestGameLoopSuspension` and
  `TestGameLoopSuspensionOwnership`. This is 50 candidate classes; the case count
  includes nested suites. Original S1/S2/S3&K ROM paths and explicit EGL were
  supplied. All 34 Time Attack and 14 input test classes are covered across the
  groups. This is bounded regression coverage, not the release-only full suite.

The compiled signature export exactly matches the three reviewed additions.
The production chooser was rendered without a display at 256/320/400/640 × 224;
the narrowest output was visually inspected. Controller tests use simulated
physical devices through the real input bridge; no hardware-pad playtest is
claimed.

## Delivery and evidence

Implementation is split by owned files in one isolated development worktree:
protocol/host/client history; preferences/filter; Time Attack UI; lead-owned public
server integration, documentation, candidate API pins and delivery validation.
Workers add meaningful focused checks; the lead freezes source before combined
queued validation so concurrent compilation cannot observe a moving tree.

Cover mode transitions and authorization, both chat kinds sharing cooldown/trust
gates, invalid phrase IDs, sender/context authority, metadata propagation, mute
and censorship in every display path, persistence/defaults and Unicode benign
substrings. Preserve the unpublished 0.7 API policy and regenerate its normalized
signature pin. Inspect the change-based smoke plan against the pinned task base;
never run the release-only full suite. Merge and push only develop after checking
for new upstream commits, then account for and remove the task worktree/branch.

## Architect grounding: existing system and rationale

The user requested `starch-rival:architect` during implementation. Its Ground,
Sketch, Agree, Implement and Scrap phases apply before integration. Existing
read-only reconnaissance supplied the module exploration; the account below is
the grounded runtime model for the candidate sketches, not a claim that code has
passed validation.

### Overview and key concepts

Time Attack is a bundled scene mod, not a gameplay manager inside the engine.
RaceSession owns connections and launches; MultiplayerRaceCoordinator drains
network events and partners with each active run. RoomHost is the shared
authoritative driver for JDK direct hosts and Netty/master relay rooms.
ClientRaceSession mirrors room state and currently owns eight chat lines.
SceneContext is the capability boundary from the engine to a mod visit.

### How it works and where things live

1. RaceSession creates a RoomHostConfig or sends a RoomCreate descriptor through
   the master. RoomBroker authenticates and validates creation, SessionRegistry
   owns browser descriptors, and RelayRoomManager creates master-owned RoomHosts.
2. A room's RoomHost authenticates the sender before dispatching control messages.
   Existing typed chat uses ChatGate, a shared per-member timestamp, a 200-code-unit
   cap, and host-owned sender identity. This is the enforcement point for both
   kinds of chat and communication ceilings.
3. ClientRaceSession receives broadcasts through the coordinator. Its old
   immediate `name: text` concatenation loses structured sender and phrase data;
   bounded structured retention makes personal filtering and localization possible.
4. The lobby renders both a card and a details/history page. The coordinator also
   exposes chat to the race HUD, which previously had no visible chat rendering.
   Therefore one filtered history must feed all these views.
5. EngineSettingsDraft/ConfigCatalog own global preferences. TimeAttackSettings
   owns mod preferences and checked persistence/retry. Read-only SceneContext
   queries bridge global preferences into the mod without net importing engine
   configuration; the net isolation guard enforces that dependency fence.

### Why these boundaries exist

Observed source-control evidence: `dfa79f3b9` extracted Time Attack into a bundled
mod with reusable core netplay and a generic Extras entry point. `399376575`
introduced fenced lobby runs and protocol 5, with explicit owner readiness and
presence validation. `TestNetIsolationRules` pins reusable networking's independence
from engine/game/mod implementations. These are preservation constraints, rather
than opportunities to move communication into Engine.java.

Inferred design rationale: room policy belongs at RoomHost because every hosting
transport already converges there; global personal display choices belong to
engine settings because the request describes player preferences across multiplayer.
The inference does not extend censorship to generic ScenePeer gameplay traffic,
simulated chats or third-party protocols without a player-chat consumer.

Preserve: authority, admission, sender identity, cooldown, trust mute, bounded
history, settings fault handling and scene/run lifecycle. Change: message kinds,
room communication capabilities, structured presentation and personal controls.
Avoid: per-render filtering, game dependencies inside net, phrase text on the
wire, controller movement interception, and claims of comprehensive multilingual
moderation. Risk: live room metadata must follow the host through owner changes,
track votes and direct-host heartbeats; one creation-time clamp is insufficient.

Source coverage: source-control history, local architecture docs, code and tests
were consulted. No task-specific issue tracker, team chat, observability, error
tracking or analytics source is connected. External document stores are not
needed to establish these repository-owned contracts; no claims about their
contents are made. The user's confirmed choices remain the product authority.

## Architect synthesis decision

Two independent configured runners compared integrated session ownership against
a dedicated public RoomCommunication component. The configured seats were
inherit-parent and GPT-6.1-sol (both the same model family); no cross-family
independence is claimed. A separate cross-judge scored A 24/25 and B 23/25 across
authority, interface depth, presentation/cost, metadata consistency and migration.
The lead read both candidates and the judgement and selected A.

Caller usage is deliberately short:

```java
session.setQuickChatFormatter(TimeAttackQuickChat::format);
session.setChatPreferences(context.censorMultiplayerProfanity(),
        context.multiplayerChatDisabled());
List<String> visibleMessages = session.chatLines();
coordinator.sendQuickChat(QuickChatPhrase.GOOD_LUCK);
```

The resulting shape uses `QuickChatContent(QuickChatPhrase phrase, String gameId,
int zone, int act)` as presentation input, private TextEntry/QuickEntry variants,
and one immutable cached rendered list. It cannot retain arbitrary control
messages. RoomHost owns enforcement; ClientRaceSession owns history; the room
descriptor is the client's sole mode source. EnglishQuickChat is the only text
catalogue, shared by chooser previews and received history.

The selected design hides admission, bounded retention and invalidation behind
existing owners. Graft B's explicit content validation and unchanged-preference
fast path. Reject a separate public communication component: it adds an accessor
and lifecycle coordination for one current consumer without reducing the public
surface. Reject per-view histories, server censorship and a speculative
localization service. Accept an eight-entry rerender on actual changes in exchange
for simple invalidation, and conservative whole-token filtering in exchange for
low cost and fewer benign-substring matches.

Implementation friction did not justify scrapping the architecture. The original
broad ControlMessage history, wire-record formatter callback and duplicate client
mode field were replaced before integration. The catalogue extraction moves the
existing exhaustive switch instead of adding another text API. Additional checks
pin no hidden backlog, unchanged preference pumps, catalogue equivalence, held
movement/jump and continuing gameplay during Quick Chat, and late join receiving
live mode even from an older browser row.
