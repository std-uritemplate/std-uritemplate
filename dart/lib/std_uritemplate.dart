// ignore_for_file: constant_identifier_names

import 'dart:math';

enum _SubstitutionType { EMPTY, STRING, LIST, MAP }

enum _Operator { PLUS, HASH, DOT, SLASH, SEMICOLON, QUESTION_MARK, AMP, NO_OP }

extension on DateTime {
  /// This is the same as [toIso8601String] but without milli- and microseconds.
  String toIso8601StringWithoutMilliseconds() {
    String y =
        (year >= -9999 && year <= 9999) ? _fourDigits(year) : _sixDigits(year);
    String m = _twoDigits(month);
    String d = _twoDigits(day);
    String h = _twoDigits(hour);
    String min = _twoDigits(minute);
    String sec = _twoDigits(second);
    if (isUtc) {
      return "$y-$m-${d}T$h:$min:${sec}Z";
    } else {
      return "$y-$m-${d}T$h:$min:$sec";
    }
  }

  // Function below are copied from dart:core DateTime

  static String _fourDigits(int n) {
    int absN = n.abs();
    String sign = n < 0 ? "-" : "";
    if (absN >= 1000) return "$n";
    if (absN >= 100) return "${sign}0$absN";
    if (absN >= 10) return "${sign}00$absN";
    return "${sign}000$absN";
  }

  static String _sixDigits(int n) {
    assert(n < -9999 || n > 9999);
    int absN = n.abs();
    String sign = n < 0 ? "-" : "+";
    if (absN >= 100000) return "$sign$absN";
    return "${sign}0$absN";
  }

  static String _twoDigits(int n) {
    if (n >= 10) return "$n";
    return "0$n";
  }
}

class StdUriTemplate {
  const StdUriTemplate._();

  static String expand(String template, Map<String, Object?> substitutions) {
    return _expandImpl(template, substitutions);
  }

