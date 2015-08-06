/**
 * By Marc-Antoine Gouillart, 2012
 *
 * See the NOTICE file distributed with this work for
 * information regarding copyright ownership.
 * This file is licensed to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.oxymores.chronix.core;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class NetworkObject extends ChronixObject
{
    private static final long serialVersionUID = -926121848083888054L;

    @NotNull(message = "a network object must be inside a network")
    protected Network network;

    @NotNull
    @Size(min = 1, max = 50)
    protected String name;

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Network getNetwork()
    {
        return network;
    }

    public void setNetwork(Network network)
    {
        this.network = network;
    }
}
