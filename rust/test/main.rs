use std::collections::HashMap;
use std::env;
use std::fs;
use std::process;

use stduritemplate::{Value, expand};

fn json_to_value(json: &serde_json::Value) -> Option<Value> {
    match json {
        serde_json::Value::Null => None,
        serde_json::Value::Bool(b) => Some(Value::Bool(*b)),
        serde_json::Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                Some(Value::Integer(i))
            } else if let Some(f) = n.as_f64() {
                Some(Value::Float(f))
            } else {
                None
            }
        }
        serde_json::Value::String(s) => Some(Value::String(s.clone())),
        serde_json::Value::Array(arr) => {
            let mut list = Vec::new();
            for item in arr {
                if let Some(v) = json_to_value(item) {
                    list.push(v);
                }
            }
            Some(Value::List(list))
        }
        serde_json::Value::Object(obj) => {
            let mut map = Vec::new();
            for (k, v) in obj {
                if let Some(val) = json_to_value(v) {
                    map.push((k.clone(), val));
                }
            }
            Some(Value::Map(map))
        }
    }
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 3 {
        eprintln!("Usage: {} <template_file> <substitutions_file>", args[0]);
        process::exit(1);
    }

    let template = match fs::read_to_string(&args[1]) {
        Ok(t) => t.trim().to_string(),
        Err(e) => {
            eprintln!("File '{}' not found: {}", args[1], e);
            process::exit(1);
        }
    };

    let json_str = match fs::read_to_string(&args[2]) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("File '{}' not found: {}", args[2], e);
            process::exit(1);
        }
    };

    let json: serde_json::Value = match serde_json::from_str(&json_str) {
        Ok(v) => v,
        Err(e) => {
            eprintln!("Failed to parse JSON: {}", e);
            process::exit(1);
        }
    };

    let mut substitutions = HashMap::new();
    if let serde_json::Value::Object(obj) = &json {
        for (k, v) in obj {
            if let Some(val) = json_to_value(v) {
                substitutions.insert(k.clone(), val);
            }
        }
    }

    match expand(&template, &substitutions) {
        Ok(result) => print!("{}", result),
        Err(e) => {
            eprintln!("Error occurred: '{}'.", e);
            print!("false");
        }
    }
}
