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
        StringBuilder result = new StringBuilder(str.length() * 2);

        StringBuilder token = null;

        Operator operator = null;
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
                        var expanded = expandToken(operator, token.toString(), composite, getMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken) {
                            firstToken = false;
                        }
                        token = null;
                        operator = null;
                        composite = false;
                        maxCharBuffer = null;
                    } else {
                        throw new IllegalArgumentException("Failed to expand token, invalid at col:" + i);
                    }
                    break;
                case ',':
                    if (token != null) {
                        var expanded = expandToken(operator, token.toString(), composite, getMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
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
                        if (operator == null) {
                            operator = getOperator(character, token, i);
                        } else if (maxCharBuffer != null) {
                            if (Character.isDigit(character)) {
                                maxCharBuffer.append(character);
                            } else {
                                throw new IllegalArgumentException("Illegal character identified in the token at col:" + i);
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

    private static void addValue(Operator op, String token, String value, StringBuilder result, int maxChar) {
        switch (op) {
            case PLUS:
            case HASH:
                addExpandedValue(value, result, maxChar, false);
                break;
            case QUESTION_MARK:
            case AMP:
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
            case NO_OP:
                addExpandedValue(value, result, maxChar, true);
        }
    }

    private static void addValueElement(Operator op, String token, String value, StringBuilder result, int maxChar) {
        switch (op) {
            case PLUS:
            case HASH:
                addExpandedValue(value, result, maxChar, false);
                break;
            case QUESTION_MARK:
            case AMP:
            case SEMICOLON:
            case DOT:
            case SLASH:
            case NO_OP:
                addExpandedValue(value, result, maxChar, true);
        }
    }

    private static void addExpandedValue(String value, StringBuilder result, int maxChar, boolean replaceReserved) {
        var max = (maxChar != -1) ? Math.min(maxChar, value.length()) : value.length();
        result.ensureCapacity(max * 2); // hint to SB
        StringBuilder reservedBuffer = null;

        for (var i = 0; i < max; i++) {
            char character = value.charAt(i);

            if (character == '%' && !replaceReserved) {
                reservedBuffer = new StringBuilder(3);
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
                        result.append(URLEncoder.encode(Character.toString(character), StandardCharsets.UTF_8));
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
        if (value instanceof Boolean ||
                value instanceof Integer ||
                value instanceof Long ||
                value instanceof Float ||
                value instanceof Double) {
            value = value.toString();
        } else if (value instanceof Date) {
            value = ((Date) value).toInstant().atOffset(ZoneOffset.UTC).format(RFC3339);
        } else if (value instanceof OffsetDateTime) {
            value = ((OffsetDateTime) value).format(RFC3339);
        }

        var substType = getSubstitutionType(value, col);
        if (isEmpty(substType, value)) {
            return false;
        }

        if (firstToken) {
            addPrefix(operator, result);
        } else {
            addSeparator(operator, result);
        }

        switch (substType) {
            case STRING:
                addStringValue(operator, token, (String)value, result, maxChar);
                break;
            case LIST:
                addListValue(operator, token, (List<String>)value, result, maxChar, composite);
                break;
            case MAP:
                addMapValue(operator, token, (Map<String, String>)value, result, maxChar, composite);
                break;
        }

        return true;
    }

    private static boolean addStringValue(Operator operator, String token, String value, StringBuilder result, int maxChar) {
        addValue(operator, token, value, result, maxChar);
        return true;
    }

    private static boolean addListValue(Operator operator, String token, List<String> value, StringBuilder result, int maxChar, boolean composite) {
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

    private static boolean addMapValue(Operator operator, String token, Map<String, String> value, StringBuilder result, int maxChar, boolean composite) {
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
