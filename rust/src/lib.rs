use std::any::Any;
use std::collections::HashMap;
use std::fmt::Write;

#[derive(Debug, Clone, Copy)]
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

#[derive(Debug)]
enum SubstitutionType {
    Empty,
    String,
    List,
    Map,
}

#[derive(Debug)]
pub enum Error {
    InvalidCharacter(char, usize),
    InvalidToken,
    InvalidMaxChars(usize),
    InvalidSubstitutionType,
}

#[derive(Debug, Clone)]
pub enum Value {
    String(String),
    List(Vec<String>),
    Map(HashMap<String, String>),
}

pub fn expand(template: &str, substitutions: &HashMap<String, Value>) -> Result<String, Error> {
    expand_impl(template, substitutions)
}

fn validate_literal(c: char, col: usize) -> Result<(), Error> {
    match c {
        '+' | '#' | '/' | ';' | '?' | '&' | ' ' | '!' | '=' | '$' | '|' | '*' | ':' | '~' | '-' => {
            Err(Error::InvalidCharacter(c, col))
        }
        _ => Ok(()),
    }
}

fn get_max_char(buffer: &str, col: usize) -> Result<usize, Error> {
    if buffer.is_empty() {
        Ok(0)
    } else {
        match buffer.parse::<usize>() {
            Ok(n) => Ok(n),
            Err(_) => {
                return Err(Error::InvalidMaxChars(col))
            }
        }
    }
}

fn get_operator(c: char, token: &mut String, col: usize) -> Operator {
    match c {
        '+' => Operator::Plus,
        '#' => Operator::Hash,
        '.' => Operator::Dot,
        '/' => Operator::Slash,
        ';' => Operator::Semicolon,
        '?' => Operator::QuestionMark,
        '&' => Operator::Amp,
        _ => {
            validate_literal(c, col).unwrap();
            token.push(c);
            Operator::NoOp
        }
    }
}

fn expand_impl(template: &str, substitutions: &HashMap<String, Value>) -> Result<String, Error> {
    let mut result = String::with_capacity(template.len() * 2);
    let mut to_token = false;
    let mut token = String::new();
    let mut operator = None;
    let mut composite = false;
    let mut to_max_char_buffer = false;
    let mut max_char_buffer = String::new();
    let mut first_token = true;

    for (i, c) in template.chars().enumerate() {
        match c {
            '{' => {
                to_token = true;
                token.clear();
                first_token = true;
            }
            '}' => {
                if to_token {
                    let expanded = expand_token(operator, &token, composite, get_max_char(&max_char_buffer, i), first_token, substitutions, &mut result, i)?;
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
                    return Err(Error::InvalidToken);
                }
            }
            ',' => {
                if to_token {
                    let expanded = expand_token(operator, &token, composite, get_max_char(&max_char_buffer, i), first_token, substitutions, &mut result, i)?;
                    if expanded && first_token {
                        first_token = false;
                    }
                    token.clear();
                    composite = false;
                    to_max_char_buffer = false;
                    max_char_buffer.clear();
                } else {
                    result.push(c);
                }
            }
            _ => {
                if to_token {
                    if operator.is_none() {
                        operator = Some(get_operator(c, &mut token, i));
                    } else if to_max_char_buffer {
                        if c.is_digit(10) {
                            max_char_buffer.push(c);
                        } else {
                            return Err(Error::InvalidCharacter(c, i));
                        }
                    } else {
                        if c == ':' {
                            to_max_char_buffer = true;
                            max_char_buffer.clear();
                        } else if c == '*' {
                            composite = true;
                        } else {
                            validate_literal(c, i)?;
                            token.push(c);
                        }
                    }
                } else {
                    result.push(c);
                }
            }
        }
    }

    if !to_token {
        Ok(result)
    } else {
        Err(Error::InvalidToken)
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

fn add_value(op: Operator, token: &str, value: &dyn Any, result: &mut String, max_char: usize) {
    match op {
        Operator::Plus | Operator::Hash => add_expanded_value(None, value, result, max_char, false),
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

fn encode_character(character: char) -> String {
    let mut encoded = String::new();
    
    // Check if the character needs to be percent-encoded
    if character.is_alphanumeric() || character == '-' || character == '.' || character == '_' || character == '~' {
        // If it's a valid character, just add it
        write!(&mut encoded, "{}", character).unwrap();
    } else {
        // Otherwise, percent-encode it
        write!(&mut encoded, "%{:02X}", character as u32).unwrap();
    }

    encoded
}

fn convert_native_types(value: &dyn Any) -> String {
    if let Some(v) = value.downcast_ref::<Value>() {
        match v {
            Value::String(s) => s.clone(),   // Extract and clone the String
            Value::List(_) => panic!("Expected String, found List"),
            Value::Map(_) => panic!("Expected String, found Map"),
        }
    } else {
        panic!("Invalid type passed");
    }
}

fn add_expanded_value(prefix: Option<&str>, value: &dyn Any, result: &mut String, max_char: usize, replace_reserved: bool) {
    let string_value = convert_native_types(value);
    let max = if max_char != 0 { max_char.min(string_value.len()) } else { string_value.len() };
    result.reserve(max * 2); // hint to String
    let mut to_reserved = false;
    let mut reserved_buffer = String::new();

    if max > 0 && prefix.is_some() {
        result.push_str(prefix.unwrap());
    }

    for (i, character) in string_value.chars().enumerate().take(max) {
        if character == '%' && !replace_reserved {
            to_reserved = true;
            reserved_buffer.clear();
        }

        let to_append = if replace_reserved || character.is_alphanumeric() {
            encode_character(character)
        } else {
            character.to_string()
        };

        if to_reserved {
            reserved_buffer.push_str(&to_append);

            if reserved_buffer.len() == 3 {
                result.push_str(&reserved_buffer);
                to_reserved = false;
                reserved_buffer.clear();
            }
        } else {
            result.push_str(&to_append);
        }
    }

    if to_reserved {
        result.push_str("%25");
        result.push_str(&reserved_buffer);
    }
}

fn expand_token(
    operator: Option<Operator>,
    token: &str,
    composite: bool,
    max_char: Result<usize, Error>,
    first_token: bool,
    substitutions: &HashMap<String, Value>,
    result: &mut String,
    col: usize,
) -> Result<bool, Error> {
    if token.is_empty() {
        return Err(Error::InvalidToken);
    }

    let value = substitutions.get(token);
    if value.is_none() { //  || value.unwrap().is_empty()
        return Ok(false);
    }

    if first_token {
        if let Some(op) = operator {
            add_prefix(op, result);
        }
    } else {
        add_separator(operator.unwrap(), result);
    }

    let value = value.unwrap();
    add_value(operator.unwrap(), token, value, result, max_char?);

    Ok(true)
}
