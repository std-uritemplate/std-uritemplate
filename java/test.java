///usr/bin/env jbang "$0" "$@" ; exit $?

//SOURCES src/main/java/org/uritemplate/StdUriTemplate.java
//DEPS com.fasterxml.jackson.core:jackson-databind:2.13.0

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static java.lang.System.*;

import org.uritemplate.StdUriTemplate;

public class test {

    public static void main(String... args) {
        // Here it goes the logic to parse the arguments in a positional manner, first attempt:
        // args[0] = template
        // args[1] = the dictionary to be parsed in JSON format
        // return the expanded template on stdout
        var objectReader = new ObjectMapper().readerFor(Map.class);

        try (var fis = new FileInputStream(args[1])) {
            var template = new String(Files.readAllBytes(Paths.get(args[0])));
            out.println(StdUriTemplate.expand(template, objectReader.readValue(fis)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(-1);
        } catch (Exception e) {
            e.printStackTrace();
            // * false - if the second member is boolean false, expansion is expected to fail (i.e., the template was invalid).
            out.println("false");
        }
    }
}
