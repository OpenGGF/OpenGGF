# Multiplayer testbed deployment

## Outcome

The multiplayer master for develop and next runs on James's shared host
(`vidya.jamesj999.co.uk`; `multi*.openggf.com` are CNAMEs) as CubeCoders AMP
instances, so the existing panel shows status, console, resources and restarts.
CI redeploys each branch after its smoke suite passes. Verifiers are not deployed:
they need ROMs on the host, which is undecided.

| Branch | Endpoint | AMP instance | Prerelease |
|---|---|---|---|
| master | `wss://multi.openggf.com:27900/master` | not created (master has no `racing/` yet) | `multi-latest` |
| develop | `wss://multi-dev.openggf.com:27910/master` | OpenGGF Multi-Dev (`ca88b86d-…`) | `multi-dev-latest` |
| next | `wss://multi-next.openggf.com:27920/master` | OpenGGF Multi-Next (`df4abf8b-…`) | `multi-next-latest` |

Operator details are in [racing/deploy/README.md](../../../racing/deploy/README.md#multiplayer-testbed-amp).

## Decisions

- **Port per environment, not hostname routing.** The master terminates TLS and
  applies per-IP admission without trusting forwarded headers or the PROXY
  protocol. SNI passthrough (HAProxy/nginx stream) would collapse every client onto
  the proxy address; an HTTP proxy would need plaintext masters, which the master
  forbids outside loopback tests. Either needs master code changes; ports did not.
  The host's nginx owns 443 for other sites, so rebuilding it around `ssl_preread`
  was also too invasive for a shared machine.
- **AMP instead of Docker Compose.** The host already runs AMP, and the user wanted
  the servers visible in it. AMP's Generic module runs the relocatable
  distribution with a downloaded Temurin 25 JRE (the host has only Java 21) in AMP's
  own `cubecoders/ampbase:java-lts` container with host networking, 2 GiB / 2 CPU caps.
  The repository's Dockerfile/Compose path remains the general deployment route.
- **Template in its own public repository** (`OpenGGF/openggf-amp-templates`),
  because AMP only pulls templates from GitHub repositories at their root.
  Server builds are rolling prereleases on this repository; the tag is
  force-moved to each deployed commit and the asset replaced.
- **Certificates:** one Let's Encrypt certificate for all three names, issued with
  certbot's webroot challenge through the host's default nginx site (no nginx change).
  `/etc/letsencrypt/renewal-hooks/deploy/openggf-multi.sh` copies it into every
  `OpenGGFMulti*` instance, owned by `amp`, mode 0600.
- **CI deploy job inside `ci.yml`** (`deploy-multiplayer`, `needs: smoke`).
  A separate `workflow_run` workflow was rejected: GitHub runs those from the
  default branch (master), so it would not fire until a release.
- **CI credentials:** AMP user `openggf-ci` with no password expiry and no
  self-service password change. The password was generated in the browser and
  delivered to a local one-shot receiver, then into GitHub environment secrets, so
  it never appeared in agent context. Environments `multiplayer-dev` and
  `multiplayer-next` admit only their own branch.

## Host changes (2026-11-01)

- `/etc/nginx/conf.d/amp.jamesj999.co.uk.conf`: `proxy_pass` 8080 → 8081. The AMP
  panel's HTTPS name returned 502 because nothing listened on 8080, so the panel
  was only reachable over plain HTTP on :8081. Backup in `/root/`.
- certbot certificate `multi.openggf.com` (SANs multi, multi-dev, multi-next) plus the
  deploy hook above.
- AMP: added configuration repository `OpenGGF/openggf-amp-templates:main`; two
  instances in display group OpenGGF; ADS role and per-instance roles
  "OpenGGF Deploy"; user `openggf-ci`.

## AMP pitfalls found

- A template repository needs a root `manifest.json` (`"repotype": "AppTemplates"`);
  without it AMP clones the repository and silently lists nothing.
- AMP Professional has no common/shared roles. Instance permissions need a role
  inside each instance, with the ADS user added after it first logs in there.
- Instances created from a template version keep that version's port list.
  `RefreshInstanceConfig` failed ("No meta configuration manifest data", origin
  `--main/...`), so template v2 was applied by deleting and recreating the two new
  instances. After that, ADS name-based calls (`StartInstance`) still resolved
  the deleted IDs; ID-based calls work, and `ampinstmgr --StartInstance` on the
  host started them. `amp_deploy.py` uses only IDs.
- Ports undeclared in the template are invisible to AMP's allocator: next was first
  given 27912 because dev's admin listener on 27911 was unknown to it. Template v2
  declares the admin port; `SetInstanceNetworkInfo` pins the pairs.
- `UpdateInstanceInfo` reconfigures and shuts the instance down.
- Update-stage arguments are parsed with .NET command-line rules: a backslash is
  literal unless it precedes a quote, so shell regex backslashes must not be doubled.

## Validation

- Local: distribution from develop `d27e21ddc` started with the template's generated
  `master.yaml`; TLS listener and loopback-only admin listener confirmed.
- Live, as `openggf-ci` via `racing/deploy/amp_deploy.py`: stop → update → start →
  certificate-validated WebSocket upgrade (101) on both endpoints. The admin port is
  unreachable from outside.
- The CI job itself has not run yet; it first runs on the next develop/next push
  that passes smoke.

## Open items

- Client default master URL per branch (currently blank in Time Attack settings).
  It lives in the bundled Time Attack mod, which sees only `@ModApi` types, so a
  build-time value needs either a Mod API addition or packager support.
- Prod (`multi-latest`, port 27900) once master carries multiplayer; deploy from the
  Release workflow rather than raw pushes.
- Certificate pickup on instances that are rarely redeployed (a weekly AMP restart
  or a renewal-hook restart). The current certificate expires 2027-01-30.
- Verifiers, if ROMs on the host are acceptable.
