<?php
namespace StdUriTemplate;

use InvalidArgumentException;
use TypeError;
use Exception;

class StdUriTemplate {

    /**
     * @param string $template
     * @param array $substitutions
     * @return string
     * @throws InvalidArgumentException
     */
    public static function expand(string $template, array $substitutions): string {
        return self::expandImpl($template, $substitutions);
    }

    /**
     * @param string $c
     * @param int $col
     * @throws InvalidArgumentException
     */
    private static function validateLiteral(string $c, int $col): void {
        switch ($c) {
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
                throw new InvalidArgumentException("Illegal character identified in the token at col: $col");
            default:
                break;
        }
    }

    /**
     * @param string $c
     * @return bool
     */
    private static function isHexDigit(string $c): bool {
        return ($c >= '0' && $c <= '9') || ($c >= 'a' && $c <= 'f') || ($c >= 'A' && $c <= 'F');
    }

    /**
     * @param string $token
     * @param int $col
     * @throws InvalidArgumentException
     */
    private static function validateVarname(string $token, int $col): void {
        $chars = mb_str_split($token, 1, 'UTF-8');
        $len = count($chars);
        for ($i = 0; $i < $len; $i++) {
            $r = $chars[$i];
            if ($r === '.') {
                if ($i === 0 || $i === $len - 1 || $chars[$i - 1] === '.') {
                    throw new InvalidArgumentException("Illegal character identified in the token at col: $col");
                }
            } elseif ($r === '%') {
                if ($i + 2 >= $len || !self::isHexDigit($chars[$i + 1]) || !self::isHexDigit($chars[$i + 2])) {
                    throw new InvalidArgumentException("Illegal character identified in the token at col: $col");
                }
            }
        }
    }

    /**
     * @param string|null $buffer
     * @param bool $toMaxCharBuffer
     * @param int $col
     * @return int
     * @throws InvalidArgumentException
     */
    private static function getMaxChar(?string $buffer, bool $toMaxCharBuffer, int $col): int {
        if (!$toMaxCharBuffer) {
            return -1;
        }

        if ($buffer === null || $buffer === '') {
            throw new InvalidArgumentException("Empty prefix length at col: $col");
        }

        $value = $buffer;

        if ($value[0] === '0' || strlen($value) > 4) {
            throw new InvalidArgumentException("Invalid prefix length at col: $col");
        }

        $intValue = (int)$value;
        return $intValue;
    }

    /**
     * @param string $c
     * @param string &$token
     * @param int $col
     * @return string
     */
    private static function getOperator(string $c, string &$token, int $col): string {
        switch ($c) {
            case '+': return 'PLUS';
            case '#': return 'HASH';
            case '.': return 'DOT';
            case '/': return 'SLASH';
            case ';': return 'SEMICOLON';
            case '?': return 'QUESTION_MARK';
            case '&': return 'AMP';
            default:
                self::validateLiteral($c, $col);
                $token .= $c;
                return 'NO_OP';
        }
    }

