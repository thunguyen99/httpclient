/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.client;

import java.net.ProxySelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.BackoffManager;
import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.protocol.RequestAcceptEncoding;
import org.apache.http.client.protocol.RequestAddCookies;
import org.apache.http.client.protocol.RequestAuthCache;
import org.apache.http.client.protocol.RequestClientConnControl;
import org.apache.http.client.protocol.RequestDefaultHeaders;
import org.apache.http.client.protocol.ResponseContentEncoding;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.exec.BackoffStrategyExec;
import org.apache.http.impl.client.exec.ClientExecChain;
import org.apache.http.impl.client.exec.MainClientExec;
import org.apache.http.impl.client.exec.ProtocolExec;
import org.apache.http.impl.client.exec.RedirectExec;
import org.apache.http.impl.client.exec.RetryExec;
import org.apache.http.impl.client.exec.InternalHttpClient;
import org.apache.http.impl.conn.DefaultHttpRoutePlanner;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

/**
 * {@link HttpClient} builder.
 * <p>
 * The following system properties are taken into account by this class
 *  if the {@link #useSystemProperties()} method is called.
 * <ul>
 *  <li>ssl.TrustManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.trustStoreType</li>
 *  <li>javax.net.ssl.trustStore</li>
 *  <li>javax.net.ssl.trustStoreProvider</li>
 *  <li>javax.net.ssl.trustStorePassword</li>
 *  <li>java.home</li>
 *  <li>ssl.KeyManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.keyStoreType</li>
 *  <li>javax.net.ssl.keyStore</li>
 *  <li>javax.net.ssl.keyStoreProvider</li>
 *  <li>javax.net.ssl.keyStorePassword</li>
 *  <li>http.proxyHost</li>
 *  <li>http.proxyPort</li>
 *  <li>http.nonProxyHosts</li>
 *  <li>http.keepAlive</li>
 *  <li>http.maxConnections</li>
 * </ul>
 * </p>
 * @since 4.3
 */
@NotThreadSafe
public class HttpClientBuilder {

    private HttpRequestExecutor requestExec;
    private ClientConnectionManager connManager;
    private ConnectionReuseStrategy reuseStrategy;
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    private AuthenticationStrategy targetAuthStrategy;
    private AuthenticationStrategy proxyAuthStrategy;
    private UserTokenHandler userTokenHandler;

    private HttpProcessor httpprocessor;
    private LinkedList<HttpRequestInterceptor> requestFirst;
    private LinkedList<HttpRequestInterceptor> requestLast;
    private LinkedList<HttpResponseInterceptor> responseFirst;
    private LinkedList<HttpResponseInterceptor> responseLast;

    private HttpRequestRetryHandler retryHandler;

    private HttpRoutePlanner routePlanner;
    private RedirectStrategy redirectStrategy;

    private ConnectionBackoffStrategy connectionBackoffStrategy;
    private BackoffManager backoffManager;

    private boolean systemProperties;
    private boolean laxRedirects;
    private boolean redirectHandlingDisabled;
    private boolean automaticRetriesDisabled;
    private boolean contentCompressionDisabled;
    private boolean cookieManagementDisabled;
    private boolean authCachingDisabled;

    public HttpClientBuilder() {
        super();
    }

    public final HttpRequestExecutor getRequestExecutor() {
        return requestExec;
    }

    public final HttpClientBuilder setRequestExecutor(final HttpRequestExecutor requestExec) {
        this.requestExec = requestExec;
        return this;
    }

    public final ClientConnectionManager getConnectionManager() {
        return connManager;
    }

    public final HttpClientBuilder setConnectionManager(final ClientConnectionManager connManager) {
        this.connManager = connManager;
        return this;
    }

    public final ConnectionReuseStrategy getConnectionReuseStrategy() {
        return reuseStrategy;
    }

    public final HttpClientBuilder setConnectionReuseStrategy(
            final ConnectionReuseStrategy reuseStrategy) {
        this.reuseStrategy = reuseStrategy;
        return this;
    }

    public final ConnectionKeepAliveStrategy getKeepAliveStrategy() {
        return keepAliveStrategy;
    }

    public final HttpClientBuilder setKeepAliveStrategy(
            final ConnectionKeepAliveStrategy keepAliveStrategy) {
        this.keepAliveStrategy = keepAliveStrategy;
        return this;
    }

    public final UserTokenHandler getUserTokenHandler() {
        return userTokenHandler;
    }

    public final HttpClientBuilder setUserTokenHandler(final UserTokenHandler userTokenHandler) {
        this.userTokenHandler = userTokenHandler;
        return this;
    }

    public final AuthenticationStrategy getTargetAuthenticationStrategy() {
        return targetAuthStrategy;
    }

    public final HttpClientBuilder setTargetAuthenticationStrategy(
            final AuthenticationStrategy targetAuthStrategy) {
        this.targetAuthStrategy = targetAuthStrategy;
        return this;
    }

    public final AuthenticationStrategy getProxyAuthenticationStrategy() {
        return proxyAuthStrategy;
    }

