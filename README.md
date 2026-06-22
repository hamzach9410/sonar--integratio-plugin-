# SonarQube Integration Plugin for Bitbucket Server

A Bitbucket Server plugin that integrates SonarQube quality gate results directly into pull requests — showing code quality status, inline issue annotations, and blocking merges when the quality gate fails.

---

## Features

- **Automatic Quality Gate status** on every PR open and push
- **Inline code annotations** — SonarQube issues appear as line-level comments on the diff
- **PR sidebar panel** — bugs, vulnerabilities, code smells, coverage, duplication at a glance
- **PR toolbar badge** — instant PASS / FAIL indicator
- **PR list & Branch list badges** — see quality status without opening each PR
- **Merge check** — blocks merge if the quality gate fails or analysis is still pending
- **Issue actions** — mark false positive, won't fix, assign, or comment on SonarQube issues directly from Bitbucket
- **Repository dashboard** — quality gate summary on the repo overview page
- **Per-repo config** with global fallback — each repo can have its own SonarQube project key and token

---

## Requirements

| Dependency | Version |
|---|---|
| Bitbucket Server | 6.10.7 |
| Java | 8 or 11 |
| Atlassian SDK | 8.x |
| SonarQube | 7.x or higher (REST API v1) |

---

## Project Structure

```
sonar-integration-plugin/
├── src/
│   ├── main/
│   │   ├── java/com/yourcompany/bitbucket/sonar/
│   │   │   ├── admin/
│   │   │   │   ├── SonarConfigServlet.java       # Per-repo settings page
│   │   │   │   ├── SonarGlobalConfigServlet.java # Global settings page
│   │   │   │   └── SonarProxyServlet.java        # Status API + issue action proxy
│   │   │   ├── check/
│   │   │   │   └── SonarMergeCheck.java          # Blocks merge on quality gate fail
│   │   │   ├── listener/
│   │   │   │   └── PullRequestSonarListener.java # Triggers analysis on PR events
│   │   │   └── service/
│   │   │       ├── SonarClient.java              # SonarQube REST API client
│   │   │       └── InsightDecorator.java         # Posts report + annotations to Bitbucket
│   │   └── resources/
│   │       ├── atlassian-plugin.xml              # Plugin descriptor
│   │       ├── js/sonar-pr-integration.js        # Frontend UI logic
│   │       ├── soy/config.soy                    # Soy templates (UI pages + panels)
│   │       └── icons/sonar-icon.png
│   └── test/
│       └── java/com/yourcompany/bitbucket/sonar/
│           ├── admin/SonarProxyServletTest.java
│           ├── check/SonarMergeCheckTest.java
│           ├── listener/PullRequestSonarListenerTest.java
│           └── service/SonarClientTest.java
├── automate.ps1   # Dev automation script (Build, Run, Deploy, Test, Check)
├── pom.xml
└── README.md
```

---

## Getting Started

### 1. Prerequisites

Install the [Atlassian Plugin SDK](https://developer.atlassian.com/server/framework/atlassian-sdk/downloads/) and ensure Java 8 or 11 is on your PATH.

Verify your environment:
```powershell
.\automate.ps1 -Action Check
```

### 2. Build

```powershell
.\automate.ps1 -Action Build
```

Output JAR: `target/sonar-integration-plugin-1.0.0.jar`

### 3. Run locally (with hot-reload)

```powershell
.\automate.ps1 -Action Run
```

Starts a local Bitbucket instance at `http://localhost:7990/bitbucket`.

### 4. Deploy to a running Bitbucket instance

```powershell
.\automate.ps1 -Action Deploy -BitbucketUrl "http://your-bitbucket:7990/bitbucket" -AdminUser admin -AdminPass admin
```

### 5. Run unit tests

```powershell
.\automate.ps1 -Action Test
```

---

## Configuration

### Global Settings (System Admin)

1. Go to **Administration → SonarQube Global Config**
2. Set the default SonarQube URL and API token
3. Save — this acts as the fallback for all repositories

### Per-Repository Settings

1. Go to **Repository → Settings → SonarQube Settings**
2. Set the SonarQube URL, API token, and project key
3. Use **Test Connection** to verify before saving
4. If project key is left blank, the repository slug is used

### Generating a SonarQube Token

In SonarQube: **My Account → Security → Generate Token**

---

## How It Works

```
Developer opens / updates PR
        │
        ▼
PullRequestSonarListener (async, thread pool)
        │
        ▼
SonarClient calls SonarQube REST API
  ├── /api/qualitygates/project_status  → PASS / FAIL + conditions
  ├── /api/issues/search                → bugs, vulnerabilities, code smells
  └── /api/measures/component           → coverage, duplication
        │
        ▼
InsightDecorator posts to Bitbucket Code Insights
  ├── Report  → shows in "Quality" tab with metrics
  └── Annotations → inline line-level comments on the diff
        │
        ▼
SonarMergeCheck
  └── Vetoes merge if report is missing or FAIL
```

---

## API Endpoints (SonarProxyServlet)

| Method | Path | Description |
|---|---|---|
| GET | `/plugins/servlet/sonar/proxy/{repoId}/status/pr/{prId}` | Quality gate + metrics for a PR |
| GET | `/plugins/servlet/sonar/proxy/{repoId}/status/branch/{branchName}` | Quality gate for a branch |
| GET | `/plugins/servlet/sonar/proxy/{repoId}/status/repo` | Overall repo quality gate |
| POST | `/plugins/servlet/sonar/proxy/{repoId}/assign` | Assign issue in SonarQube |
| POST | `/plugins/servlet/sonar/proxy/{repoId}/comment` | Add comment to issue |
| POST | `/plugins/servlet/sonar/proxy/{repoId}/transition` | Do transition (false-positive, wontfix) |

---

## Severity Mapping

| SonarQube | Bitbucket Code Insights |
|---|---|
| BLOCKER / CRITICAL | HIGH |
| MAJOR | MEDIUM |
| MINOR / INFO | LOW |

---

## Notes

- No external AI or third-party APIs are used — the only external call is to your own self-hosted SonarQube instance
- The SonarQube token is stored in Bitbucket's PluginSettings (encrypted at rest by Bitbucket)
- Analysis is triggered asynchronously using a fixed thread pool (4 threads) to avoid blocking PR events

---

## License

MIT
