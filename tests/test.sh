#! /bin/bash
# set -euxo pipefail
set -euo pipefail

LANGUAGE=${1}
FILE_FILTER=${2:-"*.json"}
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "Going to test compatibility with language ${LANGUAGE}"

if [ ! -d ${SCRIPT_DIR}/../${LANGUAGE} ]; then
  echo "Language ${LANGUAGE} doesn't exists! Please first create a folder and follow the instructions to create a new implementation"
  exit 1;
fi

if [ ! -f ${SCRIPT_DIR}/../${LANGUAGE}/test.sh ]; then
  echo "Please implement a `test.sh` that conforms with the requirement"
  exit 1;
fi

if [ ! -f ${SCRIPT_DIR}/../uritemplate-test/spec-examples.json ]; then
  echo "Please clone this repo and all of the submodules with \"git clone --recurse-submodules\" or run \"git submodule update --init\""
  exit 1
fi

echo "Initialize"
bash ${SCRIPT_DIR}/../${LANGUAGE}/init.sh
echo "Initialization done"

for SPEC_FILE in $(find "${SCRIPT_DIR}/../uritemplate-test" "${SCRIPT_DIR}/../uritemplate-test-additional" -name "${FILE_FILTER}" -type f); do

  echo "Test file: ${SPEC_FILE}"
  jq -rc '. | keys[]' ${SPEC_FILE} | while read -r LEVEL_KEY; do

    echo "Testing Section with Key: '${LEVEL_KEY}'"
    PARAMS=$(jq -rc ".[\"${LEVEL_KEY}\"].variables" ${SPEC_FILE})
    # passing strings with spaces seems to break a lot of stuffs, using a File instead to keep it simpler
    echo "${PARAMS}" > ${SCRIPT_DIR}/substitutions.json

    jq -rc ".[\"${LEVEL_KEY}\"].testcases[]" ${SPEC_FILE} | while read -r testcase; do
      TEMPLATE=$(echo $testcase | jq -rc '.[0]')
      echo "${TEMPLATE}" > ${SCRIPT_DIR}/template.txt

      POSSIBLE_RESULTS=$(echo $testcase | jq -rc '.[1] | if type=="string" then [.] else if type=="boolean" then [.] else . end end')
      echo ${POSSIBLE_RESULTS} > ${SCRIPT_DIR}/possible-results.json
      
      echo 0 > "${SCRIPT_DIR}/result"
      RESULT=$(${SCRIPT_DIR}/../${LANGUAGE}/test.sh "${SCRIPT_DIR}/template.txt" "${SCRIPT_DIR}/substitutions.json")
      echo ${POSSIBLE_RESULTS} | jq -rc ".[]" | while read -r possible_result; do
        if [ "${possible_result}" == "${RESULT}" ]; then
          echo 1 > "${SCRIPT_DIR}/result"
        fi
      done

      if [ $(cat "${SCRIPT_DIR}/result") -eq 1 ]; then
        echo "Passed! ✅"
      else
        echo "Test not passed. ❌"

        echo "Template: \"${TEMPLATE}\""
        echo "Substitutions: ${PARAMS}"
        echo "Expected result in those: ${POSSIBLE_RESULTS}"
        echo "but got: \"${RESULT}\""
        exit 1
      fi
    done
  done

done
