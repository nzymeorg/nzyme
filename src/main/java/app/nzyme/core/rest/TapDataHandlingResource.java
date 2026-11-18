package app.nzyme.core.rest;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TapDataHandlingResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(TapDataHandlingResource.class);

    public static final String SELECTED_ORGANIZATION_HEADER = "X-Nzyme-Organization";
    public static final String SELECTED_TENANT_HEADER = "X-Nzyme-Tenant";

    @Context
    private HttpHeaders httpHeaders;

    protected final List<UUID> parseAndValidateTapIds(AuthenticatedUser requestingUser,
                                                      NzymeNode nzyme,
                                                      String queryParamDataCsv) {
        if (Strings.isNullOrEmpty(queryParamDataCsv)) {
            return Lists.newArrayList();
        }

        if (queryParamDataCsv.equals("*")) {
            return allAccessibleTapIds(requestingUser, nzyme);
        }

        return parseAndValidateTapIds(
                requestingUser,
                Splitter.on(",").splitToList(queryParamDataCsv),
                nzyme.getTapManager().allTapUUIDsAccessibleByUser(requestingUser)
        );
    }

    protected final List<UUID> allAccessibleTapIds(AuthenticatedUser requestingUser, NzymeNode nzyme) {
        String organization = httpHeaders == null ? null : httpHeaders.getHeaderString(SELECTED_ORGANIZATION_HEADER);
        String tenant = httpHeaders == null ? null : httpHeaders.getHeaderString(SELECTED_TENANT_HEADER);
        if (Strings.isNullOrEmpty(organization) && Strings.isNullOrEmpty(tenant)) {
            return nzyme.getTapManager().allTapUUIDsAccessibleByUser(requestingUser);
        }

        if (Strings.isNullOrEmpty(organization) || Strings.isNullOrEmpty(tenant)) {
            throw new IllegalArgumentException("Organization and tenant must be selected together.");
        }

        return nzyme.getTapManager().allTapUUIDsAccessibleByUser(
                requestingUser, UUID.fromString(organization.trim()), UUID.fromString(tenant.trim())
        );
    }

    protected List<UUID> parseAndValidateTapIds(AuthenticatedUser requestingUser,
                                                NzymeNode nzyme,
                                                List<String> tapIds) {
        return parseAndValidateTapIds(
                requestingUser, tapIds, nzyme.getTapManager().allTapUUIDsAccessibleByUser(requestingUser)
        );
    }

    protected List<UUID> parseAndValidateTapIdsDirect(AuthenticatedUser requestingUser,
                                                      NzymeNode nzyme,
                                                      List<UUID> tapIds) {
        return parseAndValidateTapIds(
                requestingUser,
                tapIds.stream().map(UUID::toString).collect(Collectors.toList()),
                nzyme.getTapManager().allTapUUIDsAccessibleByUser(requestingUser)
        );
    }

    protected boolean tapIdAccessible(AuthenticatedUser requestingUser,
                                     NzymeNode nzyme,
                                     UUID tapId) {
        return nzyme.getTapManager()
                .allTapUUIDsAccessibleByUser(requestingUser)
                .contains(tapId);
    }

    private List<UUID> parseAndValidateTapIds(AuthenticatedUser requestingUser,
                                              List<String> tapIds,
                                              List<UUID> userAccessibleTaps) {
        List<UUID> uuids = Lists.newArrayList();
        for (String id : tapIds) {
            UUID tapUuid = UUID.fromString(id);

            if (userAccessibleTaps.contains(tapUuid)) {
                uuids.add(tapUuid);
            } else {
                LOG.debug("User [{}] requested data from tap <{}> but they are not allowed to access it. Skipping.",
                        requestingUser.getEmail(), tapUuid);
            }
        }

        return uuids;
    }

}
