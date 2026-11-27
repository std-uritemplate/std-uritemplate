type Substitutions = { [key: string]: any };

const enum Operator {
  NO_OP,
  PLUS,
  HASH,
  DOT,
  SLASH,
  SEMICOLON,
  QUESTION_MARK,
  AMP,
}

const enum SubstitutionType {
  EMPTY,
  STRING,
  LIST,
  MAP,
}

export class StdUriTemplate {
  public static expand(template: string, substitutions: Substitutions): string {
    return StdUriTemplate.expandImpl(template, substitutions);
  }

  private static validateLiteral(c: string, col: number): void {
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
        throw new Error(`Illegal character identified in the token at col: ${col}`);
      default:
        break;
    }
  }

  private static getMaxChar(buffer: string[] | null, col: number): number {
    if (!buffer) {
      return -1;
    } else {
      const value = buffer.join('');

      if (value.length === 0) {
        return -1;
      } else {
        if (value.startsWith('0')) {
          throw new Error(`Cannot parse max chars at col: ${col}`);
        }
        const parsed = parseInt(value, 10);
        if (isNaN(parsed) || parsed < 1 || parsed > 9999) {
          throw new Error(`Cannot parse max chars at col: ${col}`);
        }
        return parsed;
      }
    }
  }

  private static getOperator(c: string, token: string[], col: number): Operator {
    switch (c) {
      case '+':
        return Operator.PLUS;
      case '#':
        return Operator.HASH;
      case '.':
        return Operator.DOT;
      case '/':
        return Operator.SLASH;
      case ';':
        return Operator.SEMICOLON;
      case '?':
        return Operator.QUESTION_MARK;
      case '&':
        return Operator.AMP;
      default:
        StdUriTemplate.validateLiteral(c, col);
        token.push(c);
        return Operator.NO_OP;
    }
  }

  private static expandImpl(str: string, substitutions: Substitutions): string {
    const result: string[] = [];
    let token: string[] | null = null;
    let operator: Operator | null = null;
    let composite = false;
    let maxCharBuffer: string[] | null = null;
    let firstToken = true;

    for (let i = 0; i < str.length; i++) {
      const character = str.charAt(i);
      switch (character) {
        case '{':
          token = [];
          firstToken = true;
          break;
        case '}':
          if (token !== null) {
            if (maxCharBuffer !== null && maxCharBuffer.length === 0) {
              throw new Error(`Found an empty prefix at col: ${i}`);
            }
            const expanded = StdUriTemplate.expandToken(
              operator,
              token.join(''),
              composite,
              StdUriTemplate.getMaxChar(maxCharBuffer, i),
              firstToken,
              substitutions,
              result,
              i
            );
            if (expanded && firstToken) {
              firstToken = false;
            }
            token = null;
            operator = null;
            composite = false;
            maxCharBuffer = null;
          } else {
            throw new Error(`Failed to expand token, invalid at col: ${i}`);
          }
          break;
        case ',':
          if (token !== null) {
            if (maxCharBuffer !== null && maxCharBuffer.length === 0) {
              throw new Error(`Found an empty prefix at col: ${i}`);
            }
            const expanded = StdUriTemplate.expandToken(
              operator,
              token.join(''),
              composite,
              StdUriTemplate.getMaxChar(maxCharBuffer, i),
              firstToken,
              substitutions,
              result,
              i
            );
            if (expanded && firstToken) {
              firstToken = false;
            }
            token = [];
            composite = false;
            maxCharBuffer = null;
            break;
          }
          // Intentional fall-through for commas outside the {}
        default:
          if (token !== null) {
            if (operator === null) {
              operator = StdUriTemplate.getOperator(character, token, i);
            } else if (maxCharBuffer !== null) {
              if (character >= '0' && character <= '9') {
                maxCharBuffer.push(character);
              } else {
                throw new Error(`Illegal character identified in the token at col: ${i}`);
              }
            } else {
              if (character === ':') {
                maxCharBuffer = [];
              } else if (character === '*') {
                composite = true;
              } else {
                StdUriTemplate.validateLiteral(character, i);
                token.push(character);
              }
            }
          } else {
            const cp = character.codePointAt(0)!;
            if (cp > 0x7F || (cp >= 0xD800 && cp <= 0xDBFF)) {
              let toEncode: string;
              if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < str.length) {
                toEncode = character + str.charAt(++i);
              } else {
                toEncode = character;
              }
              result.push(encodeURIComponent(toEncode));
            } else {
              result.push(character);
            }
          }
          break;
      }
    }

    if (token === null) {
      return result.join('');
    } else {
      throw new Error('Unterminated token');
    }
  }

  private static addPrefix(op: Operator | null, result: string[]): void {
    switch (op) {
      case Operator.HASH:
        result.push('#');
        break;
      case Operator.DOT:
        result.push('.');
        break;
      case Operator.SLASH:
        result.push('/');
        break;
      case Operator.SEMICOLON:
        result.push(';');
        break;
      case Operator.QUESTION_MARK:
        result.push('?');
        break;
      case Operator.AMP:
        result.push('&');
        break;
      default:
        return;
    }
  }

  private static addSeparator(op: Operator | null, result: string[]): void {
    switch (op) {
      case Operator.DOT:
        result.push('.');
        break;
      case Operator.SLASH:
        result.push('/');
        break;
      case Operator.SEMICOLON:
        result.push(';');
        break;
      case Operator.QUESTION_MARK:
      case Operator.AMP:
        result.push('&');
        break;
      default:
        result.push(',');
        return;
    }
  }

  private static addValue(op: Operator | null, token: string, value: any, result: string[], maxChar: number): void {
    switch (op) {
      case Operator.PLUS:
      case Operator.HASH:
        StdUriTemplate.addExpandedValue(null, value, result, maxChar, false);
        break;
      case Operator.QUESTION_MARK:
      case Operator.AMP:
        result.push(`${token}=`);
        StdUriTemplate.addExpandedValue(null, value, result, maxChar, true);
        break;
      case Operator.SEMICOLON:
        result.push(token);
        StdUriTemplate.addExpandedValue('=', value, result, maxChar, true);
        break;
      case Operator.DOT:
      case Operator.SLASH:
      case Operator.NO_OP:
        StdUriTemplate.addExpandedValue(null, value, result, maxChar, true);
        break;
    }
  }

  private static addValueElement(op: Operator | null, token: string, value: any, result: string[], maxChar: number): void {
    switch (op) {
      case Operator.PLUS:
      case Operator.HASH:
        StdUriTemplate.addExpandedValue(null, value, result, maxChar, false);
        break;
      case Operator.QUESTION_MARK:
      case Operator.AMP:
      case Operator.SEMICOLON:
      case Operator.DOT:
      case Operator.SLASH:
      case Operator.NO_OP:
        StdUriTemplate.addExpandedValue(null, value, result, maxChar, true);
        break;
    }
  }

  private static isUnreserved(c: string): boolean {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
      c === '-' || c === '.' || c === '_' || c === '~';
  }

  private static isReserved(c: string): boolean {
    return ":/?#[]@!$&'()*+,;=".includes(c);
  }

  private static pctEncode(cp: number): string {
    if (cp < 0x80) {
      return (cp < 0x10 ? '%0' : '%') + cp.toString(16).toUpperCase();
    }
    // encodeURIComponent throws on lone surrogates
    return encodeURIComponent(String.fromCodePoint(cp >= 0xD800 && cp <= 0xDFFF ? 0xFFFD : cp));
  }

  private static addExpandedValue(prefix: string | null, value: any, result: string[], maxChar: number, replaceReserved: boolean): void {
    const stringValue = StdUriTemplate.convertNativeTypes(value);
    const max = maxChar !== -1 ? maxChar : stringValue.length;

    if (stringValue.length > 0 && prefix != null) {
      result.push(prefix);
    }

    // i indexes UTF-16 code units, n counts code points against max
    for (let i = 0, n = 0; i < stringValue.length && n < max; n++) {
      const character = stringValue.charAt(i);
      if (StdUriTemplate.isUnreserved(character) || (!replaceReserved && StdUriTemplate.isReserved(character))) {
        result.push(character);
        i++;
      } else if (!replaceReserved && character === '%' && n + 2 < max &&
          StdUriTemplate.isHexDigit(stringValue.charAt(i + 1)) && StdUriTemplate.isHexDigit(stringValue.charAt(i + 2))) {
        result.push(stringValue.slice(i, i + 3));
        i += 3;
        n += 2;
      } else {
        const cp = stringValue.codePointAt(i)!;
        result.push(StdUriTemplate.pctEncode(cp));
        i += cp > 0xFFFF ? 2 : 1;
      }
    }
  }

  private static isList(value: any): boolean {
    return Array.isArray(value) || value instanceof Set;
  }

  private static isMap(value: any): boolean {
    return value instanceof Map || typeof value === 'object';
  }

  private static getSubstitutionType(value: any, col: number): SubstitutionType {
    if (value === undefined || value === null) {
      return SubstitutionType.EMPTY;
    } else if (StdUriTemplate.isNativeType(value)) {
      return SubstitutionType.STRING;
    } else if (StdUriTemplate.isList(value)) {
      return SubstitutionType.LIST;
    } else if (StdUriTemplate.isMap(value)) {
      return SubstitutionType.MAP;
    } else {
      throw new Error(`Illegal class passed as substitution, found ${typeof value} at col: ${col}`);
    }
  }

  private static isEmpty(substType: SubstitutionType, value: any): boolean {
    if (value === undefined || value === null) {
      return true;
    } else {
      switch (substType) {
        case SubstitutionType.STRING:
          return false;
        case SubstitutionType.LIST:
          return value.length === 0;
        case SubstitutionType.MAP:
          return Object.keys(value).length === 0;
        default:
          return true;
      }
    }
  }

  private static isNativeType(value: any): boolean {
    return (typeof value === 'string' ||
      typeof value === 'number' ||
      typeof value === 'boolean');
  }

  private static convertNativeTypes(value: any): string {
    if (typeof value === 'string') {
      return value;
    } else if (typeof value === 'number' || typeof value === 'boolean') {
      return value.toString();
    } else {
      throw new Error(`Illegal class passed as substitution, found ${typeof value}`);
    }
  }

  private static isHexDigit(c: string): boolean {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
  }

  private static checkVarname(token: string, col: number): void {
    if (token.startsWith('.') || token.endsWith('.')) {
      throw new Error(`Invalid variable name at col: ${col}`);
    }
    if (token.indexOf('..') !== -1) {
      throw new Error(`Invalid variable name at col: ${col}`);
    }
    for (let i = 0; i < token.length; i++) {
      if (token.charAt(i) === '%') {
        if (i + 2 >= token.length || !StdUriTemplate.isHexDigit(token.charAt(i + 1)) || !StdUriTemplate.isHexDigit(token.charAt(i + 2))) {
          throw new Error(`Invalid variable name at col: ${col}`);
        }
      }
    }
  }

  private static expandToken(
    operator: Operator | null,
    token: string,
    composite: boolean,
    maxChar: number,
    firstToken: boolean,
    substitutions: Substitutions,
    result: string[],
    col: number
  ): boolean {
    if (token.length === 0) {
      throw new Error(`Found an empty token at col: ${col}`);
    }

    StdUriTemplate.checkVarname(token, col);

    const value = Object.prototype.hasOwnProperty.call(substitutions, token) ? substitutions[token] : undefined;
    const substType = StdUriTemplate.getSubstitutionType(value, col);
    if (substType === SubstitutionType.EMPTY || StdUriTemplate.isEmpty(substType, value)) {
      return false;
    }

    if (firstToken) {
      StdUriTemplate.addPrefix(operator, result);
    } else {
      StdUriTemplate.addSeparator(operator, result);
    }

    switch (substType) {
      case SubstitutionType.STRING:
        StdUriTemplate.addStringValue(operator, token, value, result, maxChar);
        break;
      case SubstitutionType.LIST:
        StdUriTemplate.addListValue(operator, token, value, result, maxChar, composite);
        break;
      case SubstitutionType.MAP:
        StdUriTemplate.addMapValue(operator, token, value, result, maxChar, composite);
        break;
    }

    return true;
  }

  private static addStringValue(operator: Operator | null, token: string, value: any, result: string[], maxChar: number): void {
    StdUriTemplate.addValue(operator, token, value, result, maxChar);
  }

  private static addListValue(
    operator: Operator | null,
    token: string,
    value: any[],
    result: string[],
    maxChar: number,
    composite: boolean
  ): void {
    let first = true;
    for (const v of value) {
      if (first) {
        StdUriTemplate.addValue(operator, token, v, result, maxChar);
        first = false;
      } else {
        if (composite) {
          StdUriTemplate.addSeparator(operator, result);
          StdUriTemplate.addValue(operator, token, v, result, maxChar);
        } else {
          result.push(',');
          StdUriTemplate.addValueElement(operator, token, v, result, maxChar);
        }
      }
    }
  }

  private static addMapValue(
    operator: Operator | null,
    token: string,
    value: { [key: string]: any },
    result: string[],
    maxChar: number,
    composite: boolean
  ): void {
    let first = true;
    if (maxChar !== -1) {
      throw new Error('Value trimming is not allowed on Maps');
    }
    for (const key of Object.keys(value)) {
      const v = value[key];
      if (composite) {
        if (!first) {
          StdUriTemplate.addSeparator(operator, result);
        }
        StdUriTemplate.addValueElement(operator, token, key, result, maxChar);
        result.push('=');
      } else {
        if (first) {
          StdUriTemplate.addValue(operator, token, key, result, maxChar);
        } else {
          result.push(',');
          StdUriTemplate.addValueElement(operator, token, key, result, maxChar);
        }
        result.push(',');
      }
      StdUriTemplate.addValueElement(operator, token, v, result, maxChar);
      first = false;
    }
  }
}