    /**
     * @param string $str
     * @param array $substitutions
     * @return string
     * @throws InvalidArgumentException
     */
    private static function expandImpl(string $str, array $substitutions): string {
        $result = '';
        $token = null;

        $operator = null;
        $composite = false;
        $maxCharBuffer = null;
        $toMaxCharBuffer = false;
        $firstToken = true;

        $characters = mb_str_split($str, 1, 'UTF-8');
        for ($i = 0; $i < count($characters); $i++) {
            $character = $characters[$i];
            switch ($character) {
                case '{':
                    $token = '';
                    $firstToken = true;
                    $toMaxCharBuffer = false;
                    break;
                case '}':
                    if ($token !== null) {
                        self::validateVarname($token, $i);
                        $expanded = self::expandToken($operator, $token, $composite, self::getMaxChar($maxCharBuffer, $toMaxCharBuffer, $i), $firstToken, $substitutions, $result, $i);
                        if ($expanded && $firstToken) {
                            $firstToken = false;
                        }
                        $token = null;
                        $operator = null;
                        $composite = false;
                        $maxCharBuffer = null;
                        $toMaxCharBuffer = false;
                    } else {
                        throw new InvalidArgumentException("Failed to expand token, invalid at col: $i");
                    }
                    break;
                case ',':
                    if ($token !== null) {
                        self::validateVarname($token, $i);
                        $expanded = self::expandToken($operator, $token, $composite, self::getMaxChar($maxCharBuffer, $toMaxCharBuffer, $i), $firstToken, $substitutions, $result, $i);
                        if ($expanded && $firstToken) {
                            $firstToken = false;
                        }
                        $token = '';
                        $composite = false;
                        $maxCharBuffer = null;
                        $toMaxCharBuffer = false;
                        break;
                    }
                    // Intentional fall-through for commas outside the {}
                default:
                    if ($token !== null) {
                        if ($operator === null) {
                            $operator = self::getOperator($character, $token, $i);
                        } elseif ($maxCharBuffer !== null) {
                            if (ctype_digit($character)) {
                                $maxCharBuffer .= $character;
                            } else {
                                throw new InvalidArgumentException("Illegal character identified in the token at col: $i");
                            }
                        } else {
                            if ($character === ':') {
                                $toMaxCharBuffer = true;
                                $maxCharBuffer = '';
                            } elseif ($character === '*') {
                                $composite = true;
                            } else {
                                self::validateLiteral($character, $i);
                                $token .= $character;
                            }
                        }
                    } else {
                        $cp = mb_ord($character, 'UTF-8');
                        if ($cp !== false && $cp > 0x7F) {
                            $bytes = unpack('C*', $character);
                            foreach ($bytes as $byte) {
                                $result .= sprintf('%%%02X', $byte);
                            }
                        } else {
                            $result .= $character;
                        }
                    }
                    break;
            }
        }

        if ($token === null) {
            return $result;
        } else {
            throw new InvalidArgumentException("Unterminated token");
        }
    }

    /**
     * @param string $op
     * @param string &$result
     */
    private static function addPrefix(string $op, string &$result): void {
        switch ($op) {
            case 'HASH':
                $result .= '#';
                break;
            case 'DOT':
                $result .= '.';
                break;
            case 'SLASH':
                $result .= '/';
                break;
            case 'SEMICOLON':
                $result .= ';';
                break;
            case 'QUESTION_MARK':
                $result .= '?';
                break;
            case 'AMP':
                $result .= '&';
                break;
            default:
                return;
        }
    }

    /**
     * @param string $op
     * @param string &$result
     */
    private static function addSeparator(string $op, string &$result): void {
        switch ($op) {
            case 'DOT':
                $result .= '.';
                break;
            case 'SLASH':
                $result .= '/';
                break;
            case 'SEMICOLON':
                $result .= ';';
                break;
            case 'QUESTION_MARK':
            case 'AMP':
                $result .= '&';
                break;
            default:
                $result .= ',';
                return;
        }
    }

    /**
     * @param string $op
     * @param string $token
     * @param mixed $value
     * @param string &$result
     * @param int $maxChar
     */
    private static function addValue(string $op, string $token, $value, string &$result, int $maxChar): void {
        switch ($op) {
            case 'PLUS':
            case 'HASH':
                self::addExpandedValue(null, $value, $result, $maxChar, false);
                break;
            case 'QUESTION_MARK':
            case 'AMP':

                $result .= $token . '=';
                self::addExpandedValue(null, $value, $result, $maxChar, true);
                break;
            case 'SEMICOLON':
                $result .= $token;
                self::addExpandedValue("=", $value, $result, $maxChar, true);
                break;
            case 'DOT':
            case 'SLASH':
            case 'NO_OP':
                self::addExpandedValue(null, $value, $result, $maxChar, true);
        }
    }

    /**
     * @param string $op
     * @param string $token
     * @param mixed $value
     * @param string &$result
     * @param int $maxChar
     */
    private static function addValueElement(string $op, string $token, $value, string &$result, int $maxChar): void {
        switch ($op) {
            case 'PLUS':
            case 'HASH':
                self::addExpandedValue(null, $value, $result, $maxChar, false);
                break;
            case 'QUESTION_MARK':
            case 'AMP':
            case 'SEMICOLON':
            case 'DOT':
            case 'SLASH':
            case 'NO_OP':
                self::addExpandedValue(null, $value, $result, $maxChar, true);
        }
    }

