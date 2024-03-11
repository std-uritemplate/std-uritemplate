#! /bin/bash

# This directory is a placeholder needed to add a second test target for the typescript
# project. To make the DX not suffer, let's run the init script of the typescript
# project when running tests on this, so to have cold runs work ok without surprises.
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )/../typescript

# This is intended to be a customizable entrypoint for each language, it has to be generic enough
(
  cd ${SCRIPT_DIR} && \
  rm -rf ${SCRIPT_DIR}/dist && \
  npm install && \
  npm run clean && \
  npm run build
)
