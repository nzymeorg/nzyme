package app.nzyme.core.rest;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.rest.authentication.AuthenticatedUser;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class TapDataHandlingResource extends UserAuthenticatedResource {

    private static final Logger LOG = LogManager.getLogger(TapDataHandlingResource.class);

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
        Optional<SelectedTenant> selected = selectedTenant();
        if (selected.isEmpty()) {
            return nzyme.getTapManager().allTapUUIDsAccessibleByUser(requestingUser);
        }

        return nzyme.getTapManager().allTapUUIDsAccessibleByUser(
                requestingUser, selected.get().organizationId(), selected.get().tenantId()
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
