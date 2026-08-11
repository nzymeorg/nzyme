package app.nzyme.core.rest.server;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.crypto.tls.KeyStoreBootstrapResult;
import app.nzyme.core.crypto.tls.TLSKeyAndCertificate;
import app.nzyme.core.rest.CORSFilter;
import app.nzyme.core.rest.NzymeExceptionMapper;
import app.nzyme.core.rest.NzymeLeaderInjectionBinder;
import app.nzyme.core.rest.authentication.*;
import app.nzyme.core.rest.interceptors.TapTableSizeInterceptor;
import app.nzyme.core.rest.resources.*;
import app.nzyme.core.rest.resources.alerts.AlertsResource;
import app.nzyme.core.rest.resources.assets.WebInterfaceAssetsResource;
import app.nzyme.core.rest.resources.bluetooth.BluetoothDevicesResource;
import app.nzyme.core.rest.resources.context.AssetContextResource;
import app.nzyme.core.rest.resources.dot11.*;
import app.nzyme.core.rest.resources.dot11.BanditsResource;
import app.nzyme.core.rest.resources.dot11.Dot11ClientsResource;
import app.nzyme.core.rest.resources.dot11.Dot11MonitoredNetworksResource;
import app.nzyme.core.rest.resources.dot11.Dot11NetworksResource;
import app.nzyme.core.rest.resources.ethernet.*;
import app.nzyme.core.rest.resources.locations.LocationsResource;
import app.nzyme.core.rest.resources.monitors.MonitorsResource;
import app.nzyme.core.rest.resources.system.authentication.AuthenticationResource;
import app.nzyme.core.rest.resources.system.authentication.mgmt.InitialUserResource;
import app.nzyme.core.rest.resources.system.authentication.mgmt.OrganizationsResource;
import app.nzyme.core.rest.resources.monitoring.MonitoringResource;
import app.nzyme.core.rest.resources.monitoring.PrometheusResource;
import app.nzyme.core.rest.resources.system.*;
import app.nzyme.core.rest.resources.system.cluster.MessageBusResource;
import app.nzyme.core.rest.resources.system.cluster.NodesResource;
import app.nzyme.core.rest.resources.system.cluster.TasksQueueResource;
import app.nzyme.core.rest.resources.system.connect.ConnectResource;
import app.nzyme.core.rest.resources.system.integrations.SmtpIntegrationResource;
import app.nzyme.core.rest.resources.taps.StatusResource;
import app.nzyme.core.rest.resources.taps.TablesResource;
import app.nzyme.core.rest.resources.taps.TapsResource;
import app.nzyme.core.rest.resources.timelines.TimelinesResource;
import app.nzyme.core.rest.resources.uav.UavResource;
import app.nzyme.core.rest.resources.user.UserProfileResource;
import app.nzyme.plugin.distributed.messaging.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.DeflateEncoder;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;
import tools.jackson.datatype.jsr310.JavaTimeModule;
import tools.jackson.jakarta.rs.json.JacksonJsonProvider;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NzymeHttpServer {

    private static final Logger LOG = LogManager.getLogger(NzymeHttpServer.class);

    private final NzymeNode nzyme;
    private final List<Object> pluginRestResources;

    private HttpServer server;
    private TLSKeyAndCertificate certificateInUse;

    public NzymeHttpServer(NzymeNode nzyme, List<Object> pluginRestResources) {
        this.nzyme = nzyme;
        this.pluginRestResources = pluginRestResources;

        // Register message handler for requested server restarts.
        nzyme.getMessageBus().onMessageReceived(MessageType.CHECK_RESTART_HTTP_SERVER, new MessageHandler() {
            @Override
            public MessageProcessingResult handle(ReceivedMessage message) {
                LOG.info("Received request to check for a potential restart of HTTP server. Current certificate: [{}]",
                        getCertificateInUse().signature());
                try {
                    /*
                     * Run the whole cert bootstrap and compare against cert currently in use to determine if there is
                     * a new cert. If there is a new cert, restart the server, which will load that cert.
                     */
                    KeyStoreBootstrapResult keyStore = nzyme.getCrypto().bootstrapTLSKeyStore();
                    LOG.info("TLS bootstrap certificate decision: [{}]", keyStore.loadedCertificate().signature());
                    if (!keyStore.loadedCertificate().signature().equals(getCertificateInUse().signature())) {
                        LOG.info("Restarting HTTP server to load new TLS certificate.");
                        reloadHttpServer(0, TimeUnit.SECONDS);
                    } else {
                        LOG.info("No restart of HTTP server required: Certificate did not change.");
                    }

                    return MessageProcessingResult.SUCCESS;
                } catch(Exception e) {
                    LOG.error("Could not handle requested HTTP server restart.", e);
                    return MessageProcessingResult.FAILURE;
                }
            }

            @Override
            public String getName() {
                return "Check for required HTTP server restart after TLS configuration change.";
            }
        });
    }

    public void initialize() {
        JsonMapper om = JsonMapper.builder()
                .addModule(new JodaModule())
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.property("jersey.config.server.wadl.disableWadl", "true");
        resourceConfig.register(new PreMFAAuthenticationFilter(nzyme));
        resourceConfig.register(new RESTAuthenticationFilter(nzyme));
        resourceConfig.register(new TapAuthenticationFilter(nzyme));
        resourceConfig.register(new PrometheusBasicAuthFilter(nzyme));
        resourceConfig.register(new CORSFilter());
        resourceConfig.register(new NzymeLeaderInjectionBinder(nzyme));
        resourceConfig.register(new JacksonJsonProvider(om));
        resourceConfig.register(new NzymeExceptionMapper());
        resourceConfig.register(new TapTableSizeInterceptor(nzyme));
        resourceConfig.register(MultiPartFeature.class);

        // Register REST API resources.
        resourceConfig.register(AuthenticationResource.class);
        resourceConfig.register(PingResource.class);
        resourceConfig.register(SystemResource.class);
        resourceConfig.register(StatusResource.class);
        resourceConfig.register(TablesResource.class);
        resourceConfig.register(TapsResource.class);
        resourceConfig.register(DNSResource.class);
        resourceConfig.register(PluginResource.class);
        resourceConfig.register(PrometheusResource.class);
        resourceConfig.register(CryptoResource.class);
        resourceConfig.register(MonitoringResource.class);
        resourceConfig.register(NodesResource.class);
        resourceConfig.register(HealthResource.class);
        resourceConfig.register(RegistryResource.class);
        resourceConfig.register(MessageBusResource.class);
        resourceConfig.register(TasksQueueResource.class);
        resourceConfig.register(OrganizationsResource.class);
        resourceConfig.register(InitialUserResource.class);
        resourceConfig.register(UserProfileResource.class);
        resourceConfig.register(EventsResource.class);
        resourceConfig.register(SmtpIntegrationResource.class);
        resourceConfig.register(EventActionsResource.class);
        resourceConfig.register(Dot11NetworksResource.class);
        resourceConfig.register(DatabaseResource.class);
        resourceConfig.register(Dot11ClientsResource.class);
        resourceConfig.register(Dot11MonitoredNetworksResource.class);
        resourceConfig.register(AlertsResource.class);
        resourceConfig.register(BanditsResource.class);
        resourceConfig.register(Dot11DiscoResource.class);
        resourceConfig.register(AssetContextResource.class);
        resourceConfig.register(Dot11LocationsResource.class);
        resourceConfig.register(SocksResource.class);
        resourceConfig.register(SSHResource.class);
        resourceConfig.register(ConnectResource.class);
        resourceConfig.register(BluetoothDevicesResource.class);
        resourceConfig.register(Dot11MonitoredProbeRequestResource.class);
        resourceConfig.register(Dot11MonitoredSSIDsResource.class);
        resourceConfig.register(UavResource.class);
        resourceConfig.register(DHCPResource.class);
        resourceConfig.register(AssetsResource.class);
        resourceConfig.register(L4Resource.class);
        resourceConfig.register(ArpResource.class);
        resourceConfig.register(TimeResource.class);
        resourceConfig.register(MonitorsResource.class);
        resourceConfig.register(TimelinesResource.class);
        resourceConfig.register(LocationsResource.class);
        resourceConfig.register(RTSPResource.class);
        resourceConfig.register(NATResource.class);

        // Plugin-supplied REST resources.
        for (Object resource : pluginRestResources) {
            try {
                resourceConfig.register(resource);
                LOG.info("Loaded plugin REST resource [{}].", resource.getClass().getCanonicalName());
            } catch(Exception e) {
                LOG.error("Could not register plugin REST resource [{}].", resource.getClass().getCanonicalName(), e);
            }
        }

        // Enable GZIP.
        resourceConfig.registerClasses(EncodingFilter.class, GZipEncoder.class, DeflateEncoder.class);

        // Register web interface asset resources.
        resourceConfig.register(WebInterfaceAssetsResource.class);

        try {
            KeyStoreBootstrapResult keyStore = nzyme.getCrypto().bootstrapTLSKeyStore();
            final SSLContextConfigurator sslContextConfigurator = new SSLContextConfigurator();
            sslContextConfigurator.setKeyStorePass("".toCharArray());
            sslContextConfigurator.setKeyStoreBytes(keyStore.keystoreBytes());
            final SSLContext sslContext = sslContextConfigurator.createSSLContext(true);
            SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(sslContext, false, false, false);

            server = GrizzlyHttpServerFactory.createHttpServer(
                    nzyme.getConfiguration().restListenUri(),
                    resourceConfig,
                    true,
                    sslEngineConfigurator,
                    false
            );

            this.certificateInUse = keyStore.loadedCertificate();

            LOG.info("Loaded TLS certificate: [{}/{}]",
                    certificateInUse.sourceType(), certificateInUse.signature());
        } catch(Exception e) {
            throw new RuntimeException("Could not start web server.", e);
        }

        CompressionConfig compressionConfig = server.getListener("grizzly").getCompressionConfig();
        compressionConfig.setCompressionMode(CompressionConfig.CompressionMode.ON);
        compressionConfig.setCompressionMinSize(1);
        compressionConfig.setCompressibleMimeTypes();

        // Max POST size. This is important for tap reports. A too low value will lead to HTTP 413 Payload Too Large.
        int maxPostSize = nzyme.getConfiguration().httpMaxPostSize().orElse(52428800); // Default: 50 MB
        if (maxPostSize < 5242880) { // Must be at least 5MB (for floor plan upload);
            maxPostSize = 5242880;
        }
        server.getServerConfiguration().setMaxPostSize(maxPostSize);
        LOG.info("Configuring web server with a max POST size of <{} byte>.", maxPostSize);

        // Start server.
        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Could not start REST API.", e);
        }

        LOG.info("Started web interface and REST API at [{}]. Access it at: [{}]",
                nzyme.getConfiguration().restListenUri(),
                nzyme.getConfiguration().httpExternalUri());
    }

    public TLSKeyAndCertificate getCertificateInUse() {
        return certificateInUse;
    }

    public void reloadHttpServer(int gracePeriod, TimeUnit tu) {
        Executors.newSingleThreadExecutor().submit(() -> {
            LOG.info("Restarting HTTP server.");
            server.shutdown(gracePeriod, tu);
            initialize();
        });
    }

    public void shutdownNow() {
        server.shutdownNow();
    }

}
