# Publishing a GitHub release

**Pushing `master` automatically publishes a release** after validation and
all native and universal-JAR builds succeed. No manual dispatch is required.
The workflow is `.github/workflows/release.yml`; read its definition at the
commit being investigated when diagnosing a run.

## Trigger and outcome

| Action | Publish job |
|---|---|
| Push to `master` | Publishes after required builds succeed, provided the version tag is new. |
| Pull request (any base, non-draft) | Release workflow does not run. CI runs branch policy, the bounded smoke suite and guards, without native packaging. |
| Manual Release dispatch | Public release preparation: full-suite validation/build run only; does not publish, even on `master`. |
| Push to another branch or delete `master` | Release workflow does not run; `develop`/`next` pushes get CI smoke only. |
| Re-run a master push run | Can publish if the previous attempt did not create its version tag; inspect partial results first. |

The publish condition requires a non-deletion `push` event on
`refs/heads/master`. Builds or dependency failures can still prevent the
publish job from running. A manual dispatch is not a way to publish.

## Toolchain and platform requirements

Source and universal-JAR builds use JDK 25. Native packaging uses GraalVM 25
for Windows x64, Linux x64, and macOS Apple Silicon; Intel macOS uses the JVM
distribution. The Java 25 migration does not enable preview APIs or optional VM
tuning. A manual dispatch on a candidate branch with `validate_roms=false`
checks hosted-platform packaging without publishing; it does not replace required
ROM/trace validation.

`develop` and `next` currently share the 0.7 development line through an explicit
candidate alias in the release-policy descriptor. That alias is not a publication
permission: clear `candidateBranchAliases` and select `targetBranch=master` when
preparing the release. See [release rollover](release-rollover.md).

## Prepare and publish

1. Run the full ordinary suite explicitly with
   `python3 tools/testing/maven_queue.py -Dmse=off -Pfull-suite test -B`,
   separate guards and required ROM/trace checks. Full suites are reserved for
   preparing a new public release; routine development and CI use smoke.
   `exhaustive-audit` remains separately manual even during release preparation;
   its configuration products and wording/terminology scans are not release gates.
   Complete the release gates and prepare the reviewed release in a separate
   `master` worktree. Follow [release rollover](release-rollover.md) when
   promoting versions. Read the version from that commit's `pom.xml`.
   Run the exact-candidate local route pilot below on the final committed
   candidate too. Qualification of a development ancestor does not qualify a
   later version bump, merge resolution or promotion commit.
2. Check the remote `v<version>` tag before pushing. The workflow rejects an
   existing tag rather than overwriting a published release. A later push
   with the same POM version will build but fail the existing-tag check; use
   a new release version when another release is intended. **Do not create
   the tag yourself** or delete/move an existing tag as a routine retry.
3. Once the reviewed release is ready and publication is authorized, push
   from the `master` worktree:

   ```bash
   git push origin master
   ```

   This is the publication trigger. It starts validation and builds, then
   creates a public, non-draft GitHub release. The workflow tags the exact
   pushed `github.sha`, not whichever commit a branch points to later.
4. Find the push run and verify its commit and job results:

   ```bash
   gh run list --workflow release.yml --branch master --event push --limit 5
   gh run view <run-id> --json headSha,event,status,conclusion,jobs,url
   ```

5. Confirm the `release` job succeeded and the release exists at `v<version>`,
   with Windows, macOS, Linux, and universal-JAR downloads:

   ```bash
   gh release view v<version> --json tagName,isDraft,isPrerelease,url,assets
   ```

   A push accepted by GitHub, a green build, and uploaded Actions artifacts
   are intermediate results. Report publication after checking the release
   itself. For a failed or cancelled run, inspect its tag and any partial
   release before retrying.

## Local exact-candidate ROM qualification

Keep original ROMs on the maintainer's machine or a private fixture runner.
Do not copy, symlink or upload them for this procedure. Hosted test totals do
not establish ROM qualification. Automatic master publication and the separate
manual `validate_roms` opt-in remain unchanged; neither requires a private
runner to finish hosted builds or publish.

In the isolated final release-candidate checkout, use a full commit SHA taken
from the reviewed candidate, rather than resolving a moving branch or using
the current HEAD as an implicit expectation:

```bash
python3 tools/testing/check_route_obligations.py --run \
  --expected-candidate "$RELEASE_CANDIDATE" \
  --rom "s3k=$S3K_ROM_PATH"
```

Set `RELEASE_CANDIDATE` to that reviewed 40-character lowercase SHA and
`S3K_ROM_PATH` to the absolute existing original locked-on S3&K ROM. Use Java 25;
run the testing guide's actual tool preflight with Lua 5.4 before release checks.
`--preflight` in place of `--run` checks candidate/ROM/fixture prerequisites
without executing or awarding coverage. Omit `--expected-candidate` for routine
mutable development; only the strict `--run` invocation binds qualification.

