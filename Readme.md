# Standard Uri Template

This is intended to become a cross-language implementation of the Uri Template specification RFC 6570 Level 4.

In the Kiota project they are using Uri Templates to build URLs, and I have already spent enough time of my life dealing with:

- unmaintained projects
- scarce feedback from maintainers
- long release cycles
- different nuances in different implementations
- quirks and integration issues
- diamond dependencies

I'm fed up and I want to see how much effort would take to achieve:

- single repository
- multiple implementations
- fully automated testing standardized
- fully automated releases on tag
- same tradeoffs across languages
- familiar implementation across languages
- multiple maintainers in an independent organization (eventually)

Uri Template is(likely) going to be included in the next OpenAPI specification and we need to rely on a solid foundation to prevent our selves to spend long, tedious hours and days chasing hidden bugs, verifying compatibilities and waiting for unresponsive maintainers.

## Implementation

The plan is to start with a pure hand-crafted implementation in Java that is not using RegExp and, possibly scans the strings the least number of times.
As soon as a full implementation is done I plan to use ChatGPT to scaffold other languages and fix bugs using the BASH testing infrastructure.

The Java implementation already passes all of the tests in the upstream reference repo.

## Design decisions

This has to be done, we want to keep the number of options as low as possible, rough things might be:

- zero dependencies
- only single expansion will be supported
- as little public API as possible
- no language idiomatic API, only low level primitives
- portable implementation across languages based on widely available patterns
- target Level support is 4 (should pass all the canonical tests)
- favor maintenance
- performance until they compromise readability
- one implementation per ecosystem/runtime (e.g. 1 implementation in Java and no Kotlin/Scala/Closure ...)

Speed vs. Maintainability:
the decided implementation "should" be fast enough

# Adding a new language

This section explains the steps that are currently used to add a new language implementation:

1. Clone this repository with submodules `git clone --recurse-submodules ...`
2. Create a new folder named after the language
3. Create a `<lang>/init.sh` script that will do the setup and, if applicable, compile and bundle the implementation
4. Create a `<lang>/test.sh` script that should:
  - read `argv[0]` read the content of the referenced file and use it as the first argument (`template`)
  - read `argv[1]` read the content to a Map/Dictionary structure from json and use it as the second argument (`substitutions`)
  - invoke `StdUriTemplate.expand(template, substitutions)` and return the result to std-out(tip: you can use std-err for debugging purposes), in case of exceptions, return the string "false"
5. Test locally the implementation by filtering one example file at the time, a suggested order would be:
  - `./tests/test.sh <lang> spec-examples.json`
  - `./tests/test.sh <lang> spec-examples-by-section.json`
  - `./tests/test.sh <lang> extended-tests.json`
  - `./tests/test.sh <lang> negative-tests.json`
6. If a test doesn't pass you can easily re-run just the latest invocation by running: `./tests/re-test.sh <lang>`
7. Verify one last time that everything works by running `./tests/test.sh <lang>`
8. Add the language to the GH Action Matrix CI:
  - add the language to the matrix
  - add the setup with appropriate conditionals
  - run it in CI to verify that everything is passing
9. Add the setup to publish the implementation to a package manager or support this discussion with the repo maintainers
