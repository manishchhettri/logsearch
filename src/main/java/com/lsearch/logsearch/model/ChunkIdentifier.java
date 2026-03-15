package com.lsearch.logsearch.model;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Unique identifier for a chunk of log data.
 * Format: service::date::chunk-HH
 */
public class ChunkIdentifier {

    private final String service;
    private final LocalDate date;
    private final int hourOfDay; // 0-23 or -1 for adaptive chunks
    private final int sequenceNumber; // For adaptive chunks that span multiple hours

    public ChunkIdentifier(String service, LocalDate date, int hourOfDay) {
        this(service, date, hourOfDay, 0);
    }

    public ChunkIdentifier(String service, LocalDate date, int hourOfDay, int sequenceNumber) {
        this.service = service;
        this.date = date;
        this.hourOfDay = hourOfDay;
        this.sequenceNumber = sequenceNumber;
    }

    /**
     * Generate chunk ID from timestamp (hourly chunking)
     */
    public static ChunkIdentifier fromTimestamp(String service, ZonedDateTime timestamp) {
        return new ChunkIdentifier(
            service,
            timestamp.toLocalDate(),
            timestamp.getHour()
        );
    }

    /**
     * Generate chunk ID for adaptive chunking
     */
    public static ChunkIdentifier adaptive(String service, LocalDate date, int startHour, int sequenceNumber) {
        return new ChunkIdentifier(service, date, startHour, sequenceNumber);
    }

    /**
     * Get the full chunk ID string
     */
    public String getChunkId() {
        if (sequenceNumber > 0) {
            return String.format("%s::%s::chunk-%02d-%d",
                service, date.toString(), hourOfDay, sequenceNumber);
        } else {
            return String.format("%s::%s::chunk-%02d",
                service, date.toString(), hourOfDay);
        }
    }

    public String getService() {
        return service;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getHourOfDay() {
        return hourOfDay;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkIdentifier that = (ChunkIdentifier) o;
        return hourOfDay == that.hourOfDay &&
               sequenceNumber == that.sequenceNumber &&
               Objects.equals(service, that.service) &&
               Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, date, hourOfDay, sequenceNumber);
    }

    @Override
    public String toString() {
        return getChunkId();
    }
}
