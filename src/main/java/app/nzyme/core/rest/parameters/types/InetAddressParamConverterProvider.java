package app.nzyme.core.rest.parameters.types;

import com.google.common.net.InetAddresses;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.InetAddress;

@Provider
public class InetAddressParamConverterProvider implements ParamConverterProvider {
    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != InetAddress.class) return null;

        return (ParamConverter<T>) new ParamConverter<InetAddress>() {
            @Override public InetAddress fromString(String value) {
                // A zone like fe80::1%eth0 is not valid in SQL.
                if (value == null || value.indexOf('%') >= 0) {
                    throw new BadRequestException("Invalid IP address: [" + value + "]");
                }

                try {
                    return InetAddress.ofLiteral(value);
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid IP address: [" + value + "]");
                }
            }
            @Override public String toString(InetAddress value) {
                return InetAddresses.toAddrString(value);
            }
        };
    }
}
