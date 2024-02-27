import org.junit.jupiter.api.Test;
import io.github.stduritemplate.StdUriTemplate;

import java.util.HashMap;

// Those tests are available just as a convenience to use with the debug mode in the IDE
public class StdUriTemplateTest {

    @Test
    void toDebug() {
        HashMap<String, Object> substs = new HashMap();
        HashMap<String, Object> value = new HashMap();
        value.put("semi", ";");
        value.put("dot", ".");
        value.put("comma", ",");
        substs.put("keys", value);
        String result = StdUriTemplate.expand("X{.keys}", substs);
        System.out.println(result);
        assert("X.comma,%2C,dot,.,semi,%3B".equals(result));
    }

    @Test
    void toDebug2() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("var", "value");
        String result = StdUriTemplate.expand("{/var:1,var}", substs);
        System.out.println(result);
        assert("/v/value".equals(result));
    }

    @Test
    void toDebug3() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("undef", new HashMap());
        String result = StdUriTemplate.expand("X{.undef}", substs);
        System.out.println(result);
        assert("X".equals(result));
    }

    @Test
    void toDebug4() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("half", "50%");
        String result = StdUriTemplate.expand("{half}", substs);
        System.out.println(result);
        assert("50%25".equals(result));
    }

    @Test
    void toDebug4_1() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("half", "50%");
        String result = StdUriTemplate.expand("{+half}", substs);
        System.out.println(result);
        assert("50%25".equals(result));
    }

    @Test
    void toDebug5() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("query", "PREFIX dc: <http://purl.org/dc/elements/1.1/> SELECT ?book ?who WHERE { ?book dc:creator ?who }");
        String result = StdUriTemplate.expand("/sparql{?query}", substs);
        System.out.println(result);
        assert("/sparql?query=PREFIX%20dc%3A%20%3Chttp%3A%2F%2Fpurl.org%2Fdc%2Felements%2F1.1%2F%3E%20SELECT%20%3Fbook%20%3Fwho%20WHERE%20%7B%20%3Fbook%20dc%3Acreator%20%3Fwho%20%7D".equals(result));
    }

    @Test
    void toDebug6() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("uri", "http://example.org/?uri=http%3A%2F%2Fexample.org%2F");
        String result = StdUriTemplate.expand("/go{?uri}", substs);
        System.out.println(result);
        assert( "/go?uri=http%3A%2F%2Fexample.org%2F%3Furi%3Dhttp%253A%252F%252Fexample.org%252F".equals(result));
    }

    @Test
    void toDebug7() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("uri", "%3A%2F");
        String result = StdUriTemplate.expand("/go{?uri}", substs);
        System.out.println(result);
        assert("/go?uri=%253A%252F".equals(result));
    }

    @Test
    void toDebug8() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("id", "admin");
        substs.put("token", "12345");
        substs.put("tab", "overview");
        HashMap<String, Object> keys = new HashMap<>();
        keys.put("key1", "val1");
        keys.put("key2", "val2");
        substs.put("keys", keys);
        String result = StdUriTemplate.expand("/user{/id}{?token,tab}{&keys*}", substs);
        System.out.println(result);
        assert("/user/admin?token=12345&tab=overview&key1=val1&key2=val2".equals(result));
    }

    @Test
    void toDebug9() {
        HashMap<String, Object> substs = new HashMap();
        substs.put("id", "admin");
        substs.put("token", "12345");
        substs.put("tab", "overview");
        HashMap<String, Object> keys = new HashMap<>();
        keys.put("key1", "val1");
        keys.put("key2", "val2");
        substs.put("keys", keys);
        String result = StdUriTemplate.expand("{?id,token,keys*}", substs);
        System.out.println(result);
        assert("?id=admin&token=12345&key1=val1&key2=val2".equals(result));
    }

}
