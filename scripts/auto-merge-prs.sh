#!/bin/bash

# Auto-merge script: Enable auto-merge (squash) on all open PRs
# Usage: ./scripts/auto-merge-prs.sh [--repo OWNER/REPO] [--branch BRANCH] [--dry-run]

set -euo pipefail

# Configuration
REPO="${1:-}"
BRANCH="main"
DRY_RUN=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --repo)
      REPO="$2"
      shift 2
      ;;
    --branch)
      BRANCH="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Validate repo format
if [[ -z "$REPO" ]] || [[ ! "$REPO" =~ ^[^/]+/[^/]+$ ]]; then
  echo "Usage: $0 --repo OWNER/REPO [--branch BRANCH] [--dry-run]"
  echo "Example: $0 --repo azertytr/ebooks"
  exit 1
fi

echo "🔄 Auto-merging PRs for $REPO (branch: $BRANCH)"
[[ "$DRY_RUN" == "true" ]] && echo "📋 DRY RUN MODE - no actual merges will occur"
echo ""

# Get all open PRs targeting the specified branch
PRS=$(gh pr list \
  --repo "$REPO" \
  --state open \
  --base "$BRANCH" \
  --jq '.[] | "\(.number) \(.isDraft)"' \
  --limit 100)

if [[ -z "$PRS" ]]; then
  echo "✅ No open PRs found for $REPO/$BRANCH"
  exit 0
fi

# Track results
MERGED=0
SKIPPED=0
FAILED=0

while read -r PR_LINE; do
  PR_NUMBER=$(echo "$PR_LINE" | awk '{print $1}')
  IS_DRAFT=$(echo "$PR_LINE" | awk '{print $2}')

  # Skip draft PRs
  if [[ "$IS_DRAFT" == "true" ]]; then
    echo "⏭️  PR #$PR_NUMBER (draft - skipping)"
    ((SKIPPED++))
    continue
  fi

  echo -n "🔄 PR #$PR_NUMBER: "

  # Never merge a PR whose checks are failing or still running. There is no
  # branch protection requiring status checks, so `gh pr merge` would
  # otherwise merge immediately regardless of CI.
  if ! gh pr checks "$PR_NUMBER" --repo "$REPO" >/dev/null 2>&1; then
    echo "⏭️  checks failing or pending - skipping"
    ((SKIPPED++))
    continue
  fi

  if [[ "$DRY_RUN" == "true" ]]; then
    echo "would merge (checks green)"
    ((MERGED++))
  else
    if gh pr merge --auto --squash "$PR_NUMBER" --repo "$REPO" 2>/dev/null; then
      echo "✅ merged"
      ((MERGED++))
    else
      echo "⚠️  could not merge"
      ((FAILED++))
    fi
  fi
done <<< "$PRS"

echo ""
echo "📊 Summary:"
echo "   ✅ Auto-merged: $MERGED"
echo "   ⏭️  Skipped (draft): $SKIPPED"
echo "   ⚠️  Failed: $FAILED"
