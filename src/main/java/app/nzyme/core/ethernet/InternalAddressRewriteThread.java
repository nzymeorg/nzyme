package app.nzyme.core.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.periodicals.Periodical;

public class InternalAddressRewriteThread extends Periodical {

    // Shorter than the interval of the periodical, so that runs do not pile up.
    private static final long BUDGET_SECONDS = 20;

    private final NzymeNode nzyme;

    public InternalAddressRewriteThread(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    @Override
    protected void execute() {
        nzyme.getEthernet().internalAddressRewrites().run(BUDGET_SECONDS);
    }

    @Override
    public String getName() {
        return "InternalAddressRewriteThread";
    }

}
