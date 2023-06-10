#!/bin/sh

# checks if locally staged changes are
# formatted properly. Ignores non-staged
# changes.
# Intended as git pre-commit hook

# running scalaFmt
cd "$(dirname "$0")" || exit
cd ../../
sbt scalafmtAll
git diff --quiet
formatted=$?

# format error output
if [ $formatted -eq 1 ]
then
    echo "The following files need formatting (in stage or committed):"
    git diff --name-only
    echo ""
    echo "Please run 'scalaFmtAll' to format the codebase."
    echo ""
fi

# undoing formatting:
git stash --keep-index > /dev/null
git stash drop > /dev/null