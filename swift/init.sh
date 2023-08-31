#! /bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# This is intended to be a customizable entrypoint for each language, it has to be generic enough
(
  cd ${SCRIPT_DIR} && \
  rm -f ${SCRIPT_DIR}/test/Sources/StdUriTemplate.swift && \
  cp ${SCRIPT_DIR}/Sources/stduritemplate/StdUriTemplate.swift ${SCRIPT_DIR}/test/Sources/StdUriTemplate.swift && \
  cd ${SCRIPT_DIR}/test && \
  swift build -c release
)
