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

/**
 All configuration objects belonging to an application derive from this class.<br>
 Provides (in addition to {@link ChronixObject} fields) a pointer to the application the object belongs to
 as well as the possibility to disable the object.
 @author Marc-Antoine
 */
public class ApplicationObject extends ChronixObject
{
    private static final long serialVersionUID = -926121748083888054L;

    @NotNull(message = "an application object must be inside an application")
    protected Application application;

    private boolean enabled = true;

    public Application getApplication()
    {
        return application;
    }

    // No access modifier: package private. Should only be called by Application
    // (inside an addObject method)
    public void setApplication(Application application)
    {
        this.application = application;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
