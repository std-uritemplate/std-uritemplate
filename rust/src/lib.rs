use std::collections::HashMap;
use std::any::Any;
use std::fmt::Write;
use std::str;

pub fn expand(template: &str, substitutions: &HashMap<String, Box<dyn Any>>) -> Result<String, String> {
    expand_impl(template, substitutions)
}

#[derive(Debug, Copy, Clone)]
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

// Validate Literal function
fn validate_literal(c: char, col: usize) -> Result<(), String> {
    match c {
        '+' | '#' | '/' | ';' | '?' | '&' | ' ' | '!' | '=' | '$' | '|' | '*' | ':' | '~' | '-' => {
            return Err(format!("Illegal character identified in the token at col: {}", col));
        }
        _ => Ok(()),
    }
}

// Get Max Char function
fn get_max_char(buffer: &str, col: usize) -> Result<Option<usize>, String> {
    if buffer.is_empty() {
        Ok(None)
    } else {
        buffer.parse::<usize>().map(Some).map_err(|_| format!("Cannot parse max chars at col: {}", col))
    }
}

// Get Operator function
fn get_operator(c: char, col: usize) -> Operator {
    match c {
        '+' => Operator::Plus,
        '#' => Operator::Hash,
        '.' => Operator::Dot,
        '/' => Operator::Slash,
        ';' => Operator::Semicolon,
        '?' => Operator::QuestionMark,
        '&' => Operator::Amp,
        _ => {
            if let Err(e) = validate_literal(c, col) {
                eprintln!("{}", e); // Error handling: could also return the error if needed
            }
            Operator::NoOp
        }
    }
}

