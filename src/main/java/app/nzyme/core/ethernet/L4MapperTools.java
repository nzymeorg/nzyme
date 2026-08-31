package app.nzyme.core.ethernet;

import app.nzyme.core.ethernet.l4.db.L4AddressAttributes;
import app.nzyme.core.ethernet.l4.db.L4AddressData;

import java.sql.ResultSet;
import java.sql.SQLException;

public class L4MapperTools {

    public static L4AddressData fieldsToAddressData(String prefix, ResultSet rs) throws SQLException  {
        return L4AddressData.create(
                rs.getString(prefix + "_mac"),
                rs.getString(prefix + "_address"),
                rs.getInt(prefix + "_port"),
                GeoData.create(
                        rs.getInt(prefix + "_address_geo_asn_number"),
                        rs.getString(prefix + "_address_geo_asn_name"),
                        rs.getString(prefix + "_address_geo_asn_domain"),
                        rs.getString(prefix + "_address_geo_city"),
                        rs.getString(prefix + "_address_geo_country_code"),
                        rs.getFloat(prefix + "_address_geo_latitude"),
                        rs.getFloat(prefix + "_address_geo_longitude")
                ),
                L4AddressAttributes.create(
                        rs.getBoolean(prefix + "_address_is_site_local"),
                        rs.getBoolean(prefix + "_address_is_loopback"),
                        rs.getBoolean(prefix + "_address_is_multicast")
                )
        );
    }

    public static L4AddressData fieldsToAddressDataNoMac(String prefix, ResultSet rs) throws SQLException  {
        return L4AddressData.create(
                null,
                rs.getString(prefix + "_address"),
                rs.getInt(prefix + "_port"),
                GeoData.create(
                        rs.getInt(prefix + "_address_geo_asn_number"),
                        rs.getString(prefix + "_address_geo_asn_name"),
                        rs.getString(prefix + "_address_geo_asn_domain"),
                        rs.getString(prefix + "_address_geo_city"),
                        rs.getString(prefix + "_address_geo_country_code"),
                        rs.getFloat(prefix + "_address_geo_latitude"),
                        rs.getFloat(prefix + "_address_geo_longitude")
                ),
                L4AddressAttributes.create(
                        rs.getBoolean(prefix + "_address_is_site_local"),
                        rs.getBoolean(prefix + "_address_is_loopback"),
                        rs.getBoolean(prefix + "_address_is_multicast")
                )
        );
    }

    public static L4AddressData fieldsToAddressDataNoMacNoPort(String prefix, ResultSet rs) throws SQLException  {
        return L4AddressData.create(
                null,
                rs.getString(prefix + "_address"),
                null,
                GeoData.create(
                        rs.getInt(prefix + "_address_geo_asn_number"),
                        rs.getString(prefix + "_address_geo_asn_name"),
                        rs.getString(prefix + "_address_geo_asn_domain"),
                        rs.getString(prefix + "_address_geo_city"),
                        rs.getString(prefix + "_address_geo_country_code"),
                        rs.getFloat(prefix + "_address_geo_latitude"),
                        rs.getFloat(prefix + "_address_geo_longitude")
                ),
                L4AddressAttributes.create(
                        rs.getBoolean(prefix + "_address_is_site_local"),
                        rs.getBoolean(prefix + "_address_is_loopback"),
                        rs.getBoolean(prefix + "_address_is_multicast")
                )
        );
    }

}
