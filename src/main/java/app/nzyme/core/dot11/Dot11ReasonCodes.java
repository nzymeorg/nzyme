package app.nzyme.core.dot11;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Reason codes of deauthentication and disassociation frames as listed in IEEE 802.11-2020, table 9-49.
 */
public class Dot11ReasonCodes {

    private static final Map<Integer, String> NAMES = ImmutableMap.<Integer, String>builder()
            .put(0, "Reserved")
            .put(1, "Unspecified reason")
            .put(2, "Previous authentication no longer valid")
            .put(3, "Deauthenticated because sending station is leaving or has left")
            .put(4, "Disassociated due to inactivity")
            .put(5, "Disassociated because AP is unable to handle all currently associated stations")
            .put(6, "Class 2 frame received from nonauthenticated station")
            .put(7, "Class 3 frame received from nonassociated station")
            .put(8, "Disassociated because sending station is leaving or has left BSS")
            .put(9, "Station requesting (re)association is not authenticated with responding station")
            .put(10, "Disassociated because the information in the Power Capability element is unacceptable")
            .put(11, "Disassociated because the information in the Supported Channels element is unacceptable")
            .put(12, "Disassociated due to BSS transition management")
            .put(13, "Invalid element")
            .put(14, "Message integrity code (MIC) failure")
            .put(15, "4-way handshake timeout")
            .put(16, "Group key handshake timeout")
            .put(17, "Element in 4-way handshake different from (Re)Association Request, Probe Response or Beacon frame")
            .put(18, "Invalid group cipher")
            .put(19, "Invalid pairwise cipher")
            .put(20, "Invalid AKMP")
            .put(21, "Unsupported RSNE version")
            .put(22, "Invalid RSNE capabilities")
            .put(23, "IEEE 802.1X authentication failed")
            .put(24, "Cipher suite rejected because of the security policy")
            .put(25, "TDLS direct-link teardown due to TDLS peer station unreachable via the TDLS direct link")
            .put(26, "TDLS direct-link teardown for unspecified reason")
            .put(27, "Disassociated because session terminated by SSP request")
            .put(28, "Disassociated because of lack of SSP roaming agreement")
            .put(29, "Requested service rejected because of SSP cipher suite or AKM requirement")
            .put(30, "Requested service not authorized in this location")
            .put(31, "TS deleted because QoS AP lacks sufficient bandwidth for this QoS station")
            .put(32, "Disassociated for unspecified, QoS-related reason")
            .put(33, "Disassociated because QoS AP lacks sufficient bandwidth for this QoS station")
            .put(34, "Disassociated because excessive number of frames need to be acknowledged but are not acknowledged")
            .put(35, "Disassociated because station is transmitting outside the limits of its TXOPs")
            .put(36, "Requesting station is leaving the BSS or resetting")
            .put(37, "Requesting station is no longer using the stream or session")
            .put(38, "Requesting station received frames using a mechanism for which a setup has not been completed")
            .put(39, "Requested from peer station due to timeout")
            .put(45, "Peer station does not support the requested cipher suite")
            .put(46, "Disassociated because authorized access limit reached")
            .put(47, "Disassociated due to external service requirements")
            .put(48, "Invalid FT Action frame count")
            .put(49, "Invalid pairwise master key identifier (PMKID)")
            .put(50, "Invalid MDE")
            .put(51, "Invalid FTE")
            .put(52, "Mesh peering canceled for unknown reasons")
            .put(53, "Mesh station has reached the supported maximum number of peer mesh stations")
            .put(54, "Received information violates the mesh configuration policy")
            .put(55, "Mesh station received a Mesh Peering Close frame requesting to close the peering")
            .put(56, "Mesh station resent the maximum number of Mesh Peering Open frames without confirmation")
            .put(57, "Mesh peering confirm timer expired")
            .put(58, "Mesh station failed to unwrap the GTK or the wrapped contents do not match")
            .put(59, "Mesh station received inconsistent mesh parameters between peering frames")
            .put(60, "Authenticated mesh peering exchange failed because no cipher suite could be selected")
            .put(61, "Mesh station has no proxy information for this external destination")
            .put(62, "Mesh station has no forwarding information for this destination")
            .put(63, "Mesh station determined the link to the next hop is no longer usable")
            .put(64, "Deauthenticated because the MAC address already exists in the mesh BSS")
            .put(65, "Mesh station performs channel switch to meet regulatory requirements")
            .put(66, "Mesh station performs channel switch for unspecified reason")
            .put(67, "Transmission link establishment in alternative channel failed")
            .put(68, "The alternative channel is occupied")
            .build();

    public static String name(int code) {
        String name = NAMES.get(code);

        if (name != null) {
            return name;
        }

        return code <= 65535 ? "Reserved" : "Invalid";
    }

}
