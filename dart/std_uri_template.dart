import 'dart:math';

class StdUriTemplate {
  const StdUriTemplate._();

  static String expand(String template, Map<String, dynamic> substitutions) {
    return _expandImpl(template, substitutions);
  }

  static void _validateLiteral(String c, int col) {
    switch (c) {
      case '+':
      case '#':
      case '.':
      case '/':
      case ';':
      case '?':
      case '&':
      case ' ':
      case '!':
      case '=':
      case '\$':
      case '|':
      case '*':
      case ':':
      case '~':
      case '-':
        throw ArgumentError(
            "Illegal character identified in the token at col: $col");
      default:
        break;
    }
  }

  static int _getMaxChar(String? buffer, int col) {
    if (buffer == null) {
      return -1;
    } else {
      final value = buffer;

      if (value.isEmpty) {
        return -1;
      } else {
        final intValue = int.tryParse(value);
        if (intValue == null) {
          throw ArgumentError("Cannot parse max chars at col: $col");
        }
        return intValue;
      }
    }
  }

  static String _getOperator(String c, StringBuffer token, int col) {
    switch (c) {
      case '+':
        return 'PLUS';
      case '#':
        return 'HASH';
      case '.':
        return 'DOT';
      case '/':
        return 'SLASH';
      case ';':
        return 'SEMICOLON';
      case '?':
        return 'QUESTION_MARK';
      case '&':
        return 'AMP';
      default:
        _validateLiteral(c, col);
        token.write(c);
        return 'NO_OP';
    }
  }

  static String _expandImpl(String str, Map<String, dynamic> substitutions) {
    final result = StringBuffer('');
    StringBuffer? token;

    var operator;
    var composite = false;
    String? maxCharBuffer;
    var firstToken = true;

    for (var i = 0; i < str.length; i++) {
      final character = str[i];
      switch (character) {
        case '{':
          token = StringBuffer();
          firstToken = true;
          break;
        case '}':
          if (token != null) {
            final expanded = _expandToken(
                operator,
                token.toString(),
                composite,
                _getMaxChar(maxCharBuffer, i),
                firstToken,
                substitutions,
                result,
                i);
            if (expanded && firstToken) {
              firstToken = false;
            }
            token = null;
            operator = null;
            composite = false;
            maxCharBuffer = null;
          } else {
            throw ArgumentError("Failed to expand token, invalid at col: $i");
          }
          break;
        case ',':
          if (token != null) {
            final expanded = _expandToken(
                operator,
                token.toString(),
                composite,
                _getMaxChar(maxCharBuffer, i),
                firstToken,
                substitutions,
                result,
                i);
            if (expanded && firstToken) {
              firstToken = false;
            }
            token = StringBuffer();
            composite = false;
            maxCharBuffer = null;
            break;
          }
          // Intentional fall-through for commas outside the {}
          continue;
        default:
          if (token != null) {
            if (operator == null) {
              operator = _getOperator(character, token, i);
            } else if (maxCharBuffer != null) {
              if (int.tryParse(character) != null) {
                maxCharBuffer += character;
              } else {
                throw ArgumentError(
                    "Illegal character identified in the token at col: $i");
              }
            } else {
              if (character == ':') {
                maxCharBuffer = '';
              } else if (character == '*') {
                composite = true;
              } else {
                _validateLiteral(character, i);
                token.write(character);
              }
            }
          } else {
            result.write(character);
          }
          break;
      }
    }

    if (token == null) {
      return result.toString();
    } else {
      throw ArgumentError("Unterminated token");
    }
  }

  static void _addPrefix(String op, StringBuffer result) {
    switch (op) {
      case 'HASH':
        result.write('#');
        break;
      case 'DOT':
        result.write('.');
        break;
      case 'SLASH':
        result.write('/');
        break;
      case 'SEMICOLON':
        result.write(';');
        break;
      case 'QUESTION_MARK':
        result.write('?');
        break;
      case 'AMP':
        result.write('&');
        break;
      default:
        return;
    }
  }

