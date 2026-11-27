package app.nzyme.core.distributed;

import org.testng.SkipException;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MemoryPressureMonitorTest {

    private static final String PRESSURE = "some avg10=1.20 avg60=0.40 avg300=0.10 total=%d\n"
            + "full avg10=0.00 avg60=0.00 avg300=0.00 total=7\n";

    private Path write(String content) throws IOException {
        Path file = Files.createTempFile("memory-pressure", ".test");
        file.toFile().deleteOnExit();
        Files.writeString(file, content);

        return file;
    }

    @Test
    public void testReadsStallTimeOfSomeLine() throws IOException {
        assertEquals(MemoryPressureMonitor.readStallMicros(write(String.format(PRESSURE, 239977352L))).getAsLong(),
                239977352L);
    }

    @Test
    public void testMissingFile() {
        Path missing = Path.of("/proc/pressure/does-not-exist");

        assertFalse(MemoryPressureMonitor.readStallMicros(missing).isPresent());
        assertFalse(new MemoryPressureMonitor(missing).sample().isPresent());
    }

    @Test
    public void testNoSource() {
        MemoryPressureMonitor monitor = new MemoryPressureMonitor((Path) null);

        assertFalse(monitor.sample().isPresent());
        assertFalse(monitor.sample().isPresent());
    }

    @Test
    public void testReadThatFails() throws IOException {
        Path directory = Files.createTempDirectory("memory-pressure");
        directory.toFile().deleteOnExit();

        assertFalse(MemoryPressureMonitor.readStallMicros(directory).isPresent());
        assertFalse(new MemoryPressureMonitor(directory).sample().isPresent());
    }

    @Test
    public void testUnreadableFile() throws IOException {
        if ("root".equals(System.getProperty("user.name"))) {
            throw new SkipException("root reads every file.");
        }

        Path file = write(String.format(PRESSURE, 1L));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));

        assertFalse(MemoryPressureMonitor.readStallMicros(file).isPresent());
    }

    @Test
    public void testUnknownFormats() throws IOException {
        assertFalse(MemoryPressureMonitor.readStallMicros(write("")).isPresent());
        assertFalse(MemoryPressureMonitor.readStallMicros(write("some avg10=0.00\n")).isPresent());
        assertFalse(MemoryPressureMonitor.readStallMicros(write("some avg10=0.00 total=abc\n")).isPresent());
        assertFalse(MemoryPressureMonitor.readStallMicros(write("some avg10=0.00 total=-5\n")).isPresent());
        assertFalse(MemoryPressureMonitor.readStallMicros(write("full avg10=0.00 total=12\n")).isPresent());
        assertFalse(MemoryPressureMonitor.readStallMicros(write("\u0000\u0001\u0002 total=")).isPresent());
    }

    @Test
    public void testSample() throws Exception {
        Path file = write(String.format(PRESSURE, 1_000_000L));
        MemoryPressureMonitor monitor = new MemoryPressureMonitor(file);

        Thread.sleep(200);
        Files.writeString(file, String.format(PRESSURE, 1_050_000L));
        double percent = monitor.sample().getAsDouble();

        // 50 ms of stalls in 200 ms or more.
        assertTrue(percent > 0 && percent <= 25, "was " + percent);

        Thread.sleep(50);
        assertEquals(monitor.sample().getAsDouble(), 0.0);
    }

    @Test
    public void testSampleIsCappedAtAllOfTheTime() throws Exception {
        Path file = write(String.format(PRESSURE, 0L));
        MemoryPressureMonitor monitor = new MemoryPressureMonitor(file);

        Thread.sleep(20);
        Files.writeString(file, String.format(PRESSURE, 60_000_000L));

        assertEquals(monitor.sample().getAsDouble(), 100.0);
    }

    @Test
    public void testCounterThatStartsOver() throws Exception {
        Path file = write(String.format(PRESSURE, 5_000_000L));
        MemoryPressureMonitor monitor = new MemoryPressureMonitor(file);

        Thread.sleep(20);
        Files.writeString(file, String.format(PRESSURE, 10L));
        assertFalse(monitor.sample().isPresent());

        Thread.sleep(20);
        Files.writeString(file, String.format(PRESSURE, 10L));
        assertEquals(monitor.sample().getAsDouble(), 0.0);
    }

    @Test
    public void testStallsOfAGapAreNotReportedAsOneInterval() throws Exception {
        Path file = write(String.format(PRESSURE, 1_000_000L));
        MemoryPressureMonitor monitor = new MemoryPressureMonitor(file);

        Files.delete(file);
        assertFalse(monitor.sample().isPresent());

        Thread.sleep(20);
        Files.writeString(file, String.format(PRESSURE, 61_000_000L));
        assertFalse(monitor.sample().isPresent());

        Thread.sleep(20);
        Files.writeString(file, String.format(PRESSURE, 61_000_000L));
        assertEquals(monitor.sample().getAsDouble(), 0.0);
    }

    @Test
    public void testFileThatDisappears() throws Exception {
        Path file = write(String.format(PRESSURE, 5_000_000L));
        MemoryPressureMonitor monitor = new MemoryPressureMonitor(file);

        Files.delete(file);
        assertFalse(monitor.sample().isPresent());
        assertFalse(monitor.sample().isPresent());
    }

    @Test
    public void testRealHostDoesNotFail() {
        new MemoryPressureMonitor().sample();
    }

}
