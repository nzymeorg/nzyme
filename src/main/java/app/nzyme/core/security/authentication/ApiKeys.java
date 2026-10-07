/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
package app.nzyme.core.security.authentication;

import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class ApiKeys {

    public static final String PREFIX = "nzk_";

    public static String generate() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return PREFIX + BaseEncoding.base64Url().omitPadding().encode(raw);
    }

    public static String hash(String plaintextKey) {
        return Hashing.sha256().hashString(plaintextKey, StandardCharsets.UTF_8).toString();
    }

    public static boolean isApiKey(String bearerToken) {
        return bearerToken != null && bearerToken.startsWith(PREFIX);
    }

}
