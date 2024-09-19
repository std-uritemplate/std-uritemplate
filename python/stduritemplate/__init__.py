import urllib.parse
from typing import Any, Dict, List
from enum import Enum


class _Operator(Enum):
    PLUS = "+"
    HASH = "#"
    DOT = "."
    SLASH = "/"
    SEMICOLON = ";"
    QUESTION_MARK = "?"
    AMP = "&"
    NO_OP = ""


class _SubstitutionType(Enum):
    EMPTY = 0
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
    def __get_operator(character: str, token: List[str], col: int) -> str:
        if character == "+":
            return _Operator.PLUS
        elif character == "#":
            return _Operator.HASH
        elif character == ".":
            return _Operator.DOT
        elif character == "/":
            return _Operator.SLASH
        elif character == ";":
            return _Operator.SEMICOLON
        elif character == "?":
            return _Operator.QUESTION_MARK
        elif character == "&":
            return _Operator.AMP
        else:
            StdUriTemplate.__validate_literal(character, col)
            token.append(character)
            return _Operator.NO_OP

    @staticmethod
    def __expand_impl(string: str, substitutions: Dict[str, Any]) -> str:
        result = []
        token = None
        operator = None
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
                        operator,
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
                    operator = None
                    composite = False
                    max_char_buffer = None
                else:
                    raise ValueError(f"Failed to expand token, invalid at col: {i}")
            elif character == ",":
                if token is not None:
                    expanded = StdUriTemplate.__expand_token(
                        operator,
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
                    if operator is None:
                        operator = StdUriTemplate.__get_operator(character, token, i)
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
    def __add_prefix(op: str, result: List[str]) -> None:
        if op == _Operator.HASH:
            result.append("#")
        elif op == _Operator.DOT:
            result.append(".")
        elif op == _Operator.SLASH:
            result.append("/")
        elif op == _Operator.SEMICOLON:
            result.append(";")
        elif op == _Operator.QUESTION_MARK:
            result.append("?")
        elif op == _Operator.AMP:
            result.append("&")

    @staticmethod
    def __add_separator(op: str, result: List[str]) -> None:
        if op == _Operator.DOT:
            result.append(".")
        elif op == _Operator.SLASH:
            result.append("/")
        elif op == _Operator.SEMICOLON:
            result.append(";")
        elif op == _Operator.QUESTION_MARK or op == _Operator.AMP:
            result.append("&")
        else:
            result.append(",")

    @staticmethod
    def __add_value(
        op: str, token: str, value: Any, result: List[str], max_char: int
    ) -> None:
        if op == _Operator.PLUS or op == _Operator.HASH:
            StdUriTemplate.__add_expanded_value(None, value, result, max_char, False)
        elif op == _Operator.QUESTION_MARK or op == _Operator.AMP:
            result.append(token + "=")
            StdUriTemplate.__add_expanded_value(None, value, result, max_char, True)
        elif op == _Operator.SEMICOLON:
            result.append(token)
            StdUriTemplate.__add_expanded_value("=", value, result, max_char, True)
        elif op == _Operator.DOT or op == _Operator.SLASH or op == _Operator.NO_OP:
            StdUriTemplate.__add_expanded_value(None, value, result, max_char, True)

    @staticmethod
    def __add_value_element(
        op: str, token: str, value: Any, result: List[str], max_char: int
    ) -> None:
        if op == _Operator.PLUS or op == _Operator.HASH:
            StdUriTemplate.__add_expanded_value(None, value, result, max_char, False)
        elif (
            op == _Operator.QUESTION_MARK
            or op == _Operator.AMP
            or op == _Operator.SEMICOLON
            or op == _Operator.DOT
            or op == _Operator.SLASH
            or op == _Operator.NO_OP
        ):
            StdUriTemplate.__add_expanded_value(None, value, result, max_char, True)

    @staticmethod
    def __is_surrogate(cp):
        return len(cp.encode("utf-8")) > 1

    @staticmethod
    def __is_iprivate(cp):
        val = ord(cp)
        return 0xE000 <= val <= 0xF8FF

    @staticmethod
    def __is_ucschar(cp):
        val = ord(cp)
        return (
            (0xA0 <= val <= 0xD7FF)
            or (0xF900 <= val <= 0xFDCF)
            or (0xFDF0 <= val <= 0xFFEF)
        )

    @staticmethod
    def __add_expanded_value(
        prefix: str,
        value: Any,
        result: List[str],
        max_char: int,
        replace_reserved: bool,
    ) -> None:
        stringValue = StdUriTemplate.__convert_native_types(value)
        max_val = (
            min(max_char, len(stringValue)) if max_char != -1 else len(stringValue)
        )
        reserved_buffer = None
        if max_val > 0 and prefix is not None:
            result.append(prefix)
        for i in range(max_val):
            character = stringValue[i]
            if character == "%" and not replace_reserved:
                reserved_buffer = []
            to_append = character
            if (
                StdUriTemplate.__is_surrogate(character)
                or replace_reserved
                or StdUriTemplate.__is_ucschar(character)
                or StdUriTemplate.__is_iprivate(character)
            ):
                to_append = urllib.parse.quote(to_append, encoding="utf-8", safe="")
            if reserved_buffer is not None:
                reserved_buffer.append(to_append)
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
                    result.append(to_append)

        if reserved_buffer is not None:
            result.append("%25")
            result.append("".join(reserved_buffer[1:]))

    @staticmethod
    def __is_list(value: Any) -> bool:
        return isinstance(value, list)

    @staticmethod
    def __is_map(value: Any) -> bool:
        return isinstance(value, dict)

    @staticmethod
    def __get_substitution_type(value: Any, col: int) -> str:
        if value is None:
            return _SubstitutionType.EMPTY
        elif StdUriTemplate.__is_native_type(value):
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
    def __is_native_type(value: Any) -> bool:
        if (
            isinstance(value, (str))
            or isinstance(value, (bool))
            or isinstance(value, (int, float))
        ):
            return True

    @staticmethod
    def __convert_native_types(value: Any) -> str:
        if isinstance(value, (str)):
            return value
        if isinstance(value, (bool)):
            return str(value).lower()
        elif isinstance(value, (int, float)):
            return str(value)
        else:
            raise ValueError(f"Illegal class passed as substitution: {value}")

    @staticmethod
    def __expand_token(
        operator: str,
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
        subst_type = StdUriTemplate.__get_substitution_type(value, col)
        if subst_type is _SubstitutionType.EMPTY or StdUriTemplate.__is_empty(
            subst_type, value
        ):
            return False
        if first_token:
            StdUriTemplate.__add_prefix(operator, result)
        else:
            StdUriTemplate.__add_separator(operator, result)
        if subst_type == _SubstitutionType.STRING:
            StdUriTemplate.__add_string_value(operator, token, value, result, max_char)
        elif subst_type == _SubstitutionType.LIST:
            StdUriTemplate.__add_list_value(
                operator, token, value, result, max_char, composite
            )
        elif subst_type == _SubstitutionType.MAP:
            StdUriTemplate.__add_map_value(
                operator, token, value, result, max_char, composite
            )
        return True

    @staticmethod
    def __add_string_value(
        operator: str, token: str, value: Any, result: List[str], max_char: int
    ) -> None:
        StdUriTemplate.__add_value(operator, token, value, result, max_char)

    @staticmethod
    def __add_list_value(
        operator: str,
        token: str,
        value: List[Any],
        result: List[str],
        max_char: int,
        composite: bool,
    ) -> None:
        first = True
        for v in value:
            if first:
                StdUriTemplate.__add_value(operator, token, v, result, max_char)
                first = False
            else:
                if composite:
                    StdUriTemplate.__add_separator(operator, result)
                    StdUriTemplate.__add_value(operator, token, v, result, max_char)
                else:
                    result.append(",")
                    StdUriTemplate.__add_value_element(
                        operator, token, v, result, max_char
                    )

    @staticmethod
    def __add_map_value(
        operator: str,
        token: str,
        value: Dict[str, Any],
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
                    StdUriTemplate.__add_separator(operator, result)
                StdUriTemplate.__add_value_element(operator, token, k, result, max_char)
                result.append("=")
            else:
                if first:
                    StdUriTemplate.__add_value(operator, token, k, result, max_char)
                else:
                    result.append(",")
                    StdUriTemplate.__add_value_element(
                        operator, token, k, result, max_char
                    )
                result.append(",")
            StdUriTemplate.__add_value_element(operator, token, v, result, max_char)
            first = False
