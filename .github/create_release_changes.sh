#!/usr/bin/env bash

mkdir -p target/ci

target_verison${1:-${RELEASE_VERSION}}

echo "# Changes
" > target/ci/CHANGELOG.md

start=`grep -n -P "^##\\s+\\[?0.1.0" CHANGELOG.md | cut -d: -f1`
if [[ -z "$start" ]]; then
  echo "No changelog found for $target_verison"
  exit 0
fi

end=`tail -n +$(( $start + 1 )) CHANGELOG.md | grep -n "^## " | cut -d: -f1`

if [[ -z "$end" ]]; then
  tail -n +$start CHANGELOG.md >> target/ci/CHANGELOG.md
else
  tail -n +$start CHANGELOG.md | head -n $end >> target/ci/CHANGELOG.md
fi

cat target/ci/CHANGELOG.md