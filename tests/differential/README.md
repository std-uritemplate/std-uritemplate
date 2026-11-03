# Differential testing

Property-based differential test: every implementation must expand the same template
and values exactly like the reference implementation (`java`). The fixtures in
`uritemplate-test` and `uritemplate-test-additional` remain the contract; this is for
discovering cases the fixtures do not cover.

`test_differential.py` uses [Hypothesis](https://hypothesis.readthedocs.io/):

- templates are generated from the RFC 6570 expression grammar (via `hypothesis.extra.lark`)
  and from free text over the interesting characters, so malformed templates are covered too;
- values are strings built from the character classes that matter (reserved and unreserved
  punctuation, percent triplets, controls, non-ASCII, combining marks), numbers at formatting
  boundaries, booleans, null, lists and maps;
- a failing example is shrunk to a minimal one and stored in `.hypothesis/`, so it is retried
  first on the next run;
- `seeds.jsonl` holds hand-written cases that are always replayed.

`run.py` is the executor: it discovers every top-level directory with `init.sh` and
`test.sh`, so a new implementation participates automatically.

Dependencies and the Python version are pinned in `pyproject.toml`, `uv.lock` and
`.python-version`; [uv](https://docs.astral.sh/uv/) sets everything up on first run.

```
for d in */; do if [ -f "$d/init.sh" ]; then bash "$d/init.sh"; fi; done
uv run --directory tests/differential pytest -n auto --hypothesis-seed=1
```

Environment variables: `DIFF_REFERENCE` (default `java`), `DIFF_TARGETS` (comma-separated
subset, default all discovered), `DIFF_EXAMPLES` (random cases per target, default 200).

`KNOWN` in `test_differential.py` lists divergences that are already understood (documented
RFC deviations shared by all ports, open bugs, harness quirks). Each entry excludes an input
class for the listed targets; remove the entry when the fix lands and Hypothesis will explore
that area again. A new failure means a new finding: fix the port, or add it to `KNOWN` with a
reason, and add the minimal case to `seeds.jsonl` so it is never lost.

The `Differential` workflow runs this nightly and on demand with the report in the job summary.
