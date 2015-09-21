/*
 * Copyright 2015 Midokura SARL
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
package org.midonet.cluster.rest_api.models;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.midonet.cluster.data.ZoomField;
import org.midonet.cluster.util.UUIDUtil;

public class VxLanPort extends Port {

    @ZoomField(name = "vtep_id", converter = UUIDUtil.Converter.class)
    public UUID vtepId;

    @JsonIgnore
    @ZoomField(name = "network_id", converter = UUIDUtil.Converter.class)
    public UUID networkId;

    public String getType() {
        return PortType.VXLAN;
    }

    @Override
    public UUID getDeviceId() {
        return networkId;
    }

    @Override
    public void setDeviceId(UUID deviceId) {
        networkId = deviceId;
    }

    @JsonIgnore
    @Override
    public void update(Port from) {
        super.update(from);
        VxLanPort port = (VxLanPort)from;
        networkId = port.networkId;
    }

}