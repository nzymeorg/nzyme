package app.nzyme.core.rest.resources.system;

import app.nzyme.core.crypto.Crypto;
import app.nzyme.core.crypto.CryptoRegistryKeys;
import app.nzyme.core.crypto.PGPKeyFingerprint;
import app.nzyme.core.crypto.tls.*;
import app.nzyme.core.distributed.MetricExternalName;
import app.nzyme.core.distributed.Node;
import app.nzyme.core.distributed.database.metrics.TimerSnapshot;
import app.nzyme.core.rest.requests.PGPConfigurationUpdateRequest;
import app.nzyme.core.rest.requests.UpdateTLSWildcardNodeMatcherRequest;
import app.nzyme.core.rest.responses.crypto.*;
import app.nzyme.plugin.distributed.messaging.ClusterMessage;
import app.nzyme.plugin.distributed.messaging.Message;
import app.nzyme.plugin.distributed.messaging.MessageType;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryConstraintValidator;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryResponse;
import app.nzyme.plugin.rest.configuration.ConfigurationEntryValueType;
import app.nzyme.plugin.rest.security.PermissionLevel;
import app.nzyme.plugin.rest.security.RESTSecured;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.responses.metrics.TimerResponse;
import com.google.common.collect.Sets;
import com.google.common.math.Stats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.joda.time.DateTime;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/api/system/crypto")
@RESTSecured(PermissionLevel.SUPERADMINISTRATOR)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Crypto", description = "The TLS certificates of the Nzyme web interface and API, and the PGP keys that "
        + "Nzyme uses to encrypt sensitive data in the database.")
public class CryptoResource {

    private static final Logger LOG = LogManager.getLogger(CryptoResource.class);

    @Inject
    private NzymeNode nzyme;

