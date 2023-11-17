#nullable disable
namespace Std;

using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;

public class UriTemplate
{
    // Public API
    public static string Expand(string template, Dictionary<string, object> substitutions)
    {
        return ExpandImpl(template, substitutions);
    }

    // Private implementation
    private enum Operator
    {
        NO_OP,
        PLUS,
        HASH,
        DOT,
        SLASH,
        SEMICOLON,
        QUESTION_MARK,
        AMP
    }

    private static void ValidateLiteral(char c, int col)
    {
        switch (c)
        {
            case '+':
            case '#':
            case '/':
            case ';':
            case '?':
            case '&':
            case ' ':
            case '!':
            case '=':
            case '$':
            case '|':
            case '*':
            case ':':
            case '~':
            case '-':
                throw new ArgumentException($"Illegal character identified in the token at col:{col}");
            default:
                break;
        }
    }

    private static int GetMaxChar(StringBuilder buffer, int col)
    {
        if (buffer == null || buffer.Length < 1)
        {
            return -1;
        }

        try
        {
            return int.Parse(buffer.ToString());
        }
        catch (FormatException)
        {
            throw new ArgumentException($"Cannot parse max chars at col:{col}");
        }
    }

    private static Operator GetOperator(char c, StringBuilder token, int col)
    {
        switch (c)
        {
            case '+': return Operator.PLUS;
            case '#': return Operator.HASH;
            case '.': return Operator.DOT;
            case '/': return Operator.SLASH;
            case ';': return Operator.SEMICOLON;
            case '?': return Operator.QUESTION_MARK;
            case '&': return Operator.AMP;
            default:
                ValidateLiteral(c, col);
                token.Append(c);
                return Operator.NO_OP;
        }
    }

    private static string ExpandImpl(string str, Dictionary<string, object> substitutions)
    {
        StringBuilder result = new StringBuilder(str.Length * 2);

        bool toToken = false;
        StringBuilder token = new StringBuilder();

        Operator? op = null;
        bool composite = false;
        bool toMaxCharBuffer = false;
        StringBuilder maxCharBuffer = new StringBuilder(3);
        bool firstToken = true;

        for (int i = 0; i < str.Length; i++)
        {
            char character = str[i];
            switch (character)
            {
                case '{':
                    toToken = true;
                    token.Clear();
                    firstToken = true;
                    break;
                case '}':
                    if (toToken)
                    {
                        bool expanded = ExpandToken(op, token.ToString(), composite, GetMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken)
                        {
                            firstToken = false;
                        }
                        toToken = false;
                        token.Clear();
                        op = null;
                        composite = false;
                        toMaxCharBuffer = false;
                        maxCharBuffer.Clear();
                    }
                    else
                    {
                        throw new ArgumentException($"Failed to expand token, invalid at col:{i}");
                    }
                    break;
                case ',':
                    if (toToken)
                    {
                        bool expanded = ExpandToken(op, token.ToString(), composite, GetMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken)
                        {
                            firstToken = false;
                        }
                        token.Clear();
                        composite = false;
                        toMaxCharBuffer = false;
                        maxCharBuffer.Clear();
                        break;
                    }
                    // Intentional fall-through for commas outside the {}
                    goto default;
                default:
                    if (toToken)
                    {
                        if (op == null)
                        {
                            op = GetOperator(character, token, i);
                        }
                        else if (toMaxCharBuffer)
                        {
                            if (char.IsDigit(character))
                            {
                                maxCharBuffer.Append(character);
                            }
                            else
                            {
                                throw new ArgumentException($"Illegal character identified in the token at col:{i}");
                            }
                        }
                        else
                        {
                            if (character == ':')
                            {
                                toMaxCharBuffer = true;
                                maxCharBuffer.Clear();
                            }
                            else if (character == '*')
                            {
                                composite = true;
                            }
                            else
                            {
                                ValidateLiteral(character, i);
                                token.Append(character);
                            }
                        }
                    }
                    else
                    {
                        result.Append(character);
                    }
                    break;
            }
        }

        if (!toToken)
        {
            return result.ToString();
        }
        else
        {
            throw new ArgumentException("Unterminated token");
        }
    }

    private static void AddPrefix(Operator? op, StringBuilder result)
    {
        switch (op)
        {
            case Operator.HASH:
                result.Append('#');
                break;
            case Operator.DOT:
                result.Append('.');
                break;
            case Operator.SLASH:
                result.Append('/');
                break;
            case Operator.SEMICOLON:
                result.Append(';');
                break;
            case Operator.QUESTION_MARK:
                result.Append('?');
                break;
            case Operator.AMP:
                result.Append('&');
                break;
            default:
                return;
        }
    }

