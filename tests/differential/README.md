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

## When the nightly run fails

The `Differential` workflow runs every night and on demand (inputs: examples per pair, seed,
reference). The job summary holds the pytest output; the full log is an artifact; the
Hypothesis database is cached, so a failing example is retried first the next night until it
is dealt with. A random-case failure names the pair, the minimal template and variables,
both outputs and a `@reproduce_failure` line:

```
FAILED test_differential.py::test_random_case_pair_agrees[go-java]
AssertionError: go gave '', java gave '%C2%85'
Failing test case: test_random_case_pair_agrees(a='go', b='java', template='\x85', variables={})
```

A seed failure groups the implementations by output, so the odd one out is visible at once:

```
FAILED test_differential.py::test_seed_case_all_agree[swift-literal-combining-mark]
AssertionError: {'a%CC%81': ['java', 'python', 'go', ...], '%61%CC%81': ['swift']}
```

`CRASH(rc=N)` or `TIMEOUT` in place of an output is a harness that exited non-zero or hung.
The same `CRASH` on every case of one implementation means its build or toolchain is broken,
not the library.

Procedure, the same day the run goes red:

1. **Reproduce.** Write the template and the substitutions JSON to two files and run
   `<lang>/test.sh template.txt substitutions.json` for the implementations involved. To
   replay the whole night, trigger the workflow with the same seed, or locally
   `DIFF_TARGETS=<a>,<b> uv run --directory tests/differential pytest -n auto --hypothesis-seed=<run id>`.
2. **Classify.** A bug in one port; a bug in the reference that the others mirrored (fix
   `java` first, then the followers); an accepted divergence (implementation-defined, or an
   RFC decision not taken yet); or a harness quirk (trimming, exit code, stray output).
3. **Pin it.** Add the minimal case to `seeds.jsonl` with a descriptive id. If the fix is not
   in the same change, add a `KNOWN` entry (next section) for the affected implementations in
   the same PR: the job is green again and the case stays visible as a skip with its reason.
   Do not leave the nightly red: Hypothesis stops at the first counterexample per pair, so an
   unaddressed failure hides whatever that pair would find next.
4. **Fix.** The fixing PR deletes the `KNOWN` entry, so the seed asserts again and random
   cases of that class are explored again. Run the affected pair locally before pushing.
5. **Promote.** Once every implementation agrees on the intended output, move the case to
   `uritemplate-test-additional/edge-cases.json`, which the regular suite enforces on every
   PR, and drop the seed: seeds are pending cases, fixtures are settled ones.

## Excluding a known divergence

`KNOWN` in `test_differential.py` is a list of `(implementations, reason, predicate)`:

- `implementations`: a tuple of the implementations that differ, or `None` when the
  divergence is shared by all of them (a documented RFC deviation, a harness convention).
  A random case is excluded for a pair when either side is listed; a seed is skipped for the
  listed implementations only, so the others still have to agree on it.
- `reason`: one line, shown as the skip reason; say what differs and, if open, why.
- `predicate(template, variables)`: returns true for the inputs to exclude. `template` is the
  template string, `variables` the parsed substitutions (strings, numbers, booleans, `None`,
  lists, dicts). `walk(variables)` yields every nested value and `texts(variables)` every
  string, keys included. Keep it as narrow as you can: everything it matches is removed from
  what Hypothesis explores.

```python
# only the named implementation, matched on the template
(("php",), "variable name 0 is rejected",
 lambda t, v: re.search(r"\{[+#./;?&]?0[,}:*]", t)),

# shared by all implementations, matched on the values
(None, "number formatting is implementation-defined (floats, integers >= 1e6)",
 lambda t, v: any(isinstance(x, float) or (isinstance(x, int) and abs(x) >= 10 ** 6) for x in walk(v))),
```
