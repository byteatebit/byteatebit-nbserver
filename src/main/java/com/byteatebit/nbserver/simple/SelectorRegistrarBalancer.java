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

import com.byteatebit.common.stream.IObjectStream;
import com.byteatebit.nbserver.ISelectorRegistrar;

public class SelectorRegistrarBalancer implements ISelectorRegistrarBalancer {

    protected final IObjectStream<? extends ISelectorRegistrar> selectorRegistrarStream;

    public SelectorRegistrarBalancer(IObjectStream<? extends ISelectorRegistrar> selectorRegistrarStream) {
        this.selectorRegistrarStream = selectorRegistrarStream;
    }

    @Override
    public ISelectorRegistrar getSelectorRegistrar() {
        return selectorRegistrarStream.next();
    }
}
