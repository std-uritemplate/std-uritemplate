package stduritemplate

import (
	"fmt"
	"net/url"
	"sort"
	"strconv"
	"strings"
)

type Substitutions map[string]interface{}

type Modifier int

const (
	UNDEFINED Modifier = iota
	NO_MOD
	PLUS
	DASH
	DOT
	SLASH
	SEMICOLON
	QUESTION_MARK
	AT
)

const (
	SubstitutionTypeString = "STRING"
	SubstitutionTypeList   = "LIST"
	SubstitutionTypeMap    = "MAP"
)

func validateLiteral(c string, col int) error {
	switch c {
	case "+", "#", "/", ";", "?", "&", " ", "!", "=", "$", "|", "*", ":", "~", "-":
		return fmt.Errorf("Illegal character identified in the token at col: %d", col)
	default:
		return nil
	}
}

func getMaxChar(buffer *strings.Builder, col int) (int, error) {
	if buffer == nil {
		return -1, nil
	} else {
		value := buffer.String()

		if value == "" {
			return -1, nil
		} else {
			maxChar, err := strconv.Atoi(value)
			if err != nil {
				return 0, fmt.Errorf("Cannot parse max chars at col: %d", col)
			}
			return maxChar, nil
		}
	}
}

func getModifier(c string, token *strings.Builder, col int) (Modifier, error) {
	switch c {
	case "+":
		return PLUS, nil
	case "#":
		return DASH, nil
	case ".":
		return DOT, nil
	case "/":
		return SLASH, nil
	case ";":
		return SEMICOLON, nil
	case "?":
		return QUESTION_MARK, nil
	case "&":
		return AT, nil
	default:
		err := validateLiteral(c, col)
		if err != nil {
			return UNDEFINED, err
		}
		token.WriteString(c)
		return NO_MOD, nil
	}
}

func expandImpl(str string, substitutions Substitutions) (string, error) {
	var result strings.Builder

	var token *strings.Builder = nil
	var modifier Modifier = UNDEFINED
	var composite bool
	var maxCharBuffer *strings.Builder
	var firstToken bool = true

	for i := 0; i < len(str); i++ {
		character := string(str[i])
		switch character {
		case "{":
			token = &strings.Builder{}
			firstToken = true
		case "}":
			if token != nil {
				maxChar, err := getMaxChar(maxCharBuffer, i)
				if err != nil {
					return "", err
				}
				expanded, err := expandToken(modifier, token.String(), composite, maxChar, firstToken, substitutions, &result, i)
				if err != nil {
					return "", err
				}
				if expanded && firstToken {
					firstToken = false
				}
				token = nil
				modifier = UNDEFINED
				composite = false
				maxCharBuffer = nil
			} else {
				return "", fmt.Errorf("Failed to expand token, invalid at col: %d", i)
			}
		case ",":
			if token != nil {
				maxChar, err := getMaxChar(maxCharBuffer, i)
				if err != nil {
					return "", err
				}
				expanded, err := expandToken(modifier, token.String(), composite, maxChar, firstToken, substitutions, &result, i)
				if err != nil {
					return "", err
				}
				if expanded && firstToken {
					firstToken = false
				}
				token = &strings.Builder{}
				composite = false
				maxCharBuffer = nil
				break
			}
			// Intentional fall-through for commas outside the {}
		default:
			if token != nil {
				if modifier == UNDEFINED {
					var err error
					modifier, err = getModifier(character, token, i)
					if err != nil {
						return "", err
					}
				} else if maxCharBuffer != nil {
					if _, err := strconv.Atoi(character); err == nil {
						maxCharBuffer.WriteString(character)
					} else {
						return "", fmt.Errorf("Illegal character identified in the token at col: %d", i)
					}
				} else {
					if character == ":" {
						maxCharBuffer = &strings.Builder{}
					} else if character == "*" {
						composite = true
					} else {
						if err := validateLiteral(character, i); err != nil {
							return "", err
						}
						token.WriteString(character)
					}
				}
			} else {
				result.WriteString(character)
			}
		}
	}

	if token == nil {
		return result.String(), nil
	} else {
		return "", fmt.Errorf("Unterminated token")
	}
}

func addPrefix(mod Modifier, result *strings.Builder) {
	switch mod {
	case DASH:
		result.WriteString("#")
	case DOT:
		result.WriteString(".")
	case SLASH:
		result.WriteString("/")
	case SEMICOLON:
		result.WriteString(";")
	case QUESTION_MARK:
		result.WriteString("?")
	case AT:
		result.WriteString("&")
	default:
		return
	}
}

func addSeparator(mod Modifier, result *strings.Builder) {
	switch mod {
	case DOT:
		result.WriteString(".")
	case SLASH:
		result.WriteString("/")
	case SEMICOLON:
		result.WriteString(";")
	case QUESTION_MARK, AT:
		result.WriteString("&")
	default:
		result.WriteString(",")
		return
	}
}

func addValue(mod Modifier, token, value string, result *strings.Builder, maxChar int) {
	switch mod {
	case PLUS, DASH:
		addExpandedValue(value, result, maxChar, false)
	case QUESTION_MARK, AT:
		result.WriteString(token + "=")
		addExpandedValue(value, result, maxChar, true)
	case SEMICOLON:
		result.WriteString(token)
		if value != "" {
			result.WriteString("=")
		}
		addExpandedValue(value, result, maxChar, true)
	case DOT, SLASH, NO_MOD:
		addExpandedValue(value, result, maxChar, true)
	}
}

