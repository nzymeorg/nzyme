package app.nzyme.core.ethernet.segmentation;

import org.joda.time.DateTime;

import java.util.UUID;

public record Flow(UUID tap,
                   DateTime bucket,
                   Endpoint source,
                   Endpoint destination,
                   String sourceKey,
                   String destinationKey,
                   String l4Type,
                   int port,
                   // Every protocol the taps detected, joined by a slash. Empty if none.
                   String protocol,
                   Outcome outcome,
                   long sessions,
                   // Sessions that started earlier and were still open in the hour.
                   long continued,
                   DateTime firstSeen,
                   DateTime lastSeen) {

    // The group of a side in the crossing rollup: the ID of its segment, or one of these.
    public static final long GROUP_UNASSIGNED = 0;
    public static final long GROUP_INTERNET = -1;
    public static final long GROUP_BROADCAST = -2;

    public static long group(Endpoint endpoint) {
        if (endpoint.broadcast()) {
            return GROUP_BROADCAST;
        }

        if (endpoint.segment() != null) {
            return endpoint.segment().id();
        }

        return endpoint.internal() ? GROUP_UNASSIGNED : GROUP_INTERNET;
    }

    public long sourceGroup() {
        return group(source);
    }

    public long destinationGroup() {
        return group(destination);
    }

    public Flow withSessions(long sessions, long continued, DateTime firstSeen, DateTime lastSeen) {
        return new Flow(tap, bucket, source, destination, sourceKey, destinationKey, l4Type, port, protocol,
                outcome, sessions, continued, firstSeen, lastSeen);
    }

}
