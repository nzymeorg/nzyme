package app.nzyme.core.dot11.trilateration;

import app.nzyme.core.util.ImageDimensions;
import app.nzyme.core.floorplans.db.TenantLocationEntry;
import app.nzyme.core.floorplans.db.TenantLocationFloorEntry;
import app.nzyme.core.rest.responses.floorplans.TapPositionResponse;
import app.nzyme.core.taps.Tap;
import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.Response;

import java.util.List;

@AutoValue
public abstract class FloorSelectionResult {

    @Nullable
    public abstract Response errorResponse();

    @Nullable
    public abstract List<Tap> taps();
    @Nullable
    public abstract List<TapPositionResponse> tapPositions();
    @Nullable
    public abstract Long locationFloorCount();
    @Nullable
    public abstract Long locationTapCount();
    @Nullable
    public abstract TenantLocationEntry location();
    @Nullable
    public abstract TenantLocationFloorEntry floor();
    @Nullable
    public abstract ImageDimensions.Dimensions floorPlanDimensions();

    public static FloorSelectionResult create(Response errorResponse, List<Tap> taps, List<TapPositionResponse> tapPositions, Long locationFloorCount, Long locationTapCount, TenantLocationEntry location, TenantLocationFloorEntry floor, ImageDimensions.Dimensions floorPlanDimensions) {
        return builder()
                .errorResponse(errorResponse)
                .taps(taps)
                .tapPositions(tapPositions)
                .locationFloorCount(locationFloorCount)
                .locationTapCount(locationTapCount)
                .location(location)
                .floor(floor)
                .floorPlanDimensions(floorPlanDimensions)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_FloorSelectionResult.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder errorResponse(Response errorResponse);

        public abstract Builder taps(List<Tap> taps);

        public abstract Builder tapPositions(List<TapPositionResponse> tapPositions);

        public abstract Builder locationFloorCount(Long locationFloorCount);

        public abstract Builder locationTapCount(Long locationTapCount);

        public abstract Builder location(TenantLocationEntry location);

        public abstract Builder floor(TenantLocationFloorEntry floor);

        public abstract Builder floorPlanDimensions(ImageDimensions.Dimensions floorPlanDimensions);

        public abstract FloorSelectionResult build();
    }
}
