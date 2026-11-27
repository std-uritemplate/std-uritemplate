package stduritemplate

import (
	"fmt"
	"sort"
	"strconv"
	"strings"
)

type Substitutions map[string]any

// Public API
func Expand(template string, substitutions Substitutions) (string, error) {
	return expandImpl(template, substitutions)
}

// Private implementation
type Op rune

const (
	OpUndefined    Op = 0
	OpNone         Op = -1
	OpPlus         Op = '+'
	OpHash         Op = '#'
	OpDot          Op = '.'
	OpSlash        Op = '/'
	OpSemicolon    Op = ';'
	OpQuestionMark Op = '?'
	OpAmp          Op = '&'
)

const (
	SubstitutionTypeEmpty  = "EMPTY"
	SubstitutionTypeString = "STRING"
	SubstitutionTypeList   = "LIST"
	SubstitutionTypeMap    = "MAP"
)

func validateLiteral(c rune, col int) error {
	switch c {
	case '+', '#', '/', ';', '?', '&', ' ', '!', '=', '$', '|', '*', ':', '~', '-':
		return fmt.Errorf("illegal character identified in the token at col: %d", col)
	default:
		return nil
	}
}

func getMaxChar(buffer *strings.Builder, col int) (int, error) {
	if buffer == nil || buffer.Len() == 0 {
		return -1, nil
	}
	value := buffer.String()

	if value == "" {
		return -1, nil
	}

	maxChar, err := strconv.Atoi(value)
	if err != nil {
		return 0, fmt.Errorf("cannot parse max chars at col: %d", col)
	}
	if value[0] == '0' {
		return 0, fmt.Errorf("invalid max chars at col: %d", col)
	}
	if maxChar < 1 || maxChar > 9999 {
		return 0, fmt.Errorf("invalid max chars at col: %d", col)
	}
	return maxChar, nil
}

func isHexDigit(c byte) bool {
	return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')
}

func checkVarname(token string, col int) error {
	if strings.HasPrefix(token, ".") || strings.HasSuffix(token, ".") {
		return fmt.Errorf("invalid variable name at col: %d", col)
	}
	if strings.Contains(token, "..") {
		return fmt.Errorf("invalid variable name at col: %d", col)
	}
	for i := 0; i < len(token); i++ {
		if token[i] == '%' {
			if i+2 >= len(token) || !isHexDigit(token[i+1]) || !isHexDigit(token[i+2]) {
				return fmt.Errorf("invalid variable name at col: %d", col)
			}
		}
	}
	return nil
}

func getOperator(c rune, token *strings.Builder, col int) (Op, error) {
	switch c {
	case '+':
		return OpPlus, nil
	case '#':
		return OpHash, nil
	case '.':
		return OpDot, nil
	case '/':
		return OpSlash, nil
	case ';':
		return OpSemicolon, nil
	case '?':
		return OpQuestionMark, nil
	case '&':
		return OpAmp, nil
	default:
		err := validateLiteral(c, col)
		if err != nil {
			return OpUndefined, err
		}
		token.WriteRune(c)
		return OpNone, nil
	}
}

