use std::borrow::Cow;
use std::collections::HashMap;
use std::fmt;

pub fn expand(
    template: &str,
    substitutions: &HashMap<String, Value>,
) -> Result<String, StdUriTemplateError> {
    expand_impl(template, substitutions)
}

#[derive(Debug, Clone)]
pub enum Value {
    String(String),
    Bool(bool),
    Integer(i64),
    Float(f64),
    List(Vec<Value>),
    Map(Vec<(String, Value)>),
}

#[derive(Debug)]
pub struct StdUriTemplateError {
    message: String,
}

impl fmt::Display for StdUriTemplateError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl std::error::Error for StdUriTemplateError {}

impl StdUriTemplateError {
    fn new(message: String) -> Self {
        StdUriTemplateError { message }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
enum Operator {
    NoOp,
    Plus,
    Hash,
    Dot,
    Slash,
    Semicolon,
    QuestionMark,
    Amp,
}

#[derive(Debug, Clone, Copy, PartialEq)]
enum SubstitutionType {
    Empty,
    String,
    List,
    Map,
}

fn validate_literal(c: char, col: usize) -> Result<(), StdUriTemplateError> {
    match c {
        '+' | '#' | '/' | ';' | '?' | '&' | ' ' | '!' | '=' | '$' | '|' | '*' | ':' | '~'
        | '-' => Err(StdUriTemplateError::new(format!(
            "Illegal character identified in the token at col:{}",
            col
        ))),
        _ => Ok(()),
    }
}

fn get_max_char(buffer: &str, col: usize) -> Result<i32, StdUriTemplateError> {
    if buffer.is_empty() {
        return Ok(-1);
    }

    let value = buffer.parse::<i32>().map_err(|_| {
        StdUriTemplateError::new(format!("Cannot parse max chars at col:{}", col))
    })?;

    if buffer.starts_with('0') {
        return Err(StdUriTemplateError::new(format!(
            "Cannot parse max chars at col:{}",
            col
        )));
    }

    if value < 1 || value > 9999 {
        return Err(StdUriTemplateError::new(format!(
            "Cannot parse max chars at col:{}",
            col
        )));
    }

    Ok(value)
}

fn get_operator(
    c: char,
    token: &mut String,
    col: usize,
) -> Result<Operator, StdUriTemplateError> {
    match c {
        '+' => Ok(Operator::Plus),
        '#' => Ok(Operator::Hash),
        '.' => Ok(Operator::Dot),
        '/' => Ok(Operator::Slash),
        ';' => Ok(Operator::Semicolon),
        '?' => Ok(Operator::QuestionMark),
        '&' => Ok(Operator::Amp),
        _ => {
            validate_literal(c, col)?;
            token.push(c);
            Ok(Operator::NoOp)
        }
    }
}

fn expand_impl(
    template: &str,
    substitutions: &HashMap<String, Value>,
) -> Result<String, StdUriTemplateError> {
    let mut result = String::with_capacity(template.len() * 2);

    let mut to_token = false;
    let mut token = String::new();

    let mut operator: Option<Operator> = None;
    let mut composite = false;
    let mut to_max_char_buffer = false;
    let mut max_char_buffer = String::with_capacity(3);
    let mut first_token = true;

    for (i, character) in template.chars().enumerate() {
        match character {
            '{' => {
                to_token = true;
                token.clear();
                first_token = true;
            }
            '}' => {
                if to_token {
                    if to_max_char_buffer && max_char_buffer.is_empty() {
                        return Err(StdUriTemplateError::new(format!(
                            "Found an empty prefix at col:{}",
                            i
                        )));
                    }
                    let max_char = get_max_char(&max_char_buffer, i)?;
                    let expanded = expand_token(
                        operator.unwrap_or(Operator::NoOp),
                        &token,
                        composite,
                        max_char,
                        first_token,
                        substitutions,
                        &mut result,
                        i,
                    )?;
                    if expanded && first_token {
                        first_token = false;
                    }
                    to_token = false;
                    token.clear();
                    operator = None;
                    composite = false;
                    to_max_char_buffer = false;
                    max_char_buffer.clear();
                } else {
                    return Err(StdUriTemplateError::new(format!(
                        "Failed to expand token, invalid at col:{}",
                        i
                    )));
                }
            }
            ',' if to_token => {
                if to_max_char_buffer && max_char_buffer.is_empty() {
                    return Err(StdUriTemplateError::new(format!(
                        "Illegal character identified in the token at col:{}",
                        i
                    )));
                }
                let max_char = get_max_char(&max_char_buffer, i)?;
                let expanded = expand_token(
                    operator.unwrap_or(Operator::NoOp),
                    &token,
                    composite,
                    max_char,
                    first_token,
                    substitutions,
                    &mut result,
                    i,
                )?;
                if expanded && first_token {
                    first_token = false;
                }
                token.clear();
                composite = false;
                to_max_char_buffer = false;
                max_char_buffer.clear();
            }
            _ => {
                if to_token {
                    if operator.is_none() {
                        operator = Some(get_operator(character, &mut token, i)?);
                    } else if to_max_char_buffer {
                        if character.is_ascii_digit() {
                            max_char_buffer.push(character);
                        } else {
                            return Err(StdUriTemplateError::new(format!(
                                "Illegal character identified in the token at col:{}",
                                i
                            )));
                        }
                    } else {
                        match character {
                            ':' => {
                                to_max_char_buffer = true;
                                max_char_buffer.clear();
                            }
                            '*' => {
                                composite = true;
                            }
                            _ => {
                                validate_literal(character, i)?;
                                token.push(character);
                            }
                        }
                    }
                } else {
                    if (character as u32) > 0x7F {
                        let mut buf = [0u8; 4];
                        let encoded = character.encode_utf8(&mut buf);
                        for b in encoded.bytes() {
                            use std::fmt::Write;
                            write!(result, "%{:02X}", b).unwrap();
                        }
                    } else {
                        result.push(character);
                    }
                }
            }
        }
    }

    if !to_token {
        Ok(result)
    } else {
        Err(StdUriTemplateError::new("Unterminated token".to_string()))
    }
}

fn add_prefix(op: Operator, result: &mut String) {
    match op {
        Operator::Hash => result.push('#'),
        Operator::Dot => result.push('.'),
        Operator::Slash => result.push('/'),
        Operator::Semicolon => result.push(';'),
        Operator::QuestionMark => result.push('?'),
        Operator::Amp => result.push('&'),
        _ => {}
    }
}

fn add_separator(op: Operator, result: &mut String) {
    match op {
        Operator::Dot => result.push('.'),
        Operator::Slash => result.push('/'),
        Operator::Semicolon => result.push(';'),
        Operator::QuestionMark | Operator::Amp => result.push('&'),
        _ => result.push(','),
    }
}

fn add_value(op: Operator, token: &str, value: &str, result: &mut String, max_char: i32) {
    match op {
        Operator::Plus | Operator::Hash => {
            add_expanded_value(None, value, result, max_char, false);
        }
        Operator::QuestionMark | Operator::Amp => {
            result.push_str(token);
            result.push('=');
            add_expanded_value(None, value, result, max_char, true);
        }
        Operator::Semicolon => {
            result.push_str(token);
            add_expanded_value(Some("="), value, result, max_char, true);
        }
        Operator::Dot | Operator::Slash | Operator::NoOp => {
            add_expanded_value(None, value, result, max_char, true);
        }
    }
}

fn add_value_element(op: Operator, _token: &str, value: &str, result: &mut String, max_char: i32) {
    match op {
        Operator::Plus | Operator::Hash => {
            add_expanded_value(None, value, result, max_char, false);
        }
        Operator::QuestionMark
        | Operator::Amp
        | Operator::Semicolon
        | Operator::Dot
        | Operator::Slash
        | Operator::NoOp => {
            add_expanded_value(None, value, result, max_char, true);
        }
    }
}

fn is_iprivate(cp: char) -> bool {
    (0xE000..=0xF8FF).contains(&(cp as u32))
}

fn is_ucschar(cp: char) -> bool {
    let code = cp as u32;
    (0xA0..=0xD7FF).contains(&code)
        || (0xF900..=0xFDCF).contains(&code)
        || (0xFDF0..=0xFFEF).contains(&code)
}

fn is_unreserved(c: char) -> bool {
    c.is_ascii_alphanumeric() || c == '-' || c == '.' || c == '_' || c == '~'
}

fn percent_encode_char(c: char, result: &mut String) {
    let mut buf = [0u8; 4];
    let encoded = c.encode_utf8(&mut buf);
    for byte in encoded.as_bytes() {
        result.push('%');
        result.push(to_hex_digit(byte >> 4));
        result.push(to_hex_digit(byte & 0x0F));
    }
}

fn url_encode_char(c: char, result: &mut String) {
    if is_unreserved(c) {
        result.push(c);
    } else {
        percent_encode_char(c, result);
    }
}

fn to_hex_digit(nibble: u8) -> char {
    match nibble {
        0..=9 => (b'0' + nibble) as char,
        10..=15 => (b'A' + nibble - 10) as char,
        _ => unreachable!(),
    }
}

fn add_expanded_value(
    prefix: Option<&str>,
    value: &str,
    result: &mut String,
    max_char: i32,
    replace_reserved: bool,
) {
    let max = if max_char != -1 {
        max_char as usize
    } else {
        usize::MAX
    };

    let mut to_reserved = false;
    let mut reserved_buffer = String::with_capacity(3);
    let mut to_append = String::with_capacity(12);
    let mut prefix_pending = prefix;

    for character in value.chars().take(max) {
        if let Some(p) = prefix_pending.take() {
            result.push_str(p);
        }

        if character == '%' && !replace_reserved {
            to_reserved = true;
            reserved_buffer.clear();
        }

        to_append.clear();
        if replace_reserved || is_ucschar(character) || is_iprivate(character) {
            url_encode_char(character, &mut to_append);
        } else if !character.is_ascii() {
            percent_encode_char(character, &mut to_append);
        } else {
            to_append.push(character);
        }

        if to_reserved {
            reserved_buffer.push_str(&to_append);

            if reserved_buffer.len() == 3 {
                let is_encoded = is_valid_percent_encoded(&reserved_buffer);

                if is_encoded {
                    result.push_str(&reserved_buffer);
                } else {
                    result.push_str("%25");
                    result.push_str(&reserved_buffer[1..]);
                }
                to_reserved = false;
                reserved_buffer.clear();
            }
        } else if character == ' ' {
            result.push_str("%20");
        } else if character == '%' {
            result.push_str("%25");
        } else {
            result.push_str(&to_append);
        }
    }

    if to_reserved {
        result.push_str("%25");
        result.push_str(&reserved_buffer[1..]);
    }
}

fn is_valid_percent_encoded(s: &str) -> bool {
    let b = s.as_bytes();
    b.len() == 3 && b[0] == b'%' && b[1].is_ascii_hexdigit() && b[2].is_ascii_hexdigit()
}

fn get_substitution_type(
    value: Option<&Value>,
    _col: usize,
) -> Result<SubstitutionType, StdUriTemplateError> {
    match value {
        None => Ok(SubstitutionType::Empty),
        Some(v) => match v {
            Value::String(_) | Value::Bool(_) | Value::Integer(_) | Value::Float(_) => {
                Ok(SubstitutionType::String)
            }
            Value::List(_) => Ok(SubstitutionType::List),
            Value::Map(_) => Ok(SubstitutionType::Map),
        },
    }
}

fn is_empty(subst_type: SubstitutionType, value: &Value) -> bool {
    match subst_type {
        SubstitutionType::String => false,
        SubstitutionType::List => {
            if let Value::List(l) = value {
                l.is_empty()
            } else {
                true
            }
        }
        SubstitutionType::Map => {
            if let Value::Map(m) = value {
                m.is_empty()
            } else {
                true
            }
        }
        SubstitutionType::Empty => true,
    }
}

fn convert_native_types(value: &Value) -> Result<Cow<'_, str>, StdUriTemplateError> {
    match value {
        Value::String(s) => Ok(Cow::Borrowed(s)),
        Value::Bool(b) => Ok(Cow::Owned(b.to_string())),
        Value::Integer(i) => Ok(Cow::Owned(i.to_string())),
        Value::Float(f) => {
            if *f == (*f as i64) as f64 && f.is_finite() {
                Ok(Cow::Owned((*f as i64).to_string()))
            } else {
                Ok(Cow::Owned(f.to_string()))
            }
        }
        Value::List(_) | Value::Map(_) => Err(StdUriTemplateError::new(format!(
            "Illegal class passed as substitution, found {:?}",
            value
        ))),
    }
}

fn check_varname(token: &str, col: usize) -> Result<(), StdUriTemplateError> {
    if token.starts_with('.') || token.ends_with('.') {
        return Err(StdUriTemplateError::new(format!(
            "Illegal character identified in the token at col:{}",
            col
        )));
    }
    if token.contains("..") {
        return Err(StdUriTemplateError::new(format!(
            "Illegal character identified in the token at col:{}",
            col
        )));
    }
    let chars: Vec<char> = token.chars().collect();
    let len = chars.len();
    for i in 0..len {
        if chars[i] == '%' {
            if i + 2 < len && chars[i + 1].is_ascii_hexdigit() && chars[i + 2].is_ascii_hexdigit()
            {
                // valid percent-encoded sequence
            } else {
                return Err(StdUriTemplateError::new(format!(
                    "Illegal character identified in the token at col:{}",
                    col
                )));
            }
        }
    }
    Ok(())
}

#[allow(clippy::too_many_arguments)]
fn expand_token(
    operator: Operator,
    token: &str,
    composite: bool,
    max_char: i32,
    first_token: bool,
    substitutions: &HashMap<String, Value>,
    result: &mut String,
    col: usize,
) -> Result<bool, StdUriTemplateError> {
    if token.is_empty() {
        return Err(StdUriTemplateError::new(format!(
            "Found an empty token at col:{}",
            col
        )));
    }

    check_varname(token, col)?;

    let value = substitutions.get(token);
    let subst_type = get_substitution_type(value, col)?;
    if subst_type == SubstitutionType::Empty {
        return Ok(false);
    }

    let value = value.unwrap();
    if is_empty(subst_type, value) {
        return Ok(false);
    }

    if first_token {
        add_prefix(operator, result);
    } else {
        add_separator(operator, result);
    }

    match subst_type {
        SubstitutionType::String => {
            add_string_value(operator, token, value, result, max_char)?;
        }
        SubstitutionType::List => {
            add_list_value(operator, token, value, result, max_char, composite)?;
        }
        SubstitutionType::Map => {
            add_map_value(operator, token, value, result, max_char, composite)?;
        }
        SubstitutionType::Empty => {}
    }

    Ok(true)
}

fn add_string_value(
    operator: Operator,
    token: &str,
    value: &Value,
    result: &mut String,
    max_char: i32,
) -> Result<(), StdUriTemplateError> {
    let s = convert_native_types(value)?;
    add_value(operator, token, &s, result, max_char);
    Ok(())
}

fn add_list_value(
    operator: Operator,
    token: &str,
    value: &Value,
    result: &mut String,
    max_char: i32,
    composite: bool,
) -> Result<(), StdUriTemplateError> {
    if let Value::List(list) = value {
        let mut first = true;
        for v in list {
            let s = convert_native_types(v)?;
            if first {
                add_value(operator, token, &s, result, max_char);
                first = false;
            } else if composite {
                add_separator(operator, result);
                add_value(operator, token, &s, result, max_char);
            } else {
                result.push(',');
                add_value_element(operator, token, &s, result, max_char);
            }
        }
    }
    Ok(())
}

fn add_map_value(
    operator: Operator,
    token: &str,
    value: &Value,
    result: &mut String,
    max_char: i32,
    composite: bool,
) -> Result<(), StdUriTemplateError> {
    if max_char != -1 {
        return Err(StdUriTemplateError::new(
            "Value trimming is not allowed on Maps".to_string(),
        ));
    }

    if let Value::Map(map) = value {
        let mut first = true;
        for (key, val) in map {
            let v = convert_native_types(val)?;
            if composite {
                if !first {
                    add_separator(operator, result);
                }
                add_value_element(operator, token, key, result, max_char);
                result.push('=');
            } else {
                if first {
                    add_value(operator, token, key, result, max_char);
                } else {
                    result.push(',');
                    add_value_element(operator, token, key, result, max_char);
                }
                result.push(',');
            }
            add_value_element(operator, token, &v, result, max_char);
            first = false;
        }
    }

    Ok(())
}
