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
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.auth.AuthState;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpClientRequestExecutor;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.ClientParamsStack;
import org.apache.http.impl.client.EntityEnclosingRequestWrapper;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

/**
 * @since 4.3
 */
@ThreadSafe
public abstract class BasicHttpClient implements HttpClient {

    private final Log log = LogFactory.getLog(getClass());

    private final HttpClientRequestExecutor requestExecutor;
    private final HttpRoutePlanner routePlanner;

    public BasicHttpClient(
            final HttpClientRequestExecutor requestExecutor,
            final HttpRoutePlanner routePlanner) {
        super();
        if (requestExecutor == null) {
            throw new IllegalArgumentException("HTTP client request executor may not be null");
        }
        if (routePlanner == null) {
            throw new IllegalArgumentException("HTTP route planner may not be null");
        }
        this.requestExecutor = requestExecutor;
        this.routePlanner = routePlanner;
    }

    public final HttpResponse execute(
            final HttpUriRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null.");
        }
        return execute(determineTarget(request), request, context);
    }

    private static HttpHost determineTarget(HttpUriRequest request) throws ClientProtocolException {
        // A null target may be acceptable if there is a default target.
        // Otherwise, the null target is detected in the director.
        HttpHost target = null;

        URI requestURI = request.getURI();
        if (requestURI.isAbsolute()) {
            target = URIUtils.extractHost(requestURI);
            if (target == null) {
                throw new ClientProtocolException("URI does not specify a valid host name: "
                        + requestURI);
            }
        }
        return target;
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

    public final HttpResponse execute(
            final HttpHost target,
            final HttpRequest request) throws IOException, ClientProtocolException {
        return execute(target, request, (HttpContext) null);
    }

    public final HttpResponse execute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null.");
        }
        try {
            HttpContext execContext = context != null ? context : new BasicHttpContext();
            setupContext(context);
            RequestWrapper wrapper;
            if (request instanceof HttpEntityEnclosingRequest) {
                wrapper = new EntityEnclosingRequestWrapper((HttpEntityEnclosingRequest) request);
            } else {
                wrapper = new RequestWrapper(request);
            }
            request.setParams(new ClientParamsStack(null, getParams(), request.getParams(), null));
            HttpRoute route = determineRoute(target, request, context);
            return this.requestExecutor.execute(route, wrapper, execContext);
        } catch (HttpException httpException) {
            throw new ClientProtocolException(httpException);
        }
    }

    public <T> T execute(final HttpUriRequest request,
            final ResponseHandler<? extends T> responseHandler) throws IOException,
            ClientProtocolException {
        return execute(request, responseHandler, null);
    }

    public <T> T execute(final HttpUriRequest request,
            final ResponseHandler<? extends T> responseHandler, final HttpContext context)
            throws IOException, ClientProtocolException {
        HttpHost target = determineTarget(request);
        return execute(target, request, responseHandler, context);
    }

    public <T> T execute(final HttpHost target, final HttpRequest request,
            final ResponseHandler<? extends T> responseHandler) throws IOException,
            ClientProtocolException {
        return execute(target, request, responseHandler, null);
    }

    public <T> T execute(final HttpHost target, final HttpRequest request,
            final ResponseHandler<? extends T> responseHandler, final HttpContext context)
            throws IOException, ClientProtocolException {
        if (responseHandler == null) {
            throw new IllegalArgumentException("Response handler must not be null.");
        }

        HttpResponse response = execute(target, request, context);

        T result;
        try {
            result = responseHandler.handleResponse(response);
        } catch (Exception t) {
            HttpEntity entity = response.getEntity();
            try {
                EntityUtils.consume(entity);
            } catch (Exception t2) {
                // Log this exception. The original exception is more
                // important and will be thrown to the caller.
                this.log.warn("Error consuming content after an exception.", t2);
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new UndeclaredThrowableException(t);
        }

        // Handling the response was successful. Ensure that the content has
        // been fully consumed.
        HttpEntity entity = response.getEntity();
        EntityUtils.consume(entity);
        return result;
    }

}
