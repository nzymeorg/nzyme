package app.nzyme.core.ethernet.topology;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TopologyBucketsTest {

    private static final DateTime TO = new DateTime(2026, 11, 9, 19, 24, 31, DateTimeZone.UTC);

    @Test
    public void testPicksBucketSizeByRange() {
        assertEquals(TopologyBuckets.of(TO.minusMinutes(15), TO).sizeSeconds(), 60);
        assertEquals(TopologyBuckets.of(TO.minusHours(1), TO).sizeSeconds(), 60);
        assertEquals(TopologyBuckets.of(TO.minusHours(24), TO).sizeSeconds(), 1_800);
        assertEquals(TopologyBuckets.of(TO.minusDays(7), TO).sizeSeconds(), 10_800);
        assertEquals(TopologyBuckets.of(TO.minusDays(30), TO).sizeSeconds(), 43_200);
        assertEquals(TopologyBuckets.of(TO.minusDays(90), TO).sizeSeconds(), 172_800);
        assertEquals(TopologyBuckets.of(TO.minusDays(365), TO).sizeSeconds(), 604_800);
        assertEquals(TopologyBuckets.of(TO.minusDays(730), TO).sizeSeconds(), 2_592_000);
    }

    @Test
    public void testNeverExceedsMaximumByMoreThanTheAlignmentBucket() {
        for (int minutes : new int[]{1, 5, 59, 60, 61, 720, 1440, 10_080, 43_200, 525_600, 5_256_000}) {
            TopologyBuckets buckets = TopologyBuckets.of(TO.minusMinutes(minutes), TO);
            assertTrue(buckets.count() >= 1, "range of " + minutes + " minutes");
            assertTrue(buckets.count() <= TopologyBuckets.MAX_BUCKETS + 1, "range of " + minutes + " minutes");
        }
    }

    @Test
    public void testRangesOfManyYearsStayWithinTheMaximum() {
        for (int years : new int[]{5, 6, 10, 20, 50}) {
            TopologyBuckets buckets = TopologyBuckets.of(TO.minusYears(years), TO);
            assertTrue(buckets.count() <= TopologyBuckets.MAX_BUCKETS + 1, years + " years");
            assertFalse(buckets.from().isAfter(TO.minusYears(years)), years + " years");
            assertTrue((buckets.fromEpochSeconds() + buckets.count() * buckets.sizeSeconds()) * 1000 >= TO.getMillis(), years + " years");
        }
    }

    @Test
    public void testBucketsStartOnMultiplesOfTheirSize() {
        TopologyBuckets buckets = TopologyBuckets.of(TO.minusHours(24), TO);
        assertEquals(buckets.fromEpochSeconds() % buckets.sizeSeconds(), 0);
        assertEquals(buckets.from(), new DateTime(2026, 11, 8, 19, 0, 0, DateTimeZone.UTC).toDateTime(buckets.from().getZone()));
    }

    @Test
    public void testBucketsCoverTheWholeRange() {
        for (int minutes : new int[]{1, 15, 60, 1440, 10_080}) {
            DateTime from = TO.minusMinutes(minutes);
            TopologyBuckets buckets = TopologyBuckets.of(from, TO);

            assertFalse(buckets.from().isAfter(from));
            long end = buckets.fromEpochSeconds() + buckets.count() * buckets.sizeSeconds();
            assertTrue(end * 1000 >= TO.getMillis());
            // The last bucket holds the end of the range.
            assertTrue(buckets.bucketStart(buckets.count() - 1).getMillis() <= TO.getMillis());
        }
    }

    @Test
    public void testNearlyIdenticalRangesShareBoundaries() {
        TopologyBuckets first = TopologyBuckets.of(TO.minusHours(24), TO);
        TopologyBuckets second = TopologyBuckets.of(TO.minusHours(24).plusSeconds(2), TO.plusSeconds(2));
        assertEquals(second.fromEpochSeconds(), first.fromEpochSeconds());
        assertEquals(second.sizeSeconds(), first.sizeSeconds());
    }

    @Test
    public void testEmptyAndInvertedRangesYieldOneBucket() {
        assertEquals(TopologyBuckets.of(TO, TO).count(), 1);
        assertEquals(TopologyBuckets.of(TO, TO.minusHours(1)).count(), 1);
    }

}
