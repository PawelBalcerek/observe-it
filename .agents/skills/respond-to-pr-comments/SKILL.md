---
name: respond-to-pr-comments
description: "Respond to PR review comments, apply agreed fixes, and reply to and resolve review threads."
disable-model-invocation: true
---

Runs stage 3 of _Ticket lifecycle_ in `CONTRIBUTING.md`. First aligns with the human in chat on every unresolved review
thread, then applies the agreed fixes and closes the loop on GitHub so every fixed or dropped thread ends **replied and
resolved** while threads still under discussion stay open.

## Steps

1. **Fetch unresolved threads.** Infer the PR for the current branch (`gh pr view --json number`) unless a PR number was
   passed, then fetch every unresolved review thread:

   ```bash
   gh api graphql -F owner=<owner> -F repo=<repo> -F pr=<n> -f query='
     query($owner: String!, $repo: String!, $pr: Int!) {
       repository(owner: $owner, name: $repo) {
         pullRequest(number: $pr) {
           reviewThreads(first: 100) {
             nodes { id isResolved path line comments(first: 20) { nodes { databaseId author { login } body } } }
           }
         }
       }
     }' --jq '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved | not)'
   ```

   Done when every unresolved thread and its file/line context are loaded.

2. **Discuss in chat.** Present a concise response for each unresolved thread in chat: state whether you agree with the
   comment, propose the concrete fix (or an alternative, or push back with reasons), and stop to wait for the human's
   reply. Iterate in chat until every thread has an agreed outcome: _fix_, _drop_ (agreed not to change, with the
   reason), or _open_ (keep discussing later).

   Classify every human reply. An **approval** is a bare yes to the whole plan as last presented ("good", "ok", "yes",
   "go ahead", "I accept"). Anything else is an **amendment**: a suggestion, question, condition, alternative, "wdyt",
   or "yes, but…", even when the rest of the reply approves. On an amendment, fold it into the plan, restate the full
   amended plan (every thread, not just the changed ones), ask whether we are on the same page, and wait. This step
   edits no files. Done when the human's latest reply is an approval of the plan exactly as last restated.

3. **Fix & push.** Implement every _fix_ outcome, run `./gradlew check`, run the `code-review` skill on the branch,
   amend the single commit (`git commit --amend`, keeping the commit message accurate), and `git push --force-with-lease`.
   Done when `./gradlew check` is green and the remote branch holds the amended commit.

4. **Refresh the PR.** Update the PR title and body (`gh pr edit <n> --title ... --body ...`, using the `pr` skill
   template) so they reflect the amended commit. Done when `gh pr view <n>` matches the updated commit scope.

5. **Reply.** For each _fix_ and _drop_ thread, reply to its top-level comment (`databaseId`):

   ```bash
   gh api repos/<owner>/<repo>/pulls/<n>/comments/<databaseId>/replies -f body='🤖 <what changed, or why dropped> (<short-sha>)'
   ```

   Start with `🤖`, state the concrete change (or the agreed reason for dropping it) in one or two sentences, and end
   with the amended commit's short SHA. Done when every _fix_ and _drop_ thread has a posted reply ending with the
   pushed commit SHA.

6. **Resolve.** Resolve each replied thread by its GraphQL node `id`:

   ```bash
   gh api graphql -f id=<thread-id> -f query='mutation($id: ID!) { resolveReviewThread(input: {threadId: $id}) { thread { isResolved } } }'
   ```

   Done when re-running step 1's query returns only _open_ threads.

7. **Report** in chat: the pushed SHA, the threads resolved, and any threads left open. Done when the summary is posted
   in chat.