func expandImpl(str string, substitutions Substitutions) (string, error) {
	var result strings.Builder

	var token = &strings.Builder{}
	var toToken = false
	var operator = OpUndefined
	var composite bool
	var maxCharBuffer = &strings.Builder{}
	var toMaxCharBuffer = false
	var firstToken = true

	for i, character := range str {
		switch character {
		case '{':
			toToken = true
			token.Reset()
			firstToken = true
		case '}':
			if toToken {
				if toMaxCharBuffer && maxCharBuffer.Len() == 0 {
					return "", fmt.Errorf("after a prefix, the value is missing at col: %d", i)
				}
				maxChar, err := getMaxChar(maxCharBuffer, i)
				if err != nil {
					return "", err
				}
				expanded, err := expandToken(operator, token.String(), composite, maxChar, firstToken, substitutions, &result, i)
				if err != nil {
					return "", err
				}
				if expanded && firstToken {
					firstToken = false
				}
				toToken = false
				token.Reset()
				operator = OpUndefined
				composite = false
				toMaxCharBuffer = false
				maxCharBuffer.Reset()
			} else {
				return "", fmt.Errorf("failed to expand token, invalid at col: %d", i)
			}
		case ',':
			if toToken {
				if toMaxCharBuffer && maxCharBuffer.Len() == 0 {
					return "", fmt.Errorf("after a prefix, the value is missing at col: %d", i)
				}
				maxChar, err := getMaxChar(maxCharBuffer, i)
				if err != nil {
					return "", err
				}
				expanded, err := expandToken(operator, token.String(), composite, maxChar, firstToken, substitutions, &result, i)
				if err != nil {
					return "", err
				}
				if expanded && firstToken {
					firstToken = false
				}
				token.Reset()
				composite = false
				toMaxCharBuffer = false
				maxCharBuffer.Reset()
				break
			}
			// Intentional fall-through for commas outside the {}
			fallthrough
		default:
			if toToken {
				switch {
				case operator == OpUndefined:
					var err error
					operator, err = getOperator(character, token, i)
					if err != nil {
						return "", err
					}
				case toMaxCharBuffer:
					if _, err := strconv.Atoi(string(character)); err == nil {
						maxCharBuffer.WriteRune(character)
					} else {
						return "", fmt.Errorf("illegal character identified in the token at col: %d", i)
					}
				default:
					switch character {
					case ':':
						toMaxCharBuffer = true
						maxCharBuffer.Reset()
					case '*':
						composite = true
					default:
						if err := validateLiteral(character, i); err != nil {
							return "", err
						}
						token.WriteRune(character)
					}
				}
			} else {
				if character > 0x7F {
					for _, b := range []byte(string(character)) {
						result.WriteString(fmt.Sprintf("%%%02X", b))
					}
				} else {
					result.WriteRune(character)
				}
			}
		}
	}

	if !toToken {
		return result.String(), nil
	}

	return "", fmt.Errorf("unterminated token")
}

func addPrefix(op Op, result *strings.Builder) {
	switch op {
	case OpHash, OpDot, OpSlash, OpSemicolon, OpQuestionMark, OpAmp:
		result.WriteRune(rune(op))
	default:
		return
	}
}

func addSeparator(op Op, result *strings.Builder) {
	switch op {
	case OpDot, OpSlash, OpSemicolon:
		result.WriteRune(rune(op))
	case OpQuestionMark, OpAmp:
		result.WriteByte('&')
	default:
		result.WriteByte(',')
		return
	}
}

func addValue(op Op, token string, value string, result *strings.Builder, maxChar int) {
	switch op {
	case OpPlus, OpHash:
		addExpandedValue("", value, result, maxChar, false)
	case OpQuestionMark, OpAmp:
		result.WriteString(token + "=")
		addExpandedValue("", value, result, maxChar, true)
	case OpSemicolon:
		result.WriteString(token)
		if value != "" {
			result.WriteByte('=')
		}
		addExpandedValue("", value, result, maxChar, true)
	case OpDot, OpSlash, OpNone:
		addExpandedValue("", value, result, maxChar, true)
	}
}

func addValueElement(op Op, _, value string, result *strings.Builder, maxChar int) {
	switch op {
	case OpPlus, OpHash:
		addExpandedValue("", value, result, maxChar, false)
	case OpQuestionMark, OpAmp, OpSemicolon, OpDot, OpSlash, OpNone:
		addExpandedValue("", value, result, maxChar, true)
	}
}

func isUnreserved(c byte) bool {
	return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
		c == '-' || c == '.' || c == '_' || c == '~'
}

func isReserved(c byte) bool {
	switch c {
	case ':', '/', '?', '#', '[', ']', '@', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=':
		return true
	default:
		return false
	}
}

func truncate(value string, maxChar int) string {
	if maxChar < 0 {
		return value
	}
	count := 0
	for i := range value {
		if count == maxChar {
			return value[:i]
		}
		count++
	}
	return value
}

const upperHex = "0123456789ABCDEF"

