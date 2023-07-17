import urllib.parse
from typing import Any, Dict, List
from enum import Enum

class Modifier(Enum):
    PLUS = '+'
    DASH = '#'
    DOT = '.'
    SLASH = '/'
    SEMICOLON = ';'
    QUESTION_MARK = '?'
    AT = '&'
    NO_MOD = ''
    
class SubstitutionType(Enum):
  STRING = 1
  LIST = 2
  MAP = 3

class StdUriTemplate:
    
    @staticmethod
    def expand(template: str, substitutions: Dict[str, Any]) -> str:
        return StdUriTemplate.expand_impl(template, substitutions)

    @staticmethod
    def validate_literal(character: str, col: int) -> None:
        illegal_characters = ['+', '#', '/', ';', '?', '&', ' ', '!', '=', '$', '|', '*', ':', '~', '-']
        if character in illegal_characters:
            raise ValueError(f"Illegal character identified in the token at col: {col}")

    @staticmethod
    def get_max_char(buffer: str, col: int) -> int:
        if buffer is None or len(buffer) == 0:
            return -1
        else:
            try:
                return int(buffer)
            except ValueError:
                raise ValueError(f"Cannot parse max chars at col: {col}")

    @staticmethod
    def get_modifier(character: str, token: List[str], col: int) -> str:
        if character == '+':
            return Modifier.PLUS
        elif character == '#':
            return Modifier.DASH
        elif character == '.':
            return Modifier.DOT
        elif character == '/':
            return Modifier.SLASH
        elif character == ';':
            return Modifier.SEMICOLON
        elif character == '?':
            return Modifier.QUESTION_MARK
        elif character == '&':
            return Modifier.AT
        else:
            StdUriTemplate.validate_literal(character, col)
            token.append(character)
            return Modifier.NO_MOD

    @staticmethod
    def expand_impl(string: str, substitutions: Dict[str, Any]) -> str:
        result = []
        token = None
        modifier = None
        composite = False
        max_char_buffer = None
        first_token = True

        for i in range(len(string)):
            character = string[i]
            if character == '{':
                token = []
                first_token = True
            elif character == '}':
                if token is not None:
                    expanded = StdUriTemplate.expand_token(modifier, ''.join(token), composite, StdUriTemplate.get_max_char(max_char_buffer, i), first_token, substitutions, result, i)
                    if expanded and first_token:
                        first_token = False
                    token = None
                    modifier = None
                    composite = False
                    max_char_buffer = None
                else:
                    raise ValueError(f"Failed to expand token, invalid at col: {i}")
            elif character == ',':
                if token is not None:
                    expanded = StdUriTemplate.expand_token(modifier, ''.join(token), composite, StdUriTemplate.get_max_char(max_char_buffer, i), first_token, substitutions, result, i)
                    if expanded and first_token:
                        first_token = False
                    token = []
                    composite = False
                    max_char_buffer = None
                    continue
            else:
                if token is not None:
                    if modifier is None:
                        modifier = StdUriTemplate.get_modifier(character, token, i)
                    elif max_char_buffer is not None:
                        if character.isdigit():
                            max_char_buffer.append(character)
                        else:
                            raise ValueError(f"Illegal character identified in the token at col: {i}")
                    else:
                        if character == ':':
                            max_char_buffer = []
                        elif character == '*':
                            composite = True
                        else:
                            StdUriTemplate.validate_literal(character, i)
                            token.append(character)
                else:
                    result.append(character)

        if token is None:
            return ''.join(result)
        else:
            raise ValueError("Unterminated token")

    @staticmethod
    def add_prefix(mod: str, result: List[str]) -> None:
        if mod == Modifier.DASH:
            result.append('#')
        elif mod == Modifier.DOT:
            result.append('.')
        elif mod == Modifier.SLASH:
            result.append('/')
        elif mod == Modifier.SEMICOLON:
            result.append(';')
        elif mod == Modifier.QUESTION_MARK:
            result.append('?')
        elif mod == Modifier.AT:
            result.append('&')

    @staticmethod
    def add_separator(mod: str, result: List[str]) -> None:
        if mod == Modifier.DOT:
            result.append('.')
        elif mod == Modifier.SLASH:
            result.append('/')
        elif mod == Modifier.SEMICOLON:
            result.append(';')
        elif mod == Modifier.QUESTION_MARK or mod == Modifier.AT:
            result.append('&')
        else:
            result.append(',')

    @staticmethod
    def add_value(mod: str, token: str, value: str, result: List[str], max_char: int) -> None:
        if mod == Modifier.PLUS or mod == Modifier.DASH:
            StdUriTemplate.add_expanded_value(value, result, max_char, False)
        elif mod == Modifier.QUESTION_MARK or mod == Modifier.AT:
            result.append(token + '=')
            StdUriTemplate.add_expanded_value(value, result, max_char, True)
        elif mod == Modifier.SEMICOLON:
            result.append(token)
            if len(value) > 0:
                result.append('=')
            StdUriTemplate.add_expanded_value(value, result, max_char, True)
        elif mod == Modifier.DOT or mod == Modifier.SLASH or mod == Modifier.NO_MOD:
            StdUriTemplate.add_expanded_value(value, result, max_char, True)

    @staticmethod
    def add_value_element(mod: str, token: str, value: str, result: List[str], max_char: int) -> None:
        if mod == Modifier.PLUS or mod == Modifier.DASH:
            StdUriTemplate.add_expanded_value(value, result, max_char, False)
        elif mod == Modifier.QUESTION_MARK or mod == Modifier.AT or mod == Modifier.SEMICOLON or mod == Modifier.DOT or mod == Modifier.SLASH or mod == Modifier.NO_MOD:
            StdUriTemplate.add_expanded_value(value, result, max_char, True)

    @staticmethod
    def add_expanded_value(value: str, result: List[str], max_char: int, replace_reserved: bool) -> None:
        max_val = min(max_char, len(value)) if max_char != -1 else len(value)
        reserved_buffer = None
        for i in range(max_val):
            character = value[i]
            if character == '%' and not replace_reserved:
                reserved_buffer = []
            if reserved_buffer is not None:
                reserved_buffer.append(character)
                if len(reserved_buffer) == 3:
                    try:
                        urllib.parse.unquote(''.join(reserved_buffer))
                        is_encoded = True
                    except Exception:
                        is_encoded = False
                    if is_encoded:
                        result.append(''.join(reserved_buffer))
                    else:
                        result.append("%25")
                        result.append(''.join(reserved_buffer[1:]))
                    reserved_buffer = None
            else:
                if character == ' ':
                    result.append("%20")
                elif character == '%':
                    result.append("%25")
                else:
                    if replace_reserved:
                        result.append(urllib.parse.quote(character))
                    else:
                        result.append(character)

        if reserved_buffer is not None:
            result.append("%25")
            if replace_reserved:
                result.append(urllib.parse.quote(''.join(reserved_buffer[1:])))
            else:
                result.append(''.join(reserved_buffer[1:]))

    @staticmethod
    def is_list(value: Any) -> bool:
        return isinstance(value, list)

    @staticmethod
    def is_map(value: Any) -> bool:
        return isinstance(value, dict)

    @staticmethod
    def get_substitution_type(value: Any, col: int) -> str:
        if isinstance(value, str) or value is None:
            return SubstitutionType.STRING
        elif StdUriTemplate.is_list(value):
            return SubstitutionType.LIST
        elif StdUriTemplate.is_map(value):
            return SubstitutionType.MAP
        else:
            raise ValueError(f"Illegal class passed as substitution, found {type(value)} at col: {col}")

    @staticmethod
    def is_empty(subst_type: str, value: Any) -> bool:
        if value is None:
            return True
        else:
            if subst_type == SubstitutionType.STRING:
                return False
            elif subst_type == SubstitutionType.LIST:
                return len(value) == 0
            elif subst_type == SubstitutionType.MAP:
                return len(value) == 0
            else:
                return True

    @staticmethod
    def expand_token(modifier: str, token: str, composite: bool, max_char: int, first_token: bool, substitutions: Dict[str, Any], result: List[str], col: int) -> bool:
        if len(token) == 0:
            raise ValueError(f"Found an empty token at col: {col}")
        value = substitutions.get(token)
        if isinstance(value, (int, float)):
            value = str(value)
        subst_type = StdUriTemplate.get_substitution_type(value, col)
        if StdUriTemplate.is_empty(subst_type, value):
            return False
        if first_token:
            StdUriTemplate.add_prefix(modifier, result)
        else:
            StdUriTemplate.add_separator(modifier, result)
        if subst_type == SubstitutionType.STRING:
            StdUriTemplate.add_string_value(modifier, token, value, result, max_char)
        elif subst_type == SubstitutionType.LIST:
            StdUriTemplate.add_list_value(modifier, token, value, result, max_char, composite)
        elif subst_type == SubstitutionType.MAP:
            StdUriTemplate.add_map_value(modifier, token, value, result, max_char, composite)
        return True

    @staticmethod
    def add_string_value(modifier: str, token: str, value: str, result: List[str], max_char: int) -> None:
        StdUriTemplate.add_value(modifier, token, value, result, max_char)

    @staticmethod
    def add_list_value(modifier: str, token: str, value: List[str], result: List[str], max_char: int, composite: bool) -> None:
        first = True
        for v in value:
            if first:
                StdUriTemplate.add_value(modifier, token, v, result, max_char)
                first = False
            else:
                if composite:
                    StdUriTemplate.add_separator(modifier, result)
                    StdUriTemplate.add_value(modifier, token, v, result, max_char)
                else:
                    result.append(',')
                    StdUriTemplate.add_value_element(modifier, token, v, result, max_char)

    @staticmethod
    def add_map_value(modifier: str, token: str, value: Dict[str, str], result: List[str], max_char: int, composite: bool) -> None:
        first = True
        if max_char != -1:
            raise ValueError("Value trimming is not allowed on Maps")
        for k, v in value.items():
            if composite:
                if not first:
                    StdUriTemplate.add_separator(modifier, result)
                StdUriTemplate.add_value_element(modifier, token, k, result, max_char)
                result.append('=')
            else:
                if first:
                    StdUriTemplate.add_value(modifier, token, k, result, max_char)
                else:
                    result.append(',')
                    StdUriTemplate.add_value_element(modifier, token, k, result, max_char)
                result.append(',')
            StdUriTemplate.add_value_element(modifier, token, v, result, max_char)
            first = False
