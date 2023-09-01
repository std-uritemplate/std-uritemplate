#nullable disable
namespace test;

using System;
using System.IO;
using System.Xml;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using stduritemplate;

class Program
{
    static void Main(string[] args)
    {
        if (args.Length < 2)
        {
            Console.WriteLine("Usage: UriTemplateConverter <template_file> <data_file>");
            return;
        }

        string templateFile = args[0];
        string dataFile = args[1];

        try
        {
            string jsonData = File.ReadAllText(dataFile);
            var data = JsonConvert.DeserializeObject<Dictionary<string, object>>(jsonData, new DictionaryConverter());

            if (data.ContainsKey("nativedate"))
            {
                data["nativedate"] = new DateTime(1970, 1, 1).AddMilliseconds((long)data["nativedate"]);
            }

            string template = File.ReadAllText(templateFile).Trim();

            try
            {
                string result = StdUriTemplate.Expand(template, data);
                Console.Write(result);
            }
            catch (Exception e)
            {
                Console.Error.WriteLine($"Error expanding template: {e.Message}");
                Console.Error.WriteLine(e.StackTrace);
                Console.WriteLine("false");
            }
        }
        catch (FileNotFoundException)
        {
            Console.Error.WriteLine($"File not found: {dataFile}");
        }
        catch (JsonException e)
        {
            Console.Error.WriteLine($"Error parsing JSON data: {e.Message}");
        }
    }
}

// Converting to native C# types
// from: https://stackoverflow.com/a/38029052
public class DictionaryConverter : JsonConverter
{
    public override void WriteJson(JsonWriter writer, object value, JsonSerializer serializer) { this.WriteValue(writer, value); }

    private void WriteValue(JsonWriter writer, object value)
    {
        var t = JToken.FromObject(value);
        switch (t.Type)
        {
            case JTokenType.Object:
                this.WriteObject(writer, value);
                break;
            case JTokenType.Array:
                this.WriteArray(writer, value);
                break;
            default:
                writer.WriteValue(value);
                break;
        }
    }

    private void WriteObject(JsonWriter writer, object value)
    {
        writer.WriteStartObject();
        var obj = value as IDictionary<string, object>;
        foreach (var kvp in obj)
        {
            writer.WritePropertyName(kvp.Key);
            this.WriteValue(writer, kvp.Value);
        }
        writer.WriteEndObject();
    }

    private void WriteArray(JsonWriter writer, object value)
    {
        writer.WriteStartArray();
        var array = value as IEnumerable<object>;
        foreach (var o in array)
        {
            this.WriteValue(writer, o);
        }
        writer.WriteEndArray();
    }

    public override object ReadJson(JsonReader reader, Type objectType, object existingValue, JsonSerializer serializer)
    {
        return ReadValue(reader);
    }

    private object ReadValue(JsonReader reader)
    {
        while (reader.TokenType == JsonToken.Comment)
        {
            if (!reader.Read()) throw new JsonSerializationException("Unexpected Token when converting IDictionary<string, object>");
        }

        switch (reader.TokenType)
        {
            case JsonToken.StartObject:
                return ReadObject(reader);
            case JsonToken.StartArray:
                return this.ReadArray(reader);
            case JsonToken.Integer:
            case JsonToken.Float:
            case JsonToken.String:
            case JsonToken.Boolean:
            case JsonToken.Undefined:
            case JsonToken.Null:
            case JsonToken.Date:
            case JsonToken.Bytes:
                return reader.Value;
            default:
                throw new JsonSerializationException
                    (string.Format("Unexpected token when converting IDictionary<string, object>: {0}", reader.TokenType));
        }
    }

    private object ReadArray(JsonReader reader)
    {
        IList<object> list = new List<object>();

        while (reader.Read())
        {
            switch (reader.TokenType)
            {
                case JsonToken.Comment:
                    break;
                default:
                    var v = ReadValue(reader);

                    list.Add(v);
                    break;
                case JsonToken.EndArray:
                    return list;
            }
        }

        throw new JsonSerializationException("Unexpected end when reading IDictionary<string, object>");
    }

    private object ReadObject(JsonReader reader)
    {
        var obj = new Dictionary<string, object>();

        while (reader.Read())
        {
            switch (reader.TokenType)
            {
                case JsonToken.PropertyName:
                    var propertyName = reader.Value.ToString();

                    if (!reader.Read())
                    {
                        throw new JsonSerializationException("Unexpected end when reading IDictionary<string, object>");
                    }

                    var v = ReadValue(reader);

                    obj[propertyName] = v;
                    break;
                case JsonToken.Comment:
                    break;
                case JsonToken.EndObject:
                    return obj;
            }
        }

        throw new JsonSerializationException("Unexpected end when reading IDictionary<string, object>");
    }

    public override bool CanConvert(Type objectType) { return typeof(IDictionary<string, object>).IsAssignableFrom(objectType); }
}
