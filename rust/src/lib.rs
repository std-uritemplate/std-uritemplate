use std::collections::HashMap;
use std::fmt;

pub fn expand(
    template: &str,
    substitutions: &HashMap<String, Value>,
) -> Result<String, StdUriTemplateError> {
    expand_impl(template, substitutions)
}

// value type for substitutions
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

    buffer.parse::<i32>().map_err(|_| {
        StdUriTemplateError::new(format!("Cannot parse max chars at col:{}", col))
    })
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
                    result.push(character);
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

fn is_hex_digit(c: char) -> bool {
    c.is_ascii_hexdigit()
}

fn add_expanded_value(
    prefix: Option<&str>,
    value: &str,
    result: &mut String,
    max_char: i32,
    replace_reserved: bool,
) {
    let chars: Vec<char> = value.chars().collect();
    let max = if max_char != -1 {
        std::cmp::min(max_char as usize, chars.len())
    } else {
        chars.len()
    };

    let mut to_reserved = false;
    let mut reserved_buffer = String::with_capacity(3);

    if max > 0 {
        if let Some(p) = prefix {
            result.push_str(p);
        }
    }

    let mut i = 0;
    while i < max {
        let character = chars[i];

        if character == '%' && !replace_reserved {
            to_reserved = true;
            reserved_buffer.clear();
        }

        let mut to_append = String::new();
        if replace_reserved || !character.is_ascii() {
            url_encode_char(character, &mut to_append);
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
                    // only if !replace_reserved
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

        i += 1;
    }

    if to_reserved {
        result.push_str("%25");
        result.push_str(&reserved_buffer[1..]);
    }
}

fn is_valid_percent_encoded(s: &str) -> bool {
    let bytes: Vec<char> = s.chars().collect();
    if bytes.len() != 3 {
        return false;
    }
    if bytes[0] != '%' {
        return false;
    }
    is_hex_digit(bytes[1]) && is_hex_digit(bytes[2])
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

fn convert_native_types(value: &Value) -> String {
    match value {
        Value::String(s) => s.clone(),
        Value::Bool(b) => b.to_string(),
        Value::Integer(i) => i.to_string(),
        Value::Float(f) => {
            if *f == (*f as i64) as f64 && f.is_finite() {
                (*f as i64).to_string()
            } else {
                f.to_string()
            }
        }
        Value::List(_) | Value::Map(_) => String::new(),
    }
}

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
            add_string_value(operator, token, value, result, max_char);
        }
        SubstitutionType::List => {
            add_list_value(operator, token, value, result, max_char, composite);
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
) {
    let s = convert_native_types(value);
    add_value(operator, token, &s, result, max_char);
}

fn add_list_value(
    operator: Operator,
    token: &str,
    value: &Value,
    result: &mut String,
    max_char: i32,
    composite: bool,
) {
    if let Value::List(list) = value {
        let mut first = true;
        for v in list {
            let s = convert_native_types(v);
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
        let mut entries: Vec<(&String, &Value)> = map.iter().map(|(k, v)| (k, v)).collect();
        entries.sort_by(|a, b| a.0.cmp(b.0));

        let mut first = true;
        for (key, val) in entries {
            let v = convert_native_types(val);
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
