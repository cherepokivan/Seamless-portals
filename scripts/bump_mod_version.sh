#!/usr/bin/env bash
# Increments mod_version=A.B.C using decimal digits only:
# 1.0.9 -> 1.1.0 and 1.9.9 -> 2.0.0.
set -euo pipefail

properties_file="${1:-gradle.properties}"

if [[ ! -f "$properties_file" ]]; then
  echo "Version file not found: $properties_file" >&2
  exit 1
fi

current="$(sed -nE 's/^mod_version=([0-9]+\.[0-9]+\.[0-9]+)$/\1/p' "$properties_file")"
if [[ -z "$current" ]]; then
  echo "mod_version must be present as three non-negative decimal fields" >&2
  exit 1
fi

IFS='.' read -r major minor patch <<< "$current"
if (( minor > 9 || patch > 9 )); then
  echo "minor and patch fields must be within 0..9, found $current" >&2
  exit 1
fi

if (( patch < 9 )); then
  ((patch += 1))
elif (( minor < 9 )); then
  ((minor += 1))
  patch=0
else
  ((major += 1))
  minor=0
  patch=0
fi

next="$major.$minor.$patch"
if ! sed -i -E "s/^mod_version=[0-9]+\.[0-9]+\.[0-9]+$/mod_version=$next/" "$properties_file"; then
  echo "Unable to update mod_version" >&2
  exit 1
fi

echo "$next"
