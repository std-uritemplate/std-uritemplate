package org.uritemplate;

import java.net.URLDecoder;
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
        for (var character : str.toCharArray()) {

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

        if (token == null) {
            return result.toString();
        } else {
            throw new IllegalArgumentException("Unterminated token");
        }
    }

    private static final class NoMod extends Modifier {
        NoMod(String token) { super(token); }

        @Override
        String key() {
            return sanitized;
        }
    }

    private static final class Plus extends Modifier {
        Plus(String token) { super(token); }

        @Override
        String expand(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }
        @Override
        String expandNext(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }
    }

    private static final class Dash extends Modifier {
        Dash(String token) { super(token); }

        @Override
        String prefix() {
            return "#";
        }
        @Override
        String expand(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }
        @Override
        String expandNext(String key, String value, int maxChar) {
            return freeValue(value, maxChar);
        }
    }

    private static final class Dot extends Modifier {
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

    private static final class Slash extends Modifier {
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

    private static final class Semicolon extends Modifier {
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

    private static final class QuestionMark extends Modifier {
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

    private static final class At extends Modifier {
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

    private static class Modifier extends Token {
        Modifier(String token) {
            super(token);
        }

        @Override
        void validate() {
            validateToken(key());
        }

        String expand(String key, String value, int maxChar) {
            return expandValue(value, maxChar);
        }

        String expandNext(String key, String value, int maxChar) {
            return expandValue(value, maxChar);
        }

        char separator() {
            return ',';
        }

        String key() {
            return sanitized.substring(1);
        }

        String prefix() {
            return "";
        }
    }

    private static class Token {
        protected final String token;
        protected final String sanitized;
        protected final int maxChar;
        protected final boolean composite;

        Token(String token) {
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

        void validate() {
            validateToken(sanitized);
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
    }

    private final static String[] RESERVED = new String[]{"+", "#", "/", ";", "?", "&", " ", "!", "=", "$", "|", "*", ":", "~", "-"};

    private static void validateToken(String token) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Empty key found");
        }
        for (var res: RESERVED) {
            if (token.contains(res)) {
                throw new IllegalArgumentException("Found a key with invalid content: `" + token + "` contains the '" + res + "' character");
            }
        }
    }

    private static String expandKV(String key, String value, int maxChar) {
        return key + "=" + expandValue(value, maxChar);
    }

    private static String expandValue(String value, int maxChar) {
        return expandValueImpl(value, maxChar, true);
    }

    private static String expandValueImpl(String value, int maxChar, boolean replaceReserved) {
        var max = (maxChar != -1) ? Math.min(maxChar, value.length()) : value.length();
        var chars = value.toCharArray();
        var result = new StringBuilder();
        StringBuilder reservedBuffer = null;

        for (var i = 0; i < max; i++) {
            char character = chars[i];

            if (character == '%' && !replaceReserved) {
                reservedBuffer = new StringBuilder();
            }

            if (reservedBuffer != null) {
                reservedBuffer.append(character);

                if (reservedBuffer.length() == 3) {
                    boolean isEncoded = false;
                    try {
                        URLDecoder.decode(reservedBuffer.toString(), StandardCharsets.UTF_8);
                        isEncoded = true;
                    } catch (Exception e) {
                        // ignore
                    }

                    if (isEncoded) {
                        result.append(reservedBuffer);
                    } else {
                        result.append("%25");
                        // only if !replaceReserved
                        result.append(reservedBuffer.substring(1));
                    }
                    reservedBuffer = null;
                }
            } else {
                if (character == ' ') {
                    result.append("%20");
                } else {
                    if (replaceReserved) {
                        result.append(URLEncoder.encode(new String(new char[]{character}), StandardCharsets.UTF_8));
                    } else {
                        result.append(character);
                    }
                }
            }
        }

        if (reservedBuffer != null) {
            result.append("%25");
            if (replaceReserved) {
                result.append(URLEncoder.encode(reservedBuffer.substring(1), StandardCharsets.UTF_8));
            } else {
                result.append(reservedBuffer.substring(1));
            }
        }

        return result.toString();
    }

    // Double check correctness
    private static String freeValue(String value, int maxChar) {
        return expandValueImpl(value, maxChar, false);
    }

    private static Modifier getModifier(String token) {
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

    private static String expandTokens(List<String> tokens, Map<String, Object> substitutions) {
        StringBuilder result = new StringBuilder();

        boolean firstToken = true;
        Modifier mod = null;
        String key;
        for (var token: tokens) {
            Token tok = new Token(token);
            if (mod == null) {
                mod = getModifier(token);
                key = mod.key();
                mod.validate();
            } else {
                key = tok.sanitized();
                tok.validate();
            }

            if (substitutions.containsKey(key)) {
                Object value = substitutions.get(key);

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


                if (firstToken) {
                    result.append(mod.prefix());
                } else {
                    result.append(mod.separator());
                }

                if (value instanceof String) {
                    result.append(mod.expand(key, (String) value, tok.maxChar()));
                } else if (value instanceof Integer) {
                } else if (value instanceof List) {
                    boolean first = true;
                    for (var subst : (List<String>) value) {
                        if (first) {
                            first = false;
                            result.append(mod.expand(key, subst, tok.maxChar()));
                        } else {
                            if (tok.composite()) {
                                result.append(mod.separator());
                                result.append(mod.expand(key, subst, tok.maxChar()));
                            } else {
                                result.append(',');
                                result.append(mod.expandNext(key, subst, tok.maxChar()));
                            }
                        }
                    }
                } else if (value instanceof Map) {
                    boolean first = true;
                    for (var subst: ((Map<String, String>) value).entrySet()) {
                        if (tok.maxChar() != -1) {
                            throw new IllegalArgumentException("Value trimming is not allowed on Maps");
                        }
                        if (first) {
                            first = false;
                            if (tok.composite()) {
                                result.append(mod.expandNext(key, subst.getKey(), tok.maxChar()));
                            } else {
                                result.append(mod.expand(key, subst.getKey(), tok.maxChar()));
                            }
                        } else {
                            if (tok.composite()) {
                                result.append(mod.separator());
                            } else {
                                result.append(',');
                            }
                            result.append(mod.expandNext(key, subst.getKey(), mod.maxChar()));
                        }

                        if (tok.composite()) {
                            result.append('=');
                        } else {
                            result.append(',');
                        }
                        result.append(mod.expandNext(key, subst.getValue(), mod.maxChar()));
                    }
                } else {
                    throw new IllegalArgumentException("Substitution type not supported, found " + value.getClass() + ", but only Integer, Float, Long, Double, String, List<String> and Map<String, String> are allowed.");
                }

                firstToken = false;
            }
        }

        return result.toString();
    }

}
