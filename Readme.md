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

As a first iteration I'm going to exploit a reg-exp based implementation as presented here:
https://medialize.github.io/URI.js/uri-template.html

## Design decisions

This has to be done, we want to keep the number of options as low as possible, rough things might be:

- zero dependencies
- only single expansion will be supported
- as little public API as possible
- portable implementation across languages based on widely available patterns
- target Level support is 4

Speed vs. Maintainability -> TODO: let's start with a naive implementation from https://github.com/uri-templates/uritemplate-py and improve over it

Let's try with a single pass -> input to output
