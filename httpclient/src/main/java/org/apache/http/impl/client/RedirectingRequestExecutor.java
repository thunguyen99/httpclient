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

import java.io.IOException;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthState;
import org.apache.http.client.HttpClientRequestExecutor;
import org.apache.http.client.RedirectException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.client.params.ClientPNames#HANDLE_REDIRECTS}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#MAX_REDIRECTS}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#ALLOW_CIRCULAR_REDIRECTS}</li>
 * </ul>
 *
 * @since 4.3
 */
@ThreadSafe
public class RedirectingRequestExecutor implements HttpClientRequestExecutor {

    private final Log log = LogFactory.getLog(getClass());

    private final HttpClientRequestExecutor requestExecutor;
    private final RedirectStrategy redirectStrategy;
    private final HttpRoutePlanner routePlanner;

    public RedirectingRequestExecutor(
            final HttpClientRequestExecutor requestExecutor,
            final HttpRoutePlanner routePlanner,
            final RedirectStrategy redirectStrategy) {
        super();
        if (requestExecutor == null) {
            throw new IllegalArgumentException("HTTP client request executor may not be null");
        }
        if (routePlanner == null) {
            throw new IllegalArgumentException("HTTP route planner may not be null");
        }
        if (redirectStrategy == null) {
            throw new IllegalArgumentException("HTTP redirect strategy may not be null");
        }
        this.requestExecutor = requestExecutor;
        this.routePlanner = routePlanner;
        this.redirectStrategy = redirectStrategy;
    }

    public HttpResponse execute(
            final HttpRoute route,
            final HttpUriRequest request,
            final HttpContext context) throws IOException, HttpException {
        HttpParams params = request.getParams();
        int redirectCount = 0;
        int maxRedirects = params.getIntParameter(ClientPNames.MAX_REDIRECTS, 100);
        HttpRoute currentRoute = route;
        HttpUriRequest currentRequest = request;
        for (;;) {
            HttpResponse response = requestExecutor.execute(currentRoute, currentRequest, context);
            if (HttpClientParams.isRedirecting(params) &&
                    this.redirectStrategy.isRedirected(currentRequest, response, context)) {

                if (redirectCount >= maxRedirects) {
                    throw new RedirectException("Maximum redirects ("+ maxRedirects + ") exceeded");
                }
                redirectCount++;

                currentRequest = this.redirectStrategy.getRedirect(currentRequest, response, context);
                currentRequest.setHeaders(request.getAllHeaders());
                currentRequest.setParams(params);

                URI uri = currentRequest.getURI();
                if (uri.getHost() == null) {
                    throw new ProtocolException("Redirect URI does not specify a valid host name: " +
                            uri);
                }

                HttpHost newTarget = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());

                // Reset auth states if redirecting to another host
                if (!currentRoute.getTargetHost().equals(newTarget)) {
                    AuthState targetAuthState = (AuthState) context.getAttribute(
                            ClientContext.TARGET_AUTH_STATE);
                    if (targetAuthState != null) {
                        this.log.debug("Resetting target auth state");
                        targetAuthState.reset();
                    }
                    AuthState proxyAuthState = (AuthState) context.getAttribute(
                            ClientContext.PROXY_AUTH_STATE);
                    if (proxyAuthState != null) {
                        AuthScheme authScheme = proxyAuthState.getAuthScheme();
                        if (authScheme != null && authScheme.isConnectionBased()) {
                            this.log.debug("Resetting proxy auth state");
                            proxyAuthState.reset();
                        }
                    }
                }

                currentRoute = this.routePlanner.determineRoute(newTarget, currentRequest, context);
                if (this.log.isDebugEnabled()) {
                    this.log.debug("Redirecting to '" + uri + "' via " + currentRoute);
                }
            } else {
                return response;
            }
        }
    }

}
