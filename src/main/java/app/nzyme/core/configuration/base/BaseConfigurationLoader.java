/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.configuration.base;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import app.nzyme.core.configuration.ConfigurationKeys;
import app.nzyme.core.configuration.ConfigurationValidator;
import app.nzyme.core.configuration.IncompleteConfigurationException;
import app.nzyme.core.configuration.InvalidConfigurationException;
import app.nzyme.core.util.Tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;

public class BaseConfigurationLoader {

    private final Config general;

    public BaseConfigurationLoader(File configFile) throws InvalidConfigurationException, IncompleteConfigurationException, FileNotFoundException {
        if (!Files.isReadable(configFile.toPath())) {
            throw new FileNotFoundException("File at [" + configFile.getPath() + "] does not exist or is not readable. Check path and permissions.");
        }

        Config root = ConfigFactory.parseFile(configFile).resolve();

        try {
            this.general = root.getConfig(ConfigurationKeys.GENERAL);
        } catch(ConfigException e) {
            throw new IncompleteConfigurationException("Incomplete configuration.", e);
        }

        validate();
    }

    public BaseConfiguration get() {
        return BaseConfiguration.create(parseNodeName(), parseDataDirectory());
    }

    public String parseNodeName() {
        String nodeName = general.getString(ConfigurationKeys.NAME);

        if (!nodeName.matches("^[a-zA-Z0-9_-]{1,64}$")) {
            throw new IllegalArgumentException("Invalid node name: " + nodeName);
        }

        return nodeName;
    }

    private String parseDataDirectory() {
        return general.getString(ConfigurationKeys.DATA_DIRECTORY);
    }

    public void validate() throws InvalidConfigurationException, IncompleteConfigurationException {
        // Node ID exists and is valid.
        ConfigurationValidator.expect(general, ConfigurationKeys.NAME, ConfigurationKeys.GENERAL, String.class);
        if(!Tools.isSafeNodeName(parseNodeName())) {
            throw new InvalidConfigurationException("Node ID must only contain alphanumeric characters, underscores or dashes.");
        }

        // Data directory exists and is readable?
        File dataDirectory = new File(parseDataDirectory());
        if (!dataDirectory.exists()) {
            throw new InvalidConfigurationException("Data directory [" + parseDataDirectory() + "] does not exist or is not accessible by user [" + System.getProperty("user.name") + "].");
        }

        if (!dataDirectory.isDirectory()) {
            throw new InvalidConfigurationException("Data directory [" + parseDataDirectory() + "] is not a directory.");
        }

        if (!dataDirectory.canWrite()) {
            throw new InvalidConfigurationException("Data directory [" + parseDataDirectory() + "] is not writable by user [" + System.getProperty("user.name") + "].");
        }
    }


}
