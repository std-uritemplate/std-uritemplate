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

## Running

You need [uv](https://docs.astral.sh/uv/) and the toolchains of the implementations you want
to compare. uv installs Python and the test dependencies (pinned in `pyproject.toml`,
`uv.lock`, `.python-version`) on first run. Build each implementation once with its
`init.sh`, then run from the repository root:

```
# two implementations, 50 random cases plus the seeds: about a minute
DIFF_TARGETS=java,rust DIFF_EXAMPLES=50 uv run --directory tests/differential pytest -n auto

# a new port against the reference
DIFF_TARGETS=java,<lang> uv run --directory tests/differential pytest -n auto

# every pair among the implementations you have built
DIFF_TARGETS=java,python,go,rust uv run --directory tests/differential pytest -n auto

# everything discovered (requires every toolchain), reproducible
for d in */; do if [ -f "$d/init.sh" ]; then bash "$d/init.sh"; fi; done
uv run --directory tests/differential pytest -n auto --hypothesis-seed=1
```

- `DIFF_TARGETS`: comma-separated implementations (default: every top-level directory with
  `init.sh` and `test.sh`; an implementation whose toolchain is missing fails every case).
- `DIFF_REFERENCE`: only compare pairs that include this implementation.
- `DIFF_EXAMPLES`: random cases per pair (default 200).
- `--hypothesis-seed=N` makes a run reproducible; without it every run draws new cases.
- `-k random` runs only the property, `-k seed` only the seed replay, `-k go-rust` one pair.
- `-rs` lists the skipped seeds with the `KNOWN` reason; any other failure is a new finding.

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

## Excluding a known divergence

`KNOWN` in `test_differential.py` is a list of `(implementations, reason, predicate)`:

- `implementations`: a tuple of the implementations that differ, or `None` when the
  divergence is shared by all of them (a documented RFC deviation, a harness convention).
  A random case is excluded for a pair when either side is listed; a seed is skipped for the
  listed implementations only, so the others still have to agree on it.
- `reason`: one line, shown as the skip reason; say what differs and, if open, why.
- `predicate(template, variables)`: returns true for the inputs to exclude. `template` is the
  template string, `variables` the parsed substitutions (strings, numbers, booleans, `None`,
  lists, dicts). Keep it as narrow as you can: the broader the predicate, the less Hypothesis
  explores. `walk(variables)` yields every nested value for checks on values.

```python
# only the named implementation, matched on the template
(("php",), "variable name 0 is rejected",
 lambda t, v: re.search(r"\{[+#./;?&]?0[,}:*]", t)),

# shared by all implementations, matched on the values
(None, "number formatting is implementation-defined (floats, integers >= 1e6)",
 lambda t, v: any(isinstance(x, float) or (isinstance(x, int) and abs(x) >= 10 ** 6) for x in walk(v))),
```

Add the entry in the same change that adds the seed; remove it in the change that fixes the
implementation, and run the pair that was affected to confirm the area is clean again.

The `Differential` workflow runs this nightly and on demand with the report in the job summary.
