from datetime import datetime
import urllib.parse
from typing import Any, Dict, List
from enum import Enum


class _Modifier(Enum):
    PLUS = "+"
    DASH = "#"
    DOT = "."
    SLASH = "/"
    SEMICOLON = ";"
    QUESTION_MARK = "?"
    AT = "&"
    NO_MOD = ""


class _SubstitutionType(Enum):
    STRING = 1
    LIST = 2
    MAP = 3


class StdUriTemplate:
    @staticmethod
    def expand(template: str, substitutions: Dict[str, Any]) -> str:
        return StdUriTemplate.__expand_impl(template, substitutions)

    __illegal_characters = [
        "+",
        "#",
        "/",
        ";",
        "?",
        "&",
        " ",
        "!",
        "=",
        "$",
        "|",
        "*",
        ":",
        "~",
        "-",
    ]

    @staticmethod
    def __validate_literal(character: str, col: int) -> None:
        if character in StdUriTemplate.__illegal_characters:
            raise ValueError(f"Illegal character identified in the token at col: {col}")

    @staticmethod
    def __get_max_char(buffer: str, col: int) -> int:
        if buffer is None or len(buffer) == 0:
            return -1
        else:
            try:
                return int("".join(buffer))
            except ValueError:
                raise ValueError(f"Cannot parse max chars at col: {col}")

    @staticmethod
    def __get_modifier(character: str, token: List[str], col: int) -> str:
        if character == "+":
            return _Modifier.PLUS
        elif character == "#":
            return _Modifier.DASH
        elif character == ".":
            return _Modifier.DOT
        elif character == "/":
            return _Modifier.SLASH
        elif character == ";":
            return _Modifier.SEMICOLON
        elif character == "?":
            return _Modifier.QUESTION_MARK
        elif character == "&":
            return _Modifier.AT
        else:
            StdUriTemplate.__validate_literal(character, col)
            token.append(character)
            return _Modifier.NO_MOD

    @staticmethod
    def __expand_impl(string: str, substitutions: Dict[str, Any]) -> str:
        result = []
        token = None
        modifier = None
        composite = False
        max_char_buffer = None
        first_token = True

        for i in range(len(string)):
            character = string[i]
            if character == "{":
                token = []
                first_token = True
            elif character == "}":
                if token is not None:
                    expanded = StdUriTemplate.__expand_token(
                        modifier,
                        "".join(token),
                        composite,
                        StdUriTemplate.__get_max_char(max_char_buffer, i),
                        first_token,
                        substitutions,
                        result,
                        i,
                    )
                    if expanded and first_token:
                        first_token = False
                    token = None
                    modifier = None
                    composite = False
                    max_char_buffer = None
                else:
                    raise ValueError(f"Failed to expand token, invalid at col: {i}")
            elif character == ",":
                if token is not None:
                    expanded = StdUriTemplate.__expand_token(
                        modifier,
                        "".join(token),
                        composite,
                        StdUriTemplate.__get_max_char(max_char_buffer, i),
                        first_token,
                        substitutions,
                        result,
                        i,
                    )
                    if expanded and first_token:
                        first_token = False
                    token = []
                    composite = False
                    max_char_buffer = None
                    continue
            else:
                if token is not None:
                    if modifier is None:
                        modifier = StdUriTemplate.__get_modifier(character, token, i)
                    elif max_char_buffer is not None:
                        if character.isdigit():
                            max_char_buffer.append(character)
                        else:
                            raise ValueError(
                                f"Illegal character identified in the token at col: {i}"
                            )
                    else:
                        if character == ":":
                            max_char_buffer = []
                        elif character == "*":
                            composite = True
                        else:
                            StdUriTemplate.__validate_literal(character, i)
                            token.append(character)
                else:
                    result.append(character)

        if token is None:
            return "".join(result)
        else:
            raise ValueError("Unterminated token")

    @staticmethod
    def __add_prefix(mod: str, result: List[str]) -> None:
        if mod == _Modifier.DASH:
            result.append("#")
        elif mod == _Modifier.DOT:
            result.append(".")
        elif mod == _Modifier.SLASH:
            result.append("/")
        elif mod == _Modifier.SEMICOLON:
            result.append(";")
        elif mod == _Modifier.QUESTION_MARK:
            result.append("?")
        elif mod == _Modifier.AT:
            result.append("&")

    @staticmethod
    def __add_separator(mod: str, result: List[str]) -> None:
        if mod == _Modifier.DOT:
            result.append(".")
        elif mod == _Modifier.SLASH:
            result.append("/")
        elif mod == _Modifier.SEMICOLON:
            result.append(";")
        elif mod == _Modifier.QUESTION_MARK or mod == _Modifier.AT:
            result.append("&")
        else:
            result.append(",")

    @staticmethod
    def __add_value(
        mod: str, token: str, value: str, result: List[str], max_char: int
    ) -> None:
        if mod == _Modifier.PLUS or mod == _Modifier.DASH:
            StdUriTemplate.__add_expanded_value(value, result, max_char, False)
        elif mod == _Modifier.QUESTION_MARK or mod == _Modifier.AT:
            result.append(token + "=")
            StdUriTemplate.__add_expanded_value(value, result, max_char, True)
        elif mod == _Modifier.SEMICOLON:
            result.append(token)
            if len(value) > 0:
                result.append("=")
            StdUriTemplate.__add_expanded_value(value, result, max_char, True)
        elif mod == _Modifier.DOT or mod == _Modifier.SLASH or mod == _Modifier.NO_MOD:
            StdUriTemplate.__add_expanded_value(value, result, max_char, True)

    @staticmethod
    def __add_value_element(
        mod: str, token: str, value: str, result: List[str], max_char: int
    ) -> None:
        if mod == _Modifier.PLUS or mod == _Modifier.DASH:
            StdUriTemplate.__add_expanded_value(value, result, max_char, False)
        elif (
            mod == _Modifier.QUESTION_MARK
            or mod == _Modifier.AT
            or mod == _Modifier.SEMICOLON
            or mod == _Modifier.DOT
            or mod == _Modifier.SLASH
            or mod == _Modifier.NO_MOD
        ):
            StdUriTemplate.__add_expanded_value(value, result, max_char, True)

    @staticmethod
    def __add_expanded_value(
        value: str, result: List[str], max_char: int, replace_reserved: bool
    ) -> None:
        max_val = min(max_char, len(value)) if max_char != -1 else len(value)
        reserved_buffer = None
        for i in range(max_val):
            character = value[i]
            if character == "%" and not replace_reserved:
                reserved_buffer = []
            if reserved_buffer is not None:
                reserved_buffer.append(character)
                if len(reserved_buffer) == 3:
                    try:
                        reserved = "".join(reserved_buffer)
                        decoded = urllib.parse.unquote(
                            reserved, encoding="utf-8", errors="strict"
                        )
                        is_encoded = decoded != reserved
                    except Exception:
                        is_encoded = False
                    if is_encoded:
                        result.append("".join(reserved_buffer))
                    else:
                        result.append("%25")
                        result.append("".join(reserved_buffer[1:]))
                    reserved_buffer = None
            else:
                if character == " ":
                    result.append("%20")
                elif character == "%":
                    result.append("%25")
                else:
                    if replace_reserved:
                        result.append(
                            urllib.parse.quote(character, encoding="utf-8", safe="")
                        )
                    else:
                        result.append(character)

        if reserved_buffer is not None:
            result.append("%25")
            if replace_reserved:
                result.append(
                    urllib.parse.quote(
                        "".join(reserved_buffer[1:], encoding="utf-8", safe="")
                    )
                )
            else:
                result.append("".join(reserved_buffer[1:]))

    @staticmethod
    def __is_list(value: Any) -> bool:
        return isinstance(value, list)

    @staticmethod
    def __is_map(value: Any) -> bool:
        return isinstance(value, dict)

    @staticmethod
    def __get_substitution_type(value: Any, col: int) -> str:
        if isinstance(value, str) or value is None:
            return _SubstitutionType.STRING
        elif StdUriTemplate.__is_list(value):
            return _SubstitutionType.LIST
        elif StdUriTemplate.__is_map(value):
            return _SubstitutionType.MAP
        else:
            raise ValueError(
                f"Illegal class passed as substitution, found {type(value)} at col: {col}"
            )

    @staticmethod
    def __is_empty(subst_type: str, value: Any) -> bool:
        if value is None:
            return True
        else:
            if subst_type == _SubstitutionType.STRING:
                return False
            elif subst_type == _SubstitutionType.LIST:
                return len(value) == 0
            elif subst_type == _SubstitutionType.MAP:
                return len(value) == 0
            else:
                return True

    @staticmethod
    def __expand_token(
        modifier: str,
        token: str,
        composite: bool,
        max_char: int,
        first_token: bool,
        substitutions: Dict[str, Any],
        result: List[str],
        col: int,
    ) -> bool:
        if len(token) == 0:
            raise ValueError(f"Found an empty token at col: {col}")
        value = substitutions.get(token)
        if isinstance(value, (bool)):
            value = str(value).lower()
        elif isinstance(value, (int, float)):
            value = str(value)
        elif isinstance(value, datetime):
            value = value.isoformat("T") + "Z"
        subst_type = StdUriTemplate.__get_substitution_type(value, col)
        if StdUriTemplate.__is_empty(subst_type, value):
            return False
        if first_token:
            StdUriTemplate.__add_prefix(modifier, result)
        else:
            StdUriTemplate.__add_separator(modifier, result)
        if subst_type == _SubstitutionType.STRING:
            StdUriTemplate.__add_string_value(modifier, token, value, result, max_char)
        elif subst_type == _SubstitutionType.LIST:
            StdUriTemplate.__add_list_value(
                modifier, token, value, result, max_char, composite
            )
        elif subst_type == _SubstitutionType.MAP:
            StdUriTemplate.__add_map_value(
                modifier, token, value, result, max_char, composite
            )
        return True

    @staticmethod
    def __add_string_value(
        modifier: str, token: str, value: str, result: List[str], max_char: int
    ) -> None:
        StdUriTemplate.__add_value(modifier, token, value, result, max_char)

    @staticmethod
    def __add_list_value(
        modifier: str,
        token: str,
        value: List[str],
        result: List[str],
        max_char: int,
        composite: bool,
    ) -> None:
        first = True
        for v in value:
            if first:
                StdUriTemplate.__add_value(modifier, token, v, result, max_char)
                first = False
            else:
                if composite:
                    StdUriTemplate.__add_separator(modifier, result)
                    StdUriTemplate.__add_value(modifier, token, v, result, max_char)
                else:
                    result.append(",")
                    StdUriTemplate.__add_value_element(
                        modifier, token, v, result, max_char
                    )

    @staticmethod
    def __add_map_value(
        modifier: str,
        token: str,
        value: Dict[str, str],
        result: List[str],
        max_char: int,
        composite: bool,
    ) -> None:
        first = True
        if max_char != -1:
            raise ValueError("Value trimming is not allowed on Maps")
        for k, v in value.items():
            if composite:
                if not first:
                    StdUriTemplate.__add_separator(modifier, result)
                StdUriTemplate.__add_value_element(modifier, token, k, result, max_char)
                result.append("=")
            else:
                if first:
                    StdUriTemplate.__add_value(modifier, token, k, result, max_char)
                else:
                    result.append(",")
                    StdUriTemplate.__add_value_element(
                        modifier, token, k, result, max_char
                    )
                result.append(",")
            StdUriTemplate.__add_value_element(modifier, token, v, result, max_char)
            first = False