func addExpandedValue(prefix string, value string, result *strings.Builder, maxChar int, replaceReserved bool) {
	value = truncate(value, maxChar)
	if value == "" {
		return
	}
	result.WriteString(prefix)
	result.Grow(len(value))

	allowReserved := !replaceReserved
	// Bytes >= 0x80 are always encoded, so this encodes each rune as its UTF-8 bytes.
	for i := 0; i < len(value); {
		c := value[i]
		switch {
		case isUnreserved(c), allowReserved && isReserved(c):
			result.WriteByte(c)
			i++
		case allowReserved && c == '%' && i+2 < len(value) && isHexDigit(value[i+1]) && isHexDigit(value[i+2]):
			result.WriteString(value[i : i+3])
			i += 3
		default:
			result.WriteByte('%')
			result.WriteByte(upperHex[c>>4])
			result.WriteByte(upperHex[c&0x0F])
			i++
		}
	}
}

func getSubstitutionType(value any, col int) string {
	switch value.(type) {
	case nil:
		return SubstitutionTypeEmpty
	case string, float32, float64, int, int8, int16, int32, int64, bool:
		return SubstitutionTypeString
	case []string, []float32, []float64, []int, []int8, []int16, []int32, []int64, []bool, []any:
		return SubstitutionTypeList
	case map[string]string, map[string]float32, map[string]float64, map[string]int, map[string]int8, map[string]int16, map[string]int32, map[string]int64, map[string]bool, map[string]any:
		return SubstitutionTypeMap
	default:
		return fmt.Sprintf("illegal class passed as substitution, found %T at col: %d", value, col)
	}
}

func isEmpty(substType string, value any) bool {
	switch substType {
	case SubstitutionTypeString:
		switch value.(type) {
		case string:
			return value == nil
		default: // primitives are value types
			return false
		}
	case SubstitutionTypeList:
		return getListLength(value) == 0
	case SubstitutionTypeMap:
		return getMapLength(value) == 0
	default:
		return true
	}
}

func getListLength(value any) int {
	switch value.(type) {
	case []string:
		return len(value.([]string))
	case []float32:
		return len(value.([]float32))
	case []float64:
		return len(value.([]float64))
	case []int:
		return len(value.([]int))
	case []int8:
		return len(value.([]int8))
	case []int16:
		return len(value.([]int16))
	case []int32:
		return len(value.([]int32))
	case []int64:
		return len(value.([]int64))
	case []bool:
		return len(value.([]bool))
	case []any:
		return len(value.([]any))
	}
	return 0
}

func getMapLength(value any) int {
	switch value.(type) {
	case map[string]string:
		return len(value.(map[string]string))
	case map[string]float32:
		return len(value.(map[string]float32))
	case map[string]float64:
		return len(value.(map[string]float64))
	case map[string]int:
		return len(value.(map[string]int))
	case map[string]int8:
		return len(value.(map[string]int8))
	case map[string]int16:
		return len(value.(map[string]int16))
	case map[string]int32:
		return len(value.(map[string]int32))
	case map[string]int64:
		return len(value.(map[string]int64))
	case map[string]bool:
		return len(value.(map[string]bool))
	case map[string]any:
		return len(value.(map[string]any))
	}
	return 0
}

func convertNativeList(value any) ([]string, error) {
	var stringList = make([]string, getListLength(value))
	switch value.(type) {
	case []string:
		for index, val := range value.([]string) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []float32:
		for index, val := range value.([]float32) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []float64:
		for index, val := range value.([]float64) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []int:
		for index, val := range value.([]int) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []int8:
		for index, val := range value.([]int8) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []int16:
		for index, val := range value.([]int16) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []int32:
		for index, val := range value.([]int32) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []int64:
		for index, val := range value.([]int64) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []bool:
		for index, val := range value.([]bool) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	case []any:
		for index, val := range value.([]any) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringList[index] = str
		}
	default:
		return nil, fmt.Errorf("unrecognized type: %s", value)
	}
	return stringList, nil
}

