package app.nzyme.core.rest.authentication;

import app.nzyme.core.dashboards.db.DisplayEntry;
import jakarta.annotation.Nullable;

import java.security.Principal;

public class AuthenticatedDisplay implements Principal {

    private final DisplayEntry display;

    @Nullable
    private final String remoteAddress;

    public AuthenticatedDisplay(DisplayEntry display, @Nullable String remoteAddress) {
        this.display = display;
        this.remoteAddress = remoteAddress;
    }

    public DisplayEntry getDisplay() {
        return display;
    }

    @Nullable
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public String getName() {
        return display.settings().name();
    }

}
