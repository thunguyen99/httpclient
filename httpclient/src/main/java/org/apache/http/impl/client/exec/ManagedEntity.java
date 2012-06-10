/*
 * $Revision $
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.conn.EofSensorInputStream;
import org.apache.http.conn.EofSensorWatcher;
import org.apache.http.conn.ManagedClientConnection;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.util.EntityUtils;

/**
 * An entity that automatically releases a {@link ManagedClientConnection connection}.
 *
 * @since 4.3
 */
@NotThreadSafe
class ManagedEntity extends HttpEntityWrapper implements EofSensorWatcher {

    private ManagedClientConnection managedConn;
    private final boolean attemptReuse;

    public ManagedEntity(
            final HttpEntity entity,
            final ManagedClientConnection managedConn,
            boolean reuse) {
        super(entity);
        this.managedConn = managedConn;
        this.attemptReuse = reuse;
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public InputStream getContent() throws IOException {
        return new EofSensorInputStream(this.wrappedEntity.getContent(), this);
    }

    private void ensureConsumed() throws IOException {
        if (this.managedConn == null) {
            return;
        }
        try {
            if (this.attemptReuse) {
                // this will not trigger a callback from EofSensorInputStream
                EntityUtils.consume(this.wrappedEntity);
                releaseConnection();
            }
        } finally {
            cleanup();
        }
    }

    @Deprecated
    @Override
    public void consumeContent() throws IOException {
        ensureConsumed();
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        super.writeTo(outstream);
        ensureConsumed();
    }

    public boolean eofDetected(final InputStream wrapped) throws IOException {
        try {
            if (this.attemptReuse && (this.managedConn != null)) {
                // there may be some cleanup required, such as
                // reading trailers after the response body:
                wrapped.close();
                this.managedConn.markReusable();
                releaseConnection();
            }
        } finally {
            cleanup();
        }
        return false;
    }

    public boolean streamClosed(InputStream wrapped) throws IOException {
        try {
            if (this.attemptReuse && (this.managedConn != null)) {
                boolean valid = this.managedConn.isOpen();
                // this assumes that closing the stream will
                // consume the remainder of the response body:
                try {
                    wrapped.close();
                    this.managedConn.markReusable();
                    releaseConnection();
                } catch (SocketException ex) {
                    if (valid) {
                        throw ex;
                    }
                }
            }
        } finally {
            cleanup();
        }
        return false;
    }

    public boolean streamAbort(InputStream wrapped) throws IOException {
        cleanup();
        return false;
    }

    private void releaseConnection() throws IOException {
        if (this.managedConn != null) {
            this.managedConn.releaseConnection();
            this.managedConn = null;
        }
    }

    private void cleanup() throws IOException {
        if (this.managedConn != null) {
            this.managedConn.abortConnection();
            this.managedConn = null;
        }
    }
}
