/*
 * Copyright (c) 2016 byteatebit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.byteatebit.nbserver.simple;

import com.byteatebit.common.builder.BaseBuilder;
import com.byteatebit.nbserver.NbServiceConfig;
import com.google.common.base.MoreObjects;

public class SimpleNbServerConfig {

    protected String listenAddress;
    protected Integer listenPort;
    protected NbServiceConfig nbServiceConfig;

    protected SimpleNbServerConfig(ObjectBuilder builder) {
        this.listenAddress = builder.listenAddress;
        this.listenPort = builder.listenPort;
        this.nbServiceConfig = builder.nbServiceConfig;
    }

    /**
     * The network address on which the server socket should listen for
     * incoming connections.  If the value is null, the server will
     * listen on all interfaces available on the host.
     * @return listen address or null
     */
    public String getListenAddress() {
        return listenAddress;
    }

    /**
     * The port on which the server socket should listent for
     * incoming connections.  The default
     * @return listen address or null
     */
    public Integer getListenPort() {
        return listenPort;
    }

    public NbServiceConfig getNbServiceConfig() {
        return nbServiceConfig;
    }

    protected MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("listenAddress", listenAddress)
                .add("listenPort", listenPort)
                .add("nbServiceConfig", nbServiceConfig);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ObjectBuilder<Builder> {

        @Override
        public Builder self() {
            return this;
        }
    }

    public abstract static class ObjectBuilder<T extends ObjectBuilder<T>> extends BaseBuilder<T> {

        protected String listenAddress;
        protected Integer listenPort = 8080;
        protected NbServiceConfig nbServiceConfig = NbServiceConfig.Builder.builder().build();

        public T withListenAddress(String listenAddress) {
            this.listenAddress = listenAddress;
            return self();
        }

        public T withListenPort(Integer listenPort) {
            this.listenPort = listenPort;
            return self();
        }

        public T withNbServiceConfig(NbServiceConfig nbServiceConfig) {
            this.nbServiceConfig = nbServiceConfig;
            return self();
        }

        public SimpleNbServerConfig build() {
            return new SimpleNbServerConfig(this);
        }
    }
}
