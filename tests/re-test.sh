#! /bin/bash
# set -euxo pipefail
set -euo pipefail

LANGUAGE=${1}
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

echo "Re-running the latest test for ${LANGUAGE}"

echo "Initialize"
bash ${SCRIPT_DIR}/../${LANGUAGE}/init.sh
echo "Initialization done"

echo 0 > "${SCRIPT_DIR}/result"
RESULT=$(${SCRIPT_DIR}/../${LANGUAGE}/test.sh "${SCRIPT_DIR}/template.txt" "${SCRIPT_DIR}/substitutions.json")
cat ${SCRIPT_DIR}/possible-results.json | jq -rc ".[]" | while read -r possible_result; do
  if [ "${possible_result}" == "${RESULT}" ]; then
    echo 1 > "${SCRIPT_DIR}/result"
  fi
done

if [ $(cat "${SCRIPT_DIR}/result") -eq 1 ]; then
  echo "Passed! ✅"
else
  echo "Test not passed. ❌"

  echo "Template: \"$(cat ${SCRIPT_DIR}/template.txt)\""
  echo "Substitutions: $(cat ${SCRIPT_DIR}/substitutions.json)"
  echo "Expected result in those: $(cat ${SCRIPT_DIR}/possible-results.json)"
  echo "but got: \"${RESULT}\""
  exit 1
fi
