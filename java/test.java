///usr/bin/env jbang "$0" "$@" ; exit $?

//SOURCES src/main/java/io/github/stduritemplate/StdUriTemplate.java
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static java.lang.System.*;

import io.github.stduritemplate.StdUriTemplate;

public class test {

    public static void main(String... args) {
        // Here it goes the logic to parse the arguments in a positional manner, first attempt:
        // args[0] = A file that contains the template
        // args[1] = A file that contains the dictionary to be parsed in JSON format
        // return the expanded template to stdout
        // stderr can be used for debugging purposes
        ObjectReader objectReader = new ObjectMapper().readerFor(Map.class);

        try (FileInputStream fis = new FileInputStream(args[1])) {
            String template = new String(Files.readAllBytes(Paths.get(args[0])));

            Map<String, Object> substs = (Map<String, Object>) objectReader.readValue(fis);
            substs.computeIfPresent("nativedate", (k, v) ->
                new Date(Long.valueOf(v.toString())));
            substs.computeIfPresent("nativedatetwo", (k, v) ->
                new Date(Long.valueOf(v.toString())).toInstant().atOffset(ZoneOffset.UTC));
            substs.computeIfPresent("uuid", (k, v) -> UUID.fromString(v.toString()));

            out.println(StdUriTemplate.expand(template, substs));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            // this is an error in the testing infrastructure
            System.exit(-1);
        } catch (Exception e) {
            e.printStackTrace();
            // * false - if the second member is boolean false, expansion is expected to fail (i.e., the template was invalid).
            out.println("false");
        }
    }
}
