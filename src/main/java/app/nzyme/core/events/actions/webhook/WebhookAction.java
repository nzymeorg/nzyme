package app.nzyme.core.events.actions.webhook;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.events.actions.Action;
import app.nzyme.core.events.actions.ActionExecutionResult;
import app.nzyme.core.events.types.DetectionEvent;
import app.nzyme.core.events.types.SystemEvent;
import tools.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class WebhookAction implements Action {

    private static final Logger LOG = LogManager.getLogger(WebhookAction.class);

    private final NzymeNode nzyme;

    private final ObjectMapper om;
    private final OkHttpClient httpClient;

    private final URL url;
    private final Optional<String> bearerToken;

    public WebhookAction(NzymeNode nzyme, WebhookActionConfiguration configuration) {
        this.nzyme = nzyme;

        this.om = new ObjectMapper();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS);

        if (configuration.allowInsecure()) {
            try {
                TrustManager[] insecureTrustManager = buildInsecureTrustManager();
                SSLContext insecureSSLContext = SSLContext.getInstance("SSL");
                insecureSSLContext.init(null, insecureTrustManager, new java.security.SecureRandom());

                builder.sslSocketFactory(
                        insecureSSLContext.getSocketFactory(),
                        (X509TrustManager) insecureTrustManager[0]
                );

                builder.hostnameVerifier((hostname, session) -> true);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        httpClient = builder.build();

        try {
            this.url = new URL(configuration.url());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid webhook URL: " + configuration.url());
        }

        this.bearerToken = Optional.ofNullable(configuration.bearerToken());
    }

    @Override
    public ActionExecutionResult execute(SystemEvent event) {
        LOG.info("Executing [{}] for event type [{}].", this.getClass().getCanonicalName(), event.type());

        Map<String, Object> payload = Maps.newHashMap();
        payload.put("timestamp", event.timestamp().toString());
        payload.put("event_supertype", "system");
        payload.put("event_type", event.type().name());
        payload.put("event_type_category", event.type().getCategory());
        payload.put("event_type_description", event.type().getDescription());
        payload.put("event_type_human_readable", event.type().getHumanReadableName());
        payload.put("details", event.details());

        return execute(payload);
    }

    @Override
    public ActionExecutionResult execute(DetectionEvent event) {
        LOG.info("Executing [{}] for event type [{}].", this.getClass().getCanonicalName(), event.detectionType());

        Map<String, Object> payload = Maps.newHashMap();
        payload.put("timestamp", event.timestamp().toString());
        payload.put("event_supertype", "detection");
        payload.put("event_type", event.detectionType().name());
        payload.put("event_type_subsystem", event.detectionType().getSubsystem().name());
        payload.put("event_type_human_readable", event.detectionType().getTitle());
        payload.put("details", event.details());
        payload.put("location_id", event.locationId() == null ? null : event.locationId().toString());
        payload.put("location_name", event.locationName());

        return execute(payload);
    }

    private ActionExecutionResult execute(Map<String, Object> payload) {
        try {
            byte[] body = this.om.writeValueAsBytes(payload);

            Request.Builder requestBuilder = new Request.Builder()
                    .post(RequestBody.create(body))
                    .url(this.url)
                    .addHeader(HttpHeaders.CONTENT_TYPE, "application/json");

            if (bearerToken.isPresent() && !bearerToken.get().isEmpty()) {
                byte[] decrypted = nzyme.getCrypto().decryptWithClusterKey(bearerToken.get().getBytes());
                requestBuilder.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + new String(decrypted));
            }

            try (Response response = httpClient.newCall(requestBuilder.build()).execute()){
                if (!response.isSuccessful()) {
                    LOG.error("Could not execute Webhook. Received HTTP <{}>.", response.code());
                    return ActionExecutionResult.FAILURE;
                }
            }

            return ActionExecutionResult.SUCCESS;
        } catch (IOException | Crypto.CryptoOperationException e) {
            LOG.error("Could not execute Webhook.", e);
            return ActionExecutionResult.FAILURE;
        }
    }

    private TrustManager[] buildInsecureTrustManager() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };
    }

}
