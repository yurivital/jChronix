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

package org.oxymores.chronix.core.active;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.oxymores.chronix.core.ActiveNodeBase;

public class External extends ActiveNodeBase
{
	private static final long serialVersionUID = 3722102490089534147L;

	public String machineRestriction, accountRestriction;
	public String regularExpression;

	public String getRegularExpression()
	{
		return regularExpression;
	}

	public void setRegularExpression(String regularExpression)
	{
		this.regularExpression = regularExpression;
	}

	public String getCalendarString(String data)
	{
		if (regularExpression == null || regularExpression == "")
			return null;

		Pattern p = Pattern.compile(regularExpression);
		Matcher m = p.matcher(data);

		if (m.find())
			return m.group(1);
		else
			return null;
	}
}