func convertNativeMap(value any) (map[string]string, error) {
	var stringMap = make(map[string]string, getMapLength(value))
	switch value.(type) {
	case map[string]string:
		for key, val := range value.(map[string]string) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]float32:
		for key, val := range value.(map[string]float32) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]float64:
		for key, val := range value.(map[string]float64) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]int:
		for key, val := range value.(map[string]int) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]int8:
		for key, val := range value.(map[string]int8) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]int16:
		for key, val := range value.(map[string]int16) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]int32:
		for key, val := range value.(map[string]int32) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]int64:
		for key, val := range value.(map[string]int64) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]bool:
		for key, val := range value.(map[string]bool) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	case map[string]any:
		for key, val := range value.(map[string]any) {
			str, err := convertNativeTypes(val)
			if err != nil {
				return nil, err
			}
			stringMap[key] = str
		}
	default:
		return nil, fmt.Errorf("unrecognized type: %s", value)
	}
	return stringMap, nil
}

func convertNativeTypes(value any) (string, error) {
	switch value.(type) {
	case string, float32, float64, int, int8, int16, int32, int64, bool:
		return fmt.Sprintf("%v", value), nil
	default:
		return "", fmt.Errorf("unrecognized type: %s", value)
	}
}

func expandToken(
	operator Op,
	token string,
	composite bool,
	maxChar int,
	firstToken bool,
	substitutions Substitutions,
	result *strings.Builder,
	col int,
) (bool, error) {
	if len(token) == 0 {
		return false, fmt.Errorf("found an empty token at col: %d", col)
	}

	if err := checkVarname(token, col); err != nil {
		return false, err
	}

	value, ok := substitutions[token]
	if !ok {
		return false, nil
	}

	substType := getSubstitutionType(value, col)
	if substType == SubstitutionTypeEmpty || isEmpty(substType, value) {
		return false, nil
	}

	if firstToken {
		addPrefix(operator, result)
	} else {
		addSeparator(operator, result)
	}

	switch substType {
	case SubstitutionTypeString:
		stringValue, err := convertNativeTypes(value)
		if err != nil {
			return false, err
		}
		addStringValue(operator, token, stringValue, result, maxChar)
	case SubstitutionTypeList:
		listValue, err := convertNativeList(value)
		if err != nil {
			return false, err
		}
		addListValue(operator, token, listValue, result, maxChar, composite)
	case SubstitutionTypeMap:
		mapValue, err := convertNativeMap(value)
		if err != nil {
			return false, err
		}
		err = addMapValue(operator, token, mapValue, result, maxChar, composite)
		if err != nil {
			return false, err
		}
	}

	return true, nil
}

func addStringValue(operator Op, token string, value string, result *strings.Builder, maxChar int) {
	addValue(operator, token, value, result, maxChar)
}

func addListValue(operator Op, token string, value []string, result *strings.Builder, maxChar int, composite bool) {
	first := true
	for _, v := range value {
		if first {
			addValue(operator, token, v, result, maxChar)
			first = false
		} else {
			if composite {
				addSeparator(operator, result)
				addValue(operator, token, v, result, maxChar)
			} else {
				result.WriteString(",")
				addValueElement(operator, token, v, result, maxChar)
			}
		}
	}
}

func addMapValue(operator Op, token string, value map[string]string, result *strings.Builder, maxChar int, composite bool) error {
	if maxChar != -1 {
		return fmt.Errorf("value trimming is not allowed on Maps")
	}

	// workaround to make Map ordering not random
	// https://github.com/uri-templates/uritemplate-test/pull/58#issuecomment-1640029982
	keys := make([]string, 0, len(value))
	for k := range value {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for i := range keys {
		k := keys[i]
		v := value[k]

		if composite {
			if i > 0 {
				addSeparator(operator, result)
			}
			addValueElement(operator, token, k, result, maxChar)
			result.WriteString("=")
		} else {
			if i == 0 {
				addValue(operator, token, k, result, maxChar)
			} else {
				result.WriteString(",")
				addValueElement(operator, token, k, result, maxChar)
			}
			result.WriteString(",")
		}
		addValueElement(operator, token, v, result, maxChar)
	}
	return nil
}
