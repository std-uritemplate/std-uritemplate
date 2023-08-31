import Foundation

public class StdUriTemplate {

    // Public API
    public static func expand(_ template: String, substitutions: [String: Any]) throws -> String {
        return try expandImpl(template, substitutions)
    }

    // Private implementation
    private enum Modifier {
        case NO_MOD
        case PLUS
        case DASH
        case DOT
        case SLASH
        case SEMICOLON
        case QUESTION_MARK
        case AT
    }
    
    private static func validateLiteral(_ c: Character, _ col: Int) throws {
        switch c {
            case "+", "#", "/", ";", "?", "&", " ", "!", "=", "$", "|", "*", ":", "~", "-":
                throw NSError(domain: "IllegalArgumentException", code: col, userInfo: [NSLocalizedDescriptionKey: "Illegal character identified in the token at col: \(col)"])
            default:
                break
        }
    }
    
    private static func getMaxChar(_ buffer: String?, _ col: Int) throws -> Int {
        if buffer == nil {
            return -1
        } else {
            guard let value = buffer, !value.isEmpty else {
                return -1
            }
            guard let maxChar = Int(value) else {
                throw NSError(domain: "IllegalArgumentException", code: col, userInfo: [NSLocalizedDescriptionKey: "Cannot parse max chars at col: \(col)"])
            }
            return maxChar
        }
    }
    
    private static func getModifier(_ c: Character, _ token: inout String, _ col: Int) throws -> Modifier {
        switch c {
            case "+": return .PLUS
            case "#": return .DASH
            case ".": return .DOT
            case "/": return .SLASH
            case ";": return .SEMICOLON
            case "?": return .QUESTION_MARK
            case "&": return .AT
            default:
                try validateLiteral(c, col)
                token.append(c)
                return .NO_MOD
        }
    }
    
    private static func expandImpl(_ str: String, _ substitutions: [String: Any]) throws -> String {
        var result = String()
        var token: String?
        
        var modifier: Modifier?
        var composite = false
        var maxCharBuffer: String?
        var firstToken = true
        
        for (i, character) in str.enumerated() {
            switch character {
                case "{":
                    token = String()
                    firstToken = true
                case "}":
                    if let tk = token {
                        let expanded = try expandToken(modifier, tk, composite, try getMaxChar(maxCharBuffer, i), firstToken, substitutions, &result, i)
                        if expanded && firstToken {
                            firstToken = false
                        }
                        token = nil
                        modifier = nil
                        composite = false
                        maxCharBuffer = nil
                    } else {
                        throw NSError(domain: "IllegalArgumentException", code: i, userInfo: [NSLocalizedDescriptionKey: "Failed to expand token, invalid at col: \(i)"])
                    }
                case ",":
                    if let tk = token {
                        let expanded = try expandToken(modifier, tk, composite, try getMaxChar(maxCharBuffer, i), firstToken, substitutions, &result, i)
                        if expanded && firstToken {
                            firstToken = false
                        }
                        token = String()
                        composite = false
                        maxCharBuffer = nil
                    }
                    // Intentional fall-through for commas outside the {}
                default:
                    if let _ = token {
                        if modifier == nil {
                            modifier = try getModifier(character, &token!, i)
                        } else if let _ = maxCharBuffer {
                            if character.isNumber {
                                maxCharBuffer!.append(character)
                            } else {
                                throw NSError(domain: "IllegalArgumentException", code: i, userInfo: [NSLocalizedDescriptionKey: "Illegal character identified in the token at col: \(i)"])
                            }
                        } else {
                            if character == ":" {
                                maxCharBuffer = String()
                            } else if character == "*" {
                                composite = true
                            } else {
                                try validateLiteral(character, i)
                                token!.append(character)
                            }
                        }
                    } else {
                        result.append(character)
                    }
            }
        }
        
        if token == nil {
            return result
        } else {
            throw NSError(domain: "IllegalArgumentException", code: 0, userInfo: [NSLocalizedDescriptionKey: "Unterminated token"])
        }
    }
    
    private static func addPrefix(_ mod: Modifier, _ result: inout String) {
        switch mod {
            case .DASH:
                result.append("#")
            case .DOT:
                result.append(".")
            case .SLASH:
                result.append("/")
            case .SEMICOLON:
                result.append(";")
            case .QUESTION_MARK:
                result.append("?")
            case .AT:
                result.append("&")
            default:
                return
        }
    }
    