    @GET
    @Path("summary")
    @Operation(operationId = "findCryptoSummary", summary = "Get a summary of all crypto configuration",
            description = "Returns the PGP key of every node, the PGP encryption and decryption performance metrics "
                    + "per node and for the whole cluster, the TLS certificate of every node that reported in during "
                    + "the last two minutes, all wildcard TLS certificates and the PGP configuration. Requires super "
                    + "administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Crypto summary found.",
            content = @Content(schema = @Schema(implementation = CryptoResponse.class)))
    @ApiResponse(responseCode = "500", description = "One of the certificates could not be parsed.",
            content = @Content)
    public Response summary() {
        Map<String, PGPKeyResponse> fingerprints = Maps.newHashMap();
        for (PGPKeyFingerprint fp : nzyme.getCrypto().getPGPKeysByNode()) {
            fingerprints.put(fp.nodeName(), PGPKeyResponse.create(fp.nodeName(), fp.fingerprint(), fp.createdAt()));
        }

        Map<UUID, TimerSnapshot> encryption = nzyme.getClusterManager().findMetricTimer(
                MetricExternalName.PGP_ENCRYPTION_TIMER.database_label
        );

        Map<UUID, TimerSnapshot> decryption = nzyme.getClusterManager().findMetricTimer(
                MetricExternalName.PGP_DECRYPTION_TIMER.database_label
        );

        Map<String, CryptoMetricsResponse> nodeMetrics = Maps.newTreeMap();

        List<UUID> nodeIds = Lists.newArrayList();
        nodeIds.addAll(encryption.keySet());
        nodeIds.addAll(decryption.keySet());

        Set<Long> encryptionMeans = Sets.newHashSet();
        Set<Long> encryptionMaxs = Sets.newHashSet();
        Set<Long> encryptionMins = Sets.newHashSet();
        Set<Long> encryptionStddevs = Sets.newHashSet();
        Set<Long> encryptionP99s = Sets.newHashSet();
        Set<Long> encryptionCounters = Sets.newHashSet();
        Set<Long> decryptionMeans = Sets.newHashSet();
        Set<Long> decryptionMaxs = Sets.newHashSet();
        Set<Long> decryptionMins = Sets.newHashSet();
        Set<Long> decryptionStddevs = Sets.newHashSet();
        Set<Long> decryptionP99s = Sets.newHashSet();
        Set<Long> decryptionCounters = Sets.newHashSet();

        for (UUID nodeId : nodeIds) {
            TimerSnapshot nodeEncryption = encryption.get(nodeId);
            TimerSnapshot nodeDecryption = decryption.get(nodeId);
            if (nodeEncryption != null) {
                if (nodeEncryption.mean() > 0) encryptionMeans.add(nodeEncryption.mean());
                if (nodeEncryption.max() > 0) encryptionMaxs.add(nodeEncryption.max());
                if (nodeEncryption.min() > 0) encryptionMins.add(nodeEncryption.min());
                if (nodeEncryption.stddev() > 0) encryptionStddevs.add(nodeEncryption.stddev());
                if (nodeEncryption.p99() > 0) encryptionP99s.add(nodeEncryption.p99());
                if (nodeEncryption.counter() > 0) encryptionCounters.add(nodeEncryption.counter());

                if (nodeDecryption.mean() > 0) decryptionMeans.add(nodeDecryption.mean());
                if (nodeDecryption.max() > 0) decryptionMaxs.add(nodeDecryption.max());
                if (nodeDecryption.min() > 0) decryptionMins.add(nodeDecryption.min());
                if (nodeDecryption.stddev() > 0) decryptionStddevs.add(nodeDecryption.stddev());
                if (nodeDecryption.p99() > 0) decryptionP99s.add(nodeDecryption.p99());
                if (nodeDecryption.counter() > 0) decryptionCounters.add(nodeDecryption.counter());

                nodeMetrics.put(nzyme.getNodeManager().findNameOfNode(nodeId), CryptoMetricsResponse.create(
                        TimerResponse.create(
                                nodeEncryption.mean(),
                                nodeEncryption.max(),
                                nodeEncryption.min(),
                                nodeEncryption.stddev(),
                                nodeEncryption.p99(),
                                nodeEncryption.counter()
                        ),
                        TimerResponse.create(
                                nodeDecryption.mean(),
                                nodeDecryption.max(),
                                nodeDecryption.min(),
                                nodeDecryption.stddev(),
                                nodeDecryption.p99(),
                                nodeDecryption.counter()
                        )
                ));
            }
        }

        CryptoMetricsResponse clusterMetrics = CryptoMetricsResponse.create(
                TimerResponse.create(
                        encryptionMeans.isEmpty() ? 0 : Stats.meanOf(encryptionMeans),
                        encryptionMaxs.isEmpty() ? 0 : Stats.of(encryptionMaxs).max(),
                        encryptionMins.isEmpty() ? 0 :  Stats.of(encryptionMins).min(),
                        encryptionStddevs.isEmpty() ? 0 : Stats.meanOf(encryptionStddevs),
                        encryptionP99s.isEmpty() ? 0 : Stats.of(encryptionP99s).max(),
                        encryptionCounters.isEmpty() ? 0 : ((Double) Stats.of(encryptionCounters).sum()).longValue()
                ),
                TimerResponse.create(
                        decryptionMeans.isEmpty() ? 0 : Stats.meanOf(decryptionMeans),
                        decryptionMaxs.isEmpty() ? 0 : Stats.of(decryptionMaxs).max(),
                        decryptionMins.isEmpty() ? 0 : Stats.of(decryptionMins).min(),
                        decryptionStddevs.isEmpty() ? 0 : Stats.meanOf(decryptionStddevs),
                        decryptionP99s.isEmpty() ? 0 : Stats.of(decryptionP99s).max(),
                        decryptionCounters.isEmpty() ? 0 : ((Double) Stats.of(decryptionCounters).sum()).longValue()
                )
        );

        CryptoNodeMetricsResponse metrics = CryptoNodeMetricsResponse.create(nodeMetrics, clusterMetrics);

        Map<String, TLSCertificateResponse> tlsCertificates = Maps.newTreeMap();
        for (TLSKeyAndCertificate cert : nzyme.getCrypto().getTLSCertificateByNode()) {
            Optional<Node> node = nzyme.getNodeManager().getNode(cert.nodeId());
            if (node.isPresent() && node.get().lastSeen().isAfter(DateTime.now().minusMinutes(2))) {
                String nodeName = nzyme.getNodeManager().findNameOfNode(cert.nodeId());

                // Does the node use a wildcard certificate instead?
                Map<UUID, TLSKeyAndCertificate> matchingNodes = nzyme.getCrypto().getTLSWildcardCertificatesForMatchingNodes();
                TLSKeyAndCertificate wildcardTls = matchingNodes.get(node.get().uuid());
                if (wildcardTls != null) {
                    X509Certificate firstCert = cert.certificates().get(0);
                    Collection<List<?>> issuerAlternativeNames;
                    Collection<List<?>> subjectAlternativeNames;
                    try {
                        issuerAlternativeNames = firstCert.getIssuerAlternativeNames();
                        subjectAlternativeNames = firstCert.getSubjectAlternativeNames();
                    } catch (CertificateParsingException e) {
                        LOG.error("Could not parse certificate.", e);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                    }

                    tlsCertificates.put(
                            nodeName,
                            TLSCertificateResponse.create(
                                    wildcardTls.nodeId().toString(),
                                    wildcardTls.sourceType().toString(),
                                    nodeName,
                                    wildcardTls.signature(),
                                    firstCert.getSigAlgName(),
                                    buildPrincipalResponse(firstCert.getIssuerX500Principal(), issuerAlternativeNames),
                                    buildPrincipalResponse(firstCert.getSubjectX500Principal(), subjectAlternativeNames),
                                    wildcardTls.validFrom(),
                                    wildcardTls.expiresAt()
                            )
                    );
                } else {
                    X509Certificate firstCert = cert.certificates().get(0);
                    Collection<List<?>> issuerAlternativeNames;
                    Collection<List<?>> subjectAlternativeNames;
                    try {
                        issuerAlternativeNames = firstCert.getIssuerAlternativeNames();
                        subjectAlternativeNames = firstCert.getSubjectAlternativeNames();
                    } catch (CertificateParsingException e) {
                        LOG.error("Could not parse certificate.", e);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                    }

                    tlsCertificates.put(
                            nodeName,
                            TLSCertificateResponse.create(
                                    cert.nodeId().toString(),
                                    cert.sourceType().toString(),
                                    nodeName,
                                    cert.signature(),
                                    firstCert.getSigAlgName(),
                                    buildPrincipalResponse(firstCert.getIssuerX500Principal(), issuerAlternativeNames),
                                    buildPrincipalResponse(firstCert.getSubjectX500Principal(), subjectAlternativeNames),
                                    cert.validFrom(),
                                    cert.expiresAt()
                            )
                    );
                }
            }
        }

        List<TLSWildcartCertificateResponse> tlsWildcartCertificates = Lists.newArrayList();
        for (TLSWildcardKeyAndCertificate entry : nzyme.getCrypto().getTLSWildcardCertificates()) {
            X509Certificate firstCert = entry.certificates().get(0);

            Collection<List<?>> issuerAlternativeNames;
            Collection<List<?>> subjectAlternativeNames;
            try {
                issuerAlternativeNames = firstCert.getIssuerAlternativeNames();
                subjectAlternativeNames = firstCert.getSubjectAlternativeNames();
            } catch (CertificateParsingException e) {
                LOG.error("Could not parse certificate.", e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            tlsWildcartCertificates.add(TLSWildcartCertificateResponse.create(
                    entry.id(),
                    entry.nodeMatcher(),
                    buildMatchingNodes(entry.nodeMatcher()),
                    entry.sourceType().toString(),
                    entry.signature(),
                    firstCert.getSigAlgName(),
                    buildPrincipalResponse(firstCert.getIssuerX500Principal(), issuerAlternativeNames),
                    buildPrincipalResponse(firstCert.getSubjectX500Principal(), subjectAlternativeNames),
                    entry.validFrom(),
                    entry.expiresAt()
            ));
        }

        PGPConfigurationResponse pgpConfiguration = PGPConfigurationResponse.create(ConfigurationEntryResponse.create(
                CryptoRegistryKeys.PGP_KEY_SYNC_ENABLED.key(),
                "PGP Key Sync Enabled",
                nzyme.getCrypto().isPGPKeySyncEnabled(),
                ConfigurationEntryValueType.BOOLEAN,
                CryptoRegistryKeys.PGP_KEY_SYNC_ENABLED.defaultValue().get(),
                CryptoRegistryKeys.PGP_KEY_SYNC_ENABLED.requiresRestart(),
                CryptoRegistryKeys.PGP_KEY_SYNC_ENABLED.constraints().get(),
                "pgp-key-sync"
        ));

        return Response.ok(CryptoResponse.create(
                metrics,
                fingerprints,
                tlsCertificates,
                tlsWildcartCertificates,
                nzyme.getCrypto().allPGPKeysEqualAcrossCluster(),
                pgpConfiguration
        )).build();
    }

    @GET
    @Path("/tls/node/{node_id}")
    @Operation(operationId = "findNodeTlsCertificate", summary = "Get the TLS certificate of a node",
            description = "Returns the individual TLS certificate of a node, including issuer, subject, signature "
                    + "algorithm and validity. A wildcard certificate that matches this node is not considered here. "
                    + "Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Certificate found.",
            content = @Content(schema = @Schema(implementation = TLSCertificateResponse.class)))
    @ApiResponse(responseCode = "404", description = "Node not found, or the node has no TLS certificate.",
            content = @Content)
    @ApiResponse(responseCode = "500", description = "The certificate could not be parsed.", content = @Content)
    public Response tlsCertificate(@Parameter(description = "Node UUID.") @PathParam("node_id") UUID nodeId) {
        Optional<Node> node = nzyme.getNodeManager().getNode(nodeId);
        if (node.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Optional<TLSKeyAndCertificate> tls = nzyme.getCrypto().getTLSCertificateOfNode(nodeId);

        if (tls.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TLSKeyAndCertificate cert = tls.get();
        X509Certificate firstCert = cert.certificates().get(0);

        Collection<List<?>> issuerAlternativeNames;
        Collection<List<?>> subjectAlternativeNames;
        try {
            issuerAlternativeNames = firstCert.getIssuerAlternativeNames();
            subjectAlternativeNames = firstCert.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            LOG.error("Could not parse certificate.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(TLSCertificateResponse.create(
                cert.nodeId().toString(),
                cert.sourceType().toString(),
                node.get().name(),
                cert.signature(),
                firstCert.getSigAlgName(),
                buildPrincipalResponse(firstCert.getIssuerX500Principal(), issuerAlternativeNames),
                buildPrincipalResponse(firstCert.getSubjectX500Principal(), subjectAlternativeNames),
                cert.validFrom(),
                cert.expiresAt()
        )).build();
    }

    @GET
    @Path("/tls/wildcard/{cert_id}")
    @Operation(operationId = "findWildcardTlsCertificate", summary = "Get a wildcard TLS certificate",
            description = "Returns a wildcard TLS certificate, its node matcher and the nodes the matcher currently "
                    + "applies to. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Certificate found.",
            content = @Content(schema = @Schema(implementation = TLSWildcartCertificateResponse.class)))
    @ApiResponse(responseCode = "404", description = "Certificate not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The certificate could not be parsed.", content = @Content)
    public Response tlsWildcardCertificate(@Parameter(description = "Wildcard certificate ID.") @PathParam("cert_id") long certificateId) {
        Optional<TLSWildcardKeyAndCertificate> certResult = nzyme.getCrypto().getTLSWildcardCertificate(certificateId);

        if (certResult.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TLSWildcardKeyAndCertificate cert = certResult.get();
        X509Certificate firstCert = cert.certificates().get(0);

        Collection<List<?>> issuerAlternativeNames;
        Collection<List<?>> subjectAlternativeNames;
        try {
            issuerAlternativeNames = firstCert.getIssuerAlternativeNames();
            subjectAlternativeNames = firstCert.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            LOG.error("Could not parse certificate.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.ok(TLSWildcartCertificateResponse.create(
                cert.id(),
                cert.nodeMatcher(),
                buildMatchingNodes(cert.nodeMatcher()),
                cert.sourceType().toString(),
                cert.signature(),
                firstCert.getSigAlgName(),
                buildPrincipalResponse(firstCert.getIssuerX500Principal(), issuerAlternativeNames),
                buildPrincipalResponse(firstCert.getSubjectX500Principal(), subjectAlternativeNames),
                cert.validFrom(),
                cert.expiresAt()
        )).build();
    }

    @PUT
    @Path("/tls/node/{node_id}/regenerate")
    @Operation(operationId = "regenerateNodeTlsCertificate", summary = "Regenerate the TLS certificate of a node",
            description = "Replaces the TLS certificate of a node with a newly generated self-signed certificate "
                    + "that is valid for 12 months, then asks the node to restart its HTTP server. Requires super "
                    + "administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Certificate regenerated and an HTTP server restart requested.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Node not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The certificate could not be generated.", content = @Content)
    public Response regenerateTLSCertificate(@Parameter(description = "Node UUID.") @PathParam("node_id") UUID nodeId) {
        if (nzyme.getNodeManager().getNode(nodeId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        final Crypto crypto = nzyme.getCrypto();
        try {
            crypto.updateTLSCertificateOfNode(
                    nodeId,
                    crypto.generateTLSCertificate(Crypto.DEFAULT_TLS_SUBJECT_DN, 12)
            );
        } catch (Crypto.CryptoOperationException e) {
            LOG.error("Could not generate TLS certificate.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        requestHttpServerRestart(nodeId);

        return Response.ok().build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/tls/test")
    @Operation(operationId = "testTlsCertificate", summary = "Test a TLS certificate and private key",
            description = "Parses a certificate chain and a private key sent as multipart form data and reports "
                    + "whether each of them is readable. Nothing is stored. On success, the details of the first "
                    + "certificate in the chain are returned so you can check it before uploading. Requires super "
                    + "administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Both the certificate chain and the private key are valid.",
            content = @Content(schema = @Schema(implementation = TLSCertificateTestResponse.class)))
    @ApiResponse(responseCode = "401", description = "The certificate chain or the private key could not be read. "
            + "The response body says which one failed.",
            content = @Content(schema = @Schema(implementation = TLSCertificateTestResponse.class)))
    @ApiResponse(responseCode = "500", description = "The uploaded data could not be read, or the certificate "
            + "fingerprint could not be calculated.", content = @Content)
    public Response testNodeTLSCertificate(@Parameter(description = "The certificate chain in PEM format.") @FormDataParam("certificate") InputStream certificate,
                                           @Parameter(description = "The private key in PEM format.") @FormDataParam("private_key") InputStream privateKey) {
        String certificateInput, keyInput;
        try {
            certificateInput = new String(certificate.readAllBytes());
            keyInput = new String(privateKey.readAllBytes());
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        boolean certSuccess;
        List<X509Certificate> certificates = Lists.newArrayList();
        try {
            certificates.addAll(TLSUtils.readCertificateChainFromPEM(certificateInput));
            certSuccess = true;
        } catch(Exception e) {
            certSuccess = false;
            LOG.error("Testing TLS private key failed.", e);
        }

        boolean privateKeySuccess;
        PrivateKey key = null;
        try {
            key = TLSUtils.readKeyFromPEM(keyInput);
            privateKeySuccess = true;
        } catch(Exception e) {
            privateKeySuccess = false;
            LOG.error("Testing TLS private key failed.", e);
        }

        if (certSuccess && privateKeySuccess) {
            // Return cert details.
            X509Certificate firstCert = certificates.get(0);
            String fingerprint;
            Collection<List<?>> issuerAlternativeNames;
            Collection<List<?>> subjectAlternativeNames;
            try {
                fingerprint = TLSUtils.calculateTLSCertificateFingerprint(firstCert);
                issuerAlternativeNames = firstCert.getIssuerAlternativeNames();
                subjectAlternativeNames = firstCert.getSubjectAlternativeNames();
            } catch (NoSuchAlgorithmException | CertificateEncodingException | CertificateParsingException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }

            TLSKeyAndCertificate tls = TLSKeyAndCertificate.create(
                    UUID.randomUUID(), // OK for testing.
                    TLSSourceType.TEST,
                    certificates,
                    key,
                    fingerprint,
                    new DateTime(firstCert.getNotBefore()),
                    new DateTime(firstCert.getNotAfter())
            );

            // Individual certificate response.
            return Response.ok(TLSCertificateTestResponse.create(
                    true,
                    true,
                    TLSCertificateResponse.create(
                            "[test]",
                            tls.sourceType().toString(),
                            "[test]",
                            tls.signature(),
                            firstCert.getSigAlgName(),
                            buildPrincipalResponse(firstCert.getIssuerX500Principal(), issuerAlternativeNames),
                            buildPrincipalResponse(firstCert.getSubjectX500Principal(), subjectAlternativeNames),
                            tls.validFrom(),
                            tls.expiresAt()
                    )
            )).build();
        } else {
            // Return error information.
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(TLSCertificateTestResponse.create(
                            certSuccess, privateKeySuccess, null
                    )).build();
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/tls/node/{node_id}")
    @Operation(operationId = "uploadNodeTlsCertificate", summary = "Upload the TLS certificate of a node",
            description = "Replaces the individual TLS certificate of a node with a certificate chain and private "
                    + "key sent as multipart form data, then asks the node to restart its HTTP server. Test the "
                    + "files first to avoid locking yourself out. Requires super administrator permissions.")
    @ApiResponse(responseCode = "201", description = "Certificate stored and an HTTP server restart requested.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Node not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The certificate chain or the private key could not be read.",
            content = @Content)
    public Response uploadNodeTLSCertificate(@Parameter(description = "Node UUID.") @PathParam("node_id") UUID nodeId,
                                             @Parameter(description = "The certificate chain in PEM format.") @FormDataParam("certificate") InputStream certificate,
                                             @Parameter(description = "The private key in PEM format.") @FormDataParam("private_key") InputStream privateKey) {
        if (nzyme.getNodeManager().getNode(nodeId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TLSKeyAndCertificate tls;

        try {
            tls = TLSUtils.readTLSKeyAndCertificateFromInputStreams(nodeId, TLSSourceType.INDIVIDUAL, certificate, privateKey);
        } catch (TLSUtils.TLSCertificateCreationException e) {
            LOG.error("Could not create TLS certificate.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        nzyme.getCrypto().updateTLSCertificateOfNode(nodeId, tls);

        requestHttpServerRestart(nodeId);

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/tls/wildcard")
    @Operation(operationId = "uploadWildcardTlsCertificate", summary = "Upload a wildcard TLS certificate",
            description = "Stores a certificate chain and private key sent as multipart form data as a wildcard "
                    + "certificate. The node matcher is a regular expression that decides which nodes use this "
                    + "certificate instead of their individual one. All online nodes are asked to restart their HTTP "
                    + "server. Requires super administrator permissions.")
    @ApiResponse(responseCode = "201", description = "Certificate stored and HTTP server restarts requested.",
            content = @Content)
    @ApiResponse(responseCode = "401", description = "The node matcher was empty.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The certificate chain or the private key could not be read.",
            content = @Content)
    public Response uploadWildcardTLSCertificate(@Parameter(description = "Regular expression that matches the names of the nodes this certificate applies to.") @FormDataParam("node_matcher") String nodeMatcher,
                                                 @Parameter(description = "The certificate chain in PEM format.") @FormDataParam("certificate") InputStream certificate,
                                                 @Parameter(description = "The private key in PEM format.") @FormDataParam("private_key") InputStream privateKey) {
        if (nodeMatcher == null || nodeMatcher.trim().isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        TLSWildcardKeyAndCertificate tls;

        try {
            tls = TLSUtils.readTLSWildcardKeyAndCertificateFromInputStreams(nodeMatcher, TLSSourceType.WILDCARD, certificate, privateKey);
        } catch (TLSUtils.TLSCertificateCreationException e) {
            LOG.error("Could not create TLS certificate.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        nzyme.getCrypto().writeTLSWildcardCertificate(tls);

        requestHttpServerRestartAcrossCluster();

        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/tls/wildcard/{cert_id}/replace")
    @Operation(operationId = "replaceWildcardTlsCertificate", summary = "Replace a wildcard TLS certificate",
            description = "Replaces the certificate chain and private key of an existing wildcard certificate with "
                    + "files sent as multipart form data. The node matcher of the existing certificate is kept. All "
                    + "online nodes are asked to restart their HTTP server. Requires super administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "201", description = "Certificate replaced and HTTP server restarts requested.",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Certificate not found.", content = @Content)
    @ApiResponse(responseCode = "500", description = "The certificate chain or the private key could not be read.",
            content = @Content)
    public Response replaceWildcardTLSCertificate(@Parameter(description = "Wildcard certificate ID.") @PathParam("cert_id") long certificateId,
                                                  @Parameter(description = "The certificate chain in PEM format.") @FormDataParam("certificate") InputStream certificate,
                                                  @Parameter(description = "The private key in PEM format.") @FormDataParam("private_key") InputStream privateKey) {
        Optional<TLSWildcardKeyAndCertificate> certResult = nzyme.getCrypto().getTLSWildcardCertificate(certificateId);

        if (certResult.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        TLSWildcardKeyAndCertificate oldCert = certResult.get();
        TLSWildcardKeyAndCertificate newCert;
        try {
            newCert = TLSUtils.readTLSWildcardKeyAndCertificateFromInputStreams(oldCert.nodeMatcher(), TLSSourceType.WILDCARD, certificate, privateKey);
        } catch (TLSUtils.TLSCertificateCreationException e) {
            LOG.error("Could not create TLS certificate.", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        nzyme.getCrypto().replaceTLSWildcardCertificate(certificateId, newCert);

        requestHttpServerRestartAcrossCluster();

        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/tls/wildcard/nodematchertest")
    @Operation(operationId = "testTlsWildcardNodeMatcher", summary = "Test a wildcard node matcher",
            description = "Returns all nodes whose name matches the passed regular expression. Use this to check a "
                    + "node matcher before you store it with a wildcard certificate. The list is empty if nothing "
                    + "matches. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Matching nodes found.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MatchingNodeResponse.class))))
    public Response testTLSWildcardNodeMatcher(@Parameter(description = "Regular expression to match node names against.") @QueryParam("regex") String regex) {
        return Response.ok(buildMatchingNodes(regex)).build();
    }

    @PUT
    @Path("/tls/wildcard/{cert_id}/node_matcher")
    @Operation(operationId = "updateWildcardTlsNodeMatcher",
            summary = "Update the node matcher of a wildcard certificate",
            description = "Changes the regular expression that decides which nodes use this wildcard certificate. "
                    + "All online nodes are asked to restart their HTTP server. Requires super administrator "
                    + "permissions.")
    @ApiResponse(responseCode = "200", description = "Node matcher updated and HTTP server restarts requested.",
            content = @Content)
    @ApiResponse(responseCode = "401", description = "The node matcher was empty.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Certificate not found.", content = @Content)
    public Response updateTLSWildcardCertificateNodeMatcher(@Parameter(description = "Wildcard certificate ID.") @PathParam("cert_id") long certificateId,
                                                            @RequestBody(description = "The new node matcher regular expression.", required = true, content = @Content(mediaType = "application/json"))
                                                            UpdateTLSWildcardNodeMatcherRequest request) {
        Optional<TLSWildcardKeyAndCertificate> certResult = nzyme.getCrypto().getTLSWildcardCertificate(certificateId);

        if (certResult.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (request.nodeMatcher().trim().isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        nzyme.getCrypto().updateTLSWildcardCertificateNodeMatcher(certificateId, request.nodeMatcher());

        requestHttpServerRestartAcrossCluster();

        return Response.ok().build();
    }

    @DELETE
    @Path("/tls/wildcard/{cert_id}")
    @Operation(operationId = "deleteWildcardTlsCertificate", summary = "Delete a wildcard TLS certificate",
            description = "Deletes a wildcard certificate. The nodes it applied to fall back to their individual "
                    + "certificates. All online nodes are asked to restart their HTTP server. Deleting a certificate "
                    + "that does not exist is not an error. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "Certificate deleted and HTTP server restarts requested.",
            content = @Content)
    public Response deleteTLSWildcardCertificate(@Parameter(description = "Wildcard certificate ID.") @PathParam("cert_id") long certificateId) {
        nzyme.getCrypto().deleteTLSWildcardCertificate(certificateId);

        requestHttpServerRestartAcrossCluster();

        return Response.ok().build();
    }

    @PUT
    @Path("/pgp/configuration")
    @Operation(operationId = "updatePgpConfiguration", summary = "Update the PGP configuration",
            description = "Changes PGP settings of the cluster. The body carries a change map of registry keys and "
                    + "their new values. The only supported key is pgp_key_sync_enabled, which controls whether "
                    + "nodes synchronize their PGP keys with each other. Requires super administrator permissions.")
    @ApiResponse(responseCode = "200", description = "PGP configuration updated.", content = @Content)
    @ApiResponse(responseCode = "422", description = "The change map was empty, contained an unknown key, or a value "
            + "did not pass the constraints of its configuration key.", content = @Content)
    public Response update(@RequestBody(description = "Map of PGP configuration keys and their new values.",
            required = true, content = @Content(mediaType = "application/json")) PGPConfigurationUpdateRequest ur) {
        if (ur.change().isEmpty()) {
            LOG.info("Empty configuration parameters.");
            return Response.status(422).build();
        }

        for (Map.Entry<String, Object> c : ur.change().entrySet()) {
            switch (c.getKey()) {
                case "pgp_key_sync_enabled":
                    if (!ConfigurationEntryConstraintValidator.checkConstraints(CryptoRegistryKeys.PGP_KEY_SYNC_ENABLED, c)) {
                        return Response.status(422).build();
                    }
                    nzyme.getDatabaseCoreRegistry().setValue(c.getKey(), c.getValue().toString());
                    break;
                default:
                    LOG.info("Unknown configuration parameter [{}].", c.getKey());
                    return Response.status(422).build();
            }
        }

        return Response.ok().build();
    }

    private TLSCertificatePrincipalResponse buildPrincipalResponse(Principal principal, Collection<List<?>> alternativeNames) {
        List<String> an = Lists.newArrayList();
        if (alternativeNames != null) {
            for (List<?> alternativeName : alternativeNames) {
                an.add((String) alternativeName.get(1));
            }
        }

        String cn;
        Matcher cnMatcher = Pattern.compile("CN=(.+?)(,|$)").matcher(principal.toString());
        if (cnMatcher.find()) {
            cn = cnMatcher.group(1);
        } else {
            cn = null;
        }

        String o;
        Matcher oMatcher = Pattern.compile("O=(.+?)(,|$)").matcher(principal.toString());
        if (oMatcher.find()) {
            o = oMatcher.group(1);
        } else {
            o = null;
        }

        String c;
        Matcher cMatcher = Pattern.compile("C=(.+?)(,|$)").matcher(principal.toString());
        if (cMatcher.find()) {
            c = cMatcher.group(1);
        } else {
            c = null;
        }

        return TLSCertificatePrincipalResponse.create(an, cn, o, c);
    }

    private List<MatchingNodeResponse> buildMatchingNodes(String regex) {
        TLSWildcardNodeMatcher matcher = new TLSWildcardNodeMatcher();
        List<MatchingNodeResponse> matchingNodes = Lists.newArrayList();

        for (Node node : matcher.match(regex, nzyme.getNodeManager().getNodes())) {
            matchingNodes.add(MatchingNodeResponse.create(node.uuid(), node.name()));
        }

        return matchingNodes;
    }

    private void requestHttpServerRestart(UUID nodeId) {
        nzyme.getMessageBus().send(Message.create(
                nodeId,
                MessageType.CHECK_RESTART_HTTP_SERVER,
                Collections.emptyMap(),
                true
        ));
    }

    private void requestHttpServerRestartAcrossCluster() {
        nzyme.getMessageBus().sendToAllOnlineNodes(ClusterMessage.create(
                MessageType.CHECK_RESTART_HTTP_SERVER,
                Collections.emptyMap(),
                true
        ));
    }

}
