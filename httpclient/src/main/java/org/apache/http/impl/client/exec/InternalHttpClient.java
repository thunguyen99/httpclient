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

package org.apache.http.impl.client.exec;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.auth.AuthState;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.AbstractBasicHttpClient;
import org.apache.http.impl.client.ClientParamsStack;
import org.apache.http.impl.client.RequestAbortedException;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.3
 */
@ThreadSafe
public class InternalHttpClient extends AbstractBasicHttpClient {

    private final ClientExecChain execChain;
    private final ClientConnectionManager connManager;
    private final HttpRoutePlanner routePlanner;
    private final HttpParams params;

    public InternalHttpClient(
            final ClientExecChain execChain,
            final ClientConnectionManager connManager,
            final HttpRoutePlanner routePlanner,
            final HttpParams params) {
        super();
        if (execChain == null) {
            throw new IllegalArgumentException("HTTP client exec chain may not be null");
        }
        if (connManager == null) {
            throw new IllegalArgumentException("HTTP connection manager may not be null");
        }
        if (routePlanner == null) {
            throw new IllegalArgumentException("HTTP route planner may not be null");
        }
        this.execChain = execChain;
        this.connManager = connManager;
        this.routePlanner = routePlanner;
        this.params = params != null ? params : new SyncBasicHttpParams();
    }

    private HttpRoute determineRoute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException {
        HttpHost host = target;
        if (host == null) {
            host = (HttpHost) request.getParams().getParameter(ClientPNames.DEFAULT_HOST);
        }
        if (host == null) {
            throw new IllegalStateException("Target host may not be null");
        }
        return this.routePlanner.determineRoute(host, request, context);
    }

    private void setupContext(final HttpContext context) {
        if (context.getAttribute(ClientContext.TARGET_AUTH_STATE) == null) {
            context.setAttribute(ClientContext.TARGET_AUTH_STATE, new AuthState());
        }
        if (context.getAttribute(ClientContext.PROXY_AUTH_STATE) == null) {
            context.setAttribute(ClientContext.PROXY_AUTH_STATE, new AuthState());
        }
    }

    public HttpResponse execute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null.");
        }
        try {
            HttpContext execContext = context != null ? context : new BasicHttpContext();
            setupContext(context);
            HttpParams params = new ClientParamsStack(null, getParams(), request.getParams(), null);
            HttpHost virtualHost = (HttpHost) params.getParameter(ClientPNames.VIRTUAL_HOST);

            HttpRequestWrapper wrapper = HttpRequestWrapper.wrap(request);
            wrapper.setParams(params);
            wrapper.setVirtualHost(virtualHost);
            HttpExecutionAware execListner = null;
            if (request instanceof HttpExecutionAware) {
                execListner = (HttpExecutionAware) request;
                if (execListner.isAborted()) {
                    throw new RequestAbortedException("Request aborted");
                }
            }
            HttpRoute route = determineRoute(target, request, context);
            return this.execChain.execute(route, wrapper, execContext, execListner);
        } catch (HttpException httpException) {
            throw new ClientProtocolException(httpException);
        }
    }

    public HttpParams getParams() {
        return this.params;
    }

    public ClientConnectionManager getConnectionManager() {
        return this.connManager;
    }

}
