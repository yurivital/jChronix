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

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CalendarDay extends ApplicationObject
{
    private static final long serialVersionUID = -8296932253108182976L;

    @NotNull
    @Size(min = 1, max = 255)
    protected String seq;
    @NotNull
    protected Calendar calendar;

    public CalendarDay(String day, Calendar calendar)
    {
        super();
        id = UUID.randomUUID();
        this.calendar = calendar;
        calendar.addDay(this);
        this.seq = day;
    }

    public void setCalendar(Calendar c)
    {
        if (this.calendar == null || !this.calendar.getId().equals(c.getId()))
            c.addDay(this);
        this.calendar = c;
        this.application = c.application;
    }

    public String getValue()
    {
        return seq;
    }
}