pub fn expand_impl(str: &str, substitutions: &HashMap<String, Box<dyn Any>>) -> Result<String, String> {
    let mut result = String::with_capacity(str.len() * 2);
    
    let mut to_token = false;
    let mut token = String::new();
    let mut operator = None;
    let mut composite = false;
    let mut to_max_char_buffer = false;
    let mut max_char_buffer = String::with_capacity(3);
    let mut first_token = true;

    for (i, character) in str.chars().enumerate() {
        match character {
            '{' => {
                to_token = true;
                token.clear();
                first_token = true;
            }
            '}' => {
                if to_token {
                    let max_char = get_max_char(&max_char_buffer, i)?;
                    if let Some(op) = operator {
                        let expanded = expand_token(op, &token, composite, max_char, first_token, substitutions, &mut result, i);
                        if expanded && first_token {
                            first_token = false;
                        }
                    }
                    to_token = false;
                    token.clear();
                    operator = None;
                    composite = false;
                    to_max_char_buffer = false;
                    max_char_buffer.clear();
                } else {
                    return Err(format!("Failed to expand token, invalid at col: {}", i));
                }
            }
            ',' => {
                if to_token {
                    let max_char = get_max_char(&max_char_buffer, i)?;
                    if let Some(op) = operator {
                        let expanded = expand_token(op, &token, composite, max_char, first_token, substitutions, &mut result, i);
                        if expanded && first_token {
                            first_token = false;
                        }
                    }
                    token.clear();
                    composite = false;
                    to_max_char_buffer = false;
                    max_char_buffer.clear();
                }
                // Intentional fall-through for commas outside the {}
            }
            _ => {
                if to_token {
                    if operator.is_none() {
                        operator = Some(get_operator(character, i));
                        // TODO: verify
                        match operator {
                            Some(Operator::NoOp) => token.push(character),
                            _ => {}
                        }
                    } else if to_max_char_buffer {
                        if character.is_digit(10) {
                            max_char_buffer.push(character);
                        } else {
                            return Err(format!("Illegal character identified in the token at col: {}", i));
                        }
                    } else {
                        if character == ':' {
                            to_max_char_buffer = true;
                            max_char_buffer.clear();
                        } else if character == '*' {
                            composite = true;
                        } else {
                            validate_literal(character, i)?;
                            token.push(character);
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
        Err("Unterminated token".to_string())
    }
}

// Add the prefix depending on the operator
fn add_prefix(op: Operator, result: &mut String) {
    match op {
        Operator::Hash => result.push('#'),
        Operator::Dot => result.push('.'),
        Operator::Slash => result.push('/'),
        Operator::Semicolon => result.push(';'),
        Operator::QuestionMark => result.push('?'),
        Operator::Amp => result.push('&'),
        _ => return,
    }
}

// Add separator depending on the operator
fn add_separator(op: Operator, result: &mut String) {
    match op {
        Operator::Dot => result.push('.'),
        Operator::Slash => result.push('/'),
        Operator::Semicolon => result.push(';'),
        Operator::QuestionMark | Operator::Amp => result.push('&'),
        _ => result.push(','),
    }
}

// Add value depending on the operator
fn add_value(op: Operator, token: &str, value: &dyn std::any::Any, result: &mut String, max_char: Option<usize>) {
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

// Add value element depending on the operator
fn add_value_element(op: Operator, _token: &str, value: &dyn std::any::Any, result: &mut String, max_char: Option<usize>) {
    match op {
        Operator::Plus | Operator::Hash => {
            add_expanded_value(None, value, result, max_char, false);
        }
        Operator::QuestionMark | Operator::Amp | Operator::Semicolon | Operator::Dot | Operator::Slash | Operator::NoOp => {
            add_expanded_value(None, value, result, max_char, true);
        }
    }
}

// Check if character is a surrogate pair (similar to Java's surrogate check)
fn is_surrogate(cp: char) -> bool {
    // Surrogate range in UTF-16 (0xD800 to 0xDFFF)
    cp as u32 >= 0xD800 && cp as u32 <= 0xDFFF
}

// Check if character is Iprivate (private use area in Unicode)
fn is_iprivate(cp: char) -> bool {
    cp >= '\u{E000}' && cp <= '\u{F8FF}'
}

// Check if character is UCSchar (defined UCS characters)
fn is_ucschar(cp: char) -> bool {
    (cp >= '\u{A0}' && cp <= '\u{D7FF}')
        || (cp >= '\u{F900}' && cp <= '\u{FDCF}')
        || (cp >= '\u{FDF0}' && cp <= '\u{FFEF}')
}

fn try_encode_character(ch: char) -> Result<String, String> {
    // Check if the character is ASCII
    if ch.is_ascii() {
        Ok(ch.to_string())
    } else {
        // For non-ASCII characters, we will percent encode them
        let mut encoded = String::new();
        for byte in ch.to_string().as_bytes() {
            write!(&mut encoded, "%{:02X}", byte).map_err(|e| e.to_string())?;
        }
        Ok(encoded)
    }
}

fn try_url_decode(encoded: &str) -> Option<char> {
    if encoded.is_empty() {
        return None; // Return None if the string is empty
    }

    // If it's not percent-encoded, return the character as-is
    if !encoded.starts_with('%') {
        return encoded.chars().next();
    }

    let mut decoded = Vec::new();
    let mut i = 0;
    while i < encoded.len() {
        if &encoded[i..i+1] == "%" {
            // Expect two characters after '%'
            if i + 2 >= encoded.len() {
                return None; // Invalid encoding, return None
            }
            let hex = &encoded[i+1..i+3];
            match u8::from_str_radix(hex, 16) {
                Ok(byte) => decoded.push(byte),
                Err(_) => return None, // If there's a parsing error, return None
            }
            i += 3;
        } else {
            // Directly append non-encoded characters
            decoded.push(encoded[i..i+1].as_bytes()[0]);
            i += 1;
        }
    }

    // Convert the decoded bytes into a character
    match str::from_utf8(&decoded) {
        Ok(decoded_str) => decoded_str.chars().next(),
        Err(_) => None, // If UTF-8 decoding fails, return None
    }
}

fn add_expanded_value(
    prefix: Option<&str>,
    value: &dyn std::any::Any,
    result: &mut String,
    max_char: Option<usize>,
    replace_reserved: bool,
) {
    let string_value = convert_native_types(value);
    let max = match max_char {
        Some(max_char_value) => std::cmp::min(max_char_value, string_value.len()),
        None => string_value.len(),
    };

    result.reserve(max * 2); // Hint to reserve space

    let mut to_reserved = false;
    let mut reserved_buffer = String::with_capacity(3);

    if max > 0 {
        if let Some(p) = prefix {
            result.push_str(p);
        }
    }

    let mut i = 0;
    while i < max {
        let character = string_value[i..=i].chars().next().unwrap(); // Get the character
        i += character.len_utf8(); // Move the index forward by the byte length of the character

        if character == '%' && !replace_reserved {
            to_reserved = true;
            reserved_buffer.clear();
        }

        let mut to_append = character.to_string();
        if let Ok(encoded) = try_encode_character(character) {
            to_append = encoded;
        }

        if to_reserved {
            reserved_buffer.push_str(&to_append);

            if reserved_buffer.len() == 3 {
                if let Some(_is_encoded) = try_url_decode(&reserved_buffer) {
                    result.push_str(&reserved_buffer);
                } else {
                    result.push_str("%25");
                    result.push_str(&reserved_buffer[1..]);
                }
                to_reserved = false;
                reserved_buffer.clear();
            }
        } else {
            if character == ' ' {
                result.push_str("%20");
            } else if character == '%' {
                result.push_str("%25");
            } else {
                result.push_str(&to_append);
            }
        }
    }

    if to_reserved {
        result.push_str("%25");
        result.push_str(&reserved_buffer[1..]);
    }
}

#[derive(Debug, PartialEq)]
enum SubstitutionType {
    Empty,
    String,
    List,
    Map,
}

fn is_list(value: &dyn std::any::Any) -> bool {
    value.is::<Vec<String>>() // Checking concrete instance for Vec<String> (similar to ArrayList in Java)
}

fn is_map(value: &dyn std::any::Any) -> bool {
    value.is::<HashMap<String, String>>() // Checking concrete instance for HashMap<String, String>
}

fn get_substitution_type(value: &dyn std::any::Any) -> SubstitutionType {
    if value.is::<()>() {
        SubstitutionType::Empty
    } else if let Some(_) = value.downcast_ref::<String>() {
        SubstitutionType::String
    } else if is_list(value) {
        SubstitutionType::List
    } else if is_map(value) {
        SubstitutionType::Map
    } else {
        panic!("Illegal type for substitution")
    }
}

fn is_empty(subst_type: &SubstitutionType, value: &dyn std::any::Any) -> bool {
    match subst_type {
        SubstitutionType::String => false,
        SubstitutionType::List => {
            if let Some(list) = value.downcast_ref::<Vec<String>>() {
                list.is_empty()
            } else {
                true
            }
        },
        SubstitutionType::Map => {
            if let Some(map) = value.downcast_ref::<HashMap<String, String>>() {
                map.is_empty()
            } else {
                true
            }
        },
        SubstitutionType::Empty => true,
    }
}

fn is_native_type(value: &dyn std::any::Any) -> bool {
    value.is::<String>() || value.is::<bool>() || value.is::<i32>() || value.is::<i64>() || value.is::<f32>() || value.is::<f64>()
}

fn convert_native_types(value: &dyn std::any::Any) -> String {
    if let Some(v) = value.downcast_ref::<String>() {
        v.clone()
    } else if let Some(v) = value.downcast_ref::<bool>() {
        v.to_string()
    } else if let Some(v) = value.downcast_ref::<i32>() {
        v.to_string()
    } else if let Some(v) = value.downcast_ref::<i64>() {
        v.to_string()
    } else if let Some(v) = value.downcast_ref::<f32>() {
        v.to_string()
    } else if let Some(v) = value.downcast_ref::<f64>() {
        v.to_string()
    } else {
        panic!("Illegal type passed for conversion")
    }
}

fn expand_token(
    operator: Operator,
    token: &str,
    composite: bool,
    max_char: Option<usize>,
    first_token: bool,
    substitutions: &HashMap<String, Box<dyn Any>>,
    result: &mut String,
    col: usize,
) -> bool {
    if token.is_empty() {
        panic!("Found an empty token at col:{}", col);
    }

    let value = substitutions.get(token);
    let subst_type = match value {
        Some(v) => get_substitution_type(v.as_ref()),
        None => return false, // No substitution found
    };

    if subst_type == SubstitutionType::Empty || is_empty(&subst_type, value.unwrap()) {
        return false;
    }

    if first_token {
        add_prefix(operator, result);
    } else {
        add_separator(operator, result);
    }

    match subst_type {
        SubstitutionType::String => {
            add_string_value(operator, token, value.unwrap(), result, max_char);
        }
        SubstitutionType::List => {
            let list_value = value.unwrap().downcast_ref::<Vec<Box<dyn std::any::Any>>>().unwrap();
            add_list_value(operator, token, list_value, result, max_char, composite);
        }
        SubstitutionType::Map => {
            let map_value = value.unwrap().downcast_ref::<HashMap<String, Box<dyn std::any::Any>>>().unwrap();
            add_map_value(operator, token, map_value, result, max_char, composite);
        }
        SubstitutionType::Empty => {}
    }

    true
}

fn add_string_value(operator: Operator, token: &str, value: &dyn std::any::Any, result: &mut String, max_char: Option<usize>) {
    add_value(operator, token, value, result, max_char);
}

fn add_list_value(operator: Operator, token: &str, value: &Vec<Box<dyn std::any::Any>>, result: &mut String, max_char: Option<usize>, composite: bool) {
    let mut first = true;
    for v in value.iter() {
        if first {
            add_value(operator, token, v, result, max_char);
            first = false;
        } else {
            if composite {
                add_separator(operator, result);
                add_value(operator, token, v, result, max_char);
            } else {
                result.push(',');
                add_value_element(operator, token, v, result, max_char);
            }
        }
    }
}

fn add_map_value(operator: Operator, token: &str, value: &HashMap<String, Box<dyn std::any::Any>>, result: &mut String, max_char: Option<usize>, composite: bool) {
    let mut first = true;
    if max_char.is_some() {
        panic!("Value trimming is not allowed on Maps");
    }
    for (key, v) in value.iter() {
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
        add_value_element(operator, token, v, result, max_char);
        first = false;
    }
}
