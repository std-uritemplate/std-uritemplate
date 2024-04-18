#! /bin/bash
set -euxo pipefail

VERSION=${1}
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

(
  cd ${SCRIPT_DIR}
  git checkout main
  git reset --hard origin/main
  git pull
  git submodule update --recursive --remote
  git add .
  git commit -m "update submodules" --allow-empty
  git push
  git tag ${VERSION}
  git push origin ${VERSION}
)
