"""Differential property test: every implementation must expand like the reference.

Hypothesis generates templates from the RFC 6570 grammar (plus arbitrary text) and
values from the character classes that matter, shrinks any failing example to a
minimal one and keeps it in .hypothesis/ so it is retried first next time.

    pytest tests/differential -n auto --hypothesis-seed=1

Environment: DIFF_REFERENCE (java), DIFF_TARGETS (comma-separated, default all),
DIFF_EXAMPLES (random cases per target, default 200).
"""
import functools
import json
import os
import re
import sys
import tempfile

import pytest
from hypothesis import HealthCheck, assume, given, settings, strategies as st
from hypothesis.database import DirectoryBasedExampleDatabase
from hypothesis.extra.lark import from_lark
from lark import Lark

sys.path.insert(0, os.path.dirname(__file__))
from run import discover, run_case  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
REFERENCE = os.environ.get("DIFF_REFERENCE", "java")
TARGETS = (os.environ.get("DIFF_TARGETS") or ",".join(discover(REPO))).split(",")
TARGETS = [t for t in TARGETS if t not in (REFERENCE, "typescript-mjs")]
WORKDIR = tempfile.mkdtemp(prefix="differential-")

NAMES = ["v", "w", "list", "map", "a.b", "a_b", "%41", "undef", "constructor"]
GRAMMAR = r"""
start: (LITERAL | expression)*
expression: "{" OPERATOR? varspec ("," varspec)* "}"
varspec: NAME MODIFIER?
OPERATOR: "+" | "#" | "." | "/" | ";" | "?" | "&"
NAME: "v" | "w" | "list" | "map" | "a.b" | "a_b" | "%41" | "undef" | "constructor"
MODIFIER: "*" | ":" /[1-9][0-9]{0,3}/
LITERAL: /[A-Za-z0-9\-._~!$&'()*+,;=:@\/?#%\[\]]{1,6}/
"""
TOKENS = list("abcxyz019-._~!*'():/?#[]@$&+,;= \"<>\\^`{|}\t\n\x00\x7fé中🌉\u0085￿﷐ ́️‍١２") \
    + ["%41", "%C3%A9", "%C3", "%2f", "%80", "%zz", "%4", "%+1", "%"]
NUMBERS = [0, 1, -1, 42, 100000, 1000000, 1234567, 2 ** 31, 2 ** 53 + 1, 2 ** 64,
           0.1, 1.0, 1.5, -0.0, 1e21, 1e-7, 1e3]

text = st.lists(st.sampled_from(TOKENS), max_size=6).map("".join)
scalar = st.one_of(text, st.sampled_from(NUMBERS), st.booleans(), st.none())
value = st.one_of(
    scalar,
    st.lists(st.one_of(scalar, st.lists(scalar, max_size=2)), max_size=3),
    # sorted keys: ports that sort maps must agree with ports that keep insertion order
    st.dictionaries(text, scalar, max_size=3).map(lambda d: dict(sorted(d.items()))),
)
variables = st.dictionaries(st.sampled_from(NAMES), value, max_size=4)
templates = st.one_of(
    from_lark(Lark(GRAMMAR, start="start")),
    st.lists(st.sampled_from(TOKENS + ["{", "}", "{v", "v}", "{,", ",}", "{v:", "*:", "{+", "{#"]), max_size=8).map("".join),
)


def walk(v):
    yield v
    if isinstance(v, dict):
        v = list(v.values())
    if isinstance(v, list):
        for x in v:
            yield from walk(x)


# Known divergences: (targets or None for all, reason, predicate(template, variables)).
# Remove an entry when the corresponding fix lands; Hypothesis then explores that area.
KNOWN = [
    (None, "harnesses trim the template file differently (leading/trailing whitespace, NUL)",
     lambda t, v: t != t.strip(" \t\n\r\x0b\x0c\x00")),
    (None, "percent in values under + or #: reserved-buffer and %XX detection differ",
     lambda t, v: re.search(r"\{[+#]", t) and "%" in json.dumps(v)),
    (None, "~ * ' ( ) encoding differs between host encoders",
     lambda t, v: any(c in json.dumps(v) for c in "~*'()")),
    (None, "non-reserved ASCII and non-ucschar copied raw under + or # by some ports",
     lambda t, v: re.search(r"\{[+#]", t) and re.search(r'["<>\\^`{|}\x00-\x1f\x7f\u0080-\u009f﷐￿]', json.dumps(v, ensure_ascii=False))),
    (None, "number formatting is implementation-defined (floats, integers >= 1e6)",
     lambda t, v: any(isinstance(x, float) or (isinstance(x, int) and not isinstance(x, bool) and abs(x) >= 10 ** 6) for x in walk(v))),
    (None, "null or nested elements inside lists and maps crash or are dropped by some ports",
     lambda t, v: any(isinstance(x, (list, dict)) and any(y is None or isinstance(y, (list, dict)) for y in walk(x) if y is not x) for x in walk(v))),
    (None, "exploded map under ; with an empty value ignores ifemp",
     lambda t, v: "{;" in t and "*" in t and any(x == "" for x in walk(v))),
    (None, "java and python accept Unicode digits as prefix length",
     lambda t, v: re.search(r"[١２]", t)),
    (("php",), "empty expression crashes with TypeError; numeric-string map keys become lists",
     lambda t, v: re.search(r"\{[+#./;?&]?(,|\})", t) or any(isinstance(x, dict) and any(k.isdigit() for k in x) for x in walk(v))),
    (("typescript",), "Object.prototype names resolve through the substitutions object",
     lambda t, v: re.search(r"constructor|toString|valueOf|hasOwnProperty|__proto__|isPrototypeOf|propertyIsEnumerable|toLocaleString", t)),
    (("swift",), "grapheme clusters: a combining mark merges with the preceding character",
     lambda t, v: re.search(r"[́️‍]", t)),
]


def known(target, template, variables):
    for targets, reason, predicate in KNOWN:
        if (targets is None or target in targets) and predicate(template, variables):
            return reason
    return None


@functools.lru_cache(maxsize=4096)
def expand(target, template, variables_json):
    case = {"template": template, "vars": json.loads(variables_json)}
    return run_case(REPO, target, case, timeout=60, workdir=WORKDIR)


def check(target, template, variables):
    dumped = json.dumps(variables, sort_keys=False)
    expected = expand(REFERENCE, template, dumped)
    actual = expand(target, template, dumped)
    assert actual == expected, f"{target} gave {actual!r}, {REFERENCE} gave {expected!r}"


@pytest.mark.parametrize("target", TARGETS)
@settings(max_examples=int(os.environ.get("DIFF_EXAMPLES", 200)), deadline=None, print_blob=True,
          database=DirectoryBasedExampleDatabase(os.path.join(HERE, ".hypothesis")),
          suppress_health_check=[HealthCheck.too_slow, HealthCheck.filter_too_much, HealthCheck.data_too_large])
@given(template=templates, variables=variables)
def test_random_case_matches_reference(target, template, variables):
    assume(known(target, template, variables) is None)
    check(target, template, variables)


with open(os.path.join(HERE, "seeds.jsonl"), encoding="utf-8") as f:
    SEEDS = [json.loads(line) for line in f if line.strip()]


@pytest.mark.parametrize("target", TARGETS)
@pytest.mark.parametrize("case", SEEDS, ids=lambda c: c["id"])
def test_seed_case_matches_reference(target, case):
    reason = known(target, case["template"], case["vars"])
    if reason:
        pytest.skip(reason)
    check(target, case["template"], case["vars"])
