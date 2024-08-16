pub struct UriTemplate {

    
}

fn validate_literal(c: char, col: u32) -> Result<(), String>
{
    match c {
        '+' | '#' | '/' | ';' | '?' | '&' | ' ' | '!' | '=' | '$' | '|' | '*' | ':' | '~' => Ok(()),
        '-' => return Err(format!("Invalid character '-' at column {col}")),
        _ => Ok(()),
    }
}

#[derive(Debug)]
#[derive(PartialEq)]
enum Operator {
    NoOp,
    Plus,
    Hash,
    Dot,
    Slash,
    Semicolon,
    QuestionMark,
    Amp
}

fn get_operator(c: char, mut token: String, col: u32) -> Result<Operator, String>
{
    match c {
        '+' => Ok(Operator::Plus),
        '#' => Ok(Operator::Hash),
        '.' => Ok(Operator::Dot),
        '/' => Ok(Operator::Slash),
        ';' => Ok(Operator::Semicolon),
        '?' => Ok(Operator::QuestionMark),
        '&' => Ok(Operator::Amp),
        _ => match validate_literal(c, col) {
                Ok(()) => {
                    token += &c.to_string();
                    Ok(Operator::NoOp)
                },
                Err(e) => {
                    Err(e)
                }
        },
    }
}

pub fn add(left: u64, right: u64) -> u64 {
    left + right
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_literals() {
        assert!(validate_literal('+', 1).is_ok());
        assert!(validate_literal('#', 1).is_ok());
        assert!(validate_literal('/', 1).is_ok());
        assert!(validate_literal(';', 1).is_ok());
        assert!(validate_literal('?', 1).is_ok());
        assert!(validate_literal('&', 1).is_ok());
        assert!(validate_literal(' ', 1).is_ok());
        assert!(validate_literal('!', 1).is_ok());
        assert!(validate_literal('=', 1).is_ok());
        assert!(validate_literal('$', 1).is_ok());
        assert!(validate_literal('|', 1).is_ok());
        assert!(validate_literal('*', 1).is_ok());
        assert!(validate_literal(':', 1).is_ok());
        assert!(validate_literal('~', 1).is_ok());
        assert!(validate_literal('-', 1).is_err());
    }
    #[test]
    fn gets_operator() {
        assert_eq!(get_operator('+', String::from(""), 1).unwrap(), Operator::Plus);
        assert_eq!(get_operator('#', String::from(""), 1).unwrap(), Operator::Hash);
        assert_eq!(get_operator('.', String::from(""), 1).unwrap(), Operator::Dot);
        assert_eq!(get_operator('/', String::from(""), 1).unwrap(), Operator::Slash);
        assert_eq!(get_operator(';', String::from(""), 1).unwrap(), Operator::Semicolon);
        assert_eq!(get_operator('?', String::from(""), 1).unwrap(), Operator::QuestionMark);
        assert_eq!(get_operator('&', String::from(""), 1).unwrap(), Operator::Amp);
        assert_eq!(get_operator(' ', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('!', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('=', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('$', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('|', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('*', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator(':', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('~', String::from(""), 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('-', String::from(""), 1).unwrap_err(), "Invalid character '-' at column 1");
    }
}