func addValueElement(mod Modifier, token, value string, result *strings.Builder, maxChar int) {
	switch mod {
	case PLUS, DASH:
		addExpandedValue(value, result, maxChar, false)
	case QUESTION_MARK, AT, SEMICOLON, DOT, SLASH, NO_MOD:
		addExpandedValue(value, result, maxChar, true)
	}
}

func addExpandedValue(value string, result *strings.Builder, maxChar int, replaceReserved bool) {
	max := maxChar
	if maxChar == -1 || maxChar > len(value) {
		max = len(value)
	}
	reservedBuffer := []string{}
	fillReserved := false

	for i := 0; i < max; i++ {
		character := value[i : i+1]

		if character == "%" && !replaceReserved {
			reservedBuffer = []string{}
			fillReserved = true
		}

		if fillReserved {
			reservedBuffer = append(reservedBuffer, character)

			if len(reservedBuffer) == 3 {
				encoded := true
				reserved := strings.Join(reservedBuffer, "")
				unescaped, err := url.QueryUnescape(reserved)
				if err != nil {
					encoded = (reserved == unescaped)
				}

				if encoded {
					result.WriteString(reserved)
				} else {
					result.WriteString("%25")
					// only if !replaceReserved
					result.WriteString(strings.Join(reservedBuffer[1:], ""))
				}
				reservedBuffer = []string{}
				fillReserved = false
			}
		} else {
			if character == " " {
				result.WriteString("%20")
			} else if character == "%" {
				result.WriteString("%25")
			} else {
				if replaceReserved {
					result.WriteString(url.QueryEscape(character))
				} else {
					result.WriteString(character)
				}
			}
		}
	}

	if fillReserved {
		result.WriteString("%25")
		if replaceReserved {
			result.WriteString(url.QueryEscape(strings.Join(reservedBuffer[1:], "")))
		} else {
			result.WriteString(strings.Join(reservedBuffer[1:], ""))
		}
	}
}

func getSubstitutionType(value interface{}, col int) string {
	switch value.(type) {
	case string, nil:
		return SubstitutionTypeString
	case []interface{}:
		return SubstitutionTypeList
	case map[string]interface{}:
		return SubstitutionTypeMap
	default:
		return fmt.Sprintf("Illegal class passed as substitution, found %T at col: %d", value, col)
	}
}

func isEmpty(substType string, value interface{}) bool {
	switch substType {
	case SubstitutionTypeString:
		return value == nil
	case SubstitutionTypeList:
		return len(value.([]interface{})) == 0
	case SubstitutionTypeMap:
		return len(value.(map[string]interface{})) == 0
	default:
		return true
	}
}

func expandToken(
	modifier Modifier,
	token string,
	composite bool,
	maxChar int,
	firstToken bool,
	substitutions Substitutions,
	result *strings.Builder,
	col int,
) (bool, error) {
	if len(token) == 0 {
		return false, fmt.Errorf("Found an empty token at col: %d", col)
	}

	value, ok := substitutions[token]
	if !ok {
		return false, nil
	}

	switch value.(type) {
	case bool, int, int64, float32, float64:
		value = fmt.Sprintf("%v", value)
	}

	substType := getSubstitutionType(value, col)
	if isEmpty(substType, value) {
		return false, nil
	}

	if firstToken {
		addPrefix(modifier, result)
	} else {
		addSeparator(modifier, result)
	}

	switch substType {
	case SubstitutionTypeString:
		addStringValue(modifier, token, value.(string), result, maxChar)
	case SubstitutionTypeList:
		addListValue(modifier, token, value.([]interface{}), result, maxChar, composite)
	case SubstitutionTypeMap:
		err := addMapValue(modifier, token, value.(map[string]interface{}), result, maxChar, composite)
		if err != nil {
			return false, err
		}
	}

	return true, nil
}

func addStringValue(modifier Modifier, token string, value string, result *strings.Builder, maxChar int) {
	addValue(modifier, token, value, result, maxChar)

}

func addListValue(modifier Modifier, token string, value []interface{}, result *strings.Builder, maxChar int, composite bool) {
	first := true
	for _, v := range value {
		if first {
			addValue(modifier, token, v.(string), result, maxChar)
			first = false
		} else {
			if composite {
				addSeparator(modifier, result)
				addValue(modifier, token, v.(string), result, maxChar)
			} else {
				result.WriteString(",")
				addValueElement(modifier, token, v.(string), result, maxChar)
			}
		}
	}
}

func addMapValue(modifier Modifier, token string, value map[string]interface{}, result *strings.Builder, maxChar int, composite bool) error {
	first := true
	if maxChar != -1 {
		return fmt.Errorf("Value trimming is not allowed on Maps")
	}

	// workaround to make Map ordering not random
	// https://github.com/uri-templates/uritemplate-test/pull/58#issuecomment-1640029982
	keys := make([]string, 0, len(value))
	for k := range value {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for key := range keys {
		k := keys[key]
		v := value[k]

		if composite {
			if !first {
				addSeparator(modifier, result)
			}
			addValueElement(modifier, token, k, result, maxChar)
			result.WriteString("=")
		} else {
			if first {
				addValue(modifier, token, k, result, maxChar)
			} else {
				result.WriteString(",")
				addValueElement(modifier, token, k, result, maxChar)
			}
			result.WriteString(",")
		}
		addValueElement(modifier, token, v.(string), result, maxChar)
		first = false
	}
	return nil
}

func Expand(template string, substitutions Substitutions) (string, error) {
	return expandImpl(template, substitutions)
}
