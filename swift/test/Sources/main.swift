import Foundation
import stduritemplate

let arguments = CommandLine.arguments
guard arguments.count == 3 else {
    fputs("Usage: \(arguments[0]) template_file data_file\n", stderr)
    exit(1)
}

let templateFile = arguments[1]
let dataFile = arguments[2]

do {
    let dataURL = URL(fileURLWithPath: dataFile)
    let data = try Data(contentsOf: dataURL)
    let jsonObject = try JSONSerialization.jsonObject(with: data, options: [])
    guard var jsonData = jsonObject as? [String: Any] else {
        fputs("Error: Failed to parse JSON data.\n", stderr)
        exit(1)
    }

    if let date = jsonData["nativedate"] {
        jsonData.updateValue(Date(timeIntervalSince1970: (date as! Double / 1000.0)) as Any, forKey: "nativedate")
    }
    if let date = jsonData["nativedatetwo"] {
        jsonData.updateValue(Date(timeIntervalSince1970: (date as! Double / 1000.0)) as Any, forKey: "nativedatetwo")
    }
    
    let template = try String(contentsOfFile: templateFile)
    
    do {
        let result = try StdUriTemplate.expand(template, substitutions: jsonData)
        print(result)
    } catch {
        fputs("Error expanding template: \(error)\n", stderr)
        fputs("false", stdout)
    }
} catch {
    fputs("Error: \(error)\n", stderr)
    exit(1)
}
