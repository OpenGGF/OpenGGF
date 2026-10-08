# Named input actions

Use `ActionMap` for your mod's verbs and `ActionReducer` for timestamped physical
transitions. Bindings belong to the mod; the engine's Genesis input and logical
replay mappings continue to serve gameplay independently. Sitar Hero uses the
same reducer for fret, strum, whammy, power and pause actions while keeping its
rhythm rules and existing settings file format.

```java
ActionMap actions = new ActionMap()
        .bind("jump", new PhysicalBinding('K', -1, SceneKeys.SPACE, 1),
                      new PhysicalBinding('B', 0, 0, 1));
ActionReducer reducer = new ActionReducer(actions);
```

`K` is a physical GLFW key, `B` a standard gamepad button and `A` a signed
gamepad axis. Keyboard bindings use device `-1`; pad bindings select a physical
device. Axis direction is `-1` or `1`. An axis amount is clamped to `0..1`; action
amount is the maximum of its alternatives. A held action is above `0.5`.
`ActionMap.menu()` supplies conventional accept/back/directional defaults which
you can replace.

On scene entry, pause/resume and an intentional input-context transition, call
`reducer.reset(context.physicalInput())`. Reset consumes existing edges, so a held
confirm does not immediately activate the next menu. A changed action map also
consumes that sample's edges automatically. Reset the mod's own repeat/timing
state at the same transition where needed.

```java
for (ActionFrame frame : reducer.accept(context.physicalInput())) {
    if (frame.pressed("jump")) jumpAt(frame.timestampNanos());
    moveWith(frame.amount("move-right"));
}
```

The reducer returns a frame for each physical transition plus a final held-state
baseline. A press and release between simulation ticks remains observable. Each
alternative tracks its own rising edge, so tapping a second strum binding while
the first stays held still strums. Identical alternatives produce one action
press per event. The final baseline updates disconnected/new devices without
inventing presses.

Handle a nonzero `droppedEvents()` explicitly. A rhythm or timing-sensitive mod
should pause or invalidate the run rather than infer missing transitions from a
held-state snapshot. Gamepad timestamps describe polling observations.

`PhysicalBinding.label()` supplies a readable key/button/axis label.
`PhysicalBinding.capture(event, gamepad, threshold)` captures deliberate rising
keys/buttons or axis deflections, ignoring trigger rest. The reducer has no
capture UI: the mod decides when capture starts, which device class it accepts,
how escape cancels, and when to save.

Bindings have a compact `encode()`/`decode()` representation. `ActionMap.encode()`
stores a versioned named-action map; `read()` keeps defaults for malformed
entries and ignores unsupported versions. It bounds input settings to 65,536 characters, 64
actions and eight alternatives each. Store the text through the mod's owned
storage. Existing file formats may wrap the shared binding codec, as Sitar does;
adopting the reducer does not require replacing the mod's save format.
