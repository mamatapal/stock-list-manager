package app.stocklist.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void writesFlatMapsWithEscaping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Ravi \"R\" Stores\n");
        m.put("count", 3);
        m.put("ok", true);
        m.put("codes", List.of("A", "B"));
        m.put("none", null);
        assertEquals("{\"name\":\"Ravi \\\"R\\\" Stores\\n\",\"count\":3,\"ok\":true,\"codes\":[\"A\",\"B\"],\"none\":null}",
                Json.of(m));
        assertNull(Json.of(Map.of()));
    }
}
