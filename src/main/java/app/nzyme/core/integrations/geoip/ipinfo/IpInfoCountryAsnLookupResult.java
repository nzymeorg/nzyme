package app.nzyme.core.integrations.geoip.ipinfo;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

public class IpInfoCountryAsnLookupResult {

    private static final Logger LOG = LogManager.getLogger(IpInfoCountryAsnLookupResult.class);

    private final String countryCode;
    private final String countryName;
    private final String asName;
    private final String asNumber;
    private final String asDomain;
    private final String city;
    private final String region;
    private final String postalCode;
    private final String timezone;
    private final Float latitude;
    private final Float longitude;

    @MaxMindDbConstructor
    public IpInfoCountryAsnLookupResult(@MaxMindDbParameter(name="country") String country,
                                        @MaxMindDbParameter(name="country_code") String countryCodeKey,
                                        @MaxMindDbParameter(name="country_name") String countryNameKey,
                                        @MaxMindDbParameter(name="as_name") String asName,
                                        @MaxMindDbParameter(name="asn") String asNumber,
                                        @MaxMindDbParameter(name="as_domain") String asDomain,
                                        @MaxMindDbParameter(name="city") String city,
                                        @MaxMindDbParameter(name="region") String region,
                                        @MaxMindDbParameter(name="postal_code") String postalCode,
                                        @MaxMindDbParameter(name="timezone") String timezone,
                                        @MaxMindDbParameter(name="latitude") Object latitude,
                                        @MaxMindDbParameter(name="longitude") Object longitude,
                                        @MaxMindDbParameter(name="lat") Object lat,
                                        @MaxMindDbParameter(name="lng") Object lng,
                                        @MaxMindDbParameter(name="City") String mmCity,
                                        @MaxMindDbParameter(name="Country") String mmCountryCode,
                                        @MaxMindDbParameter(name="Country_name") String mmCountryName,
                                        @MaxMindDbParameter(name="Region_name") String mmRegionName,
                                        @MaxMindDbParameter(name="Postal") String mmPostal,
                                        @MaxMindDbParameter(name="Timezone") String mmTimezone,
                                        @MaxMindDbParameter(name="Lat") Object mmLat,
                                        @MaxMindDbParameter(name="Lng") Object mmLng) {
        country = emptyToNull(country);
        countryCodeKey = emptyToNull(countryCodeKey);
        countryNameKey = emptyToNull(countryNameKey);
        mmCountryCode = emptyToNull(mmCountryCode);
        mmCountryName = emptyToNull(mmCountryName);

        if (countryCodeKey != null) {
            this.countryCode = countryCodeKey;
            this.countryName = country != null ? country : countryNameKey;
        } else if (mmCountryCode != null) {
            this.countryCode = mmCountryCode;
            this.countryName = mmCountryName;
        } else if (country != null && !looksLikeCountryCode(country)) {
            this.countryCode = null;
            this.countryName = country;
        } else {
            this.countryCode = country;
            this.countryName = countryNameKey;
        }

        this.asName = emptyToNull(asName);
        this.asNumber = emptyToNull(asNumber);
        this.asDomain = emptyToNull(asDomain);
        this.city = firstNonNull(emptyToNull(city), emptyToNull(mmCity));
        this.region = firstNonNull(emptyToNull(region), emptyToNull(mmRegionName));
        this.postalCode = firstNonNull(emptyToNull(postalCode), emptyToNull(mmPostal));
        this.timezone = firstNonNull(emptyToNull(timezone), emptyToNull(mmTimezone));
        this.latitude = firstNonNull(toFloat("latitude", latitude),
                firstNonNull(toFloat("lat", lat), toFloat("Lat", mmLat)));
        this.longitude = firstNonNull(toFloat("longitude", longitude),
                firstNonNull(toFloat("lng", lng), toFloat("Lng", mmLng)));
    }

    private static boolean looksLikeCountryCode(String s) {
        return s.length() == 2 && Character.isLetter(s.charAt(0)) && Character.isLetter(s.charAt(1));
    }

    @Nullable
    private static <T> T firstNonNull(@Nullable T a, @Nullable T b) {
        return a != null ? a : b;
    }

    @Nullable
    private static Float toFloat(String name, Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }

        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) {
                return null;
            }

            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException e) {
                LOG.debug("Could not parse IPinfo [{}] value [{}] as float.", name, s);
                return null;
            }
        }

        LOG.debug("Unexpected IPinfo [{}] value type [{}].", name, value.getClass().getName());
        return null;
    }

    @Nullable
    private static String emptyToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }

        return s;
    }

    @Nullable
    public String getCountryName() {
        return countryName;
    }

    @Nullable
    public String getCountryCode() {
        return countryCode;
    }

    @Nullable
    public String getAsName() {
        return asName;
    }

    @Nullable
    public String getAsNumber() {
        return asNumber;
    }

    @Nullable
    public String getAsDomain() {
        return asDomain;
    }

    @Nullable
    public String getCity() {
        return city;
    }

    @Nullable
    public String getRegion() {
        return region;
    }

    @Nullable
    public String getPostalCode() {
        return postalCode;
    }

    @Nullable
    public String getTimezone() {
        return timezone;
    }

    @Nullable
    public Float getLatitude() {
        return latitude;
    }

    @Nullable
    public Float getLongitude() {
        return longitude;
    }

}
