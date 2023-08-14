#! /bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# This is intended to be a customizable entrypoint for each language, it has to be generic enough
(
  cd ${SCRIPT_DIR} && \
  gem build stduritemplate.gemspec && \
  gem install ./stduritemplate-0.0.0.gem
)