  static void _validateLiteral(String c, int col) {
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
      case '\$':
      case '|':
      case '*':
      case ':':
      case '~':
      case '-':
        throw ArgumentError(
            "Illegal character identified in the token at col: $col ($c)");
      default:
        break;
    }
  }

  static int _getMaxChar(StringBuffer buffer, int col) {
    if (buffer.isEmpty) {
      return -1;
    } else {
      return int.tryParse(buffer.toString()) ??
          (throw ArgumentError("Cannot parse max chars at col: $col"));
    }
  }

  static _Operator _getOperator(String c, StringBuffer token, int col) {
    switch (c) {
      case '+':
        return _Operator.PLUS;
      case '#':
        return _Operator.HASH;
      case '.':
        return _Operator.DOT;
      case '/':
        return _Operator.SLASH;
      case ';':
        return _Operator.SEMICOLON;
      case '?':
        return _Operator.QUESTION_MARK;
      case '&':
        return _Operator.AMP;
      default:
        _validateLiteral(c, col);
        token.write(c);
        return _Operator.NO_OP;
    }
  }

  static String _expandImpl(String str, Map<String, Object?> substitutions) {
    final result = StringBuffer();

    var toToken = false;
    final token = StringBuffer();

    _Operator? operator;
    var composite = false;
    var toMaxCharBuffer = false;

    final maxCharBuffer = StringBuffer();
    var firstToken = true;

    for (var i = 0; i < str.length; i++) {
      final character = str[i];
      switch (character) {
        case '{':
          toToken = true;
          token.clear();
          firstToken = true;
          break;
        case '}':
          if (toToken) {
            assert(operator != null, "Operator cannot be null");

            final expanded = _expandToken(
              operator!,
              token.toString(),
              composite,
              _getMaxChar(maxCharBuffer, i),
              firstToken,
              substitutions,
              result,
              i,
            );
            if (expanded && firstToken) {
              firstToken = false;
            }
            toToken = false;
            token.clear();
            operator = null;
            composite = false;
            toMaxCharBuffer = false;
            maxCharBuffer.clear();
          } else {
            throw ArgumentError("Failed to expand token, invalid at col: $i");
          }
          break;
        case ',':
          if (toToken) {
            assert(operator != null, "Operator cannot be null");

            final expanded = _expandToken(
              operator!,
              token.toString(),
              composite,
              _getMaxChar(maxCharBuffer, i),
              firstToken,
              substitutions,
              result,
              i,
            );
            if (expanded && firstToken) {
              firstToken = false;
            }
            token.clear();
            composite = false;
            toMaxCharBuffer = false;
            maxCharBuffer.clear();
            break;
          }
          // Intentional fall-through for commas outside the {}
          continue;
        default:
          if (toToken) {
            if (operator == null) {
              operator = _getOperator(character, token, i);
            } else if (toMaxCharBuffer) {
              if (int.tryParse(character) != null) {
                maxCharBuffer.write(character);
              } else {
                throw ArgumentError(
                  "Illegal character identified in the token at col: $i",
                );
              }
            } else {
              if (character == ':') {
                toMaxCharBuffer = true;
                maxCharBuffer.clear();
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

    if (!toToken) {
      return result.toString();
    } else {
      throw ArgumentError("Unterminated token");
    }
  }

  static void _addPrefix(_Operator op, StringBuffer result) {
    switch (op) {
      case _Operator.HASH:
        result.write('#');
        break;
      case _Operator.DOT:
        result.write('.');
        break;
      case _Operator.SLASH:
        result.write('/');
        break;
      case _Operator.SEMICOLON:
        result.write(';');
        break;
      case _Operator.QUESTION_MARK:
        result.write('?');
        break;
      case _Operator.AMP:
        result.write('&');
        break;
      default:
        return;
    }
  }

  static void _addSeparator(_Operator op, StringBuffer result) {
    switch (op) {
      case _Operator.DOT:
        result.write('.');
        break;
      case _Operator.SLASH:
        result.write('/');
        break;
      case _Operator.SEMICOLON:
        result.write(';');
        break;
      case _Operator.QUESTION_MARK:
      case _Operator.AMP:
        result.write('&');
        break;
      default:
        result.write(',');
        return;
    }
  }

  static void _addValue(_Operator op, String token, Object? value,
      StringBuffer result, int maxChar) {
    switch (op) {
      case _Operator.PLUS:
      case _Operator.HASH:
        _addExpandedValue(null, value, result, maxChar, false);
        break;
      case _Operator.QUESTION_MARK:
      case _Operator.AMP:
        result.write('$token=');
        _addExpandedValue(null, value, result, maxChar, true);
        break;
      case _Operator.SEMICOLON:
        result.write(token);
        _addExpandedValue('=', value, result, maxChar, true);
        break;
      case _Operator.DOT:
      case _Operator.SLASH:
      case _Operator.NO_OP:
        _addExpandedValue(null, value, result, maxChar, true);
    }
  }

  static void _addValueElement(_Operator op, String token, Object? value,
      StringBuffer result, int maxChar) {
    switch (op) {
      case _Operator.PLUS:
      case _Operator.HASH:
        _addExpandedValue(null, value, result, maxChar, false);
        break;
      case _Operator.QUESTION_MARK:
      case _Operator.AMP:
      case _Operator.SEMICOLON:
      case _Operator.DOT:
      case _Operator.SLASH:
      case _Operator.NO_OP:
        _addExpandedValue(null, value, result, maxChar, true);
    }
  }

  static bool _isSurrogate(String cp) {
    assert(cp.isNotEmpty);
    final codePoint = cp.codeUnitAt(0);
    return (codePoint >= 0xD800 && codePoint <= 0xDFFF);
  }

  static bool _isIprivate(String cp) {
    assert(cp.isNotEmpty);
    final codePoint = cp.codeUnitAt(0);
    return (0xE000 <= codePoint && codePoint <= 0xF8FF);
  }

  static bool _isUcschar(String cp) {
    assert(cp.isNotEmpty);
    final codePoint = cp.codeUnitAt(0);
    return (0xA0 <= codePoint && codePoint <= 0xD7FF) ||
        (0xF900 <= codePoint && codePoint <= 0xFDCF) ||
        (0xFDF0 <= codePoint && codePoint <= 0xFFEF);
  }

  static void _addExpandedValue(
    String? prefix,
    Object? value,
    StringBuffer result,
    int maxChar,
    bool replaceReserved,
  ) {
    final stringValue = _convertNativeTypes(value);
    final runes = stringValue.runes;
    final max = (maxChar != -1) ? min(maxChar, runes.length) : runes.length;

    var toReserved = false;
    final reservedBuffer = StringBuffer();

    if (max > 0 && prefix != null) {
      result.write(prefix);
    }

    for (var i = 0; i < max; i++) {
      final character = String.fromCharCode(runes.elementAt(i));

      if (character == '%' && !replaceReserved) {
        toReserved = true;
        reservedBuffer.clear();
      }

      var toAppend = character;
      if (replaceReserved ||
          _isSurrogate(character) ||
          _isUcschar(character) ||
          _isIprivate(character)) {
        toAppend = Uri.encodeQueryComponent(toAppend);
      }

      if (toReserved) {
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
          toReserved = false;
          reservedBuffer.clear();
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

    if (toReserved) {
      result.write("%25");
      result.write(reservedBuffer.toString().substring(1));
    }
  }

  static bool _isList(Object value) {
    return value is Iterable;
  }

  static bool _isMap(Object value) {
    return value is Map;
  }

  static _SubstitutionType _getSubstitutionType(Object? value, int col) {
    if (value == null) {
      return _SubstitutionType.EMPTY;
    } else if (_isNativeType(value)) {
      return _SubstitutionType.STRING;
    } else if (_isMap(value)) {
      return _SubstitutionType.MAP;
    } else if (_isList(value)) {
      return _SubstitutionType.LIST;
    } else {
      throw ArgumentError(
        "Illegal class passed as substitution, found ${value.runtimeType}"
        " at col: $col",
      );
    }
  }

  static bool _isEmpty(_SubstitutionType substType, Object? value) {
    if (value == null) {
      return true;
    } else {
      return switch (substType) {
        _SubstitutionType.STRING => false,
        _SubstitutionType.LIST => value is Iterable
            ? value.isEmpty
            : throw UnimplementedError(
                'Impossible case of _SubstitutionType being list'
                ' and value not being an Iterable.',
              ),
        _SubstitutionType.MAP => value is Map
            ? value.isEmpty
            : throw UnimplementedError(
                'Impossible case of _SubstitutionType being list'
                ' and value not being an Iterable.',
              ),
        _ => true
      };
    }
  }

  static bool _isNativeType(Object? value) {
    if (value is String || value is bool || value is num || value is DateTime) {
      return true;
    }
    return false;
  }

  static String _convertNativeTypes(Object? value) {
    if (value is bool || value is String || value is num) {
      return value.toString();
    } else if (value is DateTime) {
      return value.toUtc().toIso8601StringWithoutMilliseconds();
    } else {
      return '';
    }
  }

  static bool _expandToken(
    _Operator operator,
    String token,
    bool composite,
    int maxChar,
    bool firstToken,
    Map<String, Object?> substitutions,
    StringBuffer result,
    int col,
  ) {
    if (token.isEmpty) {
      throw ArgumentError("Found an empty token at col: $col");
    }

    final value = substitutions[token];
    final substType = _getSubstitutionType(value, col);
    if (substType == _SubstitutionType.EMPTY || _isEmpty(substType, value)) {
      return false;
    }

    if (firstToken) {
      _addPrefix(operator, result);
    } else {
      _addSeparator(operator, result);
    }

    switch (substType) {
      case _SubstitutionType.STRING:
        _addStringValue(operator, token, value, result, maxChar);
        break;
      case _SubstitutionType.LIST:
        _addListValue(
          operator,
          token,
          value as Iterable,
          result,
          maxChar,
          composite,
        );
        break;
      case _SubstitutionType.MAP:
        _addMapValue(
          operator,
          token,
          (value as Map).cast<String, Object?>(),
          result,
          maxChar,
          composite,
        );
        break;
      default:
        // do nothing
        break;
    }

    return true;
  }

  static void _addStringValue(
    _Operator operator,
    String token,
    Object? value,
    StringBuffer result,
    int maxChar,
  ) {
    _addValue(operator, token, value, result, maxChar);
  }

  static bool _addListValue(
    _Operator operator,
    String token,
    Iterable<Object?> value,
    StringBuffer result,
    int maxChar,
    bool composite,
  ) {
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
    _Operator operator,
    String token,
    Map<String, Object?> value,
    StringBuffer result,
    int maxChar,
    bool composite,
  ) {
    var first = true;
    if (maxChar != -1) {
      throw ArgumentError("Value trimming is not allowed on Maps");
    }
    for (var MapEntry(key: k, value: v) in value.entries) {
      if (composite) {
        if (!first) {
          _addSeparator(operator, result);
        }
        _addValueElement(operator, token, k, result, maxChar);
        result.write('=');
      } else {
        if (first) {
          _addValue(operator, token, k, result, maxChar);
        } else {
          result.write(',');
          _addValueElement(operator, token, k, result, maxChar);
        }
        result.write(',');
      }
      _addValueElement(operator, token, v, result, maxChar);
      first = false;
    }
    return !first;
  }
}
