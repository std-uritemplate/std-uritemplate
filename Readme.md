# std-uritemplate

[![GitHub license](https://img.shields.io/github/license/std-uritemplate/std-uritemplate.svg)](https://github.com/std-uritemplate/std-uritemplate/blob/main/LICENSE)
[![Build Status](https://github.com/std-uritemplate/std-uritemplate/actions/workflows/test.yml/badge.svg?branch=main)](https://github.com/std-uritemplate/std-uritemplate/blob/main/.github/workflows/test.yml)
[![GitHub Release](https://img.shields.io/github/tag/std-uritemplate/std-uritemplate.svg?style=flat&color=green)](https://github.com/std-uritemplate/std-uritemplate/tags)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.std-uritemplate/std-uritemplate/badge.svg?style=flat&color=green)](https://central.sonatype.com/artifact/io.github.std-uritemplate/std-uritemplate)
[![NPM version](https://img.shields.io/npm/v/%40std-uritemplate%2Fstd-uritemplate.svg?style=flat&color=green)](https://www.npmjs.com/package/@std-uritemplate/std-uritemplate)
[![Go Reference](https://pkg.go.dev/badge/github.com/std-uritemplate/std-uritemplate/go.svg?style=flat&color=green)](https://pkg.go.dev/github.com/std-uritemplate/std-uritemplate/go)
[![PyPI Version](https://img.shields.io/pypi/v/std-uritemplate.svg?style=flat&color=green)](https://pypi.python.org/pypi/std-uritemplate)
[![NuGet Version](https://img.shields.io/nuget/v/Std.UriTemplate.svg?style=flat&color=green)](https://www.nuget.org/packages/Std.UriTemplate/)
[![Gem Version](https://badge.fury.io/rb/stduritemplate.svg?style=flat&color=green)](https://badge.fury.io/rb/stduritemplate)
[![Packagist Version](https://poser.pugx.org/stduritemplate/stduritemplate/v?style=flat)](https://packagist.org/packages/stduritemplate/stduritemplate)
[![Pub Version](https://img.shields.io/pub/v/std_uritemplate)](https://pub.dev/packages/std_uritemplate)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/std-uritemplate/std-uritemplate/blob/main/Contributing.md)

This is a complete and maintained cross-language implementation of the [Uri Template specification RFC 6570](https://github.com/uri-templates/uritemplate-spec) Level 4.

> [!NOTE]  
> Low activity is this repository is **expected** as long as there are no outstanding bug reports the implementations are considered **stable** and **mature**.

## Available implementations

| Language | Complete | Reviewed | Published |
|---|---|---|---|
| Java | ✅ | ✅ | ✅ |
| Python | ✅ | ❌ | ✅ |
| Typescript | ✅ | ✅ | ✅ |
| Go | ✅ | ✅ | ✅ |
| C# | ✅ | ✅ | ✅ |
| Ruby | ✅ | ❌ | ✅ |
| PHP | ✅ | ✅ | ✅ |
| Swift | ✅ | ❌ | ✅ |
| Dart | ✅ | ✅ | ✅ |

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

### Python

Install the package with `pip` (or any alternative):

```bash
pip install std-uritemplate
```

Use the library in your project:

```python
from stduritemplate import StdUriTemplate

...

StdUriTemplate.expand(template, substitutions)
```

### Typescript/Javascript

Install the package using `npm`:

```bash
npm i @std-uritemplate/std-uritemplate
```

Use the package:

```js
const { StdUriTemplate } = require('@std-uritemplate/std-uritemplate');

...

StdUriTemplate.expand(template, substitutions);
```

### Go

Install the package:

```bash
go get github.com/std-uritemplate/std-uritemplate/go
```

and use it:

```go
import stduritemplate "github.com/std-uritemplate/std-uritemplate/go"

...

stduritemplate.Expand(template, substitutions)
```

### C#

Install the package:

```bash
dotnet add package Std.UriTemplate
```

and use it:

```csharp
Std.UriTemplate.Expand(template, substitutions);
```

### Ruby

Install the package:

```bash
gem install stduritemplate
```

and use it:

```ruby
require 'stduritemplate'

...

StdUriTemplate.expand(template, substitutions)
```

### PHP
<!-- Tested following this guide: https://blog.damirmiladinov.com/php/building-and-distributing-a-command-line-php-application.html -->
Install the package:

```bash
composer require stduritemplate/stduritemplate
```

and use it:

```php
use StdUriTemplate\StdUriTemplate;

...

StdUriTemplate::expand($template, $substitutions);
```

### Swift

Install the package, adding to `Package.swift`:

```swift
let package = Package(
    ...
    dependencies: [
        ...
        .package(
            url: "https://github.com/std-uritemplate/std-uritemplate-swift.git", 
            from: "<version>"
        )
    ],
    targets: [
        .executableTarget(
            ...
            dependencies: [
                ...
                .product(name: "stduritemplate",
                        package: "std-uritemplate-swift")
            ]
            ...
            ),
    ]
)
```

and use it:

```swift
import stduritemplate

...

StdUriTemplate.expand(template, substitutions: substs)
```

### Dart

Install the package:

```bash
dart pub add std_uritemplate
```

for flutter:

```bash
flutter pub add std_uritemplate
```

and use it:

```dart
import 'package:std_uritemplate/std_uritemplate.dart';

...

print(StdUriTemplate.expand(template, substitutions));
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
- substitutions will be performed for primitive types and date-time

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
