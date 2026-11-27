package app.nzyme.core.distributed;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class NodeHeapTest {

    @Test
    public void testHealthy() {
        assertEquals(NodeHeap.judge(false, 0.8, 10, 52.0, 10, 0.1, 10).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testNoSamples() {
        NodeHeap heap = NodeHeap.judge(false, null, 0, null, 0, null, 0);

        assertEquals(heap.status(), NodeHeap.Status.OK);
        assertNull(heap.pauseTimePercent());
        assertNull(heap.heapAfterCollectionPercent());
        assertNull(heap.memoryPressurePercent());
    }

    @Test
    public void testEstablishedNodeWithoutMeasurements() {
        assertEquals(NodeHeap.judge(true, null, 0, null, 0, null, 0).status(), NodeHeap.Status.NO_MEASUREMENTS);
        assertEquals(NodeHeap.judge(true, 40.0, 7, 96.0, 2, 0.1, 7).status(), NodeHeap.Status.NO_MEASUREMENTS);
        assertEquals(NodeHeap.judge(true, 0.4, 8, null, 0, null, 0).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testFindingsComeBeforeMissingMeasurements() {
        assertEquals(NodeHeap.judge(true, 40.0, 5, 97.0, 4, 0.1, 5).status(), NodeHeap.Status.FULL);
        assertEquals(NodeHeap.judge(true, null, 0, null, 0, 45.0, 9).status(), NodeHeap.Status.MEMORY_PRESSURE);
    }

    @Test
    public void testNone() {
        assertEquals(NodeHeap.none().status(), NodeHeap.Status.NO_MEASUREMENTS);
        assertNull(NodeHeap.none().pauseTimePercent());
    }

    @Test
    public void testMemoryPressure() {
        assertEquals(NodeHeap.judge(false, 0.2, 10, 20.0, 10, 50.3, 10).status(), NodeHeap.Status.MEMORY_PRESSURE);
        assertEquals(NodeHeap.judge(false, 0.0, 10, null, 0, 10.0, 8).status(), NodeHeap.Status.MEMORY_PRESSURE);
    }

    @Test
    public void testSqueezedFileCacheIsNotMemoryPressure() {
        assertEquals(NodeHeap.judge(false, 0.5, 10, 24.0, 10, 2.6, 10).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testMemoryPressureNeedsMostOfTheWindow() {
        assertEquals(NodeHeap.judge(false, 0.5, 10, 24.0, 10, 80.0, 7).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testNoPressureStallInformation() {
        assertEquals(NodeHeap.judge(false, 0.5, 10, 24.0, 10, null, 0).status(), NodeHeap.Status.OK);
        assertEquals(NodeHeap.judge(false, 14.6, 10, 89.0, 10, null, 0).status(), NodeHeap.Status.GC_OVERHEAD);
    }

    @Test
    public void testMemoryPressureComesBeforeFullAndOverhead() {
        assertEquals(NodeHeap.judge(false, 2.0, 10, 96.0, 10, 30.0, 10).status(), NodeHeap.Status.MEMORY_PRESSURE);
        assertEquals(NodeHeap.judge(false, 25.0, 10, 40.0, 10, 30.0, 10).status(), NodeHeap.Status.MEMORY_PRESSURE);
        assertEquals(NodeHeap.judge(false, 25.0, 10, 97.0, 10, 30.0, 10).status(), NodeHeap.Status.EXHAUSTED);
    }

    @Test
    public void testNoCollections() {
        assertEquals(NodeHeap.judge(false, 0.0, 10, null, 0, 0.1, 10).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testFull() {
        assertEquals(NodeHeap.judge(false, 6.7, 10, 96.0, 10, 0.1, 10).status(), NodeHeap.Status.FULL);
        assertEquals(NodeHeap.judge(false, 2.9, 10, 95.0, 3, 0.1, 10).status(), NodeHeap.Status.FULL);
    }

    @Test
    public void testOneLowPointInTheWindowIsNotFull() {
        assertEquals(NodeHeap.judge(false, 2.9, 10, 94.9, 10, 0.1, 10).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testG1HoldingGarbageBelowItsReserveIsNotFull() {
        assertEquals(NodeHeap.judge(false, 0.7, 10, 88.0, 10, 0.1, 10).status(), NodeHeap.Status.OK);
        assertEquals(NodeHeap.judge(false, 1.9, 10, 92.0, 10, 0.1, 10).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testFullNeedsSeveralCollections() {
        assertEquals(NodeHeap.judge(false, 0.1, 10, 97.0, 2, 0.1, 10).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testOverhead() {
        assertEquals(NodeHeap.judge(false, 14.6, 10, 89.0, 10, 0.1, 10).status(), NodeHeap.Status.GC_OVERHEAD);
        assertEquals(NodeHeap.judge(false, 10.0, 8, 20.0, 8, 0.1, 10).status(), NodeHeap.Status.GC_OVERHEAD);
        assertEquals(NodeHeap.judge(false, 35.0, 10, null, 0, 0.1, 10).status(), NodeHeap.Status.GC_OVERHEAD);
    }

    @Test
    public void testOverheadNeedsMostOfTheWindow() {
        assertEquals(NodeHeap.judge(false, 60.0, 7, 50.0, 7, 0.1, 10).status(), NodeHeap.Status.OK);
    }

    @Test
    public void testExhausted() {
        assertEquals(NodeHeap.judge(false, 10.5, 10, 96.0, 10, 0.1, 10).status(), NodeHeap.Status.EXHAUSTED);
    }

    @Test
    public void testFullOfAFreshlyRestartedNodeIsNotExhausted() {
        assertEquals(NodeHeap.judge(false, 40.0, 4, 96.0, 4, 0.1, 10).status(), NodeHeap.Status.FULL);
    }

}
