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
6. There are extended tests to cover edge cases and the correct handling of dates, in case the substitutions map contains a key "nativedate" convert it to the language native date format and run the extended tests:
    - `./tests/test.sh <lang> edge-cases.json`
7. If a test doesn't pass you can easily re-run just the latest invocation by running: `./tests/re-test.sh <lang>`
8. Verify one last time that everything works by running `./tests/test.sh <lang>`
9. Add the language to the GH Action Matrix CI:
    - add the language to the matrix
    - add the setup with appropriate conditionals
    - run it in CI to verify that everything is passing
10. Add the corresponding dependabot configuration in `.github/dependabot.yml`
11. Add the setup to publish the implementation to a package manager or support this discussion with the repo maintainers
12. Add the documentation regarding how to use the library as a dependency

## Advanced details

### Speed

The original Java implementation have been benchmarked against alternative implementations [here](https://github.com/std-uritemplate/std-uritemplate/tree/jmh).
Now it has a rough speed comparable with more advanced and mainstream implementation without sacrificing too much on readability and portability.
We do consider this the sweet spot for this project:

- fast enough to be competitive
- readable and maintainable across languages

### Publishing

Publishing should be fully automated and it will be performed anytime the repository is tagged on the `main` branch.
Please contact the repository owners to setup the required Secrets.

A full release of all of the implementations is performed with:

```bash
git checkout main
git pull
git tag <version>
git push origin <version>
```

### Go / Swift

In Go and Swift is not possible to deserialize JSON into a Map preserving the order of the elements ([reference](https://github.com/uri-templates/uritemplate-test/pull/58#issuecomment-1640029982)).
For the moment, we decided to sort the keys to have predictable results.
