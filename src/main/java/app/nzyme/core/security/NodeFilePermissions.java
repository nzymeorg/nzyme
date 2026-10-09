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

package app.nzyme.core.security;

import app.nzyme.core.configuration.node.NodeConfiguration;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.util.FilePermissions;
import com.google.common.collect.Lists;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class NodeFilePermissions {

    public record Problem(String description, String fix) { }

    private final Path cryptoDirectory;
    private final Path configurationFile;

    public NodeFilePermissions(NodeConfiguration configuration) {
        this.cryptoDirectory = Path.of(configuration.cryptoDirectory()).toAbsolutePath();
        this.configurationFile = configuration.configurationFile()
                .map(f -> Path.of(f).toAbsolutePath())
                .orElse(null);
    }

    public List<Problem> check() {
        if (!FilePermissions.isSupported()) {
            return Collections.emptyList();
        }

        List<Problem> problems = Lists.newArrayList();

        String problem = FilePermissions.checkNotWorldAccessible(cryptoDirectory);
        if (problem != null) {
            problems.add(new Problem(problem, "chmod 700 " + cryptoDirectory));
        }

        Path privateKey = cryptoDirectory.resolve(Crypto.PGP_PRIVATE_KEY_FILE_NAME);
        problem = FilePermissions.checkOwnerOnly(privateKey);
        if (problem != null) {
            problems.add(new Problem(problem, "chmod 600 " + privateKey));
        }

        Path tlsKey = cryptoDirectory.resolve(Crypto.TLS_KEY_FILE_NAME);
        problem = FilePermissions.checkOwnerOnly(tlsKey);
        if (problem != null) {
            problems.add(new Problem(problem, "chmod 600 " + tlsKey));
        }

        if (configurationFile != null) {
            problem = FilePermissions.checkNotWorldAccessible(configurationFile);
            if (problem != null) {
                problems.add(new Problem(problem, "chmod 640 " + configurationFile));
            }
        }

        return problems;
    }

    public static String buildMessage(List<Problem> problems) {
        StringBuilder message = new StringBuilder("Insecure permissions on files holding secrets. ")
                .append("Other users on this system could read the cluster private key or the database password.\n")
                .append("Problems:\n");
        for (Problem p : problems) {
            message.append("  - ").append(p.description()).append("\n");
        }
        message.append("Fix by running the following commands as root:\n");
        for (Problem p : problems) {
            message.append("  ").append(p.fix()).append("\n");
        }
        message.append("The directory and files should be owned by the user nzyme runs as (chown).");

        return message.toString();
    }

}
