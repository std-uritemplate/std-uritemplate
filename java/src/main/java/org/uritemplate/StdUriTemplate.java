package org.uritemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StdUriTemplate {

    // Public API
    public static String expand(final String template, final Map<String, Object> substitutions) {
        return expandImpl(template, substitutions);
    }

    // Private implementation
    private final static Character[] RESERVED = new Character[]{'+', '#', '/', ';', '?', '&', ' ', '!', '=', '$', '|', '*', ':', '~', '-' };

    private enum Modifier {
        NO_MOD,
        PLUS,
        DASH,
        DOT,
        SLASH,
        SEMICOLON,
        QUESTION_MARK,
        AT;
    }

    private static void validateLiteral(Character c, int col) {
        for (var reserved: RESERVED) {
            if (reserved.equals(c)) {
                throw new IllegalArgumentException("Illegal character identified in the token at col:" + col);
            }
        }
    }

    private static int getMaxChar(StringBuilder buffer, int col) {
        if (buffer == null) {
            return -1;
        } else {
            String value = buffer.toString();

            if (value.isEmpty()) {
                return -1;
            } else {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Cannot parse max chars at col:" + col);
                }
            }
        }
    }

    private static Modifier getModifier(Character c, StringBuilder token, int col) {
        switch (c) {
            case '+': return Modifier.PLUS;
            case '#': return Modifier.DASH;
            case '.': return Modifier.DOT;
            case '/': return Modifier.SLASH;
            case ';': return Modifier.SEMICOLON;
            case '?': return Modifier.QUESTION_MARK;
            case '&': return Modifier.AT;
            default:
                validateLiteral(c, col);
                token.append(c);
                return Modifier.NO_MOD;
        }
    }

    private static String expandImpl(String str, Map<String, Object> substitutions) {
        StringBuilder result = new StringBuilder(str.length() * 2);

        StringBuilder token = null;

        Modifier modifier = null;
        boolean composite = false;
        StringBuilder maxCharBuffer = null;
        boolean firstToken = true;

        for (var i = 0; i < str.length(); i++) {
            var character = str.charAt(i);
            switch (character) {
                case '{':
                    token = new StringBuilder();
                    firstToken = true;
                    break;
                case '}':
                    if (token != null) {
                        var expanded = expandToken(modifier, token.toString(), composite, getMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken) {
                            firstToken = false;
                        }
                        token = null;
                        modifier = null;
                        composite = false;
                        maxCharBuffer = null;
                    } else {
                        throw new IllegalArgumentException("Failed to expand token, invalid at col:" + i);
                    }
                    break;
                case ',':
                    if (token != null) {
                        var expanded = expandToken(modifier, token.toString(), composite, getMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken) {
                            firstToken = false;
                        }
                        token = new StringBuilder(token.length() * 2);
                        composite = false;
                        maxCharBuffer = null;
                        break;
                    }
                    // Intentional fall-through for commas outside the {}
                default:
                    if (token != null) {
                        if (modifier == null) {
                            modifier = getModifier(character, token, i);
                        } else if (maxCharBuffer != null) {
                            if (Character.isDigit(character)) {
                                maxCharBuffer.append(character);
                            } else {
                                throw new IllegalArgumentException("Illegal character idetified in the token at col:" + i);
                            }
                        } else {
                            if (character == ':') {
                                maxCharBuffer = new StringBuilder(3);
                            } else if (character == '*') {
                                composite = true;
                            } else {
                                validateLiteral(character, i);
                                token.append(character);
                            }
                        }
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

    private static boolean isList(Object value) {
        return value instanceof ArrayList || // checking concrete instances first as it's faster
                value instanceof List;
    }

    private static boolean isMap(Object value) {
        return value instanceof HashMap || // checking concrete instances first as it's faster
                value instanceof Map;
    }

    enum SubstitutionType {
        STRING,
        LIST,
        MAP;
    }

    private static SubstitutionType getSubstitutionType(Object value, int col) {
        if (value instanceof String || value == null) {
            return SubstitutionType.STRING;
        } else if (isList(value)) {
            return SubstitutionType.LIST;
        } else if (isMap(value)) {
            return SubstitutionType.MAP;
        } else {
            throw new IllegalArgumentException("Illegal class passed as substitution, found " + value.getClass() + " at col:" + col);
        }
    }

    private static void addPrefix(Modifier mod, StringBuilder result) {
        switch (mod) {
            case DASH:
                result.append('#');
                break;
            case DOT:
                result.append('.');
                break;
            case SLASH:
                result.append('/');
                break;
            case SEMICOLON:
                result.append(';');
                break;
            case QUESTION_MARK:
                result.append('?');
                break;
            case AT:
                result.append('&');
                break;
            default:
                return;
        }
    }

    private static void addSeparator(Modifier mod, StringBuilder result) {
        switch (mod) {
            case DOT:
                result.append('.');
                break;
            case SLASH:
                result.append('/');
                break;
            case SEMICOLON:
                result.append(';');
                break;
            case QUESTION_MARK:
            case AT:
                result.append('&');
                break;
            default:
                result.append(',');
                return;
        }
    }

    private static void addValue(Modifier mod, String token, String value, StringBuilder result, int maxChar) {
        switch (mod) {
            case PLUS:
            case DASH:
                addExpandedValue(value, result, maxChar, false);
                break;
            case QUESTION_MARK:
            case AT:
                result.append(token + '=');
                addExpandedValue(value, result, maxChar, false);
                break;
            case SEMICOLON:
                result.append(token);
                if (!value.isEmpty()) {
                    result.append("=");
                }
                addExpandedValue(value, result, maxChar, false);
                break;
            case DOT:
            case SLASH:
            case NO_MOD:
                addExpandedValue(value, result, maxChar, true);
        }
    }

    private static void addValueElement(Modifier mod, String token, String value, StringBuilder result, int maxChar) {
        switch (mod) {
            case PLUS:
            case DASH:
                addExpandedValue(value, result, maxChar, false);
                break;
            case QUESTION_MARK:
            case AT:
            case SEMICOLON:
            case DOT:
            case SLASH:
            case NO_MOD:
                addExpandedValue(value, result, maxChar, true);
        }
    }

    private static void addExpandedValue(String value, StringBuilder result, int maxChar, boolean replaceReserved) {
        var max = (maxChar != -1) ? Math.min(maxChar, value.length()) : value.length();
        StringBuilder reservedBuffer = null;

        for (var i = 0; i < max; i++) {
            char character = value.charAt(i);

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
                } else if (character == '%') {
                    result.append("%25");
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
    }

    private static boolean isEmpty(SubstitutionType substType, Object value) {
        if (value == null) {
            return true;
        } else {
            switch (substType) {
                case STRING: return false;
                case LIST: return ((List)value).isEmpty();
                case MAP: return ((Map)value).isEmpty();
                default: return true;
            }
        }
    }

    // returns true if expansion happened
    private static boolean expandToken(
            Modifier modifier,
            String token,
            boolean composite,
            int maxChar,
            boolean firstToken,
            Map<String, Object> substitutions,
            StringBuilder result,
            int col) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Found an empty token at col:" + col);
        }

        Object value = substitutions.get(token);
        if (value instanceof Integer ||
                value instanceof Long ||
                value instanceof Float ||
                value instanceof Double) {
            value = value.toString();
        }

        var substType = getSubstitutionType(value, col);
        if (isEmpty(substType, value)) {
            return false;
        }

        if (firstToken) {
            addPrefix(modifier, result);
        } else {
            addSeparator(modifier, result);
        }

        switch (substType) {
            case STRING:
                addStringValue(modifier, token, (String)value, result, maxChar);
                break;
            case LIST:
                addListValue(modifier, token, (List<String>)value, result, maxChar, composite);
                break;
            case MAP:
                addMapValue(modifier, token, (Map<String, String>)value, result, maxChar, composite);
                break;
        }

        return true;
    }

    private static boolean addStringValue(Modifier modifier, String token, String value, StringBuilder result, int maxChar) {
        addValue(modifier, token, value, result, maxChar);
        return true;
    }

    private static boolean addListValue(Modifier modifier, String token, List<String> value, StringBuilder result, int maxChar, boolean composite) {
        boolean first = true;
        for (var v: value) {
            if (first) {
                addValue(modifier, token, v, result, maxChar);
                first = false;
            } else {
                if (composite) {
                    addSeparator(modifier, result);
                    addValue(modifier, token, v, result, maxChar);
                } else {
                    result.append(',');
                    addValueElement(modifier, token, v, result, maxChar);
                }
            }
        }
        return !first;
    }

    private static boolean addMapValue(Modifier modifier, String token, Map<String, String> value, StringBuilder result, int maxChar, boolean composite) {
        boolean first = true;
        if (maxChar != -1) {
            throw new IllegalArgumentException("Value trimming is not allowed on Maps");
        }
        for (var v : value.entrySet()) {
            if (composite) {
                if (!first) {
                    addSeparator(modifier, result);
                }
                addValueElement(modifier, token, v.getKey(), result, maxChar);
                result.append('=');
            } else {
                if (first) {
                    addValue(modifier, token, v.getKey(), result, maxChar);
                } else {
                    result.append(',');
                    addValueElement(modifier, token, v.getKey(), result, maxChar);
                }
                result.append(',');
            }
            addValueElement(modifier, token, v.getValue(), result, maxChar);
            first = false;
        }
        return !first;
    }

//
//    private static final class NoMod extends Modifier {
//        NoMod(String token) {
//            super(token);
//        }
//
//        @Override
//        String key() {
//            return sanitized;
//        }
//    }
//
//    private static final class Plus extends Modifier {
//        Plus(String token) {
//            super(token);
//        }
//
//        @Override
//        String expand(String key, String value, int maxChar) {
//            return freeValue(value, maxChar);
//        }
//
//        @Override
//        String expandElements(String key, String value, int maxChar) {
//            return freeValue(value, maxChar);
//        }
//    }
//
//    private static final class Dash extends Modifier {
//        Dash(String token) {
//            super(token);
//        }
//
//        @Override
//        String prefix() {
//            return "#";
//        }
//
//        @Override
//        String expand(String key, String value, int maxChar) {
//            return freeValue(value, maxChar);
//        }
//
//        @Override
//        String expandElements(String key, String value, int maxChar) {
//            return freeValue(value, maxChar);
//        }
//    }
//
//    private static final class Dot extends Modifier {
//        Dot(String token) {
//            super(token);
//        }
//
//        @Override
//        char separator() {
//            return '.';
//        }
//
//        @Override
//        String prefix() {
//            return ".";
//        }
//    }
//
//    private static final class Slash extends Modifier {
//        Slash(String token) {
//            super(token);
//        }
//
//        @Override
//        char separator() {
//            return '/';
//        }
//
//        @Override
//        String prefix() {
//            return "/";
//        }
//    }
//
//    private static final class Semicolon extends Modifier {
//        Semicolon(String token) {
//            super(token);
//        }
//
//        @Override
//        char separator() {
//            return ';';
//        }
//
//        @Override
//        String prefix() {
//            return ";";
//        }
//
//        @Override
//        String expand(String key, String value, int maxChar) {
//            var encoded = expandValue(value, maxChar);
//            if (encoded != null && !encoded.isEmpty()) {
//                return key + "=" + encoded;
//            } else {
//                return key;
//            }
//        }
//    }
//
//    private static final class QuestionMark extends Modifier {
//        QuestionMark(String token) {
//            super(token);
//        }
//
//        @Override
//        char separator() {
//            return '&';
//        }
//
//        @Override
//        String prefix() {
//            return "?";
//        }
//
//        @Override
//        String expand(String key, String value, int maxChar) {
//            return expandKV(key, value, maxChar);
//        }
//    }
//
//    private static final class At extends Modifier {
//        At(String token) {
//            super(token);
//        }
//
//        @Override
//        char separator() {
//            return '&';
//        }
//
//        @Override
//        String prefix() {
//            return "&";
//        }
//
//        @Override
//        String expand(String key, String value, int maxChar) {
//            return expandKV(key, value, maxChar);
//        }
//    }
//
//    private static class Modifier extends Token {
//        Modifier(String token) {
//            super(token);
//        }
//
//        @Override
//        void validate() {
//            validateToken(key());
//        }
//
//        String expand(String key, String value, int maxChar) {
//            return expandValue(value, maxChar);
//        }
//
//        String expandElements(String key, String value, int maxChar) {
//            return expandValue(value, maxChar);
//        }
//
//        char separator() {
//            return ',';
//        }
//
//        String key() {
//            return sanitized.substring(1);
//        }
//
//        String prefix() {
//            return "";
//        }
//    }
//
//    private static class Token {
//        protected final String token;
//        protected final String sanitized;
//        protected final int maxChar;
//        protected final boolean composite;
//
//        Token(String token) {
//            this.token = token;
//
//            var sanitized = token;
//            int suffixIndex = token.indexOf(':');
//            if (suffixIndex != -1) {
//                sanitized = token.substring(0, suffixIndex);
//                maxChar = Integer.parseInt(token.substring(suffixIndex + 1));
//            } else {
//                maxChar = -1;
//            }
//
//            suffixIndex = token.indexOf('*');
//            if (suffixIndex != -1) {
//                sanitized = token.substring(0, suffixIndex);
//                composite = true;
//            } else {
//                composite = false;
//            }
//
//            this.sanitized = sanitized;
//        }
//
//        void validate() {
//            validateToken(sanitized);
//        }
//
//        String sanitized() {
//            return sanitized;
//        }
//
//        boolean composite() {
//            return composite;
//        }
//
//        int maxChar() {
//            return maxChar;
//        }
//    }
//
//    private final static String[] RESERVED = new String[]{"+", "#", "/", ";", "?", "&", " ", "!", "=", "$", "|", "*", ":", "~", "-"};
//
//    private static void validateToken(String token) {
//        if (token.isEmpty()) {
//            throw new IllegalArgumentException("Empty key found");
//        }
//        for (var res : RESERVED) {
//            if (token.contains(res)) {
//                throw new IllegalArgumentException("Found a key with invalid content: `" + token + "` contains the '" + res + "' character");
//            }
//        }
//    }
//
//    private static String expandKV(String key, String value, int maxChar) {
//        return key + "=" + expandValue(value, maxChar);
//    }
//
//    private static String expandValue(String value, int maxChar) {
//        return expandValueImpl(value, maxChar, true);
//    }
//
//    private static String expandValueImpl(String value, int maxChar, boolean replaceReserved) {
//        var max = (maxChar != -1) ? Math.min(maxChar, value.length()) : value.length();
//        var chars = value.toCharArray();
//        var result = new StringBuilder();
//        StringBuilder reservedBuffer = null;
//
//        for (var i = 0; i < max; i++) {
//            char character = chars[i];
//
//            if (character == '%' && !replaceReserved) {
//                reservedBuffer = new StringBuilder();
//            }
//
//            if (reservedBuffer != null) {
//                reservedBuffer.append(character);
//
//                if (reservedBuffer.length() == 3) {
//                    boolean isEncoded = false;
//                    try {
//                        URLDecoder.decode(reservedBuffer.toString(), StandardCharsets.UTF_8);
//                        isEncoded = true;
//                    } catch (Exception e) {
//                        // ignore
//                    }
//
//                    if (isEncoded) {
//                        result.append(reservedBuffer);
//                    } else {
//                        result.append("%25");
//                        // only if !replaceReserved
//                        result.append(reservedBuffer.substring(1));
//                    }
//                    reservedBuffer = null;
//                }
//            } else {
//                if (character == ' ') {
//                    result.append("%20");
//                } else if (character == '%') {
//                    result.append("%25");
//                } else {
//                    if (replaceReserved) {
//                        result.append(URLEncoder.encode(new String(new char[]{character}), StandardCharsets.UTF_8));
//                    } else {
//                        result.append(character);
//                    }
//                }
//            }
//        }
//
//        if (reservedBuffer != null) {
//            result.append("%25");
//            if (replaceReserved) {
//                result.append(URLEncoder.encode(reservedBuffer.substring(1), StandardCharsets.UTF_8));
//            } else {
//                result.append(reservedBuffer.substring(1));
//            }
//        }
//
//        return result.toString();
//    }
//
//    private static String freeValue(String value, int maxChar) {
//        return expandValueImpl(value, maxChar, false);
//    }
//
//    private static Modifier getModifier(String token) {
//        switch (token.charAt(0)) {
//            case '+':
//                return new Plus(token);
//            case '#':
//                return new Dash(token);
//            case '.':
//                return new Dot(token);
//            case '/':
//                return new Slash(token);
//            case ';':
//                return new Semicolon(token);
//            case '?':
//                return new QuestionMark(token);
//            case '&':
//                return new At(token);
//            default:
//                return new NoMod(token);
//        }
//    }
//
//    private static void expandToken(Character modifier, String token, Map<String, Object> substitutions, StringBuilder result) {
//
//
//    }
//
//    private static String expandTokens(List<String> tokens, Map<String, Object> substitutions) {
//        StringBuilder result = new StringBuilder();
//
//        boolean firstToken = true;
//        Modifier mod = null;
//        String key;
//        for (var token : tokens) {
//            Token tok = new Token(token);
//            if (mod == null) {
//                mod = getModifier(token);
//                key = mod.key();
//                mod.validate();
//            } else {
//                key = tok.sanitized();
//                tok.validate();
//            }
//
//            if (substitutions.containsKey(key)) {
//                Object value = substitutions.get(key);
//
//                // null and equivalent, simply skip
//                if (value == null ||
//                        (value instanceof List && ((List) value).isEmpty()) || // verify -> not sure its tested
//                        (value instanceof Map && ((Map) value).isEmpty())) {
//                    continue;
//                }
//
//                // Number are supported, should they be supported even in List and Maps?
//                // This seems like a dumb but working way of supporting them
//                if (value instanceof Integer ||
//                        value instanceof Long ||
//                        value instanceof Float ||
//                        value instanceof Double) {
//                    value = value.toString();
//                }
//
//
//                if (firstToken) {
//                    result.append(mod.prefix());
//                } else {
//                    result.append(mod.separator());
//                }
//
//                if (value instanceof String) {
//                    result.append(mod.expand(key, (String) value, tok.maxChar()));
//                } else if (value instanceof Integer) {
//                } else if (value instanceof List) {
//                    boolean first = true;
//                    for (var subst : (List<String>) value) {
//                        if (first) {
//                            first = false;
//                            result.append(mod.expand(key, subst, tok.maxChar()));
//                        } else {
//                            if (tok.composite()) {
//                                result.append(mod.separator());
//                                result.append(mod.expand(key, subst, tok.maxChar()));
//                            } else {
//                                result.append(',');
//                                result.append(mod.expandElements(key, subst, tok.maxChar()));
//                            }
//                        }
//                    }
//                } else if (value instanceof Map) {
//                    boolean first = true;
//                    for (var subst : ((Map<String, String>) value).entrySet()) {
//                        if (tok.maxChar() != -1) {
//                            throw new IllegalArgumentException("Value trimming is not allowed on Maps");
//                        }
//                        if (first) {
//                            first = false;
//                            if (tok.composite()) {
//                                result.append(mod.expandElements(key, subst.getKey(), tok.maxChar()));
//                            } else {
//                                result.append(mod.expand(key, subst.getKey(), tok.maxChar()));
//                            }
//                        } else {
//                            if (tok.composite()) {
//                                result.append(mod.separator());
//                            } else {
//                                result.append(',');
//                            }
//                            result.append(mod.expandElements(key, subst.getKey(), mod.maxChar()));
//                        }
//
//                        if (tok.composite()) {
//                            result.append('=');
//                        } else {
//                            result.append(',');
//                        }
//                        result.append(mod.expandElements(key, subst.getValue(), mod.maxChar()));
//                    }
//                } else {
//                    throw new IllegalArgumentException("Substitution type not supported, found " + value.getClass() + ", but only Integer, Float, Long, Double, String, List<String> and Map<String, String> are allowed.");
//                }
//
//                firstToken = false;
//            }
//        }
//
//        return result.toString();
//    }

}
