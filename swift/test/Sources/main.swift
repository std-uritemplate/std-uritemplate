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
        fputs("Converting to Date\n", stderr)
        let RFC3339DateFormatter = DateFormatter()
        RFC3339DateFormatter.locale = Locale(identifier: "en_US_POSIX")
        RFC3339DateFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ssZZZZZ"
        RFC3339DateFormatter.timeZone = TimeZone(secondsFromGMT: 0)
        jsonData.updateValue(RFC3339DateFormatter.date(from: String(describing: date))! as Any, forKey: "nativedate")
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
