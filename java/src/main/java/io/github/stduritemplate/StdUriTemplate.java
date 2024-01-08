package io.github.stduritemplate;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StdUriTemplate {

    // Public API
    public static String expand(final String template, final Map<String, Object> substitutions) {
        return expandImpl(template, substitutions);
    }

    // Private implementation
    private enum Operator {
        NO_OP,
        PLUS,
        HASH,
        DOT,
        SLASH,
        SEMICOLON,
        QUESTION_MARK,
        AMP;
    }

    private static void validateLiteral(Character c, int col) {
        switch (c) {
            case '+':
            case '#':
            case '/':
            case ';':
            case '?':
            case '&':
            case ' ':
            case '!':
            case '=':
            case '$':
            case '|':
            case '*':
            case ':':
            case '~':
            case '-':
                throw new IllegalArgumentException("Illegal character identified in the token at col:" + col);
            default:
                break;
        }
    }

    private static int getMaxChar(StringBuilder buffer, int col) {
        if (buffer == null || buffer.length() == 0) {
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

    private static Operator getOperator(Character c, StringBuilder token, int col) {
        switch (c) {
            case '+': return Operator.PLUS;
            case '#': return Operator.HASH;
            case '.': return Operator.DOT;
            case '/': return Operator.SLASH;
            case ';': return Operator.SEMICOLON;
            case '?': return Operator.QUESTION_MARK;
            case '&': return Operator.AMP;
            default:
                validateLiteral(c, col);
                token.append(c);
                return Operator.NO_OP;
        }
    }

    private static String expandImpl(String str, Map<String, Object> substitutions) {
        final StringBuilder result = new StringBuilder(str.length() * 2);

        boolean toToken = false;
        final StringBuilder token = new StringBuilder();

        Operator operator = null;
        boolean composite = false;
        boolean toMaxCharBuffer = false;
        final StringBuilder maxCharBuffer = new StringBuilder(3);
        boolean firstToken = true;

        for (var i = 0; i < str.length(); i++) {
            var character = str.charAt(i);
            switch (character) {
                case '{':
                    toToken = true;
                    token.setLength(0);
                    firstToken = true;
                    break;
                case '}':
                    if (toToken) {
                        var expanded = expandToken(operator, token.toString(), composite, getMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken) {
                            firstToken = false;
                        }
                        toToken = false;
                        token.setLength(0);
                        operator = null;
                        composite = false;
                        toMaxCharBuffer = false;
                        maxCharBuffer.setLength(0);
                    } else {
                        throw new IllegalArgumentException("Failed to expand token, invalid at col:" + i);
                    }
                    break;
                case ',':
                    if (toToken) {
                        var expanded = expandToken(operator, token.toString(), composite, getMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken) {
                            firstToken = false;
                        }
                        token.setLength(0);
                        composite = false;
                        toMaxCharBuffer = false;
                        maxCharBuffer.setLength(0);
                        break;
                    }
                    // Intentional fall-through for commas outside the {}
                default:
                    if (toToken) {
                        if (operator == null) {
                            operator = getOperator(character, token, i);
                        } else if (toMaxCharBuffer) {
                            if (Character.isDigit(character)) {
                                maxCharBuffer.append(character);
                            } else {
                                throw new IllegalArgumentException("Illegal character identified in the token at col:" + i);
                            }
                        } else {
                            if (character == ':') {
                                toMaxCharBuffer = true;
                                maxCharBuffer.setLength(0);
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

        if (!toToken) {
            return result.toString();
        } else {
            throw new IllegalArgumentException("Unterminated token");
        }
    }

    private static void addPrefix(Operator op, StringBuilder result) {
        switch (op) {
            case HASH:
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
            case AMP:
                result.append('&');
                break;
            default:
                return;
        }
    }

    private static void addSeparator(Operator op, StringBuilder result) {
        switch (op) {
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
            case AMP:
                result.append('&');
                break;
            default:
                result.append(',');
                return;
        }
    }

    private static void addValue(Operator op, String token, Object value, StringBuilder result, int maxChar) {
        switch (op) {
            case PLUS:
            case HASH:
                addExpandedValue(null, value, result, maxChar, false);
                break;
            case QUESTION_MARK:
            case AMP:
                result.append(token + '=');
                addExpandedValue(null, value, result, maxChar, true);
                break;
            case SEMICOLON:
                result.append(token);
                addExpandedValue("=", value, result, maxChar, true);
                break;
            case DOT:
            case SLASH:
            case NO_OP:
                addExpandedValue(null, value, result, maxChar, true);
        }
    }

    private static void addValueElement(Operator op, String token, Object value, StringBuilder result, int maxChar) {
        switch (op) {
            case PLUS:
            case HASH:
                addExpandedValue(null, value, result, maxChar, false);
                break;
            case QUESTION_MARK:
            case AMP:
            case SEMICOLON:
            case DOT:
            case SLASH:
            case NO_OP:
                addExpandedValue(null, value, result, maxChar, true);
        }
    }

    private static boolean isSurrogate(char cp) {
        return (cp >= 0xD800 && cp <= 0xDFFF);
    }

    private static boolean isIprivate(char cp) {
        return (0xE000 <= cp && cp <= 0xF8FF);
    }

    private static boolean isUcschar(char cp) {
        return (0xA0 <= cp && cp <= 0xD7FF)
                || (0xF900 <= cp && cp <= 0xFDCF)
                || (0xFDF0 <= cp && cp <= 0xFFEF);
    }

    private static void addExpandedValue(String prefix, Object value, StringBuilder result, int maxChar, boolean replaceReserved) {
        String stringValue = convertNativeTypes(value);
        int max = (maxChar != -1) ? Math.min(maxChar, stringValue.length()) : stringValue.length();
        result.ensureCapacity(max * 2); // hint to SB
        boolean toReserved = false;
        final StringBuilder reservedBuffer = new StringBuilder(3);

        if (max > 0 && prefix != null) {
            result.append(prefix);
        }

        for (var i = 0; i < max; i++) {
            char character = stringValue.charAt(i);

            if (character == '%' && !replaceReserved) {
                toReserved = true;
                reservedBuffer.setLength(0);
            }

            String toAppend = Character.toString(character);
            if (isSurrogate(character)) {
                toAppend = URLEncoder.encode(Character.toString(stringValue.codePointAt(i++)), StandardCharsets.UTF_8);
            } else if (replaceReserved || isUcschar(character) || isIprivate(character)) {
                toAppend = URLEncoder.encode(toAppend, StandardCharsets.UTF_8);
            }

            if (toReserved) {
                reservedBuffer.append(toAppend);

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
                    toReserved = false;
                    reservedBuffer.setLength(0);
                }
            } else {
                if (character == ' ') {
                    result.append("%20");
                } else if (character == '%') {
                    result.append("%25");
                } else {
                    result.append(toAppend);
                }
            }
        }

        if (toReserved) {
            result.append("%25");
            result.append(reservedBuffer.substring(1));
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
        EMPTY,
        STRING,
        LIST,
        MAP;
    }

    private static SubstitutionType getSubstitutionType(Object value, int col) {
        if (value == null) {
            return SubstitutionType.EMPTY;
        } else if (isNativeType(value)) {
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

    private static boolean isNativeType(Object value) {
        if (value instanceof String ||
            value instanceof Boolean ||
            value instanceof Integer ||
            value instanceof Long ||
            value instanceof Float ||
            value instanceof Double ||
            value instanceof Date ||
            value instanceof OffsetDateTime) {
            return true;
        }
        return false;
    }

    private static String convertNativeTypes(Object value) {
        if (value instanceof String) {
            return (String)value;
        } else if (value instanceof Boolean ||
            value instanceof Integer ||
            value instanceof Long ||
            value instanceof Float ||
            value instanceof Double) {
            return value.toString();
        } else if (value instanceof Date) {
            return ((Date) value).toInstant().atOffset(ZoneOffset.UTC).format(RFC3339);
        } else if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).format(RFC3339);
        }
        throw new IllegalArgumentException("Illegal class passed as substitution, found " + value.getClass());
    }

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[XXX][VV]");

    // returns true if expansion happened
    private static boolean expandToken(
            Operator operator,
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
        var substType = getSubstitutionType(value, col);
        if (substType == SubstitutionType.EMPTY || isEmpty(substType, value)) {
            return false;
        }

        if (firstToken) {
            addPrefix(operator, result);
        } else {
            addSeparator(operator, result);
        }

        switch (substType) {
            case STRING:
                addStringValue(operator, token, (Object)value, result, maxChar);
                break;
            case LIST:
                addListValue(operator, token, (List<Object>)value, result, maxChar, composite);
                break;
            case MAP:
                addMapValue(operator, token, (Map<String, Object>)value, result, maxChar, composite);
                break;
        }

        return true;
    }

    private static boolean addStringValue(Operator operator, String token, Object value, StringBuilder result, int maxChar) {
        addValue(operator, token, value, result, maxChar);
        return true;
    }

    private static boolean addListValue(Operator operator, String token, List<Object> value, StringBuilder result, int maxChar, boolean composite) {
        boolean first = true;
        for (var v: value) {
            if (first) {
                addValue(operator, token, v, result, maxChar);
                first = false;
            } else {
                if (composite) {
                    addSeparator(operator, result);
                    addValue(operator, token, v, result, maxChar);
                } else {
                    result.append(',');
                    addValueElement(operator, token, v, result, maxChar);
                }
            }
        }
        return !first;
    }

    private static boolean addMapValue(Operator operator, String token, Map<String, Object> value, StringBuilder result, int maxChar, boolean composite) {
        boolean first = true;
        if (maxChar != -1) {
            throw new IllegalArgumentException("Value trimming is not allowed on Maps");
        }
        for (var v : value.entrySet()) {
            if (composite) {
                if (!first) {
                    addSeparator(operator, result);
                }
                addValueElement(operator, token, v.getKey(), result, maxChar);
                result.append('=');
            } else {
                if (first) {
                    addValue(operator, token, v.getKey(), result, maxChar);
                } else {
                    result.append(',');
                    addValueElement(operator, token, v.getKey(), result, maxChar);
                }
                result.append(',');
            }
            addValueElement(operator, token, v.getValue(), result, maxChar);
            first = false;
        }
        return !first;
    }

}
