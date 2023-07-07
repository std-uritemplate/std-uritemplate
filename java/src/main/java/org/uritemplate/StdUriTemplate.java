package org.uritemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StdUriTemplate {

    // Public API
    // the substitutions map accepts: String, List<String> and Map<String, String>
    public static String expand(final String template, final Map<String, Object> substitutions) {
        return expandImpl(template, substitutions);
    }

    private static String expandImpl(String str, Map<String, Object> substitutions) {
        StringBuilder result = new StringBuilder();

        StringBuilder token = null;
        List<String> tokens = null;
        for (var byt : str.getBytes()) {
            char character = (char) byt;

            switch (character) {
                case '{':
                    token = new StringBuilder();
                    tokens = new ArrayList<>();
                    break;
                case '}':
                    if (token != null) {
                        tokens.add(token.toString());
                        token = null;
                        result.append(expandTokens(tokens, substitutions));
                    } else {
                        throw new RuntimeException("Failed to expand token, invalid.");
                    }
                    break;
                case ',':
                    if (token != null) {
                        tokens.add(token.toString());
                        token = new StringBuilder();
                        break;
                    };
                    // Intentional fall-through for commas outside the {}
                default:
                    if (token != null) {
                        token.append(character);
                    } else {
                        result.append(character);
                    }
                    break;
            }
        }

        return result.toString();
    }

    static final class NoMod extends Modifier {
        NoMod(String token) { super(token); }

        @Override
        String key() {
            return sanitized;
        }
    }

    static final class Plus extends Modifier {
        Plus(String token) { super(token); }

        @Override
        String expand(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }

        // TODO: verify!
        @Override
        String expandNext(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }
    }

    static final class Dash extends Modifier {
        Dash(String token) { super(token); }

        @Override
        String prefix() {
            return "#";
        }
        @Override
        String expand(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }

        // TODO: verify!
        @Override
        String expandNext(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }
    }

    static final class Dot extends Modifier {
        Dot(String token) { super(token); }

        @Override
        char separator() {
            return '.';
        }
        @Override
        String prefix() {
            return ".";
        }
    }

    static final class Slash extends Modifier {
        Slash(String token) { super(token); }

        @Override
        char separator() {
            return '/';
        }
        @Override
        String prefix() {
            return "/";
        }
    }

    static final class Semicolon extends Modifier {
        Semicolon(String token) { super(token); }

        @Override
        char separator() {
            return ';';
        }
        @Override
        String prefix() {
            return ";";
        }
        @Override
        String expand(String key, String value, int maxChar) {
            var encoded = expandValue(value, maxChar);
            if (encoded != null && !encoded.isEmpty()) {
                return key + "=" + encoded;
            } else {
                return key;
            }
        }
    }

    static final class QuestionMark extends Modifier {
        QuestionMark(String token) { super(token); }

        @Override
        char separator() {
            return '&';
        }
        @Override
        String prefix() {
            return "?";
        }
        @Override
        String expand(String key, String value, int maxChar) {
            return expandKV(key, value, maxChar);
        }
    }

    static final class At extends Modifier {
        At(String token) { super(token); }

        @Override
        char separator() {
            return '&';
        }
        @Override
        String prefix() {
            return "&";
        }
        @Override
        String expand(String key, String value, int maxChar) {
            return expandKV(key, value, maxChar);
        }
    }

    static class Modifier {
        protected final String token;
        protected final String sanitized;
        protected final int maxChar;
        protected final boolean composite;

        Modifier(String token) {
            this.token = token;

            var sanitized = token;
            int suffixIndex = token.indexOf(':');
            if (suffixIndex != -1) {
                sanitized = token.substring(0, suffixIndex);
                maxChar = Integer.parseInt(token.substring(suffixIndex + 1));
            } else {
                maxChar = -1;
            }

            suffixIndex = token.indexOf('*');
            if (suffixIndex != -1) {
                sanitized = token.substring(0, suffixIndex);
                composite = true;
            } else {
                composite = false;
            }

            this.sanitized = sanitized;
        }

        String expand(String key, String value, int maxChar) {
            return expandValue(value, maxChar);
        }

        String expandNext(String key, String value, int maxChar) {
            return expandValue(value, maxChar);
        }

        String key() {
            return sanitized.substring(1);
        }

        String sanitized() {
            return sanitized;
        }

        boolean composite() {
            return composite;
        }

        int maxChar() {
            return maxChar;
        }

        char separator() {
            return ',';
        }

        String prefix() {
            return "";
        }
    }

    static String expandKV(String key, String value, int maxChar) {
        return key + "=" + expandValue(value, maxChar);
    }

    static String trim(String value, int maxChar) {
        return (maxChar < 0) ? value : value.substring(0, Math.min(maxChar, value.length()));
    }

    // Double check correctness
    static String expandValue(String value, int maxChar) {
        return URLEncoder.encode(trim(value, maxChar), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    // Double check correctness
    static String freeValue(String value, int maxChar) {
        return trim(value
                .replace("%", "%25")
                .replace(" ", "%20")
                , maxChar);
    }

    public static Modifier getModifier(String token) {
        if (token.trim().length() <= 0) {
            return new NoMod(token);
        } else {
            switch (token.charAt(0)) {
                case '+':
                    return new Plus(token);
                case '#':
                    return new Dash(token);
                case '.':
                    return new Dot(token);
                case '/':
                    return new Slash(token);
                case ';':
                    return new Semicolon(token);
                case '?':
                    return new QuestionMark(token);
                case '&':
                    return new At(token);
                default:
                    return new NoMod(token);
            }
        }
    }

    private static String expandTokens(List<String> tokens, Map<String, Object> substitutions) {
        StringBuilder result = new StringBuilder();

        Modifier firstMod = null;
        for (var token: tokens) {
            var mod = getModifier(token);
            // composite handling is a little messy, couldn't find anything better

            if (substitutions.containsKey(mod.key())) {
                Object value = substitutions.get(mod.key());

                // null and equivalent, simply skip
                if (value == null ||
                        (value instanceof List && ((List) value).isEmpty()) || // verify -> not sure its tested
                        (value instanceof Map && ((Map) value).isEmpty())) {
                    continue;
                }

                // Number are supported apparently, should they be supported even in List and Maps?
                // This seems like a dumb but working mechanism
                if (value instanceof Integer ||
                    value instanceof Long ||
                    value instanceof Float ||
                    value instanceof Double) {
                    value = value.toString();
                }

                if (firstMod == null) {
                    firstMod = mod;
                    result.append(mod.prefix());
                } else {
                    result.append(firstMod.separator());
                }
                if (value instanceof String) {
                    result.append(firstMod.expand(mod.key(), (String) value, mod.maxChar()));
                } else if (value instanceof Integer) {
                } else if (value instanceof List) {
                    boolean first = true;
                    for (var subst : (List<String>) value) {
                        if (first) {
                            first = false;
                            result.append(firstMod.expand(mod.key(), subst, mod.maxChar()));
                        } else {
                            if (mod.composite) {
                                result.append(firstMod.separator());
                                result.append(firstMod.expand(mod.key(), subst, mod.maxChar()));
                            } else {
                                result.append(',');
                                result.append(firstMod.expandNext(mod.key(), subst, mod.maxChar()));
                            }
                        }
                    }
                } else if (value instanceof Map) {
                    boolean first = true;
                    for (var subst: ((Map<String, String>) value).entrySet()) {
                        if (first) {
                            first = false;
                            if (mod.composite()) {
                                result.append(firstMod.expandNext(mod.key(), subst.getKey(), mod.maxChar()));
                            } else {
                                result.append(firstMod.expand(mod.key(), subst.getKey(), mod.maxChar()));
                            }
                        } else {
                            if (mod.composite()) {
                                result.append(firstMod.separator());
                            } else {
                                result.append(',');
                            }
                            result.append(firstMod.expandNext(mod.key(), subst.getKey(), mod.maxChar()));
                        }

                        if (mod.composite()) {
                            result.append('=');
                        } else {
                            result.append(',');
                        }
                        result.append(firstMod.expandNext(mod.key(), subst.getValue(), mod.maxChar()));
                    }
                } else {
                    throw new IllegalArgumentException("Substitution type not supported, found " + value.getClass() + ", but only Integer, Float, Long, Double, String, List<String> and Map<String, String> are allowed.");
                }
            }
        }

        return result.toString();
    }

}
