#!/usr/bin/env python3
"""Run cases (JSON Lines) through every <lang>/test.sh and print results as JSON Lines.

A target is any top-level directory with both init.sh and test.sh.
Each result line is the case plus "out": {target: output}, where output is the
expansion, "false", "CRASH(rc=N)" or "TIMEOUT".
"""
import argparse
import concurrent.futures
import json
import os
import resource
import subprocess
import sys
import tempfile
import threading

ENV = {**os.environ, "LC_ALL": "C.UTF-8", "LANG": "C.UTF-8"}


def discover(repo):
    return sorted(d for d in os.listdir(repo)
                  if os.path.isfile(os.path.join(repo, d, "init.sh"))
                  and os.path.isfile(os.path.join(repo, d, "test.sh")))


def init(repo, target):
    r = subprocess.run(["bash", os.path.join(repo, target, "init.sh")], capture_output=True, env=ENV)
    if r.returncode != 0:
        sys.stderr.write(f"{target}: init.sh failed\n{r.stderr.decode(errors='replace')[-2000:]}\n")
    return r.returncode == 0


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
    out = r.stdout.decode("utf-8", errors="backslashreplace")
    return out[:-1] if out.endswith("\n") else out


def run_target(repo, target, cases, workers, timeout):
    local = threading.local()
    with tempfile.TemporaryDirectory(prefix=f"differential-{target}-") as tmp:
        def one(case):
            if not hasattr(local, "dir"):
                local.dir = tempfile.mkdtemp(dir=tmp)
            return run_case(repo, target, case, timeout, local.dir)

        outs = []
        with concurrent.futures.ThreadPoolExecutor(workers) as ex:
            for i, out in enumerate(ex.map(one, cases), 1):
                outs.append(out)
                if i % 100 == 0:
                    sys.stderr.write(f"{target}: {i}/{len(cases)}\n")
    sys.stderr.write(f"{target}: done\n")
    return target, outs


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("cases", help="JSON Lines file, or - for stdin")
    ap.add_argument("--repo", default=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    ap.add_argument("--targets", help="comma-separated subset (default: all discovered)")
    ap.add_argument("--exclude", default="typescript-mjs", help="comma-separated (default: typescript-mjs, same code as typescript)")
    ap.add_argument("--workers", type=int, default=4, help="parallel calls per target")
    ap.add_argument("--timeout", type=int, default=60, help="seconds per call")
    ap.add_argument("--skip-init", action="store_true")
    args = ap.parse_args()
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))  # crashing harnesses must not dump core

    with (sys.stdin if args.cases == "-" else open(args.cases, encoding="utf-8")) as f:
        cases = [json.loads(line) for line in f if line.strip()]
    targets = args.targets.split(",") if args.targets else discover(args.repo)
    targets = [t for t in targets if t not in args.exclude.split(",")]
    if not args.skip_init:
        with concurrent.futures.ThreadPoolExecutor(len(targets)) as ex:
            ok = dict(zip(targets, ex.map(lambda t: init(args.repo, t), targets)))
        targets = [t for t in targets if ok[t]]
    sys.stderr.write(f"{len(cases)} cases, targets: {' '.join(targets)}\n")

    with concurrent.futures.ThreadPoolExecutor(len(targets)) as ex:
        results = dict(ex.map(lambda t: run_target(args.repo, t, cases, args.workers, args.timeout), targets))
    for i, case in enumerate(cases):
        print(json.dumps({**case, "out": {t: results[t][i] for t in targets}}, ensure_ascii=False))


if __name__ == "__main__":
    main()
