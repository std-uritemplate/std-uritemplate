#! /bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
TYPESCRIPT_DIR=${SCRIPT_DIR}/../typescript
TYPESCRIPT_BROWSER_DIR=${SCRIPT_DIR}/../typescript-browser

(
  # Copy the typescript/src folder, build it in the typescript-browser folder.
  cd "${TYPESCRIPT_BROWSER_DIR}" && \
  rm -rf ${TYPESCRIPT_BROWSER_DIR}/src && \
  rm -rf ${TYPESCRIPT_BROWSER_DIR}/dist && \
  cp -R ${TYPESCRIPT_DIR}/src . && \
  npm install && \
  npm run clean && \
  npm run build && \
  rm -rf ${TYPESCRIPT_BROWSER_DIR}/src
)