    /**
     * @param string $cp
     */
    private static function isSurrogate(string $cp) {
        if (empty($cp)) {
            return true;
        }
        $codePoint = mb_ord($cp, 'UTF-8');
        // Treat all non-ASCII characters (multi-byte UTF-8) as needing encoding,
        // matching the behavior of other implementations.
        return $codePoint !== false && $codePoint > 0x7F;
    }
    
    /**
     * @param string $cp
     */
    private static function isIprivate(string $cp) {
        if (empty($cp)) {
            return false;
        }
        $codePoint = mb_ord($cp, 'UTF-8');
        return (0xE000 <= $codePoint && $codePoint <= 0xF8FF);
    }
    
    /**
     * @param string $cp
     */
    private static function isUcschar(string $cp) {
        if (empty($cp)) {
            return false;
        }
        $codePoint = mb_ord($cp, 'UTF-8');
        return (0xA0 <= $codePoint && $codePoint <= 0xD7FF)
            || (0xF900 <= $codePoint && $codePoint <= 0xFDCF)
            || (0xFDF0 <= $codePoint && $codePoint <= 0xFFEF);
    }

    /**
     * @param mixed $prefix
     * @param mixed $value
     * @param string &$result
     * @param int $maxChar
     * @param bool $replaceReserved
     */
    private static function addExpandedValue($prefix, $value, string &$result, int $maxChar, bool $replaceReserved): void {
        $stringValue = self::convertNativeTypes($value);
        $strLen = mb_strlen($stringValue, 'UTF-8');
        $max = ($maxChar !== -1) ? min($maxChar, $strLen) : $strLen;
        $result .= '';
        $reservedBuffer = null;

        if ($max > 0 && $prefix !== null) {
            $result .= $prefix;
        }

        for ($i = 0; $i < $max; $i++) {
            $character = mb_substr($stringValue, $i, 1, 'UTF-8');

            if ($character === '%' && !$replaceReserved) {
                $reservedBuffer = '';
            }

            $toAppend = $character;
            if (self::isSurrogate($character) || $replaceReserved || self::isUcschar($character) || self::isIprivate($character)) {
                $toAppend = rawurlencode($toAppend);
            }

            if ($reservedBuffer !== null) {
                $reservedBuffer .= $toAppend;

                if (strlen($reservedBuffer) === 3) {
                    $isEncoded = false;
                    try {
                        $decoded = urldecode($reservedBuffer);
                        $isEncoded = ($decoded !== $reservedBuffer);
                    } catch (Exception $e) {
                        // ignore
                    }

                    if ($isEncoded) {
                        $result .= $reservedBuffer;
                    } else {
                        $result .= "%25";
                        // only if !$replaceReserved
                        $result .= substr($reservedBuffer, 1);
                    }
                    $reservedBuffer = null;
                }
            } else {
                if ($character === ' ') {
                    $result .= "%20";
                } elseif ($character === '%') {
                    $result .= "%25";
                } else {
                    $result .= $toAppend;
                }
            }
        }

        if ($reservedBuffer !== null) {
            $result .= "%25";
            $result .= substr($reservedBuffer, 1);
        }
    }

    /**
     * @param mixed $value
     * @return bool
     */
    private static function isList($value): bool {
        return is_array($value);
    }

    /**
     * @param mixed $value
     * @return bool
     */
    private static function isMap($value): bool {
        if (is_array($value)) {
            // https://stackoverflow.com/a/173479/7898052
            if (!function_exists('array_is_list')) {
                if ($value === []) {
                    return true;
                }
                return array_keys($value) !== range(0, count($value) - 1);
            }
            return !array_is_list($value);
        }
        return false;
    }

    /**
     * @param mixed $value
     * @param int $col
     * @return string
     */
    private static function getSubstitutionType($value, int $col): string {
        if ($value === null) {
            return 'EMPTY';
        } elseif (self::isNativeType($value)) {
            return 'STRING';
        } elseif (self::isMap($value)) {
            return 'MAP';
        } elseif (self::isList($value)) {
            return 'LIST';
        } else {
            throw new InvalidArgumentException("Illegal class passed as substitution, found " . get_class($value) . " at col: $col");
        }
    }

