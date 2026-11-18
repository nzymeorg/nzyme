package app.nzyme.core.ethernet.segmentation;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static app.nzyme.core.ethernet.segmentation.PolicyRule.SelectorType.*;
import static app.nzyme.core.ethernet.segmentation.SegmentationTestData.*;
import static org.testng.Assert.*;

public class PolicyReferencesTest {

    private static final Segment GUEST = internal(1, "Guest", "192.168.50.0/24");
    private static final Segment SERVERS = internal(2, "Servers", "10.1.0.0/16");
    private static final Segment PRINTERS = internal(3, "Printers", "10.2.0.0/24");
    private static final Segment UNUSED = internal(4, "Unused", "10.3.0.0/24");

    private static Policy policy(long id, String name, PolicyRule scope, PolicyRule... allowed) {
        return new Policy(id, new UUID(1, id), UUID.randomUUID(), UUID.randomUUID(), name, null, PolicyStatus.ACTIVE,
                false, false, scope, List.of(allowed), DateTime.now(), DateTime.now(), null, null, null, null, List.of(), null);
    }

    @Test
    public void segmentsAreHeldByThePoliciesThatNameThemAnywhere() {
        Policy isolation = policy(1, "Guest isolation",
                rule(List.of(segmentSelector(GUEST)), List.of(selector(INTERNAL, null)), none()),
                rule(none(), List.of(segmentSelector(PRINTERS)), List.of(tcp(631))));
        Policy access = policy(2, "Access to servers",
                rule(none(), List.of(segmentSelector(SERVERS)), none()),
                rule(List.of(segmentSelector(GUEST)), none(), List.of(tcp(443))),
                rule(List.of(segmentSelector(GUEST)), none(), List.of(tcp(80))));

        Map<UUID, List<String>> references = Policies.segmentReferences(List.of(isolation, access));

        assertEquals(references.get(GUEST.uuid()), List.of("Guest isolation", "Access to servers"));
        assertEquals(references.get(PRINTERS.uuid()), List.of("Guest isolation"));
        assertEquals(references.get(SERVERS.uuid()), List.of("Access to servers"));
        assertFalse(references.containsKey(UNUSED.uuid()));
    }

    @Test
    public void onlySegmentSelectorsHoldASegment() {
        Policy byAddress = policy(1, "By address",
                rule(List.of(selector(ADDRESS, "192.168.50.0/24")), List.of(selector(TAG, GUEST.uuid().toString())), none()));

        assertTrue(Policies.segmentReferences(List.of(byAddress)).isEmpty());
        assertTrue(Policies.segmentReferences(List.of()).isEmpty());
    }

    @Test
    public void whatThePreviousDefinitionNamesIsHeldAndResolvedForStoredSessions() {
        UUID now = UUID.randomUUID();
        UUID before = UUID.randomUUID();
        UUID tag = UUID.randomUUID();
        PolicyRule scope = rule(List.of(segmentSelector(GUEST)), List.of(selector(ASSET, now.toString())), none());
        PolicyRule allowedBefore = rule(List.of(selector(ASSET, before.toString()), segmentSelector(PRINTERS),
                selector(TAG, tag.toString())), none(), List.of(tcp(22)));

        Policy changed = new Policy(1, new UUID(1, 1), UUID.randomUUID(), UUID.randomUUID(), "Changed", null,
                PolicyStatus.ACTIVE, false, false, scope, List.of(), DateTime.now(), DateTime.now(), DateTime.now(),
                DateTime.now(), null, scope, List.of(allowedBefore), null);

        Policy unchanged = policy(2, "Unchanged", scope);

        assertEquals(Policies.referencedAssets(List.of(changed)), Set.of(now, before));
        assertEquals(Policies.referencedAssets(List.of(unchanged)), Set.of(now));

        assertEquals(Policies.segmentReferences(List.of(changed, unchanged)).get(PRINTERS.uuid()), List.of("Changed"));
        assertTrue(Policies.references(changed, TAG, tag.toString()));
        assertFalse(Policies.references(unchanged, TAG, tag.toString()));
    }

    @Test
    public void onlyAnotherScopeOrARemovedCrossingForbidsMore() {
        PolicyRule scope = rule(List.of(segmentSelector(GUEST)), List.of(selector(INTERNAL, null)), none());
        PolicyRule https = rule(none(), none(), List.of(tcp(443)));
        PolicyRule printing = rule(none(), List.of(segmentSelector(PRINTERS)), List.of(tcp(631)));

        assertFalse(Policies.forbidsMore(scope, List.of(https), scope, List.of(https)));
        assertFalse(Policies.forbidsMore(scope, List.of(printing, https), scope, List.of(https)));

        assertTrue(Policies.forbidsMore(scope, List.of(), scope, List.of(https)));
        assertTrue(Policies.forbidsMore(scope, List.of(printing), scope, List.of(https)));
        assertTrue(Policies.forbidsMore(rule(List.of(segmentSelector(GUEST)), none(), none()), List.of(https),
                scope, List.of(https)));
    }

    @Test
    public void aBulkSelectionOfNothingCheckedIsEmptyAndOneOfNoFiltersIsEveryRow() {
        assertTrue(BulkSelection.checked(List.of()).isEmpty());
        assertTrue(BulkSelection.checked(List.of()).isChecked());
        assertFalse(BulkSelection.checked(List.of(UUID.randomUUID())).isEmpty());

        BulkSelection all = BulkSelection.matching(Findings.NO_FILTERS);
        assertFalse(all.isEmpty());
        assertFalse(all.isChecked());
    }

    @Test
    public void anAllowedCrossingMustNameSomething() {
        assertTrue(Policies.allowsEverything(rule(none(), none(), none())));
        assertTrue(Policies.allowsEverything(rule(List.of(any()), List.of(any()), none())));
        assertTrue(Policies.allowsEverything(rule(List.of(any(), segmentSelector(GUEST)), none(), none())));

        assertFalse(Policies.allowsEverything(rule(List.of(segmentSelector(GUEST)), none(), none())));
        assertFalse(Policies.allowsEverything(rule(none(), List.of(selector(INTERNAL, null)), none())));
        assertFalse(Policies.allowsEverything(rule(none(), none(), List.of(tcp(443)))));
    }

    @Test
    public void onlyTheCrossingThatSaysSoMayAllowEverything() {
        assertTrue(Policies.saysEverything(rule(List.of(any()), List.of(any()), none())));

        assertFalse(Policies.saysEverything(rule(none(), none(), none())));
        assertFalse(Policies.saysEverything(rule(List.of(any()), none(), none())));
        assertFalse(Policies.saysEverything(rule(none(), List.of(any()), none())));
        assertFalse(Policies.saysEverything(rule(List.of(any(), segmentSelector(GUEST)), List.of(any()), none())));

        assertFalse(Policies.saysEverything(rule(List.of(any()), List.of(any()), List.of(tcp(443)))));
        assertFalse(Policies.allowsEverything(rule(List.of(any()), List.of(any()), List.of(tcp(443)))));
    }

}
