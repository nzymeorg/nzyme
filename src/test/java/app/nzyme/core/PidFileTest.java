package app.nzyme.core;

import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;

public class PidFileTest {

    private static final String OWN_PID = String.valueOf(ProcessHandle.current().pid());

    public static class OtherNode {
        public static void main(String[] args) throws Exception {
            System.out.println(PidFile.isAlreadyRunning(Path.of(args[0]), args[1]));
            System.in.read();
        }
    }

    @Test
    public void testStartsWithLeftoverFileHoldingOwnPid() throws Exception {
        Path dir = Files.createTempDirectory("nzyme-pid");
        Files.writeString(dir.resolve("node.pid"), OWN_PID);

        assertFalse(PidFile.isAlreadyRunning(dir, "node"));
        assertEquals(Files.readString(dir.resolve("node.pid")), OWN_PID);
    }

    @Test
    public void testStartsWithLeftoverFileHoldingPidOfOtherLiveProcess() throws Exception {
        Path dir = Files.createTempDirectory("nzyme-pid");
        long parent = ProcessHandle.current().parent().orElseThrow().pid();
        Files.writeString(dir.resolve("node.pid"), String.valueOf(parent));

        assertFalse(PidFile.isAlreadyRunning(dir, "node"));
        assertEquals(Files.readString(dir.resolve("node.pid")), OWN_PID);
    }

    @Test
    public void testReplacesLeftoverContent() throws Exception {
        Path dir = Files.createTempDirectory("nzyme-pid");
        Files.writeString(dir.resolve("node.pid"), "not a PID, and longer than any PID would be");

        assertFalse(PidFile.isAlreadyRunning(dir, "node"));
        assertEquals(Files.readString(dir.resolve("node.pid")), OWN_PID);
    }

    @Test
    public void testDetectsRunningNodeAndStartsAfterItIsGone() throws Exception {
        Path dir = Files.createTempDirectory("nzyme-pid");

        Process other = startOtherNode(dir, "node");
        try (BufferedReader out = new BufferedReader(new InputStreamReader(other.getInputStream()))) {
            assertEquals(out.readLine(), "false");

            assertTrue(PidFile.isAlreadyRunning(dir, "node"));
            assertEquals(Files.readString(dir.resolve("node.pid")), String.valueOf(other.pid()));

            assertFalse(PidFile.isAlreadyRunning(dir, "other-node"));
        } finally {
            other.destroyForcibly().waitFor();
        }

        assertFalse(PidFile.isAlreadyRunning(dir, "node"));
        assertEquals(Files.readString(dir.resolve("node.pid")), OWN_PID);
    }

    private static Process startOtherNode(Path dir, String nodeName) throws Exception {
        String classPath = classesOf(PidFile.class) + File.pathSeparator + classesOf(PidFileTest.class);

        return new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classPath, OtherNode.class.getName(), dir.toString(), nodeName
        ).redirectError(ProcessBuilder.Redirect.INHERIT).start();
    }

    private static String classesOf(Class<?> c) throws Exception {
        return Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

}
