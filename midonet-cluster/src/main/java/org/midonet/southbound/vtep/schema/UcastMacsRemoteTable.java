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

package org.midonet.southbound.vtep.schema;

import org.opendaylight.ovsdb.lib.schema.DatabaseSchema;

/**
 * Remote unicast mac table
 */
public final class UcastMacsRemoteTable extends UcastMacsTable {
    static public final String TB_NAME = "Ucast_Macs_Remote";

    public UcastMacsRemoteTable(DatabaseSchema databaseSchema) {
        super(databaseSchema, TB_NAME);
    }

    public String getName() {
        return TB_NAME;
    }
}
