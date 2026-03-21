# Session Landing Checklist

## Overview

This checklist ensures proper session completion and maintains project integrity. Follow these steps when ending your work session.

## Pre-Landing Checks

### 1. Quality Gate Verification
```bash
# Run full quality gate locally
make all

# Verify CI is passing for your branch
gh run list --branch $(git branch --show-current)
```

### 2. Code Review
- [ ] Ensure all code changes have been reviewed
- [ ] Verify commit messages follow conventional format
- [ ] Check that documentation is updated if needed
- [ ] Confirm tests cover new functionality

### 3. Issue Tracking
```bash
# Verify bd issue is still in progress
bd show <id> | grep status
```

### 4. Git Status
```bash
# Check for uncommitted changes
git status

# Ensure working directory is clean
git diff --staged --exit-code && git diff --exit-code
```

## Landing Procedure

### Step 1: Sync and Update Issue
```bash
# Sync bd state with git
bd sync

# Update issue status
bd update <id> --status=completed --reason="Completed work"
```

### Step 2: Commit and Push
```bash
# Add all changes
git add .

# Commit with proper message
git commit -m "feat: descriptive message"

# Sync again before push
bd sync

# Push with rebase
git pull --rebase && bd sync && git push
```

### Step 3: Verify Push
```bash
# Check remote status
git status  # Should show "up to date with origin"

# Monitor CI run
gh run list --branch $(git branch --show-current)
```

## Post-Landing Validation

### 1. CI Validation
- [ ] Monitor CI pipeline for your commit
- [ ] Verify all jobs pass (format-check, lint, test, build)
- [ ] Check test results in GitHub Actions artifacts
- [ ] Confirm build produces executable binary

### 2. Issue Closure
- [ ] Verify bd issue is marked as completed
- [ ] Check for any discovered-from links to new issues
- [ ] Ensure proper documentation updates

### 3. Repository Cleanliness
- [ ] No untracked files: `git clean -fd`
- [ ] No stashes: `git stash list`
- [ ] Branch is up to date: `git status`

## Common Scenarios

### Scenario: CI Failure After Push
1. **Reproduce locally**: `make all`
2. **Fix issues**: Address failing checks
3. **Commit fixes**: `git add . && git commit -m "fix: ci failures"`
4. **Push again**: `git push`
5. **Monitor CI**: `gh run list`

### Scenario: Merge Conflict
1. **Pull latest**: `git pull --rebase`
2. **Resolve conflicts**: Manually fix conflicts
3. **Run quality gate**: `make all`
4. **Commit resolution**: `git add . && git commit -m "fix: merge conflicts"`
5. **Push**: `git push`

### Scenario: Untracked Work
1. **Create bd issue**: `bd create "Unfinished work" -t task -p 2`
2. **Link to original**: Add `--deps discovered-from:<original-id>`
3. **Complete current work**: Follow landing procedure
4. **Resume later**: Use new issue for continuation

## Critical Rules

### ✅ MUST
- Run `make all` before every push
- Update bd issue status before pushing
- Push with rebase to maintain linear history
- Verify CI passes after push
- Keep working directory clean

### ❌ NEVER
- Push without running quality gate
- Leave uncommitted changes
- Skip bd issue updates
- Push to main directly (use PRs)
- End session with CI failures

## Recovery Procedures

### If Push Fails
```bash
# Pull latest changes
git pull --rebase

# Re-run quality gate
make all

# Fix any issues
# ... (fix code) ...

# Add and commit fixes
git add .
git commit -m "fix: ci failures"

# Push again
git push
```

### If CI Fails After Push
```bash
# Check CI logs for specific failures
gh run view <run-id>

# Reproduce locally
make all

# Fix issues
# ... (fix code) ...

# Commit and push
git add .
git commit -m "fix: ci failures"
git push
```

---

*Complete this checklist every time you finish working on the project to maintain quality and workflow consistency.*