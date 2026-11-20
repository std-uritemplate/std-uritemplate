"""Run one case through an implementation's test.sh.

An implementation is any top-level directory with both init.sh and test.sh
(the same contract tests/test.sh uses). Output is the expansion, "false",
"CRASH(rc=N)" or "TIMEOUT".
"""
import json
import os
import resource
import subprocess

ENV = {**os.environ, "LC_ALL": "C.UTF-8", "LANG": "C.UTF-8"}
resource.setrlimit(resource.RLIMIT_CORE, (0, 0))  # crashing harnesses must not dump core


def discover(repo):
    return sorted(d for d in os.listdir(repo)
                  if os.path.isfile(os.path.join(repo, d, "init.sh"))
                  and os.path.isfile(os.path.join(repo, d, "test.sh")))


def run_case(repo, target, case, timeout, workdir):
    template = os.path.join(workdir, "template.txt")
    subs = os.path.join(workdir, "substitutions.json")
    with open(template, "wb") as f:
        f.write(case["template"].encode("utf-8"))
    with open(subs, "w", encoding="utf-8") as f:
        json.dump(case["vars"], f, ensure_ascii=False)
    cmd = ["bash", os.path.join(repo, target, "test.sh"), template, subs]
    try:
        r = subprocess.run(cmd, capture_output=True, timeout=timeout, env=ENV)
    except subprocess.TimeoutExpired:
        return "TIMEOUT"
    if r.returncode != 0:
        return f"CRASH(rc={r.returncode})"
    # tests/test.sh compares through $(...), which drops every trailing newline
    return r.stdout.decode("utf-8", errors="backslashreplace").rstrip("\n")
