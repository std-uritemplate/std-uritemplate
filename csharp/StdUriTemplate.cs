#nullable disable
namespace stduritemplate;

using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;

public class StdUriTemplate
{
    // Public API
    public static string Expand(string template, Dictionary<string, object> substitutions)
    {
        return ExpandImpl(template, substitutions);
    }

    // Private implementation
    private enum Modifier
    {
        NO_MOD,
        PLUS,
        DASH,
        DOT,
        SLASH,
        SEMICOLON,
        QUESTION_MARK,
        AT
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

    private static Modifier GetModifier(char c, StringBuilder token, int col)
    {
        switch (c)
        {
            case '+': return Modifier.PLUS;
            case '#': return Modifier.DASH;
            case '.': return Modifier.DOT;
            case '/': return Modifier.SLASH;
            case ';': return Modifier.SEMICOLON;
            case '?': return Modifier.QUESTION_MARK;
            case '&': return Modifier.AT;
            default:
                ValidateLiteral(c, col);
                token.Append(c);
                return Modifier.NO_MOD;
        }
    }

    private static string ExpandImpl(string str, Dictionary<string, object> substitutions)
    {
        StringBuilder result = new StringBuilder(str.Length * 2);

        StringBuilder token = null;

        Modifier? modifier = null;
        bool composite = false;
        StringBuilder maxCharBuffer = null;
        bool firstToken = true;

        for (int i = 0; i < str.Length; i++)
        {
            char character = str[i];
            switch (character)
            {
                case '{':
                    token = new StringBuilder();
                    firstToken = true;
                    break;
                case '}':
                    if (token != null)
                    {
                        bool expanded = ExpandToken(modifier, token.ToString(), composite, GetMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken)
                        {
                            firstToken = false;
                        }
                        token = null;
                        modifier = null;
                        composite = false;
                        maxCharBuffer = null;
                    }
                    else
                    {
                        throw new ArgumentException($"Failed to expand token, invalid at col:{i}");
                    }
                    break;
                case ',':
                    if (token != null)
                    {
                        bool expanded = ExpandToken(modifier, token.ToString(), composite, GetMaxChar(maxCharBuffer, i), firstToken, substitutions, result, i);
                        if (expanded && firstToken)
                        {
                            firstToken = false;
                        }
                        token = new StringBuilder(token.Length * 2);
                        composite = false;
                        maxCharBuffer = null;
                        break;
                    }
                    // Intentional fall-through for commas outside the {}
                    goto default;
                default:
                    if (token != null)
                    {
                        if (modifier == null)
                        {
                            modifier = GetModifier(character, token, i);
                        }
                        else if (maxCharBuffer != null)
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
                                maxCharBuffer = new StringBuilder(3);
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

        if (token == null)
        {
            return result.ToString();
        }
        else
        {
            throw new ArgumentException("Unterminated token");
        }
    }

    private static void AddPrefix(Modifier? mod, StringBuilder result)
    {
        switch (mod)
        {
            case Modifier.DASH:
                result.Append('#');
                break;
            case Modifier.DOT:
                result.Append('.');
                break;
            case Modifier.SLASH:
                result.Append('/');
                break;
            case Modifier.SEMICOLON:
                result.Append(';');
                break;
            case Modifier.QUESTION_MARK:
                result.Append('?');
                break;
            case Modifier.AT:
                result.Append('&');
                break;
            default:
                return;
        }
    }

    private static void AddSeparator(Modifier? mod, StringBuilder result)
    {
        switch (mod)
        {
            case Modifier.DOT:
                result.Append('.');
                break;
            case Modifier.SLASH:
                result.Append('/');
                break;
            case Modifier.SEMICOLON:
                result.Append(';');
                break;
            case Modifier.QUESTION_MARK:
            case Modifier.AT:
                result.Append('&');
                break;
            default:
                result.Append(',');
                break;
        }
    }

    private static void AddValue(Modifier? mod, string token, string value, StringBuilder result, int maxChar)
    {
        switch (mod)
        {
            case Modifier.PLUS:
            case Modifier.DASH:
                AddExpandedValue(value, result, maxChar, false);
                break;
            case Modifier.QUESTION_MARK:
            case Modifier.AT:
                result.Append(token + '=');
                AddExpandedValue(value, result, maxChar, true);
                break;
            case Modifier.SEMICOLON:
                result.Append(token);
                if (!string.IsNullOrEmpty(value))
                {
                    result.Append('=');
                }
                AddExpandedValue(value, result, maxChar, true);
                break;
            case Modifier.DOT:
            case Modifier.SLASH:
            case Modifier.NO_MOD:
                AddExpandedValue(value, result, maxChar, true);
                break;
        }
    }

    private static void AddValueElement(Modifier? mod, string token, string value, StringBuilder result, int maxChar)
    {
        switch (mod)
        {
            case Modifier.PLUS:
            case Modifier.DASH:
                AddExpandedValue(value, result, maxChar, false);
                break;
            case Modifier.QUESTION_MARK:
            case Modifier.AT:
            case Modifier.SEMICOLON:
            case Modifier.DOT:
            case Modifier.SLASH:
            case Modifier.NO_MOD:
                AddExpandedValue(value, result, maxChar, true);
                break;
        }
    }

    private static void AddExpandedValue(string value, StringBuilder result, int maxChar, bool replaceReserved)
    {
        int max = (maxChar != -1) ? Math.Min(maxChar, value.Length) : value.Length;
        result.EnsureCapacity(max * 2); // hint to SB
        StringBuilder reservedBuffer = null;

        for (int i = 0; i < max; i++)
        {
            char character = value[i];

            if (character == '%' && !replaceReserved)
            {
                reservedBuffer = new StringBuilder(3);
            }

            if (reservedBuffer != null)
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
                    reservedBuffer = null;
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

        if (reservedBuffer != null)
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
        STRING,
        LIST,
        DICTIONARY
    }

    private static SubstitutionType GetSubstitutionType(object value, int col)
    {
        if (value is string || value == null)
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

    // returns true if expansion happened
    private static bool ExpandToken(
            Modifier? modifier,
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
        } else if (value is DateTime dt)
        {
            value = dt.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ssK");
        }

        SubstitutionType substType = GetSubstitutionType(value, col);
        if (IsEmpty(substType, value))
        {
            return false;
        }

        if (firstToken)
        {
            AddPrefix(modifier, result);
        }
        else
        {
            AddSeparator(modifier, result);
        }

        switch (substType)
        {
            case SubstitutionType.STRING:
                AddStringValue(modifier, token, (string)value, result, maxChar);
                break;
            case SubstitutionType.LIST:
                AddListValue(modifier, token, (IList)value, result, maxChar, composite);
                break;
            case SubstitutionType.DICTIONARY:
                AddDictionaryValue(modifier, token, ((IDictionary)value), result, maxChar, composite);
                break;
        }

        return true;
    }

    private static bool AddStringValue(Modifier? modifier, string token, string value, StringBuilder result, int maxChar)
    {
        AddValue(modifier, token, value, result, maxChar);
        return true;
    }

    private static bool AddListValue(Modifier? modifier, string token, IList value, StringBuilder result, int maxChar, bool composite)
    {
        bool first = true;
        foreach (string v in value)
        {
            if (first)
            {
                AddValue(modifier, token, v, result, maxChar);
                first = false;
            }
            else
            {
                if (composite)
                {
                    AddSeparator(modifier, result);
                    AddValue(modifier, token, v, result, maxChar);
                }
                else
                {
                    result.Append(',');
                    AddValueElement(modifier, token, v, result, maxChar);
                }
            }
        }
        return !first;
    }

    private static bool AddDictionaryValue(Modifier? modifier, string token, IDictionary value, StringBuilder result, int maxChar, bool composite)
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
                    AddSeparator(modifier, result);
                }
                AddValueElement(modifier, token, (string)v.Key, result, maxChar);
                result.Append('=');
            }
            else
            {
                if (first)
                {
                    AddValue(modifier, token, (string)v.Key, result, maxChar);
                }
                else
                {
                    result.Append(',');
                    AddValueElement(modifier, token, (string)v.Key, result, maxChar);
                }
                result.Append(',');
            }
            AddValueElement(modifier, token, (string)v.Value, result, maxChar);
            first = false;
        }
        return !first;
    }
}
