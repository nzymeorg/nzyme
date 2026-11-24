package app.nzyme.core.detection.alerts.actions;

import com.google.common.collect.Maps;

import java.util.Map;

public record AlertAction(AlertActionType type, boolean done, Map<String, String> target) {

    public static AlertAction of(AlertActionType type, boolean done, String... targetKeysAndValues) {
        Map<String, String> target = Maps.newLinkedHashMap();
        for (int i = 0; i + 1 < targetKeysAndValues.length; i += 2) {
            if (targetKeysAndValues[i + 1] != null) {
                target.put(targetKeysAndValues[i], targetKeysAndValues[i + 1]);
            }
        }

        return new AlertAction(type, done, target);
    }

    public static AlertAction available(AlertActionType type, String... targetKeysAndValues) {
        return of(type, false, targetKeysAndValues);
    }

}
