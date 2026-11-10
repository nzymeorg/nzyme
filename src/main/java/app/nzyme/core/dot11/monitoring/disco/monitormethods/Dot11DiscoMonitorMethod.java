package app.nzyme.core.dot11.monitoring.disco.monitormethods;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.dot11.Dot11;
import app.nzyme.core.dot11.db.DiscoHistogramEntry;
import app.nzyme.core.dot11.db.monitoring.MonitoredBSSID;
import app.nzyme.core.dot11.db.monitoring.MonitoredSSID;
import app.nzyme.core.dot11.monitoring.disco.DiscoMonitorAnomaly;
import app.nzyme.core.taps.Tap;
import app.nzyme.core.util.Bucketing;
import app.nzyme.core.util.TimeRangeFactory;
import com.google.common.collect.Lists;
import app.nzyme.core.util.filters.Filters;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public abstract class Dot11DiscoMonitorMethod {

    protected final NzymeNode nzyme;
    protected final MonitoredSSID monitoredNetwork;

    @Nullable
    protected final Map<String, Object> customConfig;

    public Dot11DiscoMonitorMethod(NzymeNode nzyme,
                                   MonitoredSSID monitoredNetwork,
                                   @Nullable Map<String, Object> customConfig) {
        this.nzyme = nzyme;
        this.monitoredNetwork = monitoredNetwork;
        this.customConfig = customConfig;
    }

    public Map<Tap, List<DiscoMonitorAnomaly>> execute(List<Tap> taps) {
        Map<Tap, List<DiscoMonitorAnomaly>> anomalies = Maps.newHashMap();

        for (Tap tap : taps) {
            List<DiscoMonitorAnomaly> tapAnomalies = execute(tap);

            if (!tapAnomalies.isEmpty()) {
                anomalies.put(tap, tapAnomalies);
            }
        }

        return anomalies;
    }

    public List<DiscoMonitorAnomaly> execute(Tap tap) {
        List<String> bssidList = Lists.newArrayList();

        for (MonitoredBSSID monitoredBSSID : nzyme.getDot11().findMonitoredBSSIDsOfMonitoredNetwork(monitoredNetwork.id())) {
            bssidList.add(monitoredBSSID.bssid());
        }

        return calculate(
                nzyme.getDot11().getDiscoHistogram(
                        TimeRangeFactory.oneDay(),
                        Bucketing.getConfig(TimeRangeFactory.oneDay()),
                        List.of(tap.uuid()),
                        Filters.create(Maps.newHashMap()),
                        bssidList
                )
        );
    }

    protected abstract List<DiscoMonitorAnomaly> calculate(List<DiscoHistogramEntry> histogram);

}
