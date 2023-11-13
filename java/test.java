///usr/bin/env jbang "$0" "$@" ; exit $?

//SOURCES src/main/java/io/github/stduritemplate/StdUriTemplate.java
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.lang.System.*;

import io.github.stduritemplate.StdUriTemplate;

public class test {

    enum MyEnum {
        MyValue("MY_VALUE");

        private final String value;

        MyEnum(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }
    }

    public static void main(String... args) {
        // Here it goes the logic to parse the arguments in a positional manner, first attempt:
        // args[0] = A file that contains the template
        // args[1] = A file that contains the dictionary to be parsed in JSON format
        // return the expanded template to stdout
        // stderr can be used for debugging purposes
        var objectReader = new ObjectMapper().readerFor(Map.class);

        try (var fis = new FileInputStream(args[1])) {
            var template = new String(Files.readAllBytes(Paths.get(args[0])));

            var substs = (Map<String, Object>) objectReader.readValue(fis);
            substs.computeIfPresent("nativedate", (k, v) ->
                new Date(Long.valueOf(v.toString())));
            substs.computeIfPresent("nativedatetwo", (k, v) ->
                new Date(Long.valueOf(v.toString())).toInstant().atOffset(ZoneOffset.UTC));
            substs.computeIfPresent("nativeenum", (k, v) -> MyEnum.MyValue);
            substs.computeIfPresent("nativeenumarray", (k, v) -> List.of(MyEnum.MyValue, MyEnum.MyValue));

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
