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
package org.oxymores.chronix.core.engine.api;

import java.io.Serializable;
import java.util.UUID;

public class DTOToken implements Serializable
{
    private static final long serialVersionUID = 6422487791877618666L;

    private UUID id = UUID.randomUUID();
    private String name;
    private String description;
    private int count = 1;
    private boolean byPlace = false;

    ///////////////////////////////////////////////////
    // Construction
    protected DTOToken()
    {
        // For JB convention
    }

    public DTOToken(String name, String description)
    {
        this.name = name;
        this.description = description;
    }

    public DTOToken(String name, String description, int count)
    {
        this(name, description);
        this.count = count;
    }

    ///////////////////////////////////////////////////
    // Stupid GET/SET
    public boolean isByPlace()
    {
        return byPlace;
    }

    public void setByPlace(boolean byPlace)
    {
        this.byPlace = byPlace;
    }

    public int getCount()
    {
        return count;
    }

    public void setCount(int count)
    {
        this.count = count;
    }

    public UUID getId()
    {
        return id;
    }

    void setId(UUID id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    //
    ///////////////////////////////////////////////////
}