    private static func addSeparator(_ mod: Modifier, _ result: inout String) {
        switch mod {
            case .DOT:
                result.append(".")
            case .SLASH:
                result.append("/")
            case .SEMICOLON:
                result.append(";")
            case .QUESTION_MARK, .AT:
                result.append("&")
            default:
                result.append(",")
                return
        }
    }
    
    private static func addValue(_ mod: Modifier, _ token: String, _ value: String, _ result: inout String, _ maxChar: Int) {
        switch mod {
            case .PLUS, .DASH:
                addExpandedValue(value, &result, maxChar, replaceReserved: false)
            case .QUESTION_MARK, .AT:
                result.append(token + "=")
                addExpandedValue(value, &result, maxChar, replaceReserved: true)
            case .SEMICOLON:
                result.append(token)
                if !value.isEmpty {
                    result.append("=")
                }
                addExpandedValue(value, &result, maxChar, replaceReserved: true)
            case .DOT, .SLASH, .NO_MOD:
                addExpandedValue(value, &result, maxChar, replaceReserved: true)
        }
    }
    
    private static func addValueElement(_ mod: Modifier, _ token: String, _ value: String, _ result: inout String, _ maxChar: Int) {
        switch mod {
            case .PLUS, .DASH:
                addExpandedValue(value, &result, maxChar, replaceReserved: false)
            case .QUESTION_MARK, .AT, .SEMICOLON, .DOT, .SLASH, .NO_MOD:
                addExpandedValue(value, &result, maxChar, replaceReserved: true)
        }
    }

    // from https://github.com/kylef/URITemplate.swift/blob/a309673fdf86e4919a0250730e461ac533a03b3a/Sources/URITemplate.swift#L590C1-L607C8
    private static let unreserved = {
      let upperAlpha = CharacterSet(charactersIn: "A"..."Z")
      let lowerAlpha = CharacterSet(charactersIn: "a"..."z")

      let digits = CharacterSet(charactersIn: "0"..."9")
      let unreservedSymbols = CharacterSet(charactersIn: "-._~")
      return upperAlpha.union(lowerAlpha).union(digits).union(unreservedSymbols)
    }()
    
    private static func addExpandedValue(_ value: String, _ result: inout String, _ maxChar: Int, replaceReserved: Bool) {
        let max = (maxChar != -1) ? min(maxChar, value.count) : value.count
        result.reserveCapacity(max * 2)
        var reservedBuffer: String?
        
        for (i, character) in value.enumerated() {
            if (i >= max) {
                break
            }
            if character == "%" && !replaceReserved {
                reservedBuffer = String()
            }
            
            if let _ = reservedBuffer {
                reservedBuffer!.append(character)
                
                if reservedBuffer!.count == 3 {
                    var isEncoded = false
                    if let decoded = reservedBuffer!.removingPercentEncoding {
                        isEncoded = (decoded != reservedBuffer!)
                    }
                    
                    if isEncoded {
                        result.append(reservedBuffer!)
                    } else {
                        result.append("%25")
                        result.append(String(reservedBuffer![reservedBuffer!.index(after: reservedBuffer!.startIndex)...]))
                    }
                    reservedBuffer = nil
                }
            } else {
                if character == " " {
                    result.append("%20")
                } else if character == "%" {
                    result.append("%25")
                } else {
                    if replaceReserved {
                        result.append(String(character).addingPercentEncoding(withAllowedCharacters: unreserved) ?? String(character))
                    } else {
                        result.append(character)
                    }
                }
            }
        }
        
        if let reservedBuffer = reservedBuffer {
            result.append("%25")
            if replaceReserved {
                result.append((reservedBuffer as NSString).substring(from: 1).addingPercentEncoding(withAllowedCharacters: unreserved) ?? "")
            } else {
                result.append((reservedBuffer as NSString).substring(from: 1))
            }
        }
    }
    
    private static func isList(_ value: Any) -> Bool {
        return value is [Any]
    }
    
    private static func isMap(_ value: Any) -> Bool {
        return value is Dictionary<AnyHashable,Any>
    }
    
    private enum SubstitutionType {
        case STRING
        case LIST
        case MAP
    }

