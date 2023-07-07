import org.junit.jupiter.api.Test;
import org.uritemplate.StdUriTemplate2;

import java.util.HashMap;

public class StdUriTemplateTest {

    @Test
    void toDebug() {
        var substs = new HashMap();
        var value = new HashMap();
        value.put("semi", ";");
        value.put("dot", ".");
        value.put("comma", ",");
        substs.put("keys", value);
        var result = StdUriTemplate2.expand("X{.keys}", substs);
        System.out.println(result);
        assert("X.comma,%2C,dot,.,semi,%3B".equals(result));
    }

}
