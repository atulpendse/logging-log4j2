/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.net.ssl.LaxHostnameVerifier;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfigurationFactory;
import org.apache.logging.log4j.core.util.AuthorizationProvider;
import org.apache.logging.log4j.core.util.BasicAuthorizationProvider;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.apache.logging.log4j.util.Strings;

/**
 * Constructs an HTTPURLConnection. This class should be considered to be internal
 */
public class UrlConnectionFactory {

    private static final int DEFAULT_TIMEOUT = 60000;
    private static final int connectTimeoutMillis = DEFAULT_TIMEOUT;
    private static final int readTimeoutMillis = DEFAULT_TIMEOUT;
    private static final String JSON = "application/json";
    private static final String XML = "application/xml";
    private static final String PROPERTIES = "text/x-java-properties";
    private static final String TEXT = "text/plain";
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String NO_PROTOCOLS = "_none";
    public static final String ALLOWED_PROTOCOLS = "log4j2.Configuration.allowedProtocols";

    public static HttpURLConnection createConnection(final URL url, final long lastModifiedMillis, final SslConfiguration sslConfiguration)
        throws IOException {
        PropertiesUtil props = PropertiesUtil.getProperties();
        List<String> allowed = Arrays.asList(Strings.splitList(props
                .getStringProperty(ALLOWED_PROTOCOLS, HTTPS).toLowerCase(Locale.ROOT)));
        if (allowed.size() == 1 && NO_PROTOCOLS.equals(allowed.get(0))) {
            throw new ProtocolException("No external protocols have been enabled");
        }
        String protocol = url.getProtocol();
        if (protocol == null) {
            throw new ProtocolException("No protocol was specified on " + url.toString());
        }
        if (!allowed.contains(protocol)) {
            throw new ProtocolException("Protocol " + protocol + " has not been enabled as an allowed protocol");
        }
        final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        final AuthorizationProvider provider = ConfigurationFactory.authorizationProvider(props);
        if (provider != null) {
            provider.addAuthorization(urlConnection);
        }
        urlConnection.setAllowUserInteraction(false);
        urlConnection.setDoOutput(true);
        urlConnection.setDoInput(true);
        urlConnection.setRequestMethod("GET");
        if (connectTimeoutMillis > 0) {
            urlConnection.setConnectTimeout(connectTimeoutMillis);
        }
        if (readTimeoutMillis > 0) {
            urlConnection.setReadTimeout(readTimeoutMillis);
        }
        final String[] fileParts = url.getFile().split("\\.");
        final String type = fileParts[fileParts.length - 1].trim();
        final String contentType = isXml(type) ? XML : isJson(type) ? JSON : isProperties(type) ? PROPERTIES : TEXT;
        urlConnection.setRequestProperty("Content-Type", contentType);
        if (lastModifiedMillis > 0) {
            urlConnection.setIfModifiedSince(lastModifiedMillis);
        }
        if (url.getProtocol().equals(HTTPS) && sslConfiguration != null) {
            ((HttpsURLConnection) urlConnection).setSSLSocketFactory(sslConfiguration.getSslSocketFactory());
            if (!sslConfiguration.isVerifyHostName()) {
                ((HttpsURLConnection) urlConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
            }
        }
        return urlConnection;
    }

    public static URLConnection createConnection(final URL url) throws IOException {
        URLConnection urlConnection = null;
        if (url.getProtocol().equals(HTTPS) || url.getProtocol().equals(HTTP)) {
            urlConnection = createConnection(url, 0, SslConfigurationFactory.getSslConfiguration());
        } else {
            urlConnection = url.openConnection();
        }
        return urlConnection;
    }


    private static boolean isXml(final String type) {
        return type.equalsIgnoreCase("xml");
    }

    private static boolean isJson(final String type) {
        return type.equalsIgnoreCase("json") || type.equalsIgnoreCase("jsn");
    }

    private static boolean isProperties(final String type) {
        return type.equalsIgnoreCase("properties");
    }
}
