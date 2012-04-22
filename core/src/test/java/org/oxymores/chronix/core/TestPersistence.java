/**
 * @author Marc-Antoine Gouillart
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

import junit.framework.Assert;
import junit.framework.TestCase;

public class TestPersistence extends TestCase {

	public void testApp()
	{
		Application a1 = org.oxymores.chronix.demo.DemoApplication.getNewDemoApplication();
		Application a = a1; // Test object
		
		// Test connections between objects
		Assert.assertEquals(1, a.getNodes().get(0).getCanSendTo().size());
		Assert.assertEquals(1, a.getNodes().get(1).getCanReceiveFrom().size());
		Assert.assertEquals(2, a.getPlaces().get(0).getMemberOfGroups().size());
		Assert.assertEquals(2, a.getPlaces().get(1).getMemberOfGroups().size());
		Assert.assertEquals(2, a.getGroups().get(0).getPlaces().size());
		Assert.assertEquals(1, a.getGroups().get(1).getPlaces().size());
		Assert.assertEquals(1, a.getGroups().get(2).getPlaces().size());
		
		// Serialization
		try
		{
			// Serialize
			Loader.ser2(a1, "C:\\TEMP\\meuh.txt");
			
			// Deserialization
			Application a2 = Loader.deSerialize("C:\\TEMP\\meuh.txt");
			
			// Test
			Assert.assertEquals(a1.getNodes().size(), a2.getNodes().size());
			Assert.assertEquals(a1.getPlaces().size(), a2.getPlaces().size());
			Assert.assertEquals(a1.getElements().size(), a2.getElements().size());
			Assert.assertEquals(a1.getGroups().size(), a2.getGroups().size());
		}
		catch (Exception e) {System.err.println("meuh" + e.getMessage() + e);}
		
	}
}