# Contributing & Git Workflow

## Ticket-driven delivery

Every ticket is delivered as a single, self-contained pull request following these rules:

1. **One branch per ticket (`feat/<ticket-slug>` or `fix/<ticket-slug>`)**
   - Use `feat/<ticket-slug>` for feature tickets and `fix/<ticket-slug>` for bugfix tickets, where `<ticket-slug>` is a
     concise kebab-case summary of the ticket title (for example, `feat/observed-app-create-order-endpoint` or
     `fix/mdc-cleanup-on-exception`).

2. **One commit per ticket (`feat: ...` / `fix: ...`)**
   - Keep the entire ticket implementation in a single commit on the ticket branch.
   - Format the commit subject line in lowercase Conventional Commits style: `feat: <lowercase summary>` or
     `fix: <lowercase summary>`. The optional commit body does not need to be lowercased.
   - When addressing code review feedback or follow-up changes, amend the existing commit (`git commit --amend`) or
     squash/rebase (`git rebase -i`) and force-push (`git push --force-with-lease`) rather than adding fixup commits.
   - Always keep the commit message up to date so it accurately reflects the latest changes in the commit.

3. **One pull request per ticket (including Stacked PRs)**
   - Open one pull request per ticket targeting `main` (when unblocked or blockers are already merged) or stacked on top
     of an open blocker branch (`feat/<blocker-slug>` or `fix/<blocker-slug>`).
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
