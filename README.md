# codacy-staticcheck

A standalone tool that converts [Staticcheck](https://staticcheck.io/)
diagnostics to Codacy's format.

It allows running Staticcheck either locally or as part of your CI process and then integrating the results into your Codacy workflow. This way, Codacy will present the results coming from Staticcheck alongside all the other code quality information in the dashboards.

## Usage

### Requirements

To get your Staticcheck results into Codacy you'll need to:

-   [Enable Staticcheck](https://docs.codacy.com/repositories-configure/configuring-code-patterns/) and configure the corresponding code patterns on your repository **Code patterns** page
-   Enable the setting **Run analysis through build server** on your repository **Settings**, tab **General**, **Repository analysis**
-   Obtain a [project API token](https://docs.codacy.com/codacy-api/api-tokens/#project-api-tokens)
-   Download [codacy-staticcheck](https://github.com/codacy/codacy-staticcheck/releases)

### Sending the results to Codacy

Sending the results of running Staticcheck to Codacy involves the steps below, which you can automate in your CI build process:

1.  Run Staticcheck using the json formatter
2.  Convert the Staticcheck output to a format that the Codacy API accepts
3.  Send the results to Codacy
4.  Finally, signal that Codacy can use the sent results and start a new analysis

> When the option **“Run analysis through build server”** is enabled, the Codacy analysis will not start until you call the endpoint `/2.0/commit/{commitUuid}/resultsFinal` signalling that Codacy can use the sent results and start a new analysis.

With script:

```bash
export PROJECT_TOKEN="YOUR-TOKEN"
export COMMIT="COMMIT-UUID"
export CODACY_URL="CODACY-INSTALLATION-URL" # if not defined https://api.codacy.com will be used
export CODACY_STATICCHECK_VERSION=0.2.3 # if not defined, latest will be used

staticcheck -f json "<staticcheck-configs>" | \
./<codacy-staticcheck-path>/scripts/send-results.sh # requires a codacy-staticcheck-"<version>" in the current directory
```

Without script (step-by-step):

```bash
export PROJECT_TOKEN="YOUR-TOKEN"
export COMMIT="COMMIT-UUID"

# 1. Run staticcheck
staticcheck -f json "<staticcheck-configs>" | \
# 2. Convert the staticcheck output to a format that the Codacy API accepts
./codacy-staticcheck-"<version>" | \
# 3. Send the results to Codacy
curl -XPOST -L -H "project-token: $PROJECT_TOKEN" \
    -H "Content-type: application/json" -d @- \
    "https://api.codacy.com/2.0/commit/$COMMIT/issuesRemoteResults"

# 4. Signal that Codacy can use the sent results and start a new analysis
curl -XPOST -L -H "project-token: $PROJECT_TOKEN" \
	-H "Content-type: application/json" \
	"https://api.codacy.com/2.0/commit/$COMMIT/resultsFinal"
```

For self-hosted installations:

```bash
export PROJECT_TOKEN="YOUR-TOKEN"
export COMMIT="COMMIT-UUID"
export CODACY_URL="CODACY-INSTALLATION-URL"

# 1. Run staticcheck
staticcheck -f json "<staticcheck-configs>" | \
# 2. Convert the staticcheck output to a format that the Codacy API accepts
./codacy-staticcheck-"<version>" | \
# 3. Send the results to Codacy
curl -XPOST -L -H "project-token: $PROJECT_TOKEN" \
    -H "Content-type: application/json" -d @- \
    "$CODACY_URL/2.0/commit/$COMMIT/issuesRemoteResults"

# 4. Signal that Codacy can use the sent results and start a new analysis
curl -XPOST -L -H "project-token: $PROJECT_TOKEN" \
	-H "Content-type: application/json" \
	"$CODACY_URL/2.0/commit/$COMMIT/resultsFinal"
```

## Building

##### Compile

`sbt compile`

##### Format

`sbt scalafmt test:scalafmt sbt:scalafmt`

##### Tests

`sbt test`

##### Build native image (requires docker)

`sbt "graalvm-native-image:packageBin"`

##### Build fat-jar

`sbt assembly`

##### Update Documentation

Check if any new rules need to be added to the DocGenerator. Make sure you have staticcheck installed on your local machine.
`sbt doc-generator/run`

## Agent Playbook: Updating This Repository End-to-End

This section is written for an AI coding agent (or a human) tasked with updating this repo — most commonly bumping the wrapped Staticcheck version, but also base image / orb / dependency bumps. Follow it top to bottom; it tells you what to change, how to regenerate derived files, how to test locally, and how to interpret CI so you can iterate on failures without guessing.

### 1. What this repository is

This is **not a typical Codacy Docker-executed engine**. It is a Scala **converter/CLI tool** (`src/main/scala/com/codacy/staticcheck/`, built on `codacy-engine-scala-seed` and `codacy-analysis-cli-model`) that takes the JSON output of [Staticcheck](https://staticcheck.io/) (a real Go static analyzer that customers run themselves, since it needs their Go build environment) and converts it into the format Codacy's API expects. Because Staticcheck cannot run inside Codacy's own analysis container, the shipped Docker image's `entry.sh` deliberately just prints "staticcheck cannot be run by Codacy" and exits 1 — the real deliverable is the standalone binary/jar (built via GraalVM native-image and `sbt assembly`) that customers download from GitHub Releases / S3 and run in their own CI, per the "Usage" instructions earlier in this README.

The `docs/` directory is still machine-consumed configuration, generated from the **hardcoded rule list inside the generator itself**, not scraped from upstream:

- `docs/patterns.json` — the list of Staticcheck rule IDs ("patterns", e.g. `SA1000`, `S1009`, `ST1001`) Codacy knows about, their level and category, plus a top-level `"version"` field that must match the wrapped Staticcheck version. Generated file, do not hand-edit.
- `docs/description/description.json` + `docs/description/*.md` — one Markdown file per rule with its title/description, used in the Codacy UI. Generated file, do not hand-edit.
- `docs/tests/*.go` — Go source fixtures used by `codacy-plugins-test` (`run_pattern_tests: false` in CI, so these are not run in CI — see below).
- `docs/tool-description.md` — short blurb about the tool, hand-maintained.

All the generated artifacts above come from **`DocGenerator`** (`doc-generator/src/main/scala/com/codacy/staticcheck/DocGenerator.scala`), run via `sbt doc-generator/run`. Unlike some other Codacy engines, this generator does **not** clone an upstream repo — it hardcodes the full list of rule IDs as a `Seq[String]` literal in the source file itself, then shells out to a **locally installed `staticcheck` binary** (`staticcheck -explain <ruleId>`) to fetch each rule's explanation text. This means:
- **You must have the target Staticcheck version installed locally** (`go install honnef.co/go/tools/cmd/staticcheck@<version>`) before running the generator, or the explanations will come from whatever version happens to be on `$PATH`.
- **If the new Staticcheck version adds, removes, or renames rules**, you must manually edit the `rules` list inside `DocGenerator.scala` — the generator will not discover new rules on its own.

### 2. Files that encode versions — check all of these on every update

| File | What it controls | What to check |
|---|---|---|
| `build.sbt` → `val staticcheckVersion` | Which Staticcheck release this tool claims to convert output for (baked into `Versions.scala` at compile time, and into `docs/patterns.json`'s `"version"` field) | Bump to the target version. |
| `build.sbt` → `libraryDependencies` (`codacy-engine-scala-seed`, `scala-xml`, `ujson`, `scalatest`) and `ThisBuild / scalaVersion` | Scala toolchain and shared Codacy libraries | Bump only if the task scope calls for it; the last version bump (`8ec77cd`, v2025.1.1) bumped all of these together with the Staticcheck version. |
| `project/build.properties` → `sbt.version` | sbt itself | Check alongside `build.sbt` changes; bumped in the same PR historically. |
| `project/plugins.sbt` | sbt plugins (`codacy-sbt-plugin`, `sbt-native-image`, `sbt-assembly`, `sbt-scalafmt`) | Bump if relevant. |
| `.circleci/config.yml` → `codacy/base` orb | Shared CircleCI build/publish steps | Check the latest published version. |
| `.circleci/config.yml` → `codacy/plugins-test` orb | Runs `codacy-plugins-test` in CI | Same as above. |
| `Dockerfile` → `FROM alpine:...` | Base image for the (non-functional, placeholder) Docker image | Only bump if genuinely needed; it does not affect the real deliverable. |
| `docs/patterns.json` → `"version"` | Must match `staticcheckVersion` | Regenerated by `DocGenerator`, do not hand-edit. |

Prior real-world bumps to study: `git show 8ec77cd` (v2025.1.1 — touched `.circleci/config.yml`, `Dockerfile`, `build.sbt`, `docs/patterns.json`, `project/build.properties`, `project/plugins.sbt`) and `git show 38116a4` + `git show 8175a69` (v2024.1.1 — version-bump commit followed by a separate "update docs" commit that regenerated every file in `docs/description/`).

### 3. Step-by-step update procedure

1. **Bump `val staticcheckVersion` in `build.sbt`** (and any dependency/orb/base-image versions in scope).
2. **Install the target Staticcheck version locally**: `go install honnef.co/go/tools/cmd/staticcheck@<version>`, confirm with `staticcheck -version` (or `-h`).
3. **Check upstream Staticcheck's changelog/release notes** for added/removed/renamed rule IDs, and update the hardcoded `rules` list in `doc-generator/src/main/scala/com/codacy/staticcheck/DocGenerator.scala` to match.
4. **Regenerate the docs**: `sbt doc-generator/run`. This rewrites `docs/patterns.json`, `docs/description/description.json`, and every `docs/description/<ruleId>.md`. Review the diff — expect many small wording changes in the `.md` files if Staticcheck reworded its explanations, plus any added/removed rule files.
5. **Compile and format**: `sbt scalafmt test:scalafmt sbt:scalafmt test:compile`.
6. **Run the test suite**: `sbt test`.
7. **Build the native image and fat-jar** to make sure packaging still works: `sbt "graalvm-native-image:packageBin"` (requires Docker) and `sbt assembly`.
8. **Build the Docker image**: `docker build -t codacy-staticcheck .` (this image is only used by `codacy-plugins-test`'s harness, not by end users).
9. **Run `codacy-plugins-test` locally if possible** before pushing — clone https://github.com/codacy/codacy-plugins-test. Note CI runs it with `run_pattern_tests: false`, so pattern-level fixtures in `docs/tests/` are not exercised automatically; focus on the base DockerTest checks the orb runs.
10. **Commit** the version bump(s) and any regenerated `docs/` files together (or, following the repo's own precedent, as a version-bump commit plus a separate "update docs" commit).
11. **Push and open a PR.**
12. **Poll the PR's real CI checks until they all pass — local validation is NOT the finish line.** After every push, run `gh pr checks <pr-url>` and keep re-polling (short sleep while any check is `pending`) until all checks finish. If a check fails, fetch its actual log (don't guess), find the true root cause, fix it, push again (never `--no-verify`, never force-push), and re-poll. Repeat until every check is green. **The CI environment's toolchain can differ from your local one**, so a clean local run does not guarantee CI passes. Only stop iterating when every check passes, or you hit a genuine product/infra decision that needs a human.

### 4. Common failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| `docs/description/*.md` explanations look stale or wrong after regenerating | `staticcheck` on `$PATH` is not the version you intended to bump to | Reinstall/verify with `staticcheck -version` before rerunning `sbt doc-generator/run`. |
| `DocGenerator` run doesn't pick up new upstream rules | The `rules` list in `DocGenerator.scala` is a hardcoded literal, not derived from Staticcheck's own rule registry | Manually diff Staticcheck's release notes against the existing `rules` list and edit it by hand. |
| CI `codacy/sbt` job fails on `check_fmt_and_compile` | Code not formatted with the project's `.scalafmt.conf` | Run `sbt scalafmt test:scalafmt sbt:scalafmt::test` locally and recommit. |

### 5. Definition of done

- `staticcheckVersion` in `build.sbt` (and any other in-scope dependency/orb/base-image versions) bumped everywhere listed in section 2.
- `DocGenerator.scala`'s hardcoded `rules` list reconciled against the new Staticcheck version's rule set.
- `docs/patterns.json`, `docs/description/description.json`, and `docs/description/*.md` regenerated via `sbt doc-generator/run` and reviewed.
- `sbt test:compile`, `sbt test`, and formatting checks pass locally.
- Native image and/or fat-jar build successfully.
- Docker image builds successfully.
- **After pushing and opening/updating the PR, every CI check on it is green.** Poll `gh pr checks <pr-url>` and iterate on any failure until all pass.

## What is Codacy?

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy’s features:

-   Identify new Static Analysis issues
-   Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
-   Auto-comments on Commits and Pull Requests
-   Integrations with Slack, HipChat, Jira, YouTrack
-   Track issues Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.
