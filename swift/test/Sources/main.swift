import Foundation

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
    guard let jsonData = jsonObject as? [String: Any] else {
        fputs("Error: Failed to parse JSON data.\n", stderr)
        exit(1)
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
