package app.nzyme.core.ethernet.segmentation;

import app.nzyme.core.ethernet.CIDR;
import jakarta.annotation.Nullable;
import org.joda.time.DateTime;

import java.util.List;
import java.util.UUID;

public record Segment(long id,
                      UUID uuid,
                      UUID organizationId,
                      UUID tenantId,
                      SegmentKind kind,
                      String name,
                      @Nullable String description,
                      @Nullable String notes,
                      // The taps the segment is limited to. NULL if it applies to what any tap records.
                      @Nullable List<UUID> tapUuids,
                      List<Member> members,
                      DateTime createdAt,
                      DateTime updatedAt) {

    public enum MemberType {
        CIDR, ASN
    }

    public record Member(MemberType type, @Nullable CIDR network, @Nullable Long asn) {

        public static Member ofNetwork(CIDR network) {
            return new Member(MemberType.CIDR, network, null);
        }

        public static Member ofAsn(long asn) {
            return new Member(MemberType.ASN, null, asn);
        }

    }

    public boolean coversTap(UUID tap) {
        return tapUuids == null || tapUuids.contains(tap);
    }

}
