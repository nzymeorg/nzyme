package app.nzyme.core.configuration.node;

import com.google.auto.value.AutoValue;

import java.net.URI;
import java.util.Optional;

@AutoValue
public abstract class NodeConfiguration {

    public abstract boolean versionchecksEnabled();
    public abstract Optional<String> versioncheckUri();

    public abstract String databasePath();

    public abstract URI restListenUri();
    public abstract URI httpExternalUri();
    public abstract Optional<Integer> httpMaxPostSize();

    public abstract String pluginDirectory();

    public abstract String cryptoDirectory();

    public abstract Optional<Integer> slowQueryLogThreshold();

    public abstract String ntpServer();

    public abstract Optional<ProtocolsConfiguration> protocols();

    public abstract Optional<String> connectApiUri();
    public abstract boolean connectSkip();

    public abstract PerformanceConfiguration performance();
    public abstract MiscConfiguration misc();

    // Absolute path of the loaded configuration file. Empty for configurations not loaded from a file.
    public abstract Optional<String> configurationFile();

    public static NodeConfiguration create(boolean versionchecksEnabled, Optional<String> versioncheckUri, String databasePath, URI restListenUri, URI httpExternalUri, Optional<Integer> httpMaxPostSize, String pluginDirectory, String cryptoDirectory, Optional<Integer> slowQueryLogThreshold, String ntpServer, Optional<ProtocolsConfiguration> protocols, Optional<String> connectApiUri, boolean connectSkip, PerformanceConfiguration performance, MiscConfiguration misc, Optional<String> configurationFile) {
        return builder()
                .versionchecksEnabled(versionchecksEnabled)
                .versioncheckUri(versioncheckUri)
                .databasePath(databasePath)
                .restListenUri(restListenUri)
                .httpExternalUri(httpExternalUri)
                .httpMaxPostSize(httpMaxPostSize)
                .pluginDirectory(pluginDirectory)
                .cryptoDirectory(cryptoDirectory)
                .slowQueryLogThreshold(slowQueryLogThreshold)
                .ntpServer(ntpServer)
                .protocols(protocols)
                .connectApiUri(connectApiUri)
                .connectSkip(connectSkip)
                .performance(performance)
                .misc(misc)
                .configurationFile(configurationFile)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NodeConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder versionchecksEnabled(boolean versionchecksEnabled);

        public abstract Builder versioncheckUri(Optional<String> versioncheckUri);

        public abstract Builder databasePath(String databasePath);

        public abstract Builder restListenUri(URI restListenUri);

        public abstract Builder httpExternalUri(URI httpExternalUri);

        public abstract Builder httpMaxPostSize(Optional<Integer> httpMaxPostSize);

        public abstract Builder pluginDirectory(String pluginDirectory);

        public abstract Builder cryptoDirectory(String cryptoDirectory);

        public abstract Builder slowQueryLogThreshold(Optional<Integer> slowQueryLogThreshold);

        public abstract Builder ntpServer(String ntpServer);

        public abstract Builder protocols(Optional<ProtocolsConfiguration> protocols);

        public abstract Builder connectApiUri(Optional<String> connectApiUri);

        public abstract Builder connectSkip(boolean connectSkip);

        public abstract Builder performance(PerformanceConfiguration performance);

        public abstract Builder misc(MiscConfiguration misc);

        public abstract Builder configurationFile(Optional<String> configurationFile);

        public abstract NodeConfiguration build();
    }
}