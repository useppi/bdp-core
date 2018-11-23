/**
 * BDP data - Data Access Layer for the Big Data Platform
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
package it.bz.idm.bdp.dal;

import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import javax.persistence.CascadeType;
import javax.persistence.EntityManager;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

import it.bz.idm.bdp.dal.util.JPAException;
import it.bz.idm.bdp.dto.DataMapDto;
import it.bz.idm.bdp.dto.RecordDto;
import it.bz.idm.bdp.dto.RecordDtoImpl;
import it.bz.idm.bdp.dto.SimpleRecordDto;

@MappedSuperclass
@Inheritance(strategy=InheritanceType.TABLE_PER_CLASS)
public abstract class MHistory {

	private Date created_on;
	private Date timestamp;

	@ManyToOne(cascade=CascadeType.ALL)
	private Station station;

	@ManyToOne(cascade=CascadeType.PERSIST)
	private DataType type;

	private Integer period;

	public MHistory() {
		this.created_on = new Date();
	}
	public MHistory(Station station, DataType type, Date timestamp, Integer period) {
		this.station = station;
		this.type = type;
		this.timestamp = timestamp;
		this.period = period;
		this.created_on = new Date();
	}

	public Date getCreated_on() {
		return created_on;
	}

	public void setCreated_on(Date created_on) {
		this.created_on = created_on;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	public Station getStation() {
		return station;
	}

	public void setStation(Station station) {
		this.station = station;
	}

	public DataType getType() {
		return type;
	}

	public void setType(DataType type) {
		this.type = type;
	}
	public Integer getPeriod() {
		return period;
	}
	public void setPeriod(Integer period) {
		this.period = period;
	}

	public static void pushRecords(EntityManager em, String stationType, DataMapDto<RecordDtoImpl> dataMap) {
		boolean givenDataOK = false;
		boolean stationFound = false;
		boolean typeFound = false;
		boolean jsonOK = false;
		try {
			for (Entry<String, DataMapDto<RecordDtoImpl>> stationEntry : dataMap.getBranch().entrySet()) {
				Station station = Station.findStation(em, stationType, stationEntry.getKey());
				if (station == null) {
					System.err.println("pushRecords: Station '" + stationType + "/" + stationEntry.getKey() + "' not found. Skipping...");
					continue;
				}
				stationFound = true;
				for(Entry<String,DataMapDto<RecordDtoImpl>> typeEntry : stationEntry.getValue().getBranch().entrySet()) {
					try {
						DataType type = DataType.findByCname(em, typeEntry.getKey());
						if (type == null) {
							System.err.println("pushRecords: Type '" + typeEntry.getKey() + "' not found. Skipping...");
							continue;
						}
						typeFound = true;
						List<? extends RecordDtoImpl> dataRecords = typeEntry.getValue().getData();
						if (dataRecords.isEmpty()) {
							System.err.println("pushRecords: Empty data set. Skipping...");
							continue;
						}
						em.getTransaction().begin();

						M latestNumberMeasurement = M.findLatestEntryImpl(em, station, type, Measurement.class);
						long latestNumberMeasurementTime = (latestNumberMeasurement != null) ? latestNumberMeasurement.getTimestamp().getTime() : 0;
						M latestStringMeasurement = M.findLatestEntryImpl(em, station, type, MeasurementString.class);
						long latestStringMeasurementTime = (latestStringMeasurement != null) ? latestStringMeasurement.getTimestamp().getTime() : 0;

						Date created_on = new Date();
						SimpleRecordDto newestStringDto = null;
						SimpleRecordDto newestNumberDto = null;
						for (RecordDto recordDto : dataRecords) {

							/*
							 * XXX We support only SimpleRecordDtos at the moment. This should be removed,
							 * when we see that we do not need anything else then SimpleRecords
							 */
							if (! (recordDto instanceof SimpleRecordDto))
								continue;

							SimpleRecordDto simpleRecordDto = (SimpleRecordDto)recordDto;
							Long dateOfMeasurement = simpleRecordDto.getTimestamp();
							Object valueObj = simpleRecordDto.getValue();
							if (valueObj instanceof Number) {
								if (latestNumberMeasurementTime < dateOfMeasurement) {
									Double value = ((Number)valueObj).doubleValue();
									MeasurementHistory record = new MeasurementHistory(station, type, value, new Date(dateOfMeasurement), simpleRecordDto.getPeriod(), created_on);
									em.persist(record);
								}
								if (newestNumberDto == null || newestNumberDto.getTimestamp() < simpleRecordDto.getTimestamp()) {
									newestNumberDto = simpleRecordDto;
								}
								givenDataOK = true;
							} else if (valueObj instanceof String) {
								if (latestStringMeasurementTime < dateOfMeasurement) {
									String value = (String) valueObj;
									MeasurementStringHistory record = new MeasurementStringHistory(station, type, value, new Date(dateOfMeasurement), simpleRecordDto.getPeriod(), created_on);
									em.persist(record);
								}
								if (newestStringDto == null || newestStringDto.getTimestamp() < simpleRecordDto.getTimestamp()) {
									newestStringDto = simpleRecordDto;
								}
								givenDataOK = true;
							} else {
								System.err.println("pushRecords: Unsupported data format for "
												   + stationType + "/" + stationEntry.getKey() + "/" + typeEntry.getKey()
												   + ": " + (valueObj == null ? "(null)" : valueObj.getClass().getSimpleName()
												   + ". Skipping..."));
							}
							jsonOK = true;
						}

						if (newestNumberDto != null) {
							Double valueNumber = ((Number)newestNumberDto.getValue()).doubleValue();
							if (latestNumberMeasurement == null) {
								latestNumberMeasurement = new Measurement(station, type, valueNumber, new Date(newestNumberDto.getTimestamp()), newestNumberDto.getPeriod());
								em.persist(latestNumberMeasurement);
							} else if (newestNumberDto.getTimestamp() > latestNumberMeasurementTime) {
								latestNumberMeasurement.setTimestamp(new Date(newestNumberDto.getTimestamp()));
								latestNumberMeasurement.setValue(valueNumber);
								em.merge(latestNumberMeasurement);
							}
						}

						if (newestStringDto != null) {
							String valueString = (String) newestStringDto.getValue();
							if (latestStringMeasurement == null) {
								latestStringMeasurement = new MeasurementString(station, type, valueString, new Date(newestStringDto.getTimestamp()), newestStringDto.getPeriod());
								em.persist(latestStringMeasurement);
							} else if (newestStringDto.getTimestamp() > latestStringMeasurementTime) {
								latestStringMeasurement.setTimestamp(new Date(newestStringDto.getTimestamp()));
								latestStringMeasurement.setValue(valueString);
								em.merge(latestStringMeasurement);
							}
						}

						em.getTransaction().commit();
					} catch(Exception ex) {
						ex.printStackTrace();
						if (em.getTransaction().isActive())
							em.getTransaction().rollback();
						continue;
					}
				}
			}

			if (stationFound == false) {
				throw new JPAException("No station found inside " + DataMapDto.class.getSimpleName(), DataMapDto.class);
			}
			if (typeFound == false) {
				throw new JPAException("No station/type found inside " + DataMapDto.class.getSimpleName(), DataMapDto.class);
			}
			if (givenDataOK == false) {
				throw new JPAException("No valid data format for station/type found inside " + SimpleRecordDto.class.getSimpleName(), SimpleRecordDto.class);
			}
			/* FALSE, if no valid data can be found, either because of missing station/type/data combinations, hence invalid JSON */
			if (jsonOK == false) {
				throw new JPAException("Invalid JSON for " + DataMapDto.class.getSimpleName(), DataMapDto.class);
			}

		} catch(Exception ex) {
			if (! (ex instanceof JPAException))
				ex.printStackTrace();
			if (em.getTransaction().isActive())
				em.getTransaction().rollback();
			throw ex;
		} finally {
			em.clear();
			if (em.isOpen())
				em.close();
		}
	}
}