  static void _addSeparator(String op, StringBuffer result) {
    switch (op) {
      case 'DOT':
        result.write('.');
        break;
      case 'SLASH':
        result.write('/');
        break;
      case 'SEMICOLON':
        result.write(';');
        break;
      case 'QUESTION_MARK':
      case 'AMP':
        result.write('&');
        break;
      default:
        result.write(',');
        return;
    }
  }

  static void _addValue(String op, String token, dynamic value,
      StringBuffer result, int maxChar) {
    switch (op) {
      case 'PLUS':
      case 'HASH':
        _addExpandedValue(null, value, result, maxChar, false);
        break;
      case 'QUESTION_MARK':
      case 'AMP':
        result.write('$token=');
        _addExpandedValue(null, value, result, maxChar, true);
        break;
      case 'SEMICOLON':
        result.write(token);
        _addExpandedValue('=', value, result, maxChar, true);
        break;
      case 'DOT':
      case 'SLASH':
      case 'NO_OP':
        _addExpandedValue(null, value, result, maxChar, true);
    }
  }

  static void _addValueElement(String op, String token, dynamic value,
      StringBuffer result, int maxChar) {
    switch (op) {
      case 'PLUS':
      case 'HASH':
        _addExpandedValue(null, value, result, maxChar, false);
        break;
      case 'QUESTION_MARK':
      case 'AMP':
      case 'SEMICOLON':
      case 'DOT':
      case 'SLASH':
      case 'NO_OP':
        _addExpandedValue(null, value, result, maxChar, true);
    }
  }

  static bool _isSurrogate(String cp) {
    if (cp.isEmpty) {
      return true;
    }
    final codePoint = cp.codeUnitAt(0);
    return (codePoint >= 0xD800 && codePoint <= 0xDFFF);
  }

  static bool _isIprivate(String cp) {
    if (cp.isEmpty) {
      return false;
    }
    final codePoint = cp.codeUnitAt(0);
    return (0xE000 <= codePoint && codePoint <= 0xF8FF);
  }

  static bool _isUcschar(String cp) {
    if (cp.isEmpty) {
      return false;
    }
    final codePoint = cp.codeUnitAt(0);
    return (0xA0 <= codePoint && codePoint <= 0xD7FF) ||
        (0xF900 <= codePoint && codePoint <= 0xFDCF) ||
        (0xFDF0 <= codePoint && codePoint <= 0xFFEF);
  }

  static void _addExpandedValue(dynamic prefix, dynamic value,
      StringBuffer result, int maxChar, bool replaceReserved) {
    final stringValue = _convertNativeTypes(value);
    final max =
        (maxChar != -1) ? min(maxChar, stringValue.length) : stringValue.length;
    result.write('');
    StringBuffer? reservedBuffer;

    if (max > 0 && prefix != null) {
      result.write(prefix);
    }

    for (var i = 0; i < max; i++) {
      final character = stringValue[i];

      if (character == '%' && !replaceReserved) {
        reservedBuffer = StringBuffer();
      }

      var toAppend = character;
      if (_isSurrogate(String.fromCharCode(character.codeUnitAt(0))) ||
          replaceReserved ||
          _isUcschar(String.fromCharCode(character.codeUnitAt(0))) ||
          _isIprivate(String.fromCharCode(character.codeUnitAt(0)))) {
        toAppend = Uri.encodeQueryComponent(toAppend);
      }

      if (reservedBuffer != null) {
        reservedBuffer.write(toAppend);

        if (reservedBuffer.length == 3) {
          var isEncoded = false;
          try {
            final decoded = Uri.decodeComponent(reservedBuffer.toString());
            isEncoded = (decoded != reservedBuffer.toString());
          } catch (e) {
            // ignore
          }

          if (isEncoded) {
            result.write(reservedBuffer);
          } else {
            result.write("%25");
            // only if !replaceReserved
            result.write(reservedBuffer.toString().substring(1));
          }
          reservedBuffer = null;
        }
      } else {
        if (character == ' ') {
          result.write("%20");
        } else if (character == '%') {
          result.write("%25");
        } else {
          result.write(toAppend);
        }
      }
    }

    if (reservedBuffer != null) {
      result.write("%25");
      result.write(reservedBuffer.toString().substring(1));
    }
  }

  static bool _isList(dynamic value) {
    return value is List;
  }

  static bool _isMap(dynamic value) {
    if (value is Map) {
      if (!const bool.fromEnvironment('dart.library.js')) {
        if (value.isEmpty) {
          return true;
        }
        return value.keys.toList() !=
            List.generate(value.length, (index) => index);
      }
      return !value.keys.every((key) => key is int);
    }
    return false;
  }

  static String _getSubstitutionType(dynamic value, int col) {
    if (value == null) {
      return 'EMPTY';
    } else if (_isNativeType(value)) {
      return 'STRING';
    } else if (_isMap(value)) {
      return 'MAP';
    } else if (_isList(value)) {
      return 'LIST';
    } else {
      throw ArgumentError(
          "Illegal class passed as substitution, found ${value.runtimeType} at col: $col");
    }
  }

  static bool _isEmpty(String substType, dynamic value) {
    if (value == null) {
      return true;
    } else {
      switch (substType) {
        case 'STRING':
          return false;
        case 'LIST':
        case 'MAP':
          return value.isEmpty;
        default:
          return true;
      }
    }
  }

  static bool _isNativeType(dynamic value) {
    if (value is String ||
        value is bool ||
        value is int ||
        value is double ||
        value is DateTime) {
      return true;
    }
    return false;
  }

  static String _convertNativeTypes(dynamic value) {
    if (value is bool) {
      return value ? "true" : "false";
    } else if (value is String || value is int || value is double) {
      return value.toString();
    } else if (value is DateTime) {
      return value.toUtc().toIso8601String();
    } else {
      return '';
    }
  }

  static bool _expandToken(
      String operator,
      String token,
      bool composite,
      int maxChar,
      bool firstToken,
      Map<String, dynamic> substitutions,
      StringBuffer result,
      int col) {
    if (token.isEmpty) {
      throw ArgumentError("Found an empty token at col: $col");
    }

    final value = substitutions[token] ?? null;
    final substType = _getSubstitutionType(value, col);
    if (substType == 'EMPTY' || _isEmpty(substType, value)) {
      return false;
    }

    if (firstToken) {
      _addPrefix(operator, result);
    } else {
      _addSeparator(operator, result);
    }

    switch (substType) {
      case 'STRING':
        _addStringValue(operator, token, value, result, maxChar);
        break;
      case 'LIST':
        _addListValue(operator, token, value, result, maxChar, composite);
        break;
      case 'MAP':
        _addMapValue(operator, token, value, result, maxChar, composite);
        break;
    }

    return true;
  }

  static void _addStringValue(String operator, String token, dynamic value,
      StringBuffer result, int maxChar) {
    _addValue(operator, token, value, result, maxChar);
  }

  static bool _addListValue(String operator, String token, List<dynamic> value,
      StringBuffer result, int maxChar, bool composite) {
    var first = true;
    for (final v in value) {
      if (first) {
        _addValue(operator, token, v, result, maxChar);
        first = false;
      } else {
        if (composite) {
          _addSeparator(operator, result);
          _addValue(operator, token, v, result, maxChar);
        } else {
          result.write(',');
          _addValueElement(operator, token, v, result, maxChar);
        }
      }
    }
    return !first;
  }

  static bool _addMapValue(
      String operator,
      String token,
      Map<dynamic, dynamic> value,
      StringBuffer result,
      int maxChar,
      bool composite) {
    var first = true;
    if (maxChar != -1) {
      throw ArgumentError("Value trimming is not allowed on Maps");
    }
    value.forEach((k, v) {
      if (composite) {
        if (!first) {
          _addSeparator(operator, result);
        }
        _addValueElement(operator, token, k.toString(), result, maxChar);
        result.write('=');
      } else {
        if (first) {
          _addValue(operator, token, k.toString(), result, maxChar);
        } else {
          result.write(',');
          _addValueElement(operator, token, k.toString(), result, maxChar);
        }
        result.write(',');
      }
      _addValueElement(operator, token, v, result, maxChar);
      first = false;
    });
    return !first;
  }
}
