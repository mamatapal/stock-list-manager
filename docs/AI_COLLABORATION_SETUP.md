# ChatGPT + Claude GitHub collaboration

This repository contains both Android apps and the shared backend. The Supplier Management app is in `supplier-android/`; it is not a separate repository in this project layout.

## Design
- Claude Code Action is the implementation agent. It runs only when the repository owner `mamatapal` labels an issue `ai-develop`.
- The OpenAI API is an independent, advisory pull-request reviewer. It posts or updates one review comment per PR.
- GitHub Actions runs the existing backend and Android CI workflows.
- Mamata remains the only merge and production-release approver.

The OpenAI API reviewer does not have code-write or merge permissions. It does not run the pull-request code. Its workflow checks out the trusted base commit, fetches the PR diff through the GitHub API, and sends the diff to the OpenAI Responses API as untrusted review data. Fork PRs are skipped by this secrets-bearing job.

## Required one-time configuration

After this setup PR is merged:

1. Install the official Claude GitHub App and grant it access to this repository: https://github.com/apps/claude
2. In Anthropic Console, create an API key (or configure a supported Claude Code OAuth/workload identity arrangement): https://console.anthropic.com/
3. Add repository Actions secret `ANTHROPIC_API_KEY`.
4. In OpenAI Platform, create an API key: https://platform.openai.com/api-keys
5. Add repository Actions secret `OPENAI_API_KEY`.
6. Add repository Actions variable `OPENAI_REVIEW_MODEL` with a model available to your API project that supports the Responses API and Structured Outputs. The value is an API model identifier, not your ChatGPT subscription tier.
7. Create the repository label `ai-develop` (Settings → Issues → Labels). Use a distinct color/name so it is obvious that applying it starts an AI coding workflow.
8. Confirm the default branch is protected and requires a human pull-request approval plus successful CI checks.

Secrets and variables are configured under Repository → Settings → Secrets and variables → Actions. Never put secret values in issue comments, source files, or workflow YAML.

## How to launch an implementation task

1. Create an issue using the **AI-assisted development task** template.
2. Write bounded acceptance criteria, scope, constraints, and tests.
3. Review the issue yourself.
4. Apply the `ai-develop` label. Only the repository owner can launch the workflow.
5. Claude reads the shared instructions, implements on a branch, runs what it can, and opens a PR.
6. Existing CI workflows run on the PR. The OpenAI API reviewer posts an advisory comment.
7. Claude is not automatically re-triggered by the OpenAI review. Review findings and ask Claude for a correction round by creating a follow-up issue or explicitly requesting changes on the PR.
8. You inspect CI, diffs, review findings, and app behavior, then merge manually only when satisfied.

## Safety controls
- Claude Action has repository contents, issue, and pull-request write access because it needs to create a branch and PR. Use the owner-only issue-label trigger and review all generated changes.
- OpenAI review job has contents/read and PR/read plus issue-comment/write permission. It cannot push code, approve, merge, or deploy.
- The OpenAI workflow uses the trusted base revision only and does not execute PR code. Fork PRs are skipped.
- No automatic merge, auto-deploy, release publication, production secrets, production database access, or EC2 access is granted to either model.
- Automated review is advisory and may be wrong or incomplete. Human review and CI remain mandatory.
- Keep Claude and OpenAI API usage/billing separate from ChatGPT and Claude consumer subscriptions.

## Existing workflows preserved
The existing `android-apk.yml`, `supplier-android-apk.yml`, release workflows, `backend-ci.yml`, and `deploy-backend.yml` are not replaced by this setup. Their deployment and signing secrets remain outside AI-agent access.

## Troubleshooting
- Claude does not start: verify the `ai-develop` label exists, the issue was labelled by `mamatapal`, the Claude GitHub App is installed for this repository, and `ANTHROPIC_API_KEY` is configured.
- OpenAI review does not start: this workflow becomes active after it is merged into the base branch. Check `OPENAI_API_KEY`, `OPENAI_REVIEW_MODEL`, and the Actions logs. Fork PRs are intentionally skipped.
- CI fails: use the existing workflow logs and artifacts. Do not ask an AI agent to mark a failing check as passed.
- API billing: check Anthropic Console and OpenAI Platform usage independently.
