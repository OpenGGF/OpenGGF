# S3K Generic Traversal Compatibility Remediation

Scope is the implemented locked-on S3K range through Launch Base. Flying Battery and every later zone are excluded.

## Placement inventory

Counts come from the locked-on six-byte object-placement records, stopping at the first `$FFFF` terminator.

| Object | In-scope placement |
|---|---|
| AutoSpin `$26` | AIZ2: 8; HCZ1: 8; ICZ2: 9 |
| AutomaticTunnel `$24` | LBZ1: 14 |
| CNZ Cylinder `$47` | CNZ1: 32; CNZ2: 43 |
| Door `$3C` | HCZ2: 19; CNZ1: 15; CNZ2: 12 |
| Twisted Ramp `$0E` | AIZ1: 1; AIZ2: 2 |
| Collapsing Bridge `$0F` | HCZ1: 7; MGZ1: 16; MGZ2: 9; ICZ1: 4; ICZ2: 5; LBZ1: 19; LBZ2: 13 |
| Spring `$07` | Present in every in-scope act |

## Remediation

- AutoSpin retains the native P1/P2 crossing bytes and moves the P2 value between native and identity-keyed extension storage on roster changes. Extension crossing state rewinds through stable player references.
- AutomaticTunnel retains native P1/P2 state-machine order, then processes identity-keyed extension routes. Promotion, demotion, omission, death, unload, persistence, and rewind replacement keep state with the actor. Its captured object-control generation identifies the exact lease acquired by the tunnel, so a later owner with otherwise identical ROM control bits is never released.
- CNZ Cylinder retains native standing bits, P1/P2 diagnostics, and update order. Native P2 state migrates by actor identity when the roster changes, while additional riders use identity-keyed extension slots plus an aggregate extension-standing bit solely for shared cylinder motion. Promotion recomputes that aggregate from the remaining extension states after moving the actor, preventing a sole active extension from producing a transient `$04->$06->$02` mask and false landing motion; cleanup and fresh-object rewind replacement remain tied to the actual rider.
- Door triggers, Twisted Ramp launches, and the horizontal Spring safety pass now process the native prefix followed by every configured sidekick. Their mechanics are mandatory traversal/safety checks and hold no per-player object state.
- The HCZ miniboss vortex processes the full ordered roster and records only control it acquired. Omission, death, unload, and rewind replacement release the actual owned actor without clearing unrelated object control. Fresh-object rewind coverage also proves compact restoration of non-default rocket state and live vortex-bubble/player reference graphs.
- AIZ end-boss debris uses the active viewport width for horizontal lifetime culling while retaining the ROM-exact 320px boundary.

## Audited intentional native targeting

- Directional Collapsing Bridge selection first checks native standing riders to preserve ROM direction priority, then already falls back to the rider that triggered collapse. Extra riders therefore can trigger and traverse it without a new state path.
- `S3kSignpostInstance` uses native P1/P2 order only for same-frame post-boss bump arbitration and presentation. It does not own mandatory traversal or forced control, so it remains native-only.

## Widescreen and donation

All changed traversal mechanics use world coordinates or the shared solid/contact viewport gates. AIZ debris now derives its cull center and span from the active 320/352/400/528/800 viewport, with native 320 unchanged. AutoSpin directly forces roll/pinball state and AutomaticTunnel directly captures and moves players along ROM paths; neither requires a spin-dash input or capability. No S1-donation workaround is needed.

## Regression baseline

- CNZ complete-run known red: 7,130 errors, first frame 1,846 (`tails_x_speed`, expected `$0024`, actual `-$1000`).
- ICZ complete-run known red: 3,206 errors, first frame 3,139.
- LBZ known red: first frame 2,270, 5,881 errors.
- AIZ trace owners remain expected green.
