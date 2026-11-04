# Differential testing

Property-based differential test: every pair of implementations must expand the same
template and values to the same string. The fixtures in `uritemplate-test` and
`uritemplate-test-additional` remain the contract; this is for discovering cases the
fixtures do not cover.

`test_differential.py` uses [Hypothesis](https://hypothesis.readthedocs.io/):

- templates are generated from the RFC 6570 expression grammar (via `hypothesis.extra.lark`)
  and from free text over the interesting characters, so malformed templates are covered too;
- values are strings built from the character classes that matter (reserved and unreserved
  punctuation, percent triplets, controls, non-ASCII, combining marks), numbers at formatting
  boundaries, booleans, null, lists and maps;
- a failing example is shrunk to a minimal one and stored in `.hypothesis/`, so it is retried
  first on the next run;
- `seeds.jsonl` holds hand-written cases that are always replayed through every implementation.

`run.py` runs one case through an implementation's `test.sh`. It discovers every top-level
directory with `init.sh` and `test.sh`, so a new implementation participates automatically.

Dependencies and the Python version are pinned in `pyproject.toml`, `uv.lock` and
`.python-version`; [uv](https://docs.astral.sh/uv/) sets everything up on first run.

```
for d in */; do if [ -f "$d/init.sh" ]; then bash "$d/init.sh"; fi; done
uv run --directory tests/differential pytest -n auto --hypothesis-seed=1
```

Environment variables: `DIFF_TARGETS` (comma-separated implementations, default all
discovered), `DIFF_REFERENCE` (only compare pairs that include this implementation, e.g.
`java` to check a new port against the reference), `DIFF_EXAMPLES` (random cases per pair,
default 200).

## Triage

A failure prints the minimal case Hypothesis found, both outputs, and a
`@reproduce_failure` line. Then:

1. Reproduce it by hand if needed: write the template and the substitutions JSON to two files
   and run `<lang>/test.sh template.txt substitutions.json`.
2. Decide what it is: a bug in one port (fix it), a bug in the reference that others mirrored
   (fix `java` first), an accepted divergence, or a harness quirk.
3. Add the case to `seeds.jsonl` with a descriptive id, so it is replayed on every run.
4. While the divergence is open, add an entry to `KNOWN` in `test_differential.py` naming the
   affected implementations and the reason; the seed is skipped for them and random cases of
   that class are excluded. Remove the entry in the PR that fixes it.
5. Once every implementation agrees on the intended output, add the case to
   `uritemplate-test-additional/edge-cases.json` (the contract, run on every PR) and drop the
   seed: seeds are pending cases, fixtures are settled ones.

The `Differential` workflow runs this nightly and on demand with the report in the job summary.
