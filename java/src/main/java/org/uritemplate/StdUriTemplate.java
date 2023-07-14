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
                addExpandedValue(value, result, maxChar, true);
                break;
            case SEMICOLON:
                result.append(token);
                if (!value.isEmpty()) {
                    result.append("=");
                }
                addExpandedValue(value, result, maxChar, true);
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

}
