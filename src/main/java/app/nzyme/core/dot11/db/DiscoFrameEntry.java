package app.nzyme.core.dot11.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.UUID;

@AutoValue
public abstract class DiscoFrameEntry {

    public abstract long id();
    public abstract UUID tapUuid();
    public abstract String discoType();
    public abstract String transmitter();
    public abstract String receiver();
    public abstract String bssid();
    @Nullable
    public abstract Integer reasonCode();
    public abstract boolean isProtected();
    @Nullable
    public abstract Integer frequency();
    @Nullable
    public abstract Integer signalStrengthMin();
    @Nullable
    public abstract Integer signalStrengthMax();
    @Nullable
    public abstract Float signalStrengthAverage();
    public abstract long frameCount();
    public abstract long retryFrames();
    public abstract int pairSequenceNumbers();
    public abstract DateTime createdAt();

    public static DiscoFrameEntry create(long id, UUID tapUuid, String discoType, String transmitter, String receiver, String bssid, Integer reasonCode, boolean isProtected, Integer frequency, Integer signalStrengthMin, Integer signalStrengthMax, Float signalStrengthAverage, long frameCount, long retryFrames, int pairSequenceNumbers, DateTime createdAt) {
        return builder()
                .id(id)
                .tapUuid(tapUuid)
                .discoType(discoType)
                .transmitter(transmitter)
                .receiver(receiver)
                .bssid(bssid)
                .reasonCode(reasonCode)
                .isProtected(isProtected)
                .frequency(frequency)
                .signalStrengthMin(signalStrengthMin)
                .signalStrengthMax(signalStrengthMax)
                .signalStrengthAverage(signalStrengthAverage)
                .frameCount(frameCount)
                .retryFrames(retryFrames)
                .pairSequenceNumbers(pairSequenceNumbers)
                .createdAt(createdAt)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DiscoFrameEntry.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(long id);

        public abstract Builder tapUuid(UUID tapUuid);

        public abstract Builder discoType(String discoType);

        public abstract Builder transmitter(String transmitter);

        public abstract Builder receiver(String receiver);

        public abstract Builder bssid(String bssid);

        public abstract Builder reasonCode(Integer reasonCode);

        public abstract Builder isProtected(boolean isProtected);

        public abstract Builder frequency(Integer frequency);

        public abstract Builder signalStrengthMin(Integer signalStrengthMin);

        public abstract Builder signalStrengthMax(Integer signalStrengthMax);

        public abstract Builder signalStrengthAverage(Float signalStrengthAverage);

        public abstract Builder frameCount(long frameCount);

        public abstract Builder retryFrames(long retryFrames);

        public abstract Builder pairSequenceNumbers(int pairSequenceNumbers);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract DiscoFrameEntry build();
    }
}
