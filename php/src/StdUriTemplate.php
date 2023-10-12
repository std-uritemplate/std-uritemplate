<?php
namespace stduritemplate;

class StdUriTemplate {

    public static function expand($template, $substitutions) {
        return self::expandImpl($template, $substitutions);
    }

    private static function validateLiteral($c, $col) {
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
                throw new \InvalidArgumentException("Illegal character identified in the token at col: $col");
            default:
                break;
        }
    }

    private static function getMaxChar($buffer, $col) {
        if ($buffer === null) {
            return -1;
        } else {
            $value = $buffer;

            if (empty($value)) {
                return -1;
            } else {
                try {
                    return (int)$value;
                } catch (NumberFormatException $e) {
                    throw new \InvalidArgumentException("Cannot parse max chars at col: $col");
                }
            }
        }
    }

    private static function getOperator($c, &$token, $col) {
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

    private static function expandImpl($str, $substitutions) {
        $result = '';
        $token = null;

        $operator = null;
        $composite = false;
        $maxCharBuffer = null;
        $firstToken = true;

        for ($i = 0; $i < strlen($str); $i++) {
            $character = $str[$i];
            switch ($character) {
                case '{':
                    $token = '';
                    $firstToken = true;
                    break;
                case '}':
                    if ($token !== null) {
                        $expanded = self::expandToken($operator, $token, $composite, self::getMaxChar($maxCharBuffer, $i), $firstToken, $substitutions, $result, $i);
                        if ($expanded && $firstToken) {
                            $firstToken = false;
                        }
                        $token = null;
                        $operator = null;
                        $composite = false;
                        $maxCharBuffer = null;
                    } else {
                        throw new \InvalidArgumentException("Failed to expand token, invalid at col: $i");
                    }
                    break;
                case ',':
                    if ($token !== null) {
                        $expanded = self::expandToken($operator, $token, $composite, self::getMaxChar($maxCharBuffer, $i), $firstToken, $substitutions, $result, $i);
                        if ($expanded && $firstToken) {
                            $firstToken = false;
                        }
                        $token = '';
                        $composite = false;
                        $maxCharBuffer = null;
                        break;
                    }
                    // Intentional fall-through for commas outside the {}
                default:
                    if ($token !== null) {
                        if ($operator === null) {
                            $operator = self::getOperator($character, $token, $i);
                        } elseif ($maxCharBuffer !== null) {
                            if (is_numeric($character)) {
                                $maxCharBuffer .= $character;
                            } else {
                                throw new \InvalidArgumentException("Illegal character identified in the token at col: $i");
                            }
                        } else {
                            if ($character === ':') {
                                $maxCharBuffer = '';
                            } elseif ($character === '*') {
                                $composite = true;
                            } else {
                                self::validateLiteral($character, $i);
                                $token .= $character;
                            }
                        }
                    } else {
                        $result .= $character;
                    }
                    break;
            }
        }

        if ($token === null) {
            return $result;
        } else {
            throw new \InvalidArgumentException("Unterminated token");
        }
    }

    private static function addPrefix($op, &$result) {
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

    private static function addSeparator($op, &$result) {
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

    private static function addValue($op, $token, $value, &$result, $maxChar) {
        switch ($op) {
            case 'PLUS':
            case 'HASH':
                self::addExpandedValue($value, $result, $maxChar, false);
                break;
            case 'QUESTION_MARK':
            case 'AMP':

                $result .= $token . '=';
                self::addExpandedValue($value, $result, $maxChar, true);
                break;
            case 'SEMICOLON':
                $result .= $token;
                if ($value !== '') {
                    $result .= '=';
                }
                self::addExpandedValue($value, $result, $maxChar, true);
                break;
            case 'DOT':
            case 'SLASH':
            case 'NO_OP':
                self::addExpandedValue($value, $result, $maxChar, true);
        }
    }

    private static function addValueElement($op, $token, $value, &$result, $maxChar) {
        switch ($op) {
            case 'PLUS':
            case 'HASH':
                self::addExpandedValue($value, $result, $maxChar, false);
                break;
            case 'QUESTION_MARK':
            case 'AMP':
            case 'SEMICOLON':
            case 'DOT':
            case 'SLASH':
            case 'NO_OP':
                self::addExpandedValue($value, $result, $maxChar, true);
        }
    }

    private static function addExpandedValue($value, &$result, $maxChar, $replaceReserved) {
        $max = ($maxChar !== -1) ? min($maxChar, strlen($value)) : strlen($value);
        $result .= '';
        $reservedBuffer = null;

        for ($i = 0; $i < $max; $i++) {
            $character = $value[$i];

            if ($character === '%' && !$replaceReserved) {
                $reservedBuffer = '';
            }

            if ($reservedBuffer !== null) {
                $reservedBuffer .= $character;

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
                    if ($replaceReserved) {
                        $result .= urlencode($character);
                    } else {
                        $result .= $character;
                    }
                }
            }
        }

        if ($reservedBuffer !== null) {
            $result .= "%25";
            if ($replaceReserved) {
                $result .= urlencode(substr($reservedBuffer, 1));
            } else {
                $result .= substr($reservedBuffer, 1);
            }
        }
    }

    private static function isList($value) {
        return is_array($value);
    }

    private static function isMap($value) {
        if (is_array($value)) {
            // https://stackoverflow.com/a/173479/7898052
            if (!function_exists('array_is_list')) {
                if ($value === []) {
                    return true;
                }
                return array_keys($value) === range(0, count($arr) - 1);
            }
            return !array_is_list($value);
        }
        return false;
    }

    private static function getSubstitutionType($value, $col) {
        if (is_string($value) || $value === null) {
            return 'STRING';
        } elseif (self::isMap($value)) {
            return 'MAP';
        } elseif (self::isList($value)) {
            return 'LIST';
        } else {
            throw new \InvalidArgumentException("Illegal class passed as substitution, found " . get_class($value) . " at col: $col");
        }
    }

    private static function isEmpty($substType, $value) {
        if ($value === null) {
            return true;
        } else {
            switch ($substType) {
                case 'STRING':
                    return false;
                case 'LIST':
                    return empty($value);
                case 'MAP':
                    return empty($value);
                default:
                    return true;
            }
        }
    }

    private static function expandToken($operator, $token, $composite, $maxChar, $firstToken, $substitutions, &$result, $col) {
        if (empty($token)) {
            throw new \InvalidArgumentException("Found an empty token at col: $col");
        }

        $value = $substitutions[$token] ?? null;
        if (is_bool($value)) {
            if ($value) {
                $value = "true";
            } else {
                $value = "false";
            }
        } else if (is_bool($value) || is_int($value) || is_float($value) || is_double($value)) {
            $value = (string)$value;
        } else if ($value instanceof \DateTime) {
            $value = $value->format('Y-m-d\TH:i:s\Z');
        }

        $substType = self::getSubstitutionType($value, $col);
        if (self::isEmpty($substType, $value)) {
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

    private static function addStringValue($operator, $token, $value, &$result, $maxChar) {
        self::addValue($operator, $token, $value, $result, $maxChar);
    }

    private static function addListValue($operator, $token, $value, &$result, $maxChar, $composite) {
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

    private static function addMapValue($operator, $token, $value, &$result, $maxChar, $composite) {
        $first = true;
        if ($maxChar !== -1) {
            throw new \InvalidArgumentException("Value trimming is not allowed on Maps");
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

?>
