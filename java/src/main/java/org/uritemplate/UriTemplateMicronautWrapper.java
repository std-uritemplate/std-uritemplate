package org.uritemplate;

import java.util.Map;

public class UriTemplateMicronautWrapper implements UriTemplate {

    public String benchmark(final String template, final Map<String, Object> substitutions) {
        return new UriTemplateMicronaut(template).expand(substitutions);
    }

}
