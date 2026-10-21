#!/usr/bin/env bash
# Usage: wait_ci.sh <repo dir> <sha>. Polls the ci-status branch until it reports <sha>, max ~25 min.
repo="$1"; sha="$2"
cd "$repo" || exit 2
for i in $(seq 1 100); do
  if git fetch -q origin ci-status 2>/dev/null; then
    status=$(git show FETCH_HEAD:status.txt 2>/dev/null)
    got=$(printf '%s\n' "$status" | sed -n 's/^sha=//p')
    # A newer push cancels the run for this sha: its result never appears, the newer one's does.
    # (Fetching main replaces FETCH_HEAD, so the status was read first.)
    if [ -n "$got" ] && [ "$got" != "$sha" ] && git fetch -q origin main 2>/dev/null \
        && git merge-base --is-ancestor "$sha" "$got" 2>/dev/null; then
      echo "SUPERSEDED: $sha was replaced by $got, which reported:"
      printf '%s\n' "$status"
      exit 3
    fi
    if [ "$got" = "$sha" ]; then
      git show FETCH_HEAD:status.txt
      echo "----- errors.txt -----"
      git show FETCH_HEAD:errors.txt 2>/dev/null | head -n 150
      echo "----- models.txt -----"
      git show FETCH_HEAD:models.txt 2>/dev/null
      echo "----- apk-assets.txt -----"
      git show FETCH_HEAD:apk-assets.txt 2>/dev/null
      exit 0
    fi
  fi
  sleep 15
done
echo "TIMEOUT waiting for CI result of $sha"
exit 1
