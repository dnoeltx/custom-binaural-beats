#!/usr/bin/env bash
# Fails on British spellings or em/en dashes in files this project owns.
#
# Both rules exist for the same reason: the owner is American and does not type dashes,
# so either one in his own repository reads as machine written, which is the opposite of
# what this project is meant to demonstrate. British spellings reached 73 occurrences
# across 14 files before anyone noticed, which is why this is a build step.
#
# Vendored Spec Kit content under .claude/skills and .specify is excluded. It is upstream
# text, it legitimately contains dashes, and it is overwritten on the next tool upgrade.
#
# The dash check runs in Python rather than grep: on Windows, grep '[em-dash]' matches
# BYTES, and box-drawing characters used in directory trees share bytes with the dashes,
# so a grep check reports files that are perfectly clean.
set -euo pipefail

SPELLING='\b([Cc]olour|[Bb]ehaviour|[Ff]avour|[Ff]lavour|[Hh]onour|[Cc]entre|[Cc]entres|[Cc]entred|[Mm]etre|[Ll]itre|[Cc]atalogue|[Pp]rogramme|[Jj]udgement|[Ll]icence|[Gg]rey|[Ww]hilst|[Aa]mongst|[Ll]abelled|[Mm]odelled|[Tt]ravelled|[Cc]ancelled|[Oo]rganis(e|ed|ing|ation)|[Rr]ecognis(e|ed|ing)|[Pp]rioritis(e|ed|ing)|[Aa]nalys(e|ed|ing))\b'
EXCLUDE=(':!tools/check-prose.sh' ':!.claude/skills/*' ':!.specify/*' ':!*.png' ':!*.jar')

failed=0

if matches=$(git grep -I -n -E "$SPELLING" -- "${EXCLUDE[@]}" 2>/dev/null); then
  echo "British spellings found. This project uses American spelling everywhere:"
  echo "$matches"
  echo
  failed=1
fi

python - "${EXCLUDE[@]}" <<'PY' || failed=1
import subprocess, sys
excludes = sys.argv[1:]
files = subprocess.run(['git', 'ls-files', '--', *excludes], capture_output=True, text=True).stdout.split()
hits = []
for path in files:
    try:
        text = open(path, encoding='utf-8').read()
    except (UnicodeDecodeError, OSError):
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if any(ch in line for ch in ('—', '–')):
            hits.append(f'{path}:{number}')
if hits:
    print('Em or en dashes found. This project uses commas, colons and full stops:')
    print('\n'.join(hits))
    sys.exit(1)
PY

if [ "$failed" -ne 0 ]; then
  exit 1
fi
echo "Prose check passed: American spelling, no em or en dashes."
