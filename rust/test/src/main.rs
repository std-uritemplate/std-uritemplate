use std::env;
use std::fs;
use std::process;
use serde_json::Value as JsonValue;
use std::collections::HashMap;
use std::any::Any;

// fn convert_data(json_data: &HashMap<String, JsonValue>) -> HashMap<String, UriValue> {
//     json_data.iter()
//         .filter_map(|(key, value)| convert_value(value).map(|v| (key.clone(), v)))
//         .collect()
// }

// fn convert_value(json_value: &JsonValue) -> Option<UriValue> {
//     match json_value {
//         JsonValue::String(s) => Some(UriValue::String(s.clone())),
//         JsonValue::Array(arr) => {
//             let strings: Vec<String> = arr.iter()
//                 .filter_map(|v| v.as_str().map(String::from))
//                 .collect();
//             if strings.len() == arr.len() {
//                 Some(UriValue::List(strings))
//             } else {
//                 None // Return None if not all elements are strings
//             }
//         }
//         JsonValue::Object(map) => {
//             let mut converted_map = HashMap::new();
//             for (key, value) in map {
//                 if let Some(s) = value.as_str() {
//                     converted_map.insert(key.clone(), s.to_string());
//                 } else {
//                     return None; // Return None if a value is not a string
//                 }
//             }
//             Some(UriValue::Map(converted_map))
//         }
//         _ => None, // Other types are not supported
//     }
// }

fn convert_data(json_data: &HashMap<String, JsonValue>) -> HashMap<String, Box<dyn Any>> {
    json_data.iter()
        .filter_map(|(key, value)| convert_value(value).map(|v| (key.clone(), v)))
        .collect()
}

fn convert_value(json_value: &JsonValue) -> Option<Box<dyn Any>> {
    match json_value {
        JsonValue::String(s) => Some(Box::new(s.clone())),
        // JsonValue::Array(arr) => {
        //     let strings: Vec<Box<dyn Any>> = arr.iter()
        //         .filter_map(|v| Box::new(v.as_str() as Box<dyn Any>))
        //         .collect();
        //     if strings.len() == arr.len() {
        //         Some(strings as Box<dyn Any>)
        //     } else {
        //         None // Return None if not all elements are strings
        //     }
        // }
        // JsonValue::Object(map) => {
        //     let mut converted_map = HashMap::new();
        //     for (key, value) in map {
        //         if let Some(s) = value.as_str() {
        //             converted_map.insert(key.clone(), s.to_string());
        //         } else {
        //             return None; // Return None if a value is not a string
        //         }
        //     }
        //     Some(UriValue::Map(converted_map))
        // }
        _ => None, // Other types are not supported
    }
}

fn json_to_any_map(json_map: HashMap<String, JsonValue>) -> HashMap<String, Box<dyn Any>> {
    json_map
        .into_iter()
        .map(|(key, value)| (key, Box::new(value) as Box<dyn Any>))
        .collect()
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() != 3 {
        eprintln!("Usage: {} <template_file> <data_file>", args[0]);
        process::exit(1);
    }
    
    let template_file = &args[1];
    let data_file = &args[2];
    
    let data_content = match fs::read_to_string(data_file) {
        Ok(content) => content,
        Err(_) => {
            eprintln!("File '{}' not found.", data_file);
            process::exit(1);
        }
    };
    
    let json_data: HashMap<String, JsonValue> = match serde_json::from_str(&data_content) {
        Ok(json) => json,
        Err(e) => {
            eprintln!("Error parsing JSON data: {}", e);
            process::exit(1);
        }
    };
    
    let template_content = match fs::read_to_string(template_file) {
        Ok(content) => content.trim().to_string(),
        Err(_) => {
            eprintln!("File '{}' not found.", template_file);
            process::exit(1);
        }
    };

    let data: HashMap<String, Box<dyn Any>> = json_to_any_map(json_data);

    let result = match stduritemplate::expand(&template_content, &data) {
        Ok(expanded) => expanded.to_string(),
        Err(e) => {
            eprintln!("Error expanding template: {:?}", e);
            println!("false");
            process::exit(1);
        }
    };

    println!("{}", result);
    
    // let result = match stduritemplate::expand(&template_content, &data) {
    //     Ok(expanded) => expanded.to_string(),
    //     Err(e) => {
    //         eprintln!("Error expanding template: {:?}", e);
    //         println!("false");
    //         process::exit(1);
    //     }
    // };
    
    
}

