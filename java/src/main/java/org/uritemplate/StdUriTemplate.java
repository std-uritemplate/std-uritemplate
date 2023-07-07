package org.uritemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StdUriTemplate {

    // Public API
    // the substitutions map accepts: String, List<String> and Map<String, String>
    public static String expand(final String template, final Map<String, Object> substitutions) {
        String result = template;
        Map<String, Object> subst = new HashMap<>(substitutions);

        return expandImpl(template, substitutions);
    }

    // Implementation
    private static String expandImpl(String str, Map<String, Object> substitutions) {
        StringBuilder result = new StringBuilder();

        boolean tokenStart = false;
        StringBuilder token = null;
        Character lastModifier = null;
        for (var byt : str.getBytes()) {
            char character = (char) byt;

            switch (character) {
                case '{':
                    tokenStart = true;
                    lastModifier = null;
                    token = new StringBuilder();
                    break;
                case '}':
                    if (tokenStart) {
                        var resultAndMod = getSubstitution(token.toString(), substitutions, lastModifier);
                        result.append(resultAndMod.getResult());
                        lastModifier = null;
                        token = null;
                        tokenStart = false;
                    } else {
                        throw new RuntimeException("Failed to expand token, invalid.");
                    }
                    break;
                case ',':
                    if (tokenStart) {
                        var resultAndMod = getSubstitution(token.toString(), substitutions, lastModifier);
                        result.append(resultAndMod.getResult());
                        lastModifier = resultAndMod.getModifier();
                        result.append(resultAndMod.getSeparator());
                        token = new StringBuilder();
                        break;
                    };
                    // Intentional fall-through for commas outside the {}
                default:
                    if (tokenStart) {
                        token.append(character);
                    } else {
                        result.append(character);
                    }
                    break;
            }
        }

        return result.toString();
    }

    private static class ResultAndModifier {
        private String result;
        private Character modifier;
        private Character separator;

        ResultAndModifier(String result, Character modifier, Character separator) {
            this.result = result;
            this.modifier = modifier;
            this.separator = separator;
        }

        public String getResult() {
            return result;
        }

        public Character getModifier() {
            return modifier;
        }

        public Character getSeparator() {
            return separator;
        }
    }

    private static ResultAndModifier getSubstitution(String key, Map<String, Object> substitutions, Character modifier) {
        String substitutionKey = key;
        boolean encode = true;
        String prefix = "";
        char separator = ',';
        System.err.println("SUBSTITUTING: " + key + " -> modifier " + modifier);
        if (modifier == null) {
            // First substitution in the block
            modifier = key.charAt(0);

            switch (modifier) {
                case '+':
                    substitutionKey = key.substring(1);
                    break;
                case '#':
                    prefix = "#";
                    substitutionKey = key.substring(1);
                    break;
                case '.':
                    prefix = ".";
                    substitutionKey = key.substring(1);
                    break;
                case '/':
                    prefix = "/";
                    substitutionKey = key.substring(1);
                    break;
                case ';':
                    prefix = ";";
                    substitutionKey = key.substring(1);
                    break;
                case '?':
                    prefix = "?";
                    substitutionKey = key.substring(1);
                    break;
                case '&':
                    prefix = "&";
                    substitutionKey = key.substring(1);
                    break;
                default:
                    break;
            }
        }

        //        Type    Separator
        //        ","     (default)
        //        +        ","
        //        #        ","
        //        .        "."
        //        /        "/"
        //        ;        ";"
        //        ?        "&"
        //        &        "&"
        switch (modifier) {
            case '+':
                encode = false;
                break;
            case '#':
                encode = false; // ??? - in the test but haven't seen in the docs ...
                break;
            case '.':
                separator = '.';
                break;
            case '/':
                separator = '/';
                break;
            case ';':
                separator = ';';
                prefix += substitutionKey;
                if (substitutions.containsKey(substitutionKey) &&
                        // TODO: better check the specification for this
                        (substitutions.get(substitutionKey) instanceof String && !((String)substitutions.get(substitutionKey)).isBlank())) {
                    prefix += "=";
                }
                break;
            case '?':
                separator = '&';
                prefix += substitutionKey;
                if (substitutions.containsKey(substitutionKey)) {
                    prefix += "=";
                }
                break;
            case '&':
                separator = '&';
                prefix += substitutionKey;
                if (substitutions.containsKey(substitutionKey)) {
                    prefix += "=";
                }
                break;
            default:
                modifier = null;
                break;
        }

        // Level 4 suffix handling
        // ':' handling
        int suffixIndex = substitutionKey.indexOf(':');
        int maxChars = -1; // -1 means take everything
        if (suffixIndex != -1) {
            maxChars = Integer.parseInt(substitutionKey.substring(suffixIndex + 1));
            substitutionKey = substitutionKey.substring(0, suffixIndex);
        }

        boolean expandContent = false;
        suffixIndex = substitutionKey.indexOf('*');
        if (suffixIndex != -1) {
            substitutionKey = substitutionKey.substring(0, suffixIndex);
            expandContent = true;
        }

        System.err.println("subst " + substitutionKey);

        // REFACTOR!!! this code is shit and should be refactored a lot!
        if (!substitutions.isEmpty()) {
            if (substitutions.containsKey(substitutionKey)) {
                Object value = substitutions.get(substitutionKey);

                String expanded = "";
                if (value instanceof String) {
                    if (encode) {
                        expanded += encodeValue(trimMaxChars((String) value, maxChars));
                    } else {
                        expanded += freeValue(trimMaxChars((String) value, maxChars));
                    }
                } else if (value instanceof List) {
                    boolean first = true;
                    for (var part: (List<String>)value) {
                        if (!first) {
                            if (expandContent) {
                                expanded += separator;
                            } else {
                                expanded += ',';
                            }
                        }

                        if (encode) {
                            expanded += encodeValue(trimMaxChars(part, maxChars));
                        } else {
                            expanded += freeValue(trimMaxChars(part, maxChars));
                        }
                        first = false;
                    }
                } else if (value instanceof Map) {
                    boolean first = true;
                    System.err.println("expandContent: " + expandContent);
                    if (expandContent) {
                        for (var part: ((Map<String, String>)value).entrySet()) {
                            if (!first) {
                                expanded += separator;
                            }

                            if (encode) {
                                expanded += encodeValue(trimMaxChars(part.getKey(), maxChars));
                                expanded += '=';
                                expanded += encodeValue(trimMaxChars(part.getValue(), maxChars));
                            } else {
                                expanded += freeValue(trimMaxChars(part.getKey(), maxChars));
                                expanded += '=';
                                expanded += freeValue(trimMaxChars(part.getValue(), maxChars));
                            }
                            first = false;
                        }
                    } else {
                        for (var part: ((Map<String, String>)value).entrySet()) {
                            if (!first) {
                                expanded += ',';
                            }

                            if (encode) {
                                expanded += encodeValue(trimMaxChars(part.getKey(), maxChars));
                                expanded += ',';
                                expanded += encodeValue(trimMaxChars(part.getValue(), maxChars));
                            } else {
                                expanded += freeValue(trimMaxChars(part.getKey(), maxChars));
                                expanded += ',';
                                expanded += freeValue(trimMaxChars(part.getValue(), maxChars));
                            }
                            first = false;
                        }
                    }
                } else {
                    throw new IllegalArgumentException("The value for key: " + substitutionKey + " is of an incompatible type: " + value.getClass() + ", allowed only String, List<String> and Map<String, String>");
                }

                return new ResultAndModifier(prefix + expanded, modifier, separator);
            } else {
                return new ResultAndModifier("", modifier, separator);
            }
        } else {
            return new ResultAndModifier("", modifier, separator);
        }
    }

    // All but '+' substitutions
    private static String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    // Only '+' substitutions
    private static String freeValue(String value) {
        return value.replace(" ", "%20");
    }

    private static String trimMaxChars(String str, int maxChars) {
        if (maxChars >= 0) {
            return str.substring(0, Math.min(maxChars, str.length()));
        } else {
            return str;
        }
    }

}
