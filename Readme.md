# Standard Uri Template

This is intended to become a complete and maintained cross-language implementation of the [Uri Template specification RFC 6570](https://github.com/uri-templates/uritemplate-spec) Level 4.

## Available implementations

| Language | Complete | Reviewed | Published |
|---|---|---|---|
| Java | ✅ | ✅ | ❌ |
| Python | ✅ | ❌ | ❌ |
| Typescript | ✅ | ❌ | ❌ |
| Go | ✅ | ❌ | ❌ |

## Design decisions

We have a set of design decisions to guide:

- zero dependencies
- no usage of regexp
- no options/configurations
- only single expansion will be supported
- single method public API
- no language idiomatic API, only 1 low level primitive - we do encourage language-specific wrapper/alternative libraries
- portable implementation across languages based on widely available patterns
- target Level support is 4 (should pass all the canonical tests)
- favor maintenance and readability
- performance until they compromise readability
- one implementation per ecosystem/runtime (e.g. 1 implementation in Java and no Kotlin/Scala/Closure, 1 in TS that will serve JS as well etc.)

## API

The public API is composed by a single method(in Java for simplicity):

```java
String expand(String template, Map<String, Object> substitutions)
```

all the rest, should, possibly, be marked as `private` and not directly accessible.

## Motivation

[<img alt="alt_text" src="https://imgs.xkcd.com/comics/dependency.png" />](https://xkcd.com/2347/)

In the Kiota project they are using Uri Templates to build URLs, and we have already spent enough life-time dealing with:

- unmaintained projects
- scarce feedback from maintainers
- long release cycles
- different nuances in different implementations
- quirks and integration issues
- frameworks and additional dependencies
- diamond transitive dependencies

We aim to do it differently, by reducing maintenance to a minimum by automating it, and sharing responsibilities to reduce the [bus/truck factor](https://en.wikipedia.org/wiki/Bus_factor#):

- single repository
- multiple implementations
- fully automated testing standardized
- fully automated releases on tag
- same tradeoffs across languages
- familiar implementation across languages
- multiple maintainers in an independent organization

Uri Template is(likely) going to be included in the next OpenAPI specification and we need to rely on a (more) solid foundation to prevent our selves to spend long, tedious hours and days chasing hidden bugs, verifying compatibilities and waiting for unresponsive maintainers.

## Implementation details

### Speed

The original Java implementation have been benchmarked against alternative implementations [here](https://github.com/andreaTP/std-uritemplate/tree/jmh).
Now it has a rough speed comparable with more advanced and mainstream implementation without sacrificing too much on readability and portability.
We do consider this the sweet spot for this project:

- fast enough to be competitive
- readable and maintainable across languages

### Go

In Go is not possible to deserialize JSON into a Map preserving the order of the elements ([reference](https://github.com/uri-templates/uritemplate-test/pull/58#issuecomment-1640029982)).
For the moment, we decided to sort the keys to have predictable results.

# Contributing

## Adding a new language

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
9. Add the corresponding dependabot configuration in `.github/dependabot.yml`
10. Add the setup to publish the implementation to a package manager or support this discussion with the repo maintainers
