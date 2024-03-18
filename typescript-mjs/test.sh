#! /bin/bash

# This directory is a placeholder needed to add a second test target for the typescript
# project. Sources and tests are located in the typescript project.
TYPESCRIPT_SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )/../typescript

# This is intended to be a customizable entrypoint for each language, it has to be generic enough
node ${TYPESCRIPT_SCRIPT_DIR}/test.mjs.mjs $@
