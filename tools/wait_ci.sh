#!/usr/bin/env bash
# Usage: wait_ci.sh <repo dir> <sha>. Polls the ci-status branch until it reports <sha>, max ~25 min.
repo="$1"; sha="$2"
cd "$repo" || exit 2
for i in $(seq 1 100); do
  if git fetch -q origin ci-status 2>/dev/null; then
    got=$(git show FETCH_HEAD:status.txt 2>/dev/null | sed -n 's/^sha=//p')
    if [ "$got" = "$sha" ]; then
      git show FETCH_HEAD:status.txt
      echo "----- errors.txt -----"
      git show FETCH_HEAD:errors.txt 2>/dev/null | head -n 150
      exit 0
    fi
  fi
  sleep 15
done
echo "TIMEOUT waiting for CI result of $sha"
exit 1
