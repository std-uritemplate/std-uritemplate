package org.uritemplate;

import java.util.Map;

public interface UriTemplate {
    public String benchmark(final String template, final Map<String, Object> substitutions);
}
