# Contributing & Git Workflow

## Issue first

Every commit and pull request delivers exactly one GitHub issue. Before writing code, confirm the issue number; when
none exists, open one first (`gh issue create`, see `docs/agents/issue-tracker.md`) and work against it.

## Ticket-driven delivery

Every ticket is delivered as a single, self-contained pull request following these rules:

1. **One branch per ticket (`feat/<ticket-slug>`, `fix/<ticket-slug>` or `retro/<ticket-slug>`)**
   - Use `feat/<ticket-slug>` for feature tickets, `fix/<ticket-slug>` for bugfix tickets, and `retro/<ticket-slug>` for
     retrospective tickets, where `<ticket-slug>` is a concise kebab-case summary of the ticket title (for example,
     `feat/observed-app-create-order-endpoint`, `fix/mdc-cleanup-on-exception` or `retro/pr-32-learnings-and-workflow`).
   - Create the branch from an up-to-date `main` (or the open blocker branch) before the first commit.

2. **One commit per ticket (`feat: ...` / `fix: ...` / `retro: ...`)**
   - Keep the entire ticket implementation in a single commit on the ticket branch.
   - Format the commit subject line in lowercase Conventional Commits style: `feat: <lowercase summary>`,
     `fix: <lowercase summary>` or `retro: <lowercase summary>`. The optional commit body does not need to be lowercased.
   - When addressing code review feedback or follow-up changes, amend the existing commit (`git commit --amend`) or
     squash/rebase (`git rebase -i`) and force-push (`git push --force-with-lease`) rather than adding fixup commits.
   - Always keep the commit message up to date so it accurately reflects the latest changes in the commit.

3. **One pull request per ticket (including Stacked PRs)**
   - Open one pull request per ticket targeting `main` (when unblocked or blockers are already merged) or stacked on top
     of an open blocker branch.
   - **Using `gh stack` (installed GitHub CLI extension)**:
     - Initialize a stack from the base branch (`gh stack init`), add dependent ticket branches
       (`gh stack add <branch>`), push and create/update stacked PRs (`gh stack submit`), and rebase/sync the stack
       (`gh stack sync`).
   - **Using standard `gh pr`**:
     - Create a PR targeting `main` (`gh pr create --base main --head <branch>`) or targeting an open blocker branch
       (`gh pr create --base <blocker-branch> --head <branch>`).

4. **Issue linking & up-to-date PR descriptions**
   - Every PR description must link the ticket it resolves using `Closes #<ticket-number>` or
     `Resolves #<ticket-number>`.
   - Whenever the implementation or commit changes (for example, after review feedback), update the PR title and
     description (`gh pr edit <pr-number> --title "..." --body "..."`) so the PR description stays accurate and in sync
     with the code.

## Ticket lifecycle

Each stage ends when its completion criterion holds.

1. **Implement & publish** _(`/implement #<n>`)_: fetch the issue, create the ticket branch, build test-first (TDD), run
   `./gradlew check`, run the `code-review` skill and fix its findings, commit, push the branch, and open the PR (see
   _Ticket-driven delivery_ and the `pr` skill). Done when `./gradlew check` is green and the PR is open and links the
   issue.
2. **Human review** _(human)_: the human leaves review comments on the PR.
3. **Respond to review** _(`/respond-to-review`)_: run the `respond-to-review` skill to align on a fix plan in chat,
   amend and push the agreed fixes, and reply to and resolve each agreed review thread. Done when the remote PR is
   updated and every agreed thread is replied to and resolved.
4. Repeat from step 2 until the human merges.

## Retrospectives

A retro is a ticket like any other: open a `retro: ...` issue, work on a `retro/<slug>` branch, and deliver one
`retro: ...` commit through the same lifecycle. Besides adding or sharpening rules in `docs/agents/*`,
`CONTRIBUTING.md` or `AGENTS.md`, a retro audits the whole codebase against every rule it touches and fixes each
violation in the same commit; the PR description lists every finding and its fix.
