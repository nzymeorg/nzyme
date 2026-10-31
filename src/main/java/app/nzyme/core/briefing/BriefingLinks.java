package app.nzyme.core.briefing;

import app.nzyme.core.rest.responses.briefing.BriefingLinkResponse;

import java.util.Map;
import java.util.TreeMap;

public class BriefingLinks {

    public static BriefingLinkResponse to(String target) {
        return BriefingLinkResponse.create(target, Map.of());
    }

    public static BriefingLinkResponse to(String target, String key, String value) {
        Map<String, String> params = new TreeMap<>();
        params.put(key, value);
        return BriefingLinkResponse.create(target, params);
    }

    public static BriefingLinkResponse to(String target, String k1, String v1, String k2, String v2) {
        Map<String, String> params = new TreeMap<>();
        params.put(k1, v1);
        params.put(k2, v2);
        return BriefingLinkResponse.create(target, params);
    }

    public static BriefingLinkResponse alerts(String subsystem, String type, String status) {
        Map<String, String> params = new TreeMap<>();
        if (subsystem != null) {
            params.put("subsystem", subsystem);
        }
        if (type != null) {
            params.put("type", type);
        }
        if (status != null) {
            params.put("status", status);
        }
        return BriefingLinkResponse.create("ALERTS", params);
    }

    public static BriefingLinkResponse alert(String uuid) {
        return to("ALERT", "uuid", uuid);
    }

}
