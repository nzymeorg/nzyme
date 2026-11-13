package app.nzyme.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PidFile {

    /*
     * Here to stay reachable for the lifetime of the JVM and never read. Avoids losing the lock to the GC.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private static FileChannel claimed;

    public static boolean isAlreadyRunning(Path dataDirectory, String nodeName) throws IOException {
        Path pidFile = getPidFile(dataDirectory, nodeName);

        FileChannel channel = FileChannel.open(pidFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return true;
            }

            channel.truncate(0);
            channel.write(ByteBuffer.wrap(
                    String.valueOf(ProcessHandle.current().pid()).getBytes(StandardCharsets.US_ASCII)
            ));
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }

        claimed = channel;
        return false;
    }

    /*
     * Lives in the data directory, not in the world-writable system temp directory, where any local user could
     * place a file under the predictable name and prevent nzyme from starting.
     */
    private static Path getPidFile(Path dataDirectory, String nodeName) {
        return dataDirectory.resolve(nodeName + ".pid");
    }

}
