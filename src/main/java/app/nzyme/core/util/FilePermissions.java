/*
 * This file is part of nzyme.
 *
 * nzyme is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * nzyme is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.util;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class FilePermissions {

    private static final Logger LOG = LogManager.getLogger(FilePermissions.class);

    public static final Set<PosixFilePermission> OWNER_READ_WRITE = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE
    );

    public static final Set<PosixFilePermission> OWNER_READ_WRITE_EXECUTE = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE
    );

    private static final Set<PosixFilePermission> GROUP_BITS = EnumSet.of(
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE
    );

    private static final Set<PosixFilePermission> OTHERS_BITS = EnumSet.of(
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE
    );

    public static boolean isSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /**
     * Writes a file that only the owner can read and write (mode 0600). Existing files are truncated and their
     * permissions are tightened as well.
     */
    public static void writeOwnerOnly(Path path, byte[] content) throws IOException {
        if (!isSupported()) {
            LOG.warn("File system does not support POSIX permissions. Writing [{}] with default permissions.", path);
            Files.write(path, content);
            return;
        }

        if (!Files.exists(path)) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
        }

        // Explicitly set again. The umask applies to the creation attributes.
        Files.setPosixFilePermissions(path, OWNER_READ_WRITE);
        Files.write(path, content, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Returns a description of the problem if the file or directory is accessible by group or others at all.
     * Returns null if permissions are fine or cannot be checked on this platform.
     */
    public static String checkOwnerOnly(Path path) {
        return check(path, false);
    }

    /**
     * Returns a description of the problem if the file or directory is accessible by others, or writable by
     * the group. Group read is tolerated. Returns null if permissions are fine or cannot be checked on this platform.
     */
    public static String checkNotWorldAccessible(Path path) {
        return check(path, true);
    }

    private static String check(Path path, boolean tolerateGroupRead) {
        if (!isSupported() || !Files.exists(path)) {
            return null;
        }

        Set<PosixFilePermission> perms;
        try {
            perms = Files.getPosixFilePermissions(path);
        } catch (IOException e) {
            LOG.warn("Could not read permissions of [{}].", path, e);
            return null;
        }

        List<String> problems = Lists.newArrayList();
        for (PosixFilePermission p : OTHERS_BITS) {
            if (perms.contains(p)) {
                problems.add("accessible by all users");
                break;
            }
        }

        for (PosixFilePermission p : GROUP_BITS) {
            if (perms.contains(p)) {
                boolean readOnly = p == PosixFilePermission.GROUP_READ
                        || (Files.isDirectory(path) && p == PosixFilePermission.GROUP_EXECUTE);
                if (!(tolerateGroupRead && readOnly)) {
                    problems.add("accessible by group");
                    break;
                }
            }
        }

        if (problems.isEmpty()) {
            return null;
        }

        return "[" + path + "] is " + String.join(" and ", problems)
                + " (mode " + PosixFilePermissions.toString(perms) + ")";
    }

}
