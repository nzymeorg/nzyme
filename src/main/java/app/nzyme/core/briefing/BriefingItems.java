package app.nzyme.core.briefing;

import app.nzyme.core.rest.responses.briefing.*;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;

public class BriefingItems {

    public static BriefingAttentionItemResponse attention(BriefingSeverity severity,
                                                          String category,
                                                          @Nullable String subsystem,
                                                          String title,
                                                          @Nullable String text,
                                                          long count,
                                                          @Nullable DateTime timestamp,
                                                          @Nullable BriefingLinkResponse link) {
        return BriefingAttentionItemResponse.create(
                severity.name(), category, subsystem, title, text, count, timestamp, link
        );
    }

    public static BriefingCheckResponse check(String key,
                                              String level,
                                              String title,
                                              @Nullable String detail,
                                              @Nullable BriefingLinkResponse link) {
        return BriefingCheckResponse.create(key, level, title, detail, link);
    }

    public static BriefingCheckResponse ok(String key, String title, @Nullable String detail, @Nullable BriefingLinkResponse link) {
        return check(key, "OK", title, detail, link);
    }

    public static BriefingCheckResponse warn(String key, String title, @Nullable String detail, @Nullable BriefingLinkResponse link) {
        return check(key, "WARN", title, detail, link);
    }

    public static BriefingCheckResponse info(String key, String title, @Nullable String detail, @Nullable BriefingLinkResponse link) {
        return check(key, "INFO", title, detail, link);
    }

    public static BriefingChangeTileResponse tile(String key,
                                                  String title,
                                                  long current,
                                                  @Nullable Long previous,
                                                  @Nullable String unit,
                                                  @Nullable Boolean higherIsWorse,
                                                  @Nullable BriefingLinkResponse link,
                                                  List<BriefingSampleResponse> samples) {
        return BriefingChangeTileResponse.create(key, title, current, previous, unit, higherIsWorse, link, samples, null);
    }

    public static BriefingChangeTileResponse breakdownTile(String key,
                                                           String title,
                                                           long current,
                                                           @Nullable Long previous,
                                                           @Nullable Boolean higherIsWorse,
                                                           @Nullable BriefingLinkResponse link,
                                                           List<BriefingBreakdownEntryResponse> breakdown) {
        return BriefingChangeTileResponse.create(key, title, current, previous, null, higherIsWorse, link, List.of(), breakdown);
    }

    public static BriefingBreakdownEntryResponse breakdownEntry(String label, long count) {
        return BriefingBreakdownEntryResponse.create(label, count);
    }

    public static BriefingSampleResponse sample(String label,
                                                @Nullable String detail,
                                                @Nullable DateTime timestamp,
                                                @Nullable BriefingSeverity severity,
                                                @Nullable BriefingLinkResponse link) {
        return BriefingSampleResponse.create(label, detail, timestamp, severity == null ? null : severity.name(), link);
    }

    public static BriefingChangeGroupResponse group(String key,
                                                    String title,
                                                    @Nullable String subsystem,
                                                    @Nullable BriefingLinkResponse link,
                                                    List<BriefingChangeTileResponse> tiles,
                                                    @Nullable String hint) {
        return BriefingChangeGroupResponse.create(key, title, subsystem, link, tiles, hint);
    }

    public static String plural(long count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }

}
