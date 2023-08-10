using System;
using System.IO;
using System.Text;
using Newtonsoft.Json;
using stduritemplate;

namespace UriTemplateConverter
{
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
                var data = JsonConvert.DeserializeObject<Dictionary<string, object>>(jsonData);
                
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
}
