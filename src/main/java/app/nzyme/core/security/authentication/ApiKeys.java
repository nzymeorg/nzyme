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

public class ApiKeys {

    public static final String PREFIX = "nzk_";

    public static String generate() {
        return BearerTokens.generate(PREFIX);
    }

    public static String hash(String plaintextKey) {
        return BearerTokens.hash(plaintextKey);
    }

    public static boolean isApiKey(String bearerToken) {
        return bearerToken != null && bearerToken.startsWith(PREFIX);
    }

}