    public final HttpClientBuilder setProxyAuthenticationStrategy(
            final AuthenticationStrategy proxyAuthStrategy) {
        this.proxyAuthStrategy = proxyAuthStrategy;
        return this;
    }

    public final HttpProcessor getHttpProcessor() {
        return httpprocessor;
    }

    public final HttpClientBuilder setHttpProcessor(final HttpProcessor httpprocessor) {
        this.httpprocessor = httpprocessor;
        return this;
    }

    public final HttpClientBuilder addResponseInterceptorFirst(
            final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseFirst == null) {
            responseFirst = new LinkedList<HttpResponseInterceptor>();
        }
        responseFirst.addFirst(itcp);
        return this;
    }

    public final HttpClientBuilder addResponseInterceptorLast(
            final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseLast == null) {
            responseLast = new LinkedList<HttpResponseInterceptor>();
        }
        responseLast.addLast(itcp);
        return this;
    }

    public final HttpClientBuilder addRequestInterceptorFirst(
            final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestFirst == null) {
            requestFirst = new LinkedList<HttpRequestInterceptor>();
        }
        requestFirst.addFirst(itcp);
        return this;
    }

    public final HttpClientBuilder addRequestInterceptorLast(
            final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestLast == null) {
            requestLast = new LinkedList<HttpRequestInterceptor>();
        }
        requestLast.addLast(itcp);
        return this;
    }

    public final HttpRequestRetryHandler getRetryHandler() {
        return retryHandler;
    }

    public final HttpClientBuilder setRetryHandler(final HttpRequestRetryHandler retryHandler) {
        this.retryHandler = retryHandler;
        return this;
    }

    public final HttpRoutePlanner getRoutePlanner() {
        return routePlanner;
    }

    public final HttpClientBuilder setRoutePlanner(final HttpRoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
        return this;
    }

    public final RedirectStrategy getRedirectStrategy() {
        return redirectStrategy;
    }

    public final HttpClientBuilder setRedirectStrategy(final RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
        return this;
    }

    public final ConnectionBackoffStrategy getConnectionBackoffStrategy() {
        return connectionBackoffStrategy;
    }

    public final HttpClientBuilder setConnectionBackoffStrategy(
            final ConnectionBackoffStrategy connectionBackoffStrategy) {
        this.connectionBackoffStrategy = connectionBackoffStrategy;
        return this;
    }

    public final BackoffManager getBackoffManager() {
        return backoffManager;
    }

    public final HttpClientBuilder setBackoffManager(final BackoffManager backoffManager) {
        this.backoffManager = backoffManager;
        return this;
    }

    public final HttpClientBuilder disableRedirectHandling() {
        redirectHandlingDisabled = true;
        return this;
    }

    public final HttpClientBuilder disableAutomaticRetries() {
        automaticRetriesDisabled = true;
        return this;
    }

    public final HttpClientBuilder useSystemProperties() {
        systemProperties = true;
        return this;
    }

    public final HttpClientBuilder useLaxRedirects() {
        laxRedirects = true;
        return this;
    }

    protected ClientExecChain decorateMainExec(final ClientExecChain mainExec) {
        return mainExec;
    }

    protected ClientExecChain decorateProtocolExec(final ClientExecChain protocolExec) {
        return protocolExec;
    }

    public HttpClient build() {
        // Create main request executor
        HttpRequestExecutor requestExec = getRequestExecutor();
        if (requestExec == null) {
            requestExec = new HttpRequestExecutor();
        }
        ClientConnectionManager connManager = getConnectionManager();
        if (connManager == null) {
            PoolingClientConnectionManager poolingmgr = new PoolingClientConnectionManager(
                    systemProperties ? SchemeRegistryFactory.createSystemDefault() :
                        SchemeRegistryFactory.createDefault());
            if (systemProperties) {
                String s = System.getProperty("http.keepAlive");
                if ("true".equalsIgnoreCase(s)) {
                    s = System.getProperty("http.maxConnections", "5");
                    int max = Integer.parseInt(s);
                    poolingmgr.setDefaultMaxPerRoute(max);
                    poolingmgr.setMaxTotal(2 * max);
                }
            }
            connManager = poolingmgr;
        }
        ConnectionReuseStrategy reuseStrategy = getConnectionReuseStrategy();
        if (reuseStrategy != null) {
            if (systemProperties) {
                String s = System.getProperty("http.keepAlive");
                if ("true".equalsIgnoreCase(s)) {
                    reuseStrategy = new DefaultConnectionReuseStrategy();
                } else {
                    reuseStrategy = new NoConnectionReuseStrategy();
                }
            } else {
                reuseStrategy = new DefaultConnectionReuseStrategy();
            }
        }
        ConnectionKeepAliveStrategy keepAliveStrategy = getKeepAliveStrategy();
        if (keepAliveStrategy == null) {
            keepAliveStrategy = new DefaultConnectionKeepAliveStrategy();
        }
        AuthenticationStrategy targetAuthStrategy = getTargetAuthenticationStrategy();
        if (targetAuthStrategy == null) {
            targetAuthStrategy = new TargetAuthenticationStrategy();
        }
        AuthenticationStrategy proxyAuthStrategy = getProxyAuthenticationStrategy();
        if (proxyAuthStrategy == null) {
            proxyAuthStrategy = new ProxyAuthenticationStrategy();
        }
        UserTokenHandler userTokenHandler = getUserTokenHandler();
        if (userTokenHandler == null) {
            userTokenHandler = new DefaultUserTokenHandler();
        }
        ClientExecChain execChain = new MainClientExec(
                requestExec,
                connManager,
                reuseStrategy,
                keepAliveStrategy,
                targetAuthStrategy,
                proxyAuthStrategy,
                userTokenHandler);

        execChain = decorateMainExec(execChain);

        HttpProcessor httpprocessor = getHttpProcessor();
        if (httpprocessor == null) {
            ListBuilder<HttpRequestInterceptor> reqlb = new ListBuilder<HttpRequestInterceptor>();
            reqlb.addAll(requestFirst);
            reqlb.addAll(
                    new RequestDefaultHeaders(),
                    new RequestContent(),
                    new RequestTargetHost(),
                    new RequestClientConnControl(),
                    new RequestUserAgent(),
                    new RequestExpectContinue());
            if (!cookieManagementDisabled) {
                reqlb.add(new RequestAddCookies());
            }
            if (!contentCompressionDisabled) {
                reqlb.add(new RequestAcceptEncoding());
            }
            if (!authCachingDisabled) {
                reqlb.add(new RequestAuthCache());
            }
            reqlb.addAll(requestLast);

            ListBuilder<HttpResponseInterceptor> reslb = new ListBuilder<HttpResponseInterceptor>();
            reslb.addAll(responseFirst);
            if (!cookieManagementDisabled) {
                reslb.add(new ResponseProcessCookies());
            }
            if (!contentCompressionDisabled) {
                reslb.add(new ResponseContentEncoding());
            }
            reslb.addAll(responseLast);
            List<HttpRequestInterceptor> reqincps = reqlb.build();
            List<HttpResponseInterceptor> resincps = reslb.build();
            httpprocessor = new ImmutableHttpProcessor(
                    reqincps.toArray(new HttpRequestInterceptor[reqincps.size()]),
                    resincps.toArray(new HttpResponseInterceptor[resincps.size()]));
        }
        execChain = new ProtocolExec(execChain, httpprocessor);

        execChain = decorateProtocolExec(execChain);

        // Add request retry executor, if not disabled
        if (!automaticRetriesDisabled) {
            HttpRequestRetryHandler retryHandler = getRetryHandler();
            if (retryHandler == null) {
                retryHandler = new DefaultHttpRequestRetryHandler();
            }
            execChain = new RetryExec(execChain, retryHandler);
        }

        // Add redirect executor, if not disabled
        HttpRoutePlanner routePlanner = getRoutePlanner();
        if (routePlanner == null) {
            if (systemProperties) {
                routePlanner = new ProxySelectorRoutePlanner(
                        getConnectionManager().getSchemeRegistry(),
                        ProxySelector.getDefault());
            } else {
                routePlanner = new DefaultHttpRoutePlanner(connManager.getSchemeRegistry());
            }
        }
        if (!redirectHandlingDisabled) {
            RedirectStrategy redirectStrategy = getRedirectStrategy();
            if (redirectStrategy == null) {
                if (laxRedirects) {
                    redirectStrategy = new LaxRedirectStrategy();
                } else {
                    redirectStrategy = new DefaultRedirectStrategy();
                }
            }
            execChain = new RedirectExec(execChain, routePlanner, redirectStrategy);
        }

        // Optionally, add connection back-off executor
        BackoffManager backoffManager = getBackoffManager();
        ConnectionBackoffStrategy connectionBackoffStrategy = getConnectionBackoffStrategy();
        if (backoffManager != null && connectionBackoffStrategy != null) {
            execChain = new BackoffStrategyExec(execChain, connectionBackoffStrategy, backoffManager);
        }

        return new InternalHttpClient(execChain, connManager, routePlanner, null);
    }

    static class ListBuilder<E> {

        private final LinkedList<E> list;
        private final Set<Class<?>> uniqueClasses;

        ListBuilder() {
            this.list = new LinkedList<E>();
            this.uniqueClasses = new HashSet<Class<?>>();
        }

        public void add(final E e) {
            if (e == null) {
                return;
            }
            if (!this.uniqueClasses.contains(e.getClass())) {
                this.list.addFirst(e);
                this.uniqueClasses.add(e.getClass());
            }
        }

        public void addAll(final Collection<E> c) {
            if (c == null) {
                return;
            }
            for (E e: c) {
                add(e);
            }
        }

        public void addAll(E... c) {
            if (c == null) {
                return;
            }
            for (E e: c) {
                add(e);
            }
        }

        public List<E> build() {
            return new ArrayList<E>(this.list);
        }

    }

}
