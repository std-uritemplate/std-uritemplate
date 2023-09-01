type Substitutions = { [key: string]: any };

enum Modifier {
  NO_MOD,
  PLUS,
  DASH,
  DOT,
  SLASH,
  SEMICOLON,
  QUESTION_MARK,
  AT,
}

enum SubstitutionType {
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
        try {
          return parseInt(value, 10);
        } catch (e) {
          throw new Error(`Cannot parse max chars at col: ${col}`);
        }
      }
    }
  }

  private static getModifier(c: string, token: string[], col: number): Modifier {
    switch (c) {
      case '+':
        return Modifier.PLUS;
      case '#':
        return Modifier.DASH;
      case '.':
        return Modifier.DOT;
      case '/':
        return Modifier.SLASH;
      case ';':
        return Modifier.SEMICOLON;
      case '?':
        return Modifier.QUESTION_MARK;
      case '&':
        return Modifier.AT;
      default:
        StdUriTemplate.validateLiteral(c, col);
        token.push(c);
        return Modifier.NO_MOD;
    }
  }

  private static expandImpl(str: string, substitutions: Substitutions): string {
    const result: string[] = [];
    let token: string[] | null = null;
    let modifier: Modifier | null = null;
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
            const expanded = StdUriTemplate.expandToken(
              modifier,
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
            modifier = null;
            composite = false;
            maxCharBuffer = null;
          } else {
            throw new Error(`Failed to expand token, invalid at col: ${i}`);
          }
          break;
        case ',':
          if (token !== null) {
            const expanded = StdUriTemplate.expandToken(
              modifier,
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
            if (modifier === null) {
              modifier = StdUriTemplate.getModifier(character, token, i);
            } else if (maxCharBuffer !== null) {
              if (character.match(/^\d$/)) {
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
            result.push(character);
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

  private static addPrefix(mod: Modifier | null, result: string[]): void {
    switch (mod) {
      case Modifier.DASH:
        result.push('#');
        break;
      case Modifier.DOT:
        result.push('.');
        break;
      case Modifier.SLASH:
        result.push('/');
        break;
      case Modifier.SEMICOLON:
        result.push(';');
        break;
      case Modifier.QUESTION_MARK:
        result.push('?');
        break;
      case Modifier.AT:
        result.push('&');
        break;
      default:
        return;
    }
  }

  private static addSeparator(mod: Modifier | null, result: string[]): void {
    switch (mod) {
      case Modifier.DOT:
        result.push('.');
        break;
      case Modifier.SLASH:
        result.push('/');
        break;
      case Modifier.SEMICOLON:
        result.push(';');
        break;
      case Modifier.QUESTION_MARK:
      case Modifier.AT:
        result.push('&');
        break;
      default:
        result.push(',');
        return;
    }
  }

  private static addValue(mod: Modifier | null, token: string, value: string, result: string[], maxChar: number): void {
    switch (mod) {
      case Modifier.PLUS:
      case Modifier.DASH:
        StdUriTemplate.addExpandedValue(value, result, maxChar, false);
        break;
      case Modifier.QUESTION_MARK:
      case Modifier.AT:
        result.push(`${token}=`);
        StdUriTemplate.addExpandedValue(value, result, maxChar, true);
        break;
      case Modifier.SEMICOLON:
        result.push(token);
        if (value.length > 0) {
          result.push('=');
        }
        StdUriTemplate.addExpandedValue(value, result, maxChar, true);
        break;
      case Modifier.DOT:
      case Modifier.SLASH:
      case Modifier.NO_MOD:
        StdUriTemplate.addExpandedValue(value, result, maxChar, true);
        break;
    }
  }

  private static addValueElement(mod: Modifier | null, token: string, value: string, result: string[], maxChar: number): void {
    switch (mod) {
      case Modifier.PLUS:
      case Modifier.DASH:
        StdUriTemplate.addExpandedValue(value, result, maxChar, false);
        break;
      case Modifier.QUESTION_MARK:
      case Modifier.AT:
      case Modifier.SEMICOLON:
      case Modifier.DOT:
      case Modifier.SLASH:
      case Modifier.NO_MOD:
        StdUriTemplate.addExpandedValue(value, result, maxChar, true);
        break;
    }
  }

  private static addExpandedValue(value: string, result: string[], maxChar: number, replaceReserved: boolean): void {
    const max = maxChar !== -1 ? Math.min(maxChar, value.length) : value.length;
    let reservedBuffer: string[] | undefined = undefined;

    for (let i = 0; i < max; i++) {
      const character = value.charAt(i);

      if (character === '%' && !replaceReserved) {
        reservedBuffer = [];
      }

      if (reservedBuffer) {
        reservedBuffer.push(character);

        if (reservedBuffer.length === 3) {
          let isEncoded = false;
          try {
            const reserved = reservedBuffer.join('')
            const decoded = decodeURIComponent(reservedBuffer.join(''));
            isEncoded = (reserved !== decoded);
          } catch (e) {
            // ignore
          }

          if (isEncoded) {
            result.push(reservedBuffer.join(''));
          } else {
            result.push('%25');
            // only if !replaceReserved
            result.push(reservedBuffer.slice(1).join(''));
          }
          reservedBuffer = undefined;
        }
      } else {
        if (character === ' ') {
          result.push('%20');
        } else if (character === '%') {
          result.push('%25');
        } else {
          if (replaceReserved) {
            if (character === '!') { // Specific to JS/TS: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent#description
              result.push('%21');
            } else {
              result.push(encodeURIComponent(character));
            }
          } else {
            result.push(character);
          }
        }
      }
    }

    if (reservedBuffer) {
      result.push('%25');
      if (replaceReserved) {
        result.push(encodeURIComponent(reservedBuffer.slice(1).join('')));
      } else {
        result.push(reservedBuffer.slice(1).join(''));
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
    if (typeof value === 'string' || value === undefined || value === null) {
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

  private static expandToken(
    modifier: Modifier | null,
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

    let value = substitutions[token];
    if (typeof value === 'number' || typeof value === 'boolean') {
      value = value.toString();
    } else if (value instanceof Date) {
      value = value.toISOString().split('.')[0] + "Z";
    }

    const substType = StdUriTemplate.getSubstitutionType(value, col);
    if (StdUriTemplate.isEmpty(substType, value)) {
      return false;
    }

    if (firstToken) {
      StdUriTemplate.addPrefix(modifier, result);
    } else {
      StdUriTemplate.addSeparator(modifier, result);
    }

    switch (substType) {
      case SubstitutionType.STRING:
        StdUriTemplate.addStringValue(modifier, token, value, result, maxChar);
        break;
      case SubstitutionType.LIST:
        StdUriTemplate.addListValue(modifier, token, value, result, maxChar, composite);
        break;
      case SubstitutionType.MAP:
        StdUriTemplate.addMapValue(modifier, token, value, result, maxChar, composite);
        break;
    }

    return true;
  }

  private static addStringValue(modifier: Modifier | null, token: string, value: string, result: string[], maxChar: number): void {
    StdUriTemplate.addValue(modifier, token, value, result, maxChar);
  }

  private static addListValue(
    modifier: Modifier | null,
    token: string,
    value: string[],
    result: string[],
    maxChar: number,
    composite: boolean
  ): void {
    let first = true;
    for (const v of value) {
      if (first) {
        StdUriTemplate.addValue(modifier, token, v, result, maxChar);
        first = false;
      } else {
        if (composite) {
          StdUriTemplate.addSeparator(modifier, result);
          StdUriTemplate.addValue(modifier, token, v, result, maxChar);
        } else {
          result.push(',');
          StdUriTemplate.addValueElement(modifier, token, v, result, maxChar);
        }
      }
    }
  }

  private static addMapValue(
    modifier: Modifier | null,
    token: string,
    value: { [key: string]: string },
    result: string[],
    maxChar: number,
    composite: boolean
  ): void {
    let first = true;
    if (maxChar !== -1) {
      throw new Error('Value trimming is not allowed on Maps');
    }
    for (const key in value) {
      const v = value[key];
      if (composite) {
        if (!first) {
          StdUriTemplate.addSeparator(modifier, result);
        }
        StdUriTemplate.addValueElement(modifier, token, key, result, maxChar);
        result.push('=');
      } else {
        if (first) {
          StdUriTemplate.addValue(modifier, token, key, result, maxChar);
        } else {
          result.push(',');
          StdUriTemplate.addValueElement(modifier, token, key, result, maxChar);
        }
        result.push(',');
      }
      StdUriTemplate.addValueElement(modifier, token, v, result, maxChar);
      first = false;
    }
  }
}
