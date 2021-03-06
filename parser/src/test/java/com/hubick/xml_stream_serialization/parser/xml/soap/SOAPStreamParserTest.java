/*
 * Copyright 2016-2020 by Chris Hubick. All Rights Reserved.
 * 
 * This work is licensed under the terms of the "GNU AFFERO GENERAL PUBLIC LICENSE" version 3, as published by the Free
 * Software Foundation <http://www.gnu.org/licenses/>, plus additional permissions, a copy of which you should have
 * received in the file LICENSE.txt.
 */

package com.hubick.xml_stream_serialization.parser.xml.soap;

import java.beans.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

import javax.xml.ws.soap.*;

import org.eclipse.jdt.annotation.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;


/**
 * JUnit tests for {@link SOAPStreamParser}.
 */
@NonNullByDefault
public class SOAPStreamParserTest {
  protected static final SOAPStreamParser<Departure> DEPARTURE_STREAM_PARSER = SOAPStreamParser.buildSOAP12Schema(URI.create("https://chris.hubick.com/ns/"))
      .defineSimpleElement("departureYear", Year.class, (ctx, value) -> Year.parse(value), true).defineHeaderElementWithChildBuilder().addChildValueElement("departureYear").completeDefinition()
      .defineStringElement("departing")
      .defineSimpleElement("departureMonthDay", MonthDay.class, MonthDay::parse)
      .defineElementWithInjectedTargetBuilder("departure", Departure.class).injectChildObject("Departing", "departing").injectChildObject("DepartureMonthDay", "departureMonthDay").injectSavedObject("DepartureYear", "departureYear").completeDefinition()
      // .defineElementWithChildBuilder("departure", Departure.class, (ctx) -> new Departure(ctx.getRequiredChildValue("departing", String.class), ctx.getRequiredChildValue("departureMonthDay", MonthDay.class).atYear(ctx.getRequiredSavedValue("departureYear", Year.class).getValue())), false).addChildValueElement("departing").addChildValueElement("departureMonthDay").completeDefinition()
      .defineContainerElementWithChildBuilder("departures").addChildValueElement("departure").addChildExceptionElement(SOAPStreamParser.SOAP_1_2_FAULT_QNAME).completeDefinition()
      .defineBodyElement("departures")
      .defineEnvelopeElement(true).createSOAPParser(Departure.class, "departures", "departure");

  /**
   * Test parsing the data while ignoring extra elements.
   * 
   * @throws Exception If there was a problem executing this test.
   */
  @Test
  public void testIgnoreExtra() throws Exception {
    final URL testURL = SOAPStreamParserTest.class.getResource("/com/hubick/xml_stream_serialization/parser/xml/soap/departures_ignore_extra.xml");
    final Stream<Departure> departures = StreamSupport.stream(Spliterators.spliteratorUnknownSize(DEPARTURE_STREAM_PARSER.parse(testURL.openStream()), Spliterator.ORDERED | Spliterator.NONNULL), false);
    assertEquals("Canada[2001-01-01], USA[2001-02-01], Australia[2001-03-01]", departures.map(Object::toString).collect(Collectors.joining(", ")));
    return;
  }

  /**
   * Test global (top-level) fault handling.
   * 
   * @throws Exception If there was a problem executing this test.
   */
  @Test
  public void testGlobalFaultThrown() throws Exception {
    final SOAPFaultException sfe = assertThrows(SOAPFaultException.class, () -> {
      final URL testURL = SOAPStreamParserTest.class.getResource("/com/hubick/xml_stream_serialization/parser/xml/soap/departures_global_fault.xml");
      try {
        DEPARTURE_STREAM_PARSER.parse(testURL.openStream());
      } catch (SOAPStreamParser.ExceptionElementException eee) {
        throw eee.getCause();
      }
    });

    assertEquals("Server went boom.", sfe.getMessage());

    assertNotNull(sfe.getCause());
    assertEquals(java.sql.SQLException.class, sfe.getCause().getClass());
    assertEquals("Database went boom.", sfe.getCause().getMessage());
    assertNotNull(sfe.getCause().getStackTrace());
    assertEquals("org.apache.tomcat.util.net.JIoEndpoint$SocketProcessor", sfe.getCause().getStackTrace()[0].getClassName());
    assertEquals("run", sfe.getCause().getStackTrace()[0].getMethodName());
    assertEquals("JIoEndpoint.java", sfe.getCause().getStackTrace()[0].getFileName());
    assertEquals(310, sfe.getCause().getStackTrace()[0].getLineNumber());

    assertNotNull(sfe.getCause().getCause());
    assertEquals(java.io.IOException.class, sfe.getCause().getCause().getClass());
    assertEquals("Filesystem error.", sfe.getCause().getCause().getMessage());

    return;
  }

  /**
   * Test local fault handling.
   * 
   * @throws Exception If there was a problem executing this test.
   */
  @Test
  public void testLocalFaultThrown() throws Exception {
    final SOAPFaultException sfe = assertThrows(SOAPFaultException.class, () -> {
      final URL testURL = SOAPStreamParserTest.class.getResource("/com/hubick/xml_stream_serialization/parser/xml/soap/departures_local_fault.xml");
      try {
        final Iterator<Departure> departures = DEPARTURE_STREAM_PARSER.parse(testURL.openStream());
        assertEquals("Canada[2001-01-01]", departures.next().toString());
        assertEquals("USA[2001-02-01]", departures.next().toString());
        departures.next().toString();
      } catch (SOAPStreamParser.ExceptionElementException eee) {
        throw eee.getCause();
      }
    });
    assertEquals("Invalid departure record.", sfe.getMessage());
    return;
  }

  /**
   * Test local fault recovery (can you continue iterating afterwards).
   * 
   * @throws Exception If there was a problem executing this test.
   */
  @Test
  public void testLocalFaultRecovery() throws Exception {
    final URL testURL = SOAPStreamParserTest.class.getResource("/com/hubick/xml_stream_serialization/parser/xml/soap/departures_local_fault.xml");
    final Iterator<Departure> departures = DEPARTURE_STREAM_PARSER.parse(testURL.openStream());
    assertEquals("Canada[2001-01-01]", departures.next().toString());
    assertEquals("USA[2001-02-01]", departures.next().toString());
    try {
      departures.next().toString();
      fail("Expected SOAPFaultException");
    } catch (SOAPStreamParser.ExceptionElementException eee) {}
    assertEquals("Australia[2001-03-01]", departures.next().toString());

    return;
  }

  /**
   * An example data model class representing a departure.
   */
  public static class Departure {
    private final String departing;
    private final LocalDate date;

    /**
     * Construct a new <code>Departure</code>.
     * 
     * @param departing The location of the departure.
     * @param date The departure date.
     */
    public Departure(final String departing, final LocalDate date) {
      this.departing = departing;
      this.date = date;
      return;
    }

    /**
     * Construct a new <code>Departure</code>.
     * 
     * @param departing The location of the departure.
     * @param departureMonthDay The month and day of the departure date.
     * @param departureYear The year of the departure date.
     */
    @ConstructorProperties({ "Departing", "DepartureMonthDay", "DepartureYear" })
    public Departure(final String departing, final MonthDay departureMonthDay, final @Nullable Year departureYear) {
      this(departing, departureMonthDay.atYear((departureYear != null) ? departureYear.getValue() : LocalDateTime.now().getYear()));
      return;
    }

    @Override
    public String toString() {
      return departing + '[' + date + ']';
    }

  } // Departure

}
