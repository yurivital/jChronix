/**
 * By Marc-Antoine Gouillart, 2012
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership. This file is licensed to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.oxymores.chronix.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.joda.time.DateTime;

import org.oxymores.chronix.core.active.And;
import org.oxymores.chronix.core.active.ChainEnd;
import org.oxymores.chronix.core.active.ChainStart;
import org.oxymores.chronix.core.active.ClockRRule;
import org.oxymores.chronix.core.active.Or;

public class Application extends ChronixObject
{
    private static final long serialVersionUID = 338399439626386055L;

    static final int currentModelVersion = 1;
    static final int compatibleUpToBackwards = 0;

    @Min(0)
    private int modelVersion = currentModelVersion;

    @NotNull
    @Size(min = 1, max = 50)
    protected String name;
    @NotNull
    @Size(min = 1, max = 255)
    protected String description;
    private DateTime latestSave = DateTime.now();

    @Valid
    protected Map<UUID, PlaceGroup> groups = new HashMap<>();
    @Valid
    protected Map<UUID, Parameter> parameters;
    @Valid
    protected Map<UUID, ClockRRule> rrules;
    @Valid
    protected Map<UUID, Calendar> calendars;
    @Valid
    protected Map<UUID, Token> tokens;
    @Valid
    protected Map<UUID, ActiveNodeBase> activeElements;

    protected List<ApplicationVersion> versions;

    protected transient boolean fromCurrentFile = true;
    protected transient ChronixContext ctx;

    public int getModelVersion()
    {
        return modelVersion;
    }

    public void setModelVersion(int modelVersion)
    {
        this.modelVersion = modelVersion;
    }

    protected class ApplicationVersion implements Serializable
    {
        ApplicationVersion()
        {

        }

        private static final long serialVersionUID = 338399455626386055L;

        int version = 0;
        String versionComment = "";
        DateTime created;
    }

    public Application()
    {
        super();
        this.activeElements = new HashMap<>();
        this.parameters = new HashMap<>();
        this.calendars = new HashMap<>();
        this.rrules = new HashMap<>();
        this.tokens = new HashMap<>();
        this.versions = new ArrayList<>();
        this.addVersion(0, "creation");

        // Basic elements
        ActiveNodeBase tmp = new And();
        tmp.setName("AND");
        tmp.setDescription("AND logical door - unique for the whole application");
        this.addActiveElement(tmp);
        tmp = new Or();
        tmp.setName("OR");
        tmp.setDescription("OR logical door - unique for the whole application");
        this.addActiveElement(tmp);
        tmp = new ChainEnd();
        tmp.setDescription("Marks the end of a chain. Can be ignored in global plans");
        this.addActiveElement(tmp);
        tmp = new ChainStart();
        tmp.setDescription("Marks the beginning of a chain. Can be ignored in global plans");
        this.addActiveElement(tmp);
    }

    @Override
    public String toString()
    {
        return String.format("%s (%s)", name, description);
    }

    /**
     * Will create one PlaceGroup per Place present in the Environment. Only adds. Name used is the name of the place.
     * @param n
     */
    public void createStarterGroups(Environment n)
    {
        PlaceGroup pg;
        for (Place p : n.getPlacesList())
        {
            pg = this.getGroup(p.getName());
            if (pg == null)
            {
                pg = new PlaceGroup();
                pg.setApplication(this);
                pg.setName(p.getName());
                this.addGroup(pg);
            }
        }
    }

    public void setname(String name)
    {
        this.name = name;
    }

    public void setContext(ChronixContext c)
    {
        this.ctx = c;
    }

    public void addRRule(ClockRRule r)
    {
        if (!this.rrules.containsValue(r))
        {
            this.rrules.put(r.id, r);
            r.setApplication(this);
        }
    }

    public void addCalendar(Calendar c)
    {
        if (!this.calendars.containsValue(c))
        {
            this.calendars.put(c.id, c);
            c.setApplication(this);
        }
    }

    public void removeACalendar(Calendar c)
    {
        this.calendars.remove(c.id);
        c.setApplication(null);
    }

    public void addToken(Token t)
    {
        if (!this.tokens.containsValue(t))
        {
            this.tokens.put(t.id, t);
            t.setApplication(this);
        }
    }

    public void removeToken(Token t)
    {
        this.tokens.remove(t.id);
        t.setApplication(null);
    }

    public final void addActiveElement(ActiveNodeBase o)
    {
        if (!this.activeElements.containsValue(o))
        {
            this.activeElements.put(o.id, o);
            o.setApplication(this);
        }
    }

    public void removeActiveElement(ActiveNodeBase o)
    {
        this.activeElements.remove(o.id);
        o.setApplication(null);
    }

    public void addParameter(Parameter o)
    {
        if (!this.parameters.containsValue(o))
        {
            this.parameters.put(o.id, o);
            o.setApplication(this);
        }
    }

    public void removeParameter(Parameter o)
    {
        this.parameters.remove(o.id);
        o.setApplication(null);
    }

    public String getName()
    {
        return name;
    }

    public Map<UUID, ActiveNodeBase> getActiveElements()
    {
        HashMap<UUID, ActiveNodeBase> res = new HashMap<>();
        res.putAll(this.activeElements);
        return res;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public List<Chain> getChains()
    {
        ArrayList<Chain> res = new ArrayList<>();
        for (ActiveNodeBase n : this.activeElements.values())
        {
            if (n instanceof Chain)
            {
                res.add((Chain) n);
            }
        }
        return res;
    }

    public List<State> getStates()
    {
        ArrayList<State> res = new ArrayList<>();
        for (Chain c : this.getChains())
        {
            res.addAll(c.getStates());
        }
        return res;
    }

    public State getState(UUID id)
    {
        for (State s : this.getStates())
        {
            if (s.id.equals(id))
            {
                return s;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getActiveElements(Class<T> tClass)
    {
        ArrayList<T> res = new ArrayList<>();
        for (ActiveNodeBase a : this.activeElements.values())
        {
            if (a.getClass().isAssignableFrom(tClass))
            {
                res.add((T) a);
            }
        }
        return res;
    }

    public Token getToken(UUID id)
    {
        return this.tokens.get(id);
    }

    public ActiveNodeBase getActiveNode(UUID id)
    {
        return this.activeElements.get(id);
    }

    public Calendar getCalendar(UUID id)
    {
        return this.calendars.get(id);
    }

    public Parameter getParameter(UUID id)
    {
        return this.parameters.get(id);
    }

    public ClockRRule getRRule(UUID id)
    {
        return this.rrules.get(id);
    }

    public List<ClockRRule> getRRulesList()
    {
        return new ArrayList<>(this.rrules.values());
    }

    public ExecutionNode getLocalNode()
    {
        return ctx.getLocalNode();
    }

    public List<Calendar> getCalendars()
    {
        return new ArrayList<>(this.calendars.values());
    }

    /////////////////////////////
    // Groups
    public void addGroup(PlaceGroup o)
    {
        if (!this.groups.containsValue(o))
        {
            this.groups.put(o.id, o);
            o.setApplication(this);
        }
    }

    public void removeGroup(PlaceGroup o)
    {
        this.groups.remove(o.id);
        o.setApplication(null);
    }

    public Map<UUID, PlaceGroup> getGroups()
    {
        HashMap<UUID, PlaceGroup> res = new HashMap<>();
        res.putAll(this.groups);
        return res;
    }

    public List<PlaceGroup> getGroupsList()
    {
        return new ArrayList<>(this.groups.values());
    }

    public PlaceGroup getGroup(String name)
    {
        PlaceGroup res = null;
        for (PlaceGroup pg : this.groups.values())
        {
            if (pg.getName().equals(name))
            {
                return pg;
            }
        }
        return res;
    }

    public PlaceGroup getGroup(UUID id)
    {
        return this.groups.get(id);
    }

    public List<Place> getAllPlaces()
    {
        List<Place> res = new ArrayList<>();
        for (PlaceGroup g : this.groups.values())
        {
            for (Place i : g.getPlaces())
            {
                if (!res.contains(i))
                {
                    res.add(i);
                }
            }
        }
        return res;
    }

    /////////////////////////////
    // Versioning
    public int getVersion()
    {
        return this.versions.get(this.versions.size() - 1).version;
    }

    public String getCommitComment()
    {
        return this.versions.get(this.versions.size() - 1).versionComment;
    }

    public boolean isFromCurrentFile()
    {
        return this.fromCurrentFile;
    }

    public void isFromCurrentFile(boolean b)
    {
        this.fromCurrentFile = b;
    }

    public DateTime getLatestSave()
    {
        return latestSave;
    }

    public void setLatestSave(DateTime latestSave)
    {
        this.latestSave = latestSave;
    }

    public final void addVersion(int version, String comment)
    {
        ApplicationVersion v = new ApplicationVersion();
        v.created = DateTime.now();
        v.version = version;
        v.versionComment = comment;
        this.versions.add(v);
    }
}