    /**
     * @param string $substType
     * @param mixed $value
     * @return bool
     */
    private static function isEmpty(string $substType, $value): bool {
        if ($value === null) {
            return true;
        } else {
            switch ($substType) {
                case 'STRING':
                    return false;
                case 'LIST':
                case 'MAP':
                    return empty($value);
                default:
                    return true;
            }
        }
    }

    /**
     * @param mixed $value
     * @return bool
     */
    private static function isNativeType($value): bool {
        if (is_string($value) ||
            is_bool($value) ||
            is_int($value) ||
            is_float($value) ||
            is_double($value)) {
            return true;
        }
        return false;
    }

    /**
     * @param mixed $value
     * @return string
     */
    private static function convertNativeTypes($value): string {
        if (is_bool($value)) {
            if ($value) {
                return "true";
            } else {
                return "false";
            }
        } else if (is_string($value) || is_int($value) || is_float($value) || is_double($value)) {
            return (string)$value;
        } else {
            throw new InvalidArgumentException("Illegal class passed as substitution: $value");
        }
    }

    /**
     * @param string $operator
     * @param string $token
     * @param bool $composite
     * @param int $maxChar
     * @param bool $firstToken
     * @param array $substitutions
     * @param string &$result
     * @param int $col
     * @return bool
     */
    private static function expandToken(string $operator, string $token, bool $composite, int $maxChar, bool $firstToken, array $substitutions, string &$result, int $col): bool {
        if (empty($token)) {
            throw new InvalidArgumentException("Found an empty token at col: $col");
        }

        $value = $substitutions[$token] ?? null;
        $substType = self::getSubstitutionType($value, $col);
        if ($substType === 'EMPTY' || self::isEmpty($substType, $value)) {
            return false;
        }

        if ($firstToken) {
            self::addPrefix($operator, $result);
        } else {
            self::addSeparator($operator, $result);
        }

        switch ($substType) {
            case 'STRING':
                self::addStringValue($operator, $token, $value, $result, $maxChar);
                break;
            case 'LIST':
                self::addListValue($operator, $token, $value, $result, $maxChar, $composite);
                break;
            case 'MAP':
                self::addMapValue($operator, $token, $value, $result, $maxChar, $composite);
                break;
        }

        return true;
    }

    /**
     * @param string $operator
     * @param string $token
     * @param mixed $value
     * @param string $result
     * @param int $maxChar
     */
    private static function addStringValue(string $operator, string $token, $value, string &$result, int $maxChar) {
        self::addValue($operator, $token, $value, $result, $maxChar);
    }

    /**
     * @param string $operator
     * @param string $token
     * @param array<mixed> $value
     * @param string &$result
     * @param int $maxChar
     * @param bool $composite
     * @return bool
     */
    private static function addListValue(string $operator, string $token, array $value, string &$result, int $maxChar, bool $composite): bool {
        $first = true;
        foreach ($value as $v) {
            if ($first) {
                self::addValue($operator, $token, $v, $result, $maxChar);
                $first = false;
            } else {
                if ($composite) {
                    self::addSeparator($operator, $result);
                    self::addValue($operator, $token, $v, $result, $maxChar);
                } else {
                    $result .= ',';
                    self::addValueElement($operator, $token, $v, $result, $maxChar);
                }
            }
        }
        return !$first;
    }

    /**
     * @param string $operator
     * @param string $token
     * @param array<mixed> $value
     * @param string &$result
     * @param int $maxChar
     * @param bool $composite
     * @return bool
     */
    private static function addMapValue(string $operator, string $token, array $value, string &$result, int $maxChar, bool $composite): bool {
        $first = true;
        if ($maxChar !== -1) {
            throw new InvalidArgumentException("Value trimming is not allowed on Maps");
        }
        foreach ($value as $k => $v) {
            if ($composite) {
                if (!$first) {
                    self::addSeparator($operator, $result);
                }
                self::addValueElement($operator, $token, (string)$k, $result, $maxChar);
                $result .= '=';
            } else {
                if ($first) {
                    self::addValue($operator, $token, (string)$k, $result, $maxChar);
                } else {
                    $result .= ',';
                    self::addValueElement($operator, $token, (string)$k, $result, $maxChar);
                }
                $result .= ',';
            }
            self::addValueElement($operator, $token, $v, $result, $maxChar);
            $first = false;
        }
        return !$first;
    }

}
