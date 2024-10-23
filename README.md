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