The strict mode reuses the committed
`tools/testing/aiz-hcz-route-obligations.json` inventory: 26 meaningful native
Sonic+Tails cases across six classes, covering independent AIZ1/HCZ1 routes,
seven AIZ1 and ten HCZ1 rewind spots, five HCZ1 arena widths, and both outgoing
reload boundaries. It does not permit a substituted inventory or old `--reports`.
It enforces HEAD equality, a clean checkout (optional research submodules
excluded), committed build/source contents, and no ignored extra source/fixture
inputs. It verifies the inventory's original S3&K SHA-1 and required fixtures,
then compares commit, source/build, fixture, inventory and ROM state at the
before/after boundaries, including queue waiting. Git `assume-unchanged` cannot
hide edited build inputs. Keep the checkout and inputs stable throughout; this
is boundary fingerprinting, not continuous filesystem surveillance.

Execution queues a clean rebuild with `-Dmse=off` and surfaceless EGL, uses a
unique temporary report directory under this checkout's `target/`, and requires
every exact case once, with no failure, error or skip. XML counters must agree
with individual cases and Maven must succeed. Short worktree locks protect
report creation, inspection and cleanup; no lock is held while requesting test
admission. Reports are consumed and removed on completion/cancellation; no pass
cache or receipt is retained. Record the concise command, commit, outcomes and
coverage limits in the existing release/task validation record.

This remains a **bounded route pilot**, not whole-release certification or a
joined AIZ-to-HCZ campaign. Full ordinary validation, fresh-JVM guards, required
ROM/trace checks and campaign gates remain release obligations. Verify original
S1/S2 ROM identities for their required checks separately; this pilot requires
only S3&K. Use the existing release skip classifier with truthful input
capabilities, trace coverage assertions and baseline/candidate trace comparator
for their respective lanes. They do not turn a missing/skipped pilot case into
coverage. Completion of these other gates, review of permitted skips and known
trace failures, matching the eventual pushed SHA, and authorization to publish
remain procedural: this local tool is not a GitHub status or publication gate.

## Bundled first-party mods

JVM distributions carry the first-party mods listed in the `openggf.bundled.mods`
property of `pom.xml` (comma-separated project directories in the `examples/` layout;
empty by default). During `package`, `BundledModPackager` compiles each project against
the engine build, packages it with `ggfmod package` treating validator warnings as
errors, writes the jars to `target/bundled/`, and pins their id, version, file, size and
SHA-256 in `META-INF/openggf/bundled-mods.json` inside the engine classes. The
`universal-jar` profile embeds the jars as `openggf-bundled/<file>`; the `native`
profile skips the step, and native images include neither the manifest nor the jars.

The `universal-jar` job fails unless the jar contains the manifest and every listed jar
with its pinned size and hash, and nothing unlisted. The native packaging smoke check
fails if a Windows, macOS or Linux archive contains a `bundled/` folder, embedded
bundled jars or the manifest. To check the pipeline locally with a test payload:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -DskipTests -Puniversal-jar \
    -Dopenggf.bundled.mods=examples/hello-scene package
```

See [trust](../modding/concepts/trust.md#first-party-bundled-mods-a-deliberate-narrowing)
for why only the pinned hash is trusted by default.

## Website refresh after publication

After `release` succeeds, the separate `notify-website` job sends an
`engine-release` repository dispatch to `OpenGGF/OpenGGF-WebZone`. That site's
refresh workflow updates its release cache and pushes a commit when it changes;
Cloudflare Pages then rebuilds the static site. The notification is part of the
publishing workflow because a release created with `GITHUB_TOKEN` does not
trigger another workflow listening for `release: published`.

Configure the **engine repository's** Actions secret `WEBZONE_DISPATCH_PAT`
with a fine-grained token whose resource owner is `OpenGGF`, repository access
is limited to `OpenGGF-WebZone`, and repository permission is **Contents: write**
(Metadata: read is included). Complete any organization approval required for
the token. The engine's ordinary `GITHUB_TOKEN` cannot write to the website repo.
See GitHub's [repository dispatch permissions](https://docs.github.com/en/rest/repos/repos#create-a-repository-dispatch-event).

A missing secret or rejected API request fails `notify-website`; the engine
release has already been published. Fix the credential and rerun **only the
failed job**, rather than all jobs, to avoid attempting to publish the same tag.
For immediate recovery, run:

```bash
gh workflow run refresh-on-release.yml --repo OpenGGF/OpenGGF-WebZone
```

The website also has a daily scheduled refresh as a backstop. Confirm the cache
refresh run, its Cloudflare Pages check, and the version/download links served
at [openggf.com](https://openggf.com) before reporting that the website updated.

## When `release` is skipped

Inspect the run's event, branch, commit, and required `build` and
`universal-jar` job results. Manual-dispatch runs skip publication by design,
and pull requests do not start the Release workflow at all. A failed or cancelled dependency can skip publication
on a push run. Older commits may still contain the former manual-only gate;
re-running an old run uses that old workflow definition. Deliver the current
workflow to `master` to trigger automatic publishing.

A manual dispatch can opt into the additional `rom-validation`
job with `validate_roms=true` and a configured `release-fixtures` runner.
That job is skipped on push and does not control publication. Packaging steps
for other operating systems are also expected to be skipped.