    private static func isNil(_ value: Any?) -> Bool {
        if value is NSNull {
            return true
        } else if case Optional<Any>.none = value {
            return true
        } else {
            return false
        }
    }
    
    private static func getSubstitutionType(_ value: Any, _ col: Int) throws -> SubstitutionType {
        if value is String || isNil(value) {
            return .STRING
        } else if isList(value) {
            return .LIST
        } else if isMap(value) {
            return .MAP
        } else {
            throw NSError(domain: "IllegalArgumentException", code: col, userInfo: [NSLocalizedDescriptionKey: "Illegal class passed as substitution, found \(type(of: value)) at col: \(col)"])
        }
    }
    
    private static func isEmpty(_ substType: SubstitutionType, _ value: Any?) -> Bool {
        if isNil(value) {
            return true
        }
        guard let value = value else {
            return true
        }
        switch substType {
            case .STRING: return false
            case .LIST: return (value as! [Any]).isEmpty
            case .MAP: return (value as! [String: Any]).isEmpty
        }
    }
    
    private static func expandToken(_ modifier: Modifier?, _ token: String, _ composite: Bool, _ maxChar: Int, _ firstToken: Bool, _ substitutions: [String: Any], _ result: inout String, _ col: Int) throws -> Bool {
        guard !token.isEmpty else {
            throw NSError(domain: "IllegalArgumentException", code: col, userInfo: [NSLocalizedDescriptionKey: "Found an empty token at col: \(col)"])
        }
        
        guard var value = substitutions[token] else {
            return false
        }
        
        if let intValue = value as? Int {
            value = String(intValue)
        } else if let longValue = value as? Int64 {
            value = String(longValue)
        } else if let floatValue = value as? Float {
            value = String(floatValue)
        } else if let doubleValue = value as? Double {
            value = String(doubleValue)
        }
        
        let substType = try getSubstitutionType(value, col)
        if isEmpty(substType, value) {
            return false
        }
        
        if firstToken {
            addPrefix(modifier ?? .NO_MOD, &result)
        } else {
            addSeparator(modifier ?? .NO_MOD, &result)
        }
        
        switch substType {
            case .STRING:
                addStringValue(modifier ?? .NO_MOD, token, value as! String, &result, maxChar)
            case .LIST:
                addListValue(modifier ?? .NO_MOD, token, value as! [String], &result, maxChar, composite)
            case .MAP:
                try addMapValue(modifier ?? .NO_MOD, token, value as! [String: String], &result, maxChar, composite)
        }
        
        return true
    }
    
    private static func addStringValue(_ modifier: Modifier, _ token: String, _ value: String, _ result: inout String, _ maxChar: Int) {
        addValue(modifier, token, value, &result, maxChar)
    }
    
    private static func addListValue(_ modifier: Modifier, _ token: String, _ value: [String], _ result: inout String, _ maxChar: Int, _ composite: Bool) {
        var first = true
        for v in value {
            if first {
                addValue(modifier, token, v, &result, maxChar)
                first = false
            } else {
                if composite {
                    addSeparator(modifier, &result)
                    addValue(modifier, token, v, &result, maxChar)
                } else {
                    result.append(",")
                    addValueElement(modifier, token, v, &result, maxChar)
                }
            }
        }
    }
    
    private static func addMapValue(_ modifier: Modifier, _ token: String, _ value: [String: String], _ result: inout String, _ maxChar: Int, _ composite: Bool) throws {
        var first = true
        if maxChar != -1 {
            throw NSError(domain: "IllegalArgumentException", code: 0, userInfo: [NSLocalizedDescriptionKey: "Value trimming is not allowed on Maps"])
        }
        // workaround to make Map ordering not random
	    // https://github.com/uri-templates/uritemplate-test/pull/58#issuecomment-1640029982
        for (k, v) in value.sorted( by: { $0.0 < $1.0 }) {
            if composite {
                if !first {
                    addSeparator(modifier, &result)
                }
                addValueElement(modifier, token, k, &result, maxChar)
                result.append("=")
            } else {
                if first {
                    addValue(modifier, token, k, &result, maxChar)
                } else {
                    result.append(",")
                    addValueElement(modifier, token, k, &result, maxChar)
                }
                result.append(",")
            }
            addValueElement(modifier, token, v, &result, maxChar)
            first = false
        }
    }
}
