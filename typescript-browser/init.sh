#! /bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
TYPESCRIPT_DIR=${SCRIPT_DIR}/../typescript
TYPESCRIPT_BROWSER_DIR=${SCRIPT_DIR}/../typescript-browser

(
  # Enter the typescript folder, build it and copy the dist folder to the typescript-browser folder.
  cd "${TYPESCRIPT_DIR}" && \
  rm -rf ${TYPESCRIPT_DIR}/dist && \
  npm install
  npm run clean && \
  npm run build
  cd "${TYPESCRIPT_BROWSER_DIR}" && \
  rm -rf ${TYPESCRIPT_BROWSER_DIR}/dist && \
  cp -R ${TYPESCRIPT_DIR}/dist . && \
  npm install
)
