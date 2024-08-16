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

fn get_operator(c: char, token: &mut String, col: u32) -> Result<Operator, String>
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
                    token.push(c);
                    Ok(Operator::NoOp)
                },
                Err(e) => {
                    Err(e)
                }
        },
    }
}

fn add_prefix(operator: Operator, token: &mut String)
{    
    match operator {
        Operator::Hash => token.push('#'),
        Operator::Dot => token.push('.'),
        Operator::Slash => token.push('/'),
        Operator::Semicolon => token.push(';'),
        Operator::QuestionMark => token.push('?'),
        Operator::Amp => token.push('&'),
        _ => (),
    }
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
        let mut token = "".to_string();
        assert_eq!(get_operator('+', &mut token, 1).unwrap(), Operator::Plus);
        assert_eq!(get_operator('#', &mut token, 1).unwrap(), Operator::Hash);
        assert_eq!(get_operator('.', &mut token, 1).unwrap(), Operator::Dot);
        assert_eq!(get_operator('/', &mut token, 1).unwrap(), Operator::Slash);
        assert_eq!(get_operator(';', &mut token, 1).unwrap(), Operator::Semicolon);
        assert_eq!(get_operator('?', &mut token, 1).unwrap(), Operator::QuestionMark);
        assert_eq!(get_operator('&', &mut token, 1).unwrap(), Operator::Amp);
        assert_eq!(get_operator(' ', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('!', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('=', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('$', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('|', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('*', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator(':', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('~', &mut token, 1).unwrap(), Operator::NoOp);
        assert_eq!(get_operator('-', &mut token, 1).unwrap_err(), "Invalid character '-' at column 1");
    }
    #[test]
    fn adds_prefix() {
        let mut token = String::from("");
        add_prefix(Operator::Hash, &mut token);
        assert_eq!(token, "#");
        token = String::from("");
        add_prefix(Operator::Dot, &mut token);
        assert_eq!(token, ".");
        token = String::from("");
        add_prefix(Operator::Slash, &mut token);
        assert_eq!(token, "/");
        token = String::from("");
        add_prefix(Operator::Semicolon, &mut token);
        assert_eq!(token, ";");
        token = String::from("");
        add_prefix(Operator::QuestionMark, &mut token);
        assert_eq!(token, "?");
        token = String::from("");
        add_prefix(Operator::Amp, &mut token);
        assert_eq!(token, "&");
    }
}
