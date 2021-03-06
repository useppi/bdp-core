/**
 * Big data platform - Data Writer for the Big Data Platform, that writes changes to the database
 * Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program (see LICENSES/GPL-3.0.txt). If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package it.bz.idm.bdp;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.persistence.EntityManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import it.bz.idm.bdp.dal.Station;
import it.bz.idm.bdp.dal.util.JPAUtil;
import it.bz.idm.bdp.dto.StationDto;
import it.bz.idm.bdp.writer.DataManager;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations= {"/META-INF/spring/applicationContext.xml"})
public class StationRetrievalIT {
	
	private EntityManager em = JPAUtil.createEntityManager();
	
	private DataManager manager = new DataManager();
	private String type = "TrafficSensor";
	private String origin = "FAMAS-traffic";

	@Test
	public void testStationsRetrieval() {
		List<Station> stationsWithOrigin = Station.findStations(em, type, origin );
		assertNotNull(stationsWithOrigin);
		List<Station> stations = Station.findStations(em, type, null);
		assertNotNull(stations);
	}
	
	@Test
	public void testDataManagerStationRetrival() {
		List<StationDto> stationDtos = manager.getStations(type, origin);
		assertNotNull(stationDtos);
		if (!stationDtos.isEmpty()) {
			assertTrue(stationDtos.get(0).getOrigin().equals(origin));
		}
	}

}
