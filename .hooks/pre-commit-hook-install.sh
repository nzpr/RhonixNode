#!/bin/sh
cd "$(dirname "$0")" || exit
rm -Rf ../.git/hooks
mkdir ../.git/hooks
touch ../.git/hooks/pre-commit
rm ../.git/hooks/pre-commit
chmod +x pre-commit-hook.sh
ln -s ../../.hooks/pre-commit-hook.sh ../.git/hooks/pre-commit