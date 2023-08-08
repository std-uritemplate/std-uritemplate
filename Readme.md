# std-uritemplate

[![GitHub license](https://img.shields.io/badge/license-APACHE-blue.svg)](https://github.com/std-uritemplate/std-uritemplate/blob/main/LICENSE)
[![Build Status](https://github.com/std-uritemplate/std-uritemplate/actions/workflows/test.yml/badge.svg?branch=main)](https://github.com/std-uritemplate/std-uritemplate/blob/main/.github/workflows/test.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.std-uritemplate/std-uritemplate/badge.svg?style=flat)](https://central.sonatype.com/artifact/io.github.std-uritemplate/std-uritemplate)
[![NPM version](https://img.shields.io/npm/v/%40std-uritemplate%2Fstd-uritemplate.svg?style=flat&color=green)](https://www.npmjs.com/package/@std-uritemplate/std-uritemplate)

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/std-uritemplate/std-uritemplate/blob/main/Contributing.md)

This is intended to become a complete and maintained cross-language implementation of the [Uri Template specification RFC 6570](https://github.com/uri-templates/uritemplate-spec) Level 4.

## Available implementations

| Language | Complete | Reviewed | Published |
|---|---|---|---|
| Java | ✅ | ✅ | ✅ |
| Python | ✅ | ❌ | ❌ |
| Typescript | ✅ | ✅ | ✅ |
| Go | ✅ | ❌ | ❌ |

## Usage

### Java

You can use the library as a Maven dependency:

```xml
<dependency>
    <groupId>io.github.std-uritemplate</groupId>
    <artifactId>std-uritemplate</artifactId>
    <version>REPLACE-ME</version>
</dependency>
```

in Gradle:

```groovy
implementation 'io.github.std-uritemplate:std-uritemplate:REPLACE-ME'
```

and use it in your project:

```java
import io.github.stduritemplate.StdUriTemplate;

...

StdUriTemplate.expand(template, substitutions);
```

### Typescript/Javascript

Install the package using `npm`:

```
npm i @std-uritemplate/std-uritemplate
```

Use the package:

```js
const { StdUriTemplate } = require('@std-uritemplate/std-uritemplate');

...

StdUriTemplate.expand(template, substitutions);
```

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

all the rest, should not be directly accessible.

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
