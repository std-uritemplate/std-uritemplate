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
}
