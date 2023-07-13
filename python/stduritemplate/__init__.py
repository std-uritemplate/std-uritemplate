# This code have been generated originally by ChatGPT from the Java implementation
# has been manually altered to pass the tests

import urllib.parse
from typing import List, Union, Dict

class Modifier:
    def __init__(self, token):
        self.token = token

    def validate(self):
        StdUriTemplate._validate_token(self.key())

    def expand(self, key, value, max_char):
        return self._expand_value(value, max_char)

    def expand_elements(self, key, value, max_char):
        return self._expand_value(value, max_char)

    def separator(self):
        return ','

    def key(self):
        return self.sanitized()[1:]

    def prefix(self):
        return ''

    def _expand_value(self, value, max_char):
        return StdUriTemplate._expand_value(value, max_char)

    def sanitized(self):
        return Token._sanitize_token(self.token)


class NoMod(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def key(self):
        return self.sanitized()


class Plus(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def expand(self, key, value, max_char):
        return self._free_value(value, max_char)

    def expand_elements(self, key, value, max_char):
        return self._free_value(value, max_char)

    def _free_value(self, value, max_char):
        return StdUriTemplate._expand_value_impl(value, max_char, False)


class Dash(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def prefix(self):
        return '#'

    def expand(self, key, value, max_char):
        return self._free_value(value, max_char)

    def expand_elements(self, key, value, max_char):
        return self._free_value(value, max_char)

    def _free_value(self, value, max_char):
        return StdUriTemplate._expand_value_impl(value, max_char, False)


class Dot(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def separator(self):
        return '.'

    def prefix(self):
        return '.'


class Slash(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def separator(self):
        return '/'

    def prefix(self):
        return '/'


class Semicolon(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def separator(self):
        return ';'

    def prefix(self):
        return ';'

    def expand(self, key, value, max_char):
        encoded = self._expand_value(value, max_char)
        if encoded and len(encoded) > 0:
            return "{0}={1}".format(key, encoded)
        else:
            return key


class QuestionMark(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def separator(self):
        return '&'

    def prefix(self):
        return '?'

    def expand(self, key, value, max_char):
        return StdUriTemplate._expand_kv(key, value, max_char)


class At(Modifier):
    def __init__(self, token):
        super().__init__(token)

    def separator(self):
        return '&'

    def prefix(self):
        return '&'

    def expand(self, key, value, max_char):
        return StdUriTemplate._expand_kv(key, value, max_char)


class Token:
    def __init__(self, token):
        self.token = token
        self.sanitized = Token._sanitize_token(token)
        self.max_char = self._get_max_char()
        self.composite = self._is_composite_token()

    def validate(self):
        StdUriTemplate._validate_token(self.sanitized)

    @staticmethod
    def _sanitize_token(token: str) -> str:
        suffix_index = token.find(':')
        result = token
        if suffix_index != -1:
            result = token[:suffix_index]

        suffix_index = token.find('*')
        if suffix_index != -1:
            result = token[:suffix_index]
        return result

    def _get_max_char(self):
        suffix_index = self.token.find(':')
        if suffix_index != -1:
            try:
                return int(self.token[suffix_index + 1:])
            except ValueError:
                pass
        return -1

    def _is_composite_token(self):
        return '*' in self.token


class StdUriTemplate:
    RESERVED = ["+", "#", "/", ";", "?", "&", " ", "!", "=", "$", "|", "*", ":", "~", "-"]

    @staticmethod
    def expand(template: str, substitutions: Dict[str, Union[None, int, float, str, List[str], Dict[str, str]]]) -> str:
        return StdUriTemplate._expand_impl(template, substitutions)

    @staticmethod
    def _expand_impl(template: str, substitutions: Dict[str, Union[None, int, float, str, List[str], Dict[str, str]]]) -> str:
        result = []

        tokens = []
        token = None

        for character in template:
            if character == '{':
                if token is not None:
                    tokens.append(token)
                    token = None
                else:
                    token = ""
            elif character == '}':
                if token is not None:
                    tokens.append(token)
                    token = None
                    result.append(StdUriTemplate._expand_tokens(tokens, substitutions))
                else:
                    raise ValueError("Failed to expand token, invalid.")
            elif character == ',':
                if token is not None:
                    tokens.append(token)
                    token = ""
                else:
                    result.append(character)
            else:
                if token is not None:
                    token += character
                else:
                    result.append(character)

        if token is not None:
            raise ValueError("Unterminated token")

        return ''.join(result)

    @staticmethod
    def _expand_kv(key: str, value: str, max_char: int) -> str:
        return f"{key}={StdUriTemplate._expand_value(value, max_char)}"

    @staticmethod
    def _expand_value(value: str, max_char: int) -> str:
        return StdUriTemplate._expand_value_impl(value, max_char, True)

    @staticmethod
    def _expand_value_impl(value: str, max_char: int, replace_reserved: bool) -> str:
        max_length = min(max_char, len(value)) if max_char != -1 else len(value)
        result = []
        reserved_buffer = []

        for i in range(max_length):
            character = value[i]

            if character == '%' and not replace_reserved:
                reserved_buffer = []

            if reserved_buffer:
                reserved_buffer.append(character)

                if len(reserved_buffer) == 3:
                    is_encoded = False
                    try:
                        urllib.parse.unquote(''.join(reserved_buffer), encoding='utf-8', errors='strict')
                        is_encoded = True
                    except Exception:
                        pass

                    if is_encoded:
                        result.extend(reserved_buffer)
                    else:
                        result.append("%25")
                        result.extend(reserved_buffer[1:])
                    reserved_buffer = None
            else:
                if character == ' ':
                    result.append("%20")
                elif character == '%':
                    result.append("%25")
                else:
                    if replace_reserved:
                        result.append(urllib.parse.quote(character, safe=''))
                    else:
                        result.append(character)

        if reserved_buffer:
            result.append("%25")
            if replace_reserved:
                result.append(urllib.parse.quote(''.join(reserved_buffer[1:]), safe=''))
            else:
                result.extend(reserved_buffer[1:])

        return ''.join(result)

    @staticmethod
    def _expand_tokens(tokens: List[str], substitutions: Dict[str, Union[None, int, float, str, List[str], Dict[str, str]]]) -> str:
        result = []

        first_token = True
        mod = None
        key = None

        for token in tokens:
            tok = Token(token)
            if mod is None:
                mod = StdUriTemplate._get_modifier(token)
                key = mod.key()
                mod.validate()
            else:
                key = tok.sanitized
                tok.validate()

            if key in substitutions:
                value = substitutions[key]

                if value is None or (isinstance(value, list) and len(value) == 0) or (isinstance(value, dict) and len(value) == 0):
                    continue

                if isinstance(value, (int, float)):
                    value = str(value)

                if first_token:
                    result.append(mod.prefix())
                else:
                    result.append(mod.separator())

                if isinstance(value, str):
                    result.append(mod.expand(key, value, tok.max_char))
                elif isinstance(value, list):
                    first = True
                    for subst in value:
                        if first:
                            first = False
                            result.append(mod.expand(key, subst, tok.max_char))
                        else:
                            if tok.composite:
                                result.append(mod.separator())
                                result.append(mod.expand(key, subst, tok.max_char))
                            else:
                                result.append(',')
                                result.append(mod.expand_elements(key, subst, tok.max_char))
                elif isinstance(value, dict):
                    first = True
                    for subst_key, subst_value in value.items():
                        if tok.max_char != -1:
                            raise ValueError("Value trimming is not allowed on Maps")
                        if first:
                            first = False
                            if tok.composite:
                                result.append(mod.expand_elements(key, subst_key, tok.max_char))
                            else:
                                result.append(mod.expand(key, subst_key, tok.max_char))
                        else:
                            if tok.composite:
                                result.append(mod.separator())
                            else:
                                result.append(',')
                            result.append(mod.expand_elements(key, subst_key, tok.max_char))

                        if tok.composite:
                            result.append('=')
                        else:
                            result.append(',')
                        result.append(mod.expand_elements(key, subst_value, tok.max_char))
                else:
                    raise ValueError("Substitution type not supported, found {0}, but only None, int, float, str, list, and dict are allowed.".format(type(value).__name__))

                first_token = False

        return ''.join(result)

    @staticmethod
    def _get_modifier(token: str):
        token_char = token[0]
        if token_char == '+':
            return Plus(token)
        elif token_char == '#':
            return Dash(token)
        elif token_char == '.':
            return Dot(token)
        elif token_char == '/':
            return Slash(token)
        elif token_char == ';':
            return Semicolon(token)
        elif token_char == '?':
            return QuestionMark(token)
        elif token_char == '&':
            return At(token)
        else:
            return NoMod(token)

    @staticmethod
    def _validate_token(token: str):
        if not token:
            raise ValueError("Empty key found")

        for res in StdUriTemplate.RESERVED:
            if res in token:
                raise ValueError("Found a key with invalid content: '{0}' contains the '{1}' character".format(token, res))