    private static void AddSeparator(Operator? op, StringBuilder result)
    {
        switch (op)
        {
            case Operator.DOT:
                result.Append('.');
                break;
            case Operator.SLASH:
                result.Append('/');
                break;
            case Operator.SEMICOLON:
                result.Append(';');
                break;
            case Operator.QUESTION_MARK:
            case Operator.AMP:
                result.Append('&');
                break;
            default:
                result.Append(',');
                break;
        }
    }

    private static void AddValue(Operator? op, string token, object value, StringBuilder result, int maxChar)
    {
        switch (op)
        {
            case Operator.PLUS:
            case Operator.HASH:
                AddExpandedValue(null, value, result, maxChar, false);
                break;
            case Operator.QUESTION_MARK:
            case Operator.AMP:
                result.Append(token + '=');
                AddExpandedValue(null, value, result, maxChar, true);
                break;
            case Operator.SEMICOLON:
                result.Append(token);
                AddExpandedValue("=", value, result, maxChar, true);
                break;
            case Operator.DOT:
            case Operator.SLASH:
            case Operator.NO_OP:
                AddExpandedValue(null, value, result, maxChar, true);
                break;
        }
    }

    private static void AddValueElement(Operator? op, string token, object value, StringBuilder result, int maxChar)
    {
        switch (op)
        {
            case Operator.PLUS:
            case Operator.HASH:
                AddExpandedValue(null, value, result, maxChar, false);
                break;
            case Operator.QUESTION_MARK:
            case Operator.AMP:
            case Operator.SEMICOLON:
            case Operator.DOT:
            case Operator.SLASH:
            case Operator.NO_OP:
                AddExpandedValue(null, value, result, maxChar, true);
                break;
        }
    }

    private static void AddExpandedValue(string prefix, object value, StringBuilder result, int maxChar, bool replaceReserved)
    {
        string stringValue = convertNativeTypes(value);
        int max = (maxChar != -1) ? Math.Min(maxChar, stringValue.Length) : stringValue.Length;
        result.EnsureCapacity(max * 2); // hint to SB
        bool toReserved = false;
        StringBuilder reservedBuffer = new StringBuilder(3);

        if (max > 0 && prefix != null)
        {
            result.Append(prefix);
        }

        for (int i = 0; i < max; i++)
        {
            char character = stringValue[i];

            if (character == '%' && !replaceReserved)
            {
                toReserved = true;
                reservedBuffer.Clear();
            }

            if (toReserved)
            {
                reservedBuffer.Append(character);

                if (reservedBuffer.Length == 3)
                {
                    bool isEncoded = false;
                    try
                    {
                        var original = reservedBuffer.ToString();
                        isEncoded = !original.Equals(Uri.UnescapeDataString(original));
                    }
                    catch (Exception)
                    {
                        // ignore
                    }

                    if (isEncoded)
                    {
                        result.Append(reservedBuffer);
                    }
                    else
                    {
                        result.Append("%25");
                        // only if !replaceReserved
                        result.Append(reservedBuffer.ToString(1, 2));
                    }
                    toReserved = false;
                    reservedBuffer.Clear();
                }
            }
            else
            {
                if (character == ' ')
                {
                    result.Append("%20");
                }
                else if (character == '%')
                {
                    result.Append("%25");
                }
                else
                {
                    if (replaceReserved)
                    {
                        result.Append(Uri.EscapeDataString(character.ToString()));
                    }
                    else
                    {
                        result.Append(character);
                    }
                }
            }
        }

        if (toReserved)
        {
            result.Append("%25");
            if (replaceReserved)
            {
                result.Append(Uri.EscapeDataString(reservedBuffer.ToString(1, reservedBuffer.Length - 1)));
            }
            else
            {
                result.Append(reservedBuffer.ToString(1, reservedBuffer.Length - 1));
            }
        }
    }

    private static bool IsList(object value)
    {
        return value is IList;
    }

    private static bool IsDictionary(object value)
    {
        return value is IDictionary;
    }

    private enum SubstitutionType
    {
        EMPTY,
        STRING,
        LIST,
        DICTIONARY
    }

    private static SubstitutionType GetSubstitutionType(object value, int col)
    {
        if (value == null)
        {
            return SubstitutionType.EMPTY;
        }
        if (isNativeType(value))
        {
            return SubstitutionType.STRING;
        }
        else if (IsList(value))
        {
            return SubstitutionType.LIST;
        }
        else if (IsDictionary(value))
        {
            return SubstitutionType.DICTIONARY;
        }
        else
        {
            throw new ArgumentException($"Illegal class passed as substitution, found {value.GetType()} at col:{col}");
        }
    }

    private static bool IsEmpty(SubstitutionType substType, object value)
    {
        if (value == null)
        {
            return true;
        }
        else
        {
            switch (substType)
            {
                case SubstitutionType.STRING:
                    return value == null;
                case SubstitutionType.LIST:
                    return ((IList)value).Count == 0;
                case SubstitutionType.DICTIONARY:
                    return ((IDictionary)value).Count == 0;
                default:
                    return true;
            }
        }
    }

    private static bool isNativeType(object value)
    {
        if (value is string ||
            value is bool ||
            value is int ||
            value is long ||
            value is float ||
            value is double ||
            value is DateTime dt ||
            value is DateTimeOffset dto)
        {
            return true;
        }
        return false;
    }

    private static string convertNativeTypes(object value)
    {
        if (value is string ||
            value is bool ||
            value is int ||
            value is long ||
            value is float ||
            value is double)
        {
            return value.ToString();
        }
        else if (value is DateTime dt)
        {
            return dt.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ssZ");
        }
        else if (value is DateTimeOffset dto)
        {
            return dto.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ssZ");
        }
        throw new ArgumentException($"Illegal class passed as substitution, found {value.GetType()}");
    }

    // returns true if expansion happened
    private static bool ExpandToken(
            Operator? op,
            string token,
            bool composite,
            int maxChar,
            bool firstToken,
            Dictionary<string, object> substitutions,
            StringBuilder result,
            int col)
    {
        if (string.IsNullOrEmpty(token))
        {
            throw new ArgumentException($"Found an empty token at col:{col}");
        }

        object value;
        substitutions.TryGetValue(token, out value);
        if (value is bool ||
                value is int ||
                value is long ||
                value is float ||
                value is double)
        {
            value = value.ToString().ToLower();
        }
        else if (value is DateTime dt)
        {
            value = dt.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ssZ");
        }
        else if (value is DateTimeOffset dto)
        {
            value = dto.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ssZ");
        }

        SubstitutionType substType = GetSubstitutionType(value, col);
        if (substType == SubstitutionType.EMPTY || IsEmpty(substType, value))
        {
            return false;
        }

        if (firstToken)
        {
            AddPrefix(op, result);
        }
        else
        {
            AddSeparator(op, result);
        }

        switch (substType)
        {
            case SubstitutionType.STRING:
                AddStringValue(op, token, value, result, maxChar);
                break;
            case SubstitutionType.LIST:
                AddListValue(op, token, (IList)value, result, maxChar, composite);
                break;
            case SubstitutionType.DICTIONARY:
                AddDictionaryValue(op, token, ((IDictionary)value), result, maxChar, composite);
                break;
        }

        return true;
    }

    private static bool AddStringValue(Operator? op, string token, object value, StringBuilder result, int maxChar)
    {
        AddValue(op, token, value, result, maxChar);
        return true;
    }

    private static bool AddListValue(Operator? op, string token, IList value, StringBuilder result, int maxChar, bool composite)
    {
        bool first = true;
        foreach (object v in value)
        {
            if (first)
            {
                AddValue(op, token, v, result, maxChar);
                first = false;
            }
            else
            {
                if (composite)
                {
                    AddSeparator(op, result);
                    AddValue(op, token, v, result, maxChar);
                }
                else
                {
                    result.Append(',');
                    AddValueElement(op, token, v, result, maxChar);
                }
            }
        }
        return !first;
    }

    private static bool AddDictionaryValue(Operator? op, string token, IDictionary value, StringBuilder result, int maxChar, bool composite)
    {
        bool first = true;
        if (maxChar != -1)
        {
            throw new ArgumentException("Value trimming is not allowed on Dictionaries");
        }
        foreach (DictionaryEntry v in value)
        {
            if (composite)
            {
                if (!first)
                {
                    AddSeparator(op, result);
                }
                AddValueElement(op, token, (string)v.Key, result, maxChar);
                result.Append('=');
            }
            else
            {
                if (first)
                {
                    AddValue(op, token, (string)v.Key, result, maxChar);
                }
                else
                {
                    result.Append(',');
                    AddValueElement(op, token, (string)v.Key, result, maxChar);
                }
                result.Append(',');
            }
            AddValueElement(op, token, v.Value, result, maxChar);
            first = false;
        }
        return !first;
    }
}
