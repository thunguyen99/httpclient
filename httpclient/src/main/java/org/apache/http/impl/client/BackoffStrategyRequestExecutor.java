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
import java.lang.reflect.UndeclaredThrowableException;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.BackoffManager;
import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.client.HttpClientRequestExecutor;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.3
 */
@ThreadSafe
public class BackoffStrategyRequestExecutor implements HttpClientRequestExecutor {

    private final HttpClientRequestExecutor requestExecutor;
    private final ConnectionBackoffStrategy connectionBackoffStrategy;
    private final BackoffManager backoffManager;

    public BackoffStrategyRequestExecutor(
            final HttpClientRequestExecutor requestExecutor,
            final ConnectionBackoffStrategy connectionBackoffStrategy,
            final BackoffManager backoffManager) {
        super();
        if (requestExecutor == null) {
            throw new IllegalArgumentException("HTTP client request executor may not be null");
        }
        if (connectionBackoffStrategy == null) {
            throw new IllegalArgumentException("Connection backoff strategy may not be null");
        }
        if (backoffManager == null) {
            throw new IllegalArgumentException("Backoff manager may not be null");
        }
        this.requestExecutor = requestExecutor;
        this.connectionBackoffStrategy = connectionBackoffStrategy;
        this.backoffManager = backoffManager;
    }

    public HttpResponse execute(
            final HttpRoute route,
            final HttpUriRequest request,
            final HttpContext context) throws IOException, HttpException {
        if (request == null) {
            throw new IllegalArgumentException("Request may not be null");
        }
        HttpResponse out;
        try {
            out = this.requestExecutor.execute(route, request, context);
        } catch (RuntimeException ex) {
            if (this.connectionBackoffStrategy.shouldBackoff(ex)) {
                this.backoffManager.backOff(route);
            }
            throw ex;
        } catch (Exception ex) {
            if (this.connectionBackoffStrategy.shouldBackoff(ex)) {
                this.backoffManager.backOff(route);
            }
            if (ex instanceof HttpException) throw (HttpException) ex;
            if (ex instanceof IOException) throw (IOException) ex;
            throw new UndeclaredThrowableException(ex);
        }
        if (this.connectionBackoffStrategy.shouldBackoff(out)) {
            this.backoffManager.backOff(route);
        } else {
            this.backoffManager.probe(route);
        }
        return out;
    }

}
