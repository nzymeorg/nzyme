package app.nzyme.core.taps;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class Capture {

    // The counters hold the packets dropped during the last averaging interval of the tap, not a total.
    public static final long DROPPING_THRESHOLD = 100;

    public abstract UUID uuid();
    public abstract String interfaceName();
    public abstract String captureType();
    public abstract Boolean isRunning();
    public abstract Long received();
    public abstract Long droppedBuffer();
    public abstract Long droppedInterface();
    public abstract Integer cycleTime();
    public abstract DateTime updatedAt();
    public abstract DateTime createdAt();

    public boolean isDropping() {
        return droppedBuffer() > DROPPING_THRESHOLD || droppedInterface() > DROPPING_THRESHOLD;
    }

    public static Capture create(UUID uuid, String interfaceName, String captureType, Boolean isRunning, Long received, Long droppedBuffer, Long droppedInterface, Integer cycleTime, DateTime updatedAt, DateTime createdAt) {
        return builder()
                .uuid(uuid)
                .interfaceName(interfaceName)
                .captureType(captureType)
                .isRunning(isRunning)
                .received(received)
                .droppedBuffer(droppedBuffer)
                .droppedInterface(droppedInterface)
                .cycleTime(cycleTime)
                .updatedAt(updatedAt)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_Capture.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder uuid(UUID uuid);

        public abstract Builder interfaceName(String interfaceName);

        public abstract Builder captureType(String captureType);

        public abstract Builder isRunning(Boolean isRunning);

        public abstract Builder received(Long received);

        public abstract Builder droppedBuffer(Long droppedBuffer);

        public abstract Builder droppedInterface(Long droppedInterface);

        public abstract Builder cycleTime(Integer cycleTime);

        public abstract Builder updatedAt(DateTime updatedAt);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Capture build();
    }
}
