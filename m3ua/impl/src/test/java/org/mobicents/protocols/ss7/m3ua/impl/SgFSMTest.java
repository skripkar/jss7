/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.m3ua.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javolution.util.FastMap;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.Management;
import org.mobicents.protocols.api.PayloadData;
import org.mobicents.protocols.api.Server;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.FSM;
import org.mobicents.protocols.ss7.m3ua.impl.message.M3UAMessageImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.MessageFactoryImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.asptm.ASPActiveImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.asptm.ASPInactiveImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.transfer.PayloadDataImpl;
import org.mobicents.protocols.ss7.m3ua.impl.parameter.ParameterFactoryImpl;
import org.mobicents.protocols.ss7.m3ua.impl.parameter.ProtocolDataImpl;
import org.mobicents.protocols.ss7.m3ua.message.M3UAMessage;
import org.mobicents.protocols.ss7.m3ua.message.MessageClass;
import org.mobicents.protocols.ss7.m3ua.message.MessageType;
import org.mobicents.protocols.ss7.m3ua.message.mgmt.Notify;
import org.mobicents.protocols.ss7.m3ua.parameter.DestinationPointCode;
import org.mobicents.protocols.ss7.m3ua.parameter.ErrorCode;
import org.mobicents.protocols.ss7.m3ua.parameter.LocalRKIdentifier;
import org.mobicents.protocols.ss7.m3ua.parameter.RoutingContext;
import org.mobicents.protocols.ss7.m3ua.parameter.RoutingKey;
import org.mobicents.protocols.ss7.m3ua.parameter.ServiceIndicators;
import org.mobicents.protocols.ss7.m3ua.parameter.Status;
import org.mobicents.protocols.ss7.m3ua.parameter.TrafficModeType;

/**
 * 
 * @author amit bhayani
 * 
 */
public class SgFSMTest {

	private ParameterFactoryImpl parmFactory = new ParameterFactoryImpl();
	private MessageFactoryImpl messageFactory = new MessageFactoryImpl();
	private M3UAManagement serverM3UAMgmt = null;

	private TransportManagement transportManagement = null;

	public SgFSMTest() {
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		this.transportManagement = new TransportManagement();
		this.serverM3UAMgmt = new M3UAManagement();
		this.serverM3UAMgmt.setTransportManagement(this.transportManagement);
		this.serverM3UAMgmt.start();

	}

	@After
	public void tearDown() throws Exception {
		serverM3UAMgmt.getAppServers().clear();
		serverM3UAMgmt.getAspfactories().clear();
		serverM3UAMgmt.getRoute().clear();
		serverM3UAMgmt.stop();
	}

	private AspState getAspState(FSM fsm) {
		return AspState.getState(fsm.getState().getName());
	}

	private AsState getAsState(FSM fsm) {
		return AsState.getState(fsm.getState().getName());
	}

	@Test
	public void testSingleAspInAs() throws Exception {
		// 5.1.1. Single ASP in an Application Server ("1+0" sparing),
		TestAssociation testAssociation = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
				"testAssoc1");

		RoutingContext rc = parmFactory.createRoutingContext(new long[] { 100 });

		DestinationPointCode[] dpc = new DestinationPointCode[] { parmFactory
				.createDestinationPointCode(123, (short) 0) };

		ServiceIndicators[] servInds = new ServiceIndicators[] { parmFactory.createServiceIndicators(new short[] { 3 }) };

		TrafficModeType trModType = parmFactory.createTrafficModeType(TrafficModeType.Override);
		LocalRKIdentifier lRkId = parmFactory.createLocalRKIdentifier(1);
		RoutingKey rKey = parmFactory.createRoutingKey(lRkId, rc, null, null, dpc, servInds, null);

		// As remAs = sgw.createAppServer("testas", rc, rKey, trModType);
		As remAs = serverM3UAMgmt.createAs("testas", Functionality.SGW, ExchangeType.SE, null, rc, null);
		FSM asLocalFSM = remAs.getLocalFSM();

		// AspFactory aspFactory = sgw.createAspFactory("testasp", "127.0.0.1",
		// 2777);
		AspFactory aspFactory = serverM3UAMgmt.createAspFactory("testasp", "testAssoc1");

		Asp remAsp = serverM3UAMgmt.assignAspToAs("testas", "testasp");
		FSM aspPeerFSM = remAsp.getPeerFSM();

		// Check for Communication UP
		testAssociation.signalCommUp();

		assertEquals(AspState.DOWN, this.getAspState(aspPeerFSM));

		// Check for ASP_UP
		M3UAMessageImpl message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
		aspFactory.read(message);

		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
		// also the AS should be INACTIVE now
		assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

		// Check for ASP_ACTIVE
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(rc);

		aspFactory.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(aspPeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// also the AS should be ACTIVE now
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

		// Check for ASP_INACTIVE
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE);
		((ASPInactiveImpl) message).setRoutingContext(rc);

		aspFactory.read(message);
		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE_ACK,
				-1, -1));
		// also the AS should be PENDING now
		assertEquals(AsState.PENDING, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));

		// Check for ASP_DOWN
		message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_DOWN);
		aspFactory.read(message);
		assertEquals(AspState.DOWN, this.getAspState(aspPeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_DOWN_ACK, -1,
				-1));

		// Make sure we don't have any more
		assertNull(testAssociation.txPoll());
	}

	@Test
	public void testSingleAspInMultipleAs() throws Exception {
		// 5.1.1.3. Single ASP in Multiple Application Servers (Each with "1+0"
		// Sparing)
		TestAssociation testAssociation = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
				"testAssoc1");

		// Define 1st AS
		RoutingContext rc1 = parmFactory.createRoutingContext(new long[] { 100 });

		DestinationPointCode[] dpc1 = new DestinationPointCode[] { parmFactory.createDestinationPointCode(123,
				(short) 0) };

		ServiceIndicators[] servInds1 = new ServiceIndicators[] { parmFactory
				.createServiceIndicators(new short[] { 3 }) };

		TrafficModeType trModType1 = parmFactory.createTrafficModeType(TrafficModeType.Override);
		LocalRKIdentifier lRkId1 = parmFactory.createLocalRKIdentifier(1);
		RoutingKey rKey1 = parmFactory.createRoutingKey(lRkId1, rc1, null, null, dpc1, servInds1, null);

		// As remAs1 = sgw.createAppServer("testas1", rc1, rKey1, trModType1);
		As remAs1 = serverM3UAMgmt.createAs("testas1", Functionality.SGW, ExchangeType.SE, null, rc1, null);
		FSM as1LocalFSM = remAs1.getLocalFSM();

		// Define 2nd AS
		RoutingContext rc2 = parmFactory.createRoutingContext(new long[] { 200 });

		DestinationPointCode[] dpc2 = new DestinationPointCode[] { parmFactory.createDestinationPointCode(124,
				(short) 0) };

		ServiceIndicators[] servInds2 = new ServiceIndicators[] { parmFactory
				.createServiceIndicators(new short[] { 3 }) };

		TrafficModeType trModType2 = parmFactory.createTrafficModeType(TrafficModeType.Override);
		LocalRKIdentifier lRkId2 = parmFactory.createLocalRKIdentifier(1);
		RoutingKey rKey2 = parmFactory.createRoutingKey(lRkId2, rc2, null, null, dpc2, servInds2, null);

		// As remAs2 = sgw.createAppServer("testas2", rc2, rKey2, trModType2);
		As remAs2 = serverM3UAMgmt.createAs("testas2", Functionality.SGW, ExchangeType.SE, null, rc2, null);
		FSM as2LocalFSM = remAs2.getLocalFSM();

		// AspFactory aspFactory = sgw.createAspFactory("testasp", "127.0.0.1",
		// 2777);
		AspFactory aspFactory = serverM3UAMgmt.createAspFactory("testasp", "testAssoc1");

		// Both ASP uses same underlying M3UAChannel
		Asp remAsp1 = serverM3UAMgmt.assignAspToAs("testas1", "testasp");
		Asp remAsp2 = serverM3UAMgmt.assignAspToAs("testas2", "testasp");

		FSM asp1PeerFSM = remAsp1.getPeerFSM();
		FSM asp2PeerFSM = remAsp2.getPeerFSM();

		// Check for Communication UP
		testAssociation.signalCommUp();

		assertEquals(AspState.DOWN, this.getAspState(asp1PeerFSM));
		assertEquals(AspState.DOWN, this.getAspState(asp2PeerFSM));
		// Both AS are yet DOWN
		assertEquals(AsState.DOWN, this.getAsState(as1LocalFSM));
		assertEquals(AsState.DOWN, this.getAsState(as2LocalFSM));

		// Check for ASP_UP
		M3UAMessageImpl message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
		aspFactory.read(message);
		assertEquals(AspState.INACTIVE, this.getAspState(asp1PeerFSM));
		assertEquals(AspState.INACTIVE, this.getAspState(asp2PeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
		// also both the AS should be INACTIVE now
		assertEquals(AsState.INACTIVE, this.getAsState(as1LocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));
		assertEquals(AsState.INACTIVE, this.getAsState(as2LocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

		// Check for ASP_ACTIVE for both Routing Contexts
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(this.parmFactory.createRoutingContext(new long[] { 100, 200 }));
		aspFactory.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(asp1PeerFSM));
		assertEquals(AspState.ACTIVE, this.getAspState(asp2PeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// also both the AS should be ACTIVE now
		assertEquals(AsState.ACTIVE, this.getAsState(as1LocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
		// We will have two ACK's one each for each RC
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));

		assertEquals(AsState.ACTIVE, this.getAsState(as2LocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

		// Check for ASP_INACTIVE for ASP1
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE);
		((ASPInactiveImpl) message).setRoutingContext(this.parmFactory.createRoutingContext(new long[] { 100 }));
		aspFactory.read(message);

		assertEquals(AspState.INACTIVE, this.getAspState(asp1PeerFSM));
		// The ASP2 should still be ACTIVE as we sent ASP_INACTIVE only for 100
		// RC
		assertEquals(AspState.ACTIVE, this.getAspState(asp2PeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE_ACK,
				-1, -1));
		// AS1 should be PENDING now
		assertEquals(AsState.PENDING, this.getAsState(as1LocalFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));

		// But AS2 is still ACTIVE
		assertEquals(AsState.ACTIVE, this.getAsState(as2LocalFSM));

		// Check for ASP_DOWN
		message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_DOWN);
		aspFactory.read(message);
		assertEquals(AspState.DOWN, this.getAspState(asp1PeerFSM));
		assertEquals(AspState.DOWN, this.getAspState(asp2PeerFSM));
		assertTrue(validateMessage(testAssociation, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_DOWN_ACK, -1,
				-1));

		// Make sure we don't have any more messages to be sent
		assertNull(testAssociation.txPoll());

	}

	@Test
	public void testTwoAspInAsOverride() throws Exception {
		// 5.1.2. Two ASPs in Application Server ("1+1" Sparing)

		TestAssociation testAssociation1 = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
				"testAssoc1");
		TestAssociation testAssociation2 = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
				"testAssoc2");

		RoutingContext rc = parmFactory.createRoutingContext(new long[] { 100 });
		TrafficModeType overrideMode = parmFactory.createTrafficModeType(TrafficModeType.Override);

		DestinationPointCode[] dpc = new DestinationPointCode[] { parmFactory
				.createDestinationPointCode(123, (short) 0) };

		ServiceIndicators[] servInds = new ServiceIndicators[] { parmFactory.createServiceIndicators(new short[] { 3 }) };

		TrafficModeType trModType = parmFactory.createTrafficModeType(TrafficModeType.Override);
		LocalRKIdentifier lRkId = parmFactory.createLocalRKIdentifier(1);
		RoutingKey rKey = parmFactory.createRoutingKey(lRkId, rc, null, null, dpc, servInds, null);

		// As remAs = sgw.createAppServer("testas", rc, rKey, trModType);
		As remAs = serverM3UAMgmt.createAs("testas", Functionality.SGW, ExchangeType.SE, null, rc, overrideMode);
		FSM asLocalFSM = remAs.getLocalFSM();

		// AspFactory aspFactory1 = sgw.createAspFactory("testasp1",
		// "127.0.0.1", 2777);
		AspFactory aspFactory1 = serverM3UAMgmt.createAspFactory("testasp1", "testAssoc1");

		// AspFactory aspFactory2 = sgw.createAspFactory("testasp2",
		// "127.0.0.1", 2778);
		AspFactory aspFactory2 = serverM3UAMgmt.createAspFactory("testasp2", "testAssoc2");

		Asp remAsp1 = serverM3UAMgmt.assignAspToAs("testas", "testasp1");
		Asp remAsp2 = serverM3UAMgmt.assignAspToAs("testas", "testasp2");

		FSM asp1PeerFSM = remAsp1.getPeerFSM();
		FSM asp2PeerFSM = remAsp2.getPeerFSM();

		// Check for Communication UP for ASP1
		testAssociation1.signalCommUp();
		assertEquals(AspState.DOWN, this.getAspState(asp1PeerFSM));

		// Check for Communication UP for ASP2
		testAssociation2.signalCommUp();
		assertEquals(AspState.DOWN, this.getAspState(asp2PeerFSM));

		// Check for ASP_UP for ASP1
		M3UAMessageImpl message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
		aspFactory1.read(message);
		assertEquals(AspState.INACTIVE, this.getAspState(asp1PeerFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
		// also the AS should be INACTIVE now
		assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

		// Check for ASP_UP for ASP2
		aspFactory2.read(message);
		assertEquals(AspState.INACTIVE, this.getAspState(asp2PeerFSM));
		assertTrue(validateMessage(testAssociation2, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
		// also the AS should be INACTIVE now
		assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

		// Check for ASP_ACTIVE for ASP1
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(rc);
		aspFactory1.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(asp1PeerFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// also the AS should be ACTIVE now and ACTIVE should be delivered to
		// both the ASPs
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

		// INACTIVATE the ASP1
		// 5.2.1. 1+1 Sparing, Withdrawal of ASP, Backup Override
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE);
		((ASPInactiveImpl) message).setRoutingContext(rc);
		aspFactory1.read(message);

		assertEquals(AspState.INACTIVE, this.getAspState(asp1PeerFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE,
				MessageType.ASP_INACTIVE_ACK, -1, -1));
		// also the AS should be PENDING now and should send PENDING NTFY to
		// both the ASPS
		assertEquals(AsState.PENDING, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));

		// ACTIVATE ASP2
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(rc);
		aspFactory2.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(asp2PeerFSM));
		assertTrue(validateMessage(testAssociation2, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// also the AS should be ACTIVE now and ACTIVE should be delivered to
		// both the ASPs
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

		// 5.2.2. 1+1 Sparing, Backup Override
		// ACTIVATE ASP1 also
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(rc);
		aspFactory1.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(asp1PeerFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// The AS remains ACTIVE and sends NTFY(Alt ASP-Act) to ASP2
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));

		// ASP2 should get Alternate ASP is active
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY, Status.STATUS_Other,
				Status.INFO_Alternate_ASP_Active));
		// The state of ASP2 now should be INACTIVE
		assertEquals(AspState.INACTIVE, this.getAspState(asp2PeerFSM));

		assertNull(testAssociation1.txPoll());
		assertNull(testAssociation2.txPoll());

	}

	@Test
	public void testTwoAspInAsLoadshare() throws Exception {
		// 5.1.2. Two ASPs in Application Server ("1+1" Sparing)

		TestAssociation testAssociation1 = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
				"testAssoc1");
		TestAssociation testAssociation2 = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
				"testAssoc2");

		RoutingContext rc = parmFactory.createRoutingContext(new long[] { 100 });

		DestinationPointCode[] dpc = new DestinationPointCode[] { parmFactory
				.createDestinationPointCode(123, (short) 0) };

		ServiceIndicators[] servInds = new ServiceIndicators[] { parmFactory.createServiceIndicators(new short[] { 3 }) };

		TrafficModeType trModType = parmFactory.createTrafficModeType(TrafficModeType.Loadshare);
		LocalRKIdentifier lRkId = parmFactory.createLocalRKIdentifier(1);
		RoutingKey rKey = parmFactory.createRoutingKey(lRkId, rc, null, null, dpc, servInds, null);

		// As remAs = sgw.createAppServer("testas", rc, rKey, trModType);
		As remAs = serverM3UAMgmt.createAs("testas", Functionality.SGW, ExchangeType.SE, null, rc, trModType);

		FSM asLocalFSM = remAs.getLocalFSM();

		// 2+0 sparing loadsharing
		remAs.setMinAspActiveForLb(2);

		// AspFactory aspFactory1 = sgw.createAspFactory("testasp1",
		// "127.0.0.1", 2777);
		AspFactory aspFactory1 = serverM3UAMgmt.createAspFactory("testasp1", "testAssoc1");

		// AspFactory aspFactory2 = sgw.createAspFactory("testasp2",
		// "127.0.0.1", 2778);
		AspFactory aspFactory2 = serverM3UAMgmt.createAspFactory("testasp2", "testAssoc2");

		Asp remAsp1 = serverM3UAMgmt.assignAspToAs("testas", "testasp1");
		Asp remAsp2 = serverM3UAMgmt.assignAspToAs("testas", "testasp2");

		FSM aspPeerFSM1 = remAsp1.getPeerFSM();
		FSM aspPeerFSM2 = remAsp2.getPeerFSM();

		// Check for Communication UP for ASP1
		testAssociation1.signalCommUp();
		assertEquals(AspState.DOWN, this.getAspState(aspPeerFSM1));

		// Check for Communication UP for ASP2
		testAssociation2.signalCommUp();
		assertEquals(AspState.DOWN, this.getAspState(aspPeerFSM2));

		// Check for ASP_UP for ASP1
		M3UAMessageImpl message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
		aspFactory1.read(message);
		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM1));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));

		// also the AS should be INACTIVE now
		assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

		// Check for ASP_UP for ASP2
		message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
		aspFactory2.read(message);

		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM2));
		assertTrue(validateMessage(testAssociation2, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
		// also the AS should be INACTIVE
		assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

		// Check for ASP_ACTIVE for ASP1
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(rc);
		aspFactory1.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(aspPeerFSM1));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// But AS still INACTIVE as atleast 2 ASP's should be ACTIVE
		assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));

		// Check for ASP_ACTIVE for ASP2
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(rc);
		aspFactory2.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(aspPeerFSM2));
		assertTrue(validateMessage(testAssociation2, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// Now AS will be ACTIVE and send NTFY to both the ASP's
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

		// INACTIVATE ASP1.But AS remains ACTIVE in any case
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE);
		((ASPInactiveImpl) message).setRoutingContext(rc);
		aspFactory1.read(message);
		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM1));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE,
				MessageType.ASP_INACTIVE_ACK, -1, -1));
		// ASP1 also receives NTFY Ins ASP Resource as we have fallen bellow
		// threshold
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY, Status.STATUS_Other,
				Status.INFO_Insufficient_ASP_Resources_Active));
		// AS remains ACTIVE
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));

		// Bring down ASP1
		message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_DOWN);
		aspFactory1.read(message);
		assertEquals(AspState.DOWN, this.getAspState(aspPeerFSM1));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_DOWN_ACK, -1,
				-1));
		assertNull(testAssociation1.txPoll());
		// AS remains ACTIVE
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));

		// INACTIVATE ASP2.Now AS becomes PENDING and sends NTFY to all ASP's in
		// INACTIVE state
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE);
		((ASPInactiveImpl) message).setRoutingContext(rc);
		aspFactory2.read(message);
		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM2));
		assertTrue(validateMessage(testAssociation2, MessageClass.ASP_TRAFFIC_MAINTENANCE,
				MessageType.ASP_INACTIVE_ACK, -1, -1));
		// AS remains ACTIVE
		assertEquals(AsState.PENDING, this.getAsState(asLocalFSM));
		// AS state change NTFY message
		assertTrue(validateMessage(testAssociation2, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));

		assertNull(testAssociation1.txPoll());
		assertNull(testAssociation2.txPoll());
	}

	@Test
	public void testAspUpReceivedWhileASPIsAlreadyUp() throws Exception {
		// Test bug http://code.google.com/p/mobicents/issues/detail?id=2436

		TestAssociation testAssociation1 = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
				"testAssoc1");

		// 4.3.4.1. ASP Up Procedures from http://tools.ietf.org/html/rfc4666
		RoutingContext rc = parmFactory.createRoutingContext(new long[] { 100 });

		DestinationPointCode[] dpc = new DestinationPointCode[] { parmFactory
				.createDestinationPointCode(123, (short) 0) };

		ServiceIndicators[] servInds = new ServiceIndicators[] { parmFactory.createServiceIndicators(new short[] { 3 }) };

		TrafficModeType trModType = parmFactory.createTrafficModeType(TrafficModeType.Override);
		LocalRKIdentifier lRkId = parmFactory.createLocalRKIdentifier(1);
		RoutingKey rKey = parmFactory.createRoutingKey(lRkId, rc, null, null, dpc, servInds, null);

		// As remAs = sgw.createAppServer("testas", rc, rKey, trModType);

		As remAs = serverM3UAMgmt.createAs("testas", Functionality.SGW, ExchangeType.SE, null, rc, trModType);
		// AspFactory aspFactory = sgw.createAspFactory("testasp", "127.0.0.1",
		// 2777);
		AspFactory aspFactory = serverM3UAMgmt.createAspFactory("testasp", "testAssoc1");

		Asp remAsp = serverM3UAMgmt.assignAspToAs("testas", "testasp");
		FSM aspPeerFSM = remAsp.getPeerFSM();

		FSM asLocalFSM = remAs.getLocalFSM();

		// Check for Communication UP
		testAssociation1.signalCommUp();

		assertEquals(AspState.DOWN, this.getAspState(aspPeerFSM));

		// Check for ASP_UP
		M3UAMessageImpl message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
		aspFactory.read(message);

		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
		// also the AS should be INACTIVE now
		assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

		// Check for ASP_ACTIVE
		message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
		((ASPActiveImpl) message).setRoutingContext(rc);
		aspFactory.read(message);
		assertEquals(AspState.ACTIVE, this.getAspState(aspPeerFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
				-1, -1));
		// also the AS should be ACTIVE now
		assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

		// Check for ASP_UP received while ASP is already UP
		message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
		aspFactory.read(message);
		// The ASP Transitions to INACTIVE
		assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM));
		// Receives ASP_UP Ack messages
		assertTrue(validateMessage(testAssociation1, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
		// As well as receives Error message
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.ERROR,
				ErrorCode.Unexpected_Message, 100));

		// also the AS should be PENDING now
		assertEquals(AsState.PENDING, this.getAsState(asLocalFSM));
		assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
				Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));

		// Make sure we don't have any more
		assertNull(testAssociation1.txPoll());

	}
	
	 @Test
     public void testPendingQueue() throws Exception {

             TestAssociation testAssociation1 = (TestAssociation) this.transportManagement.addAssociation(null, 0, null, 0,
                             "testAssoc1");

             RoutingContext rc = parmFactory.createRoutingContext(new long[] { 100 });

             DestinationPointCode[] dpc = new DestinationPointCode[] { parmFactory
                             .createDestinationPointCode(123, (short) 0) };

             ServiceIndicators[] servInds = new ServiceIndicators[] { parmFactory.createServiceIndicators(new short[] { 3 }) };

             TrafficModeType trModType = parmFactory.createTrafficModeType(TrafficModeType.Override);
             LocalRKIdentifier lRkId = parmFactory.createLocalRKIdentifier(1);
             RoutingKey rKey = parmFactory.createRoutingKey(lRkId, rc, null, null, dpc, servInds, null);

             // As remAs = sgw.createAppServer("testas", rc, rKey, trModType);
             As remAs = serverM3UAMgmt.createAs("testas", Functionality.SGW, ExchangeType.SE, null, rc, trModType);
             FSM asLocalFSM = remAs.getLocalFSM(); 
             
             // AspFactory aspFactory = sgw.createAspFactory("testasp", "127.0.0.1",
             // 2777);
             AspFactory aspFactory = serverM3UAMgmt.createAspFactory("testasp", "testAssoc1");

             Asp remAsp = serverM3UAMgmt.assignAspToAs("testas", "testasp");
             FSM aspPeerFSM = remAsp.getPeerFSM();

             // Check for Communication UP
             testAssociation1.signalCommUp();

             assertEquals(AspState.DOWN, this.getAspState(aspPeerFSM));

             // Check for ASP_UP
             M3UAMessageImpl message = messageFactory.createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
             aspFactory.read(message);

             assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM));
             assertTrue(validateMessage(testAssociation1, MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK, -1, -1));
             // also the AS should be INACTIVE now
             assertEquals(AsState.INACTIVE, this.getAsState(asLocalFSM));
             assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
                             Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));

             // Check for ASP_ACTIVE
             message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
             ((ASPActiveImpl) message).setRoutingContext(rc);
             aspFactory.read(message);
             assertEquals(AspState.ACTIVE, this.getAspState(aspPeerFSM));
             assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK,
                             -1, -1));
             // also the AS should be ACTIVE now
             assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));
             assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
                             Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

             // Check for ASP_INACTIVE
             message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE);
             ((ASPInactiveImpl) message).setRoutingContext(rc);
             aspFactory.read(message);
             assertEquals(AspState.INACTIVE, this.getAspState(aspPeerFSM));
             assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE,
                             MessageType.ASP_INACTIVE_ACK, -1, -1));
             // also the AS should be PENDING now
             assertEquals(AsState.PENDING, this.getAsState(asLocalFSM));
             assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
                             Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));

             // Add PayloadData
             PayloadDataImpl payload = (PayloadDataImpl) messageFactory.createMessage(MessageClass.TRANSFER_MESSAGES,
                             MessageType.PAYLOAD);
             ProtocolDataImpl p1 = (ProtocolDataImpl) parmFactory.createProtocolData(1408, 123, 3, 1, 0, 1, new byte[] { 1,
                             2, 3, 4 });
             payload.setRoutingContext(rc);
             payload.setData(p1);

             remAs.write(payload);

             // Now bring UP the ASP
             message = messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE);
             ((ASPActiveImpl) message).setRoutingContext(rc);
             aspFactory.read(message);

             assertTrue(validateMessage(testAssociation1, MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK, -1, -1));
             // also the AS should be ACTIVE now
             assertEquals(AsState.ACTIVE, this.getAsState(asLocalFSM));
             assertTrue(validateMessage(testAssociation1, MessageClass.MANAGEMENT, MessageType.NOTIFY,
                             Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

             // Also we should have PayloadData
             M3UAMessage payLoadTemp = testAssociation1.txPoll();
             assertNotNull(payLoadTemp);
             assertEquals(MessageClass.TRANSFER_MESSAGES, payLoadTemp.getMessageClass());
             assertEquals(MessageType.PAYLOAD, payLoadTemp.getMessageType());

             // Make sure we don't have any more
             assertNull(testAssociation1.txPoll());
     }

	/**
	 * 
	 * @param factory
	 * @param msgClass
	 * @param msgType
	 * @param type
	 *            The type for Notify message Or Error Code for Error Messages
	 * @param info
	 *            The Info for Notify message Or RoutingContext for Error
	 *            Message
	 * @return
	 */
	private boolean validateMessage(TestAssociation testAssociation, int msgClass, int msgType, int type, int info) {
		M3UAMessage message = testAssociation.txPoll();
		if (message == null) {
			return false;
		}

		if (message.getMessageClass() != msgClass || message.getMessageType() != msgType) {
			return false;
		}

		if (message.getMessageClass() == MessageClass.MANAGEMENT) {
			if (message.getMessageType() == MessageType.NOTIFY) {
				Status s = ((Notify) message).getStatus();
				if (s.getType() != type || s.getInfo() != info) {
					return false;
				} else {
					return true;
				}
			} else if (message.getMessageType() == MessageType.ERROR) {
				ErrorCode errCode = ((org.mobicents.protocols.ss7.m3ua.message.mgmt.Error) message).getErrorCode();
				if (errCode.getCode() != type) {
					return false;
				}

				RoutingContext rc = ((org.mobicents.protocols.ss7.m3ua.message.mgmt.Error) message).getRoutingContext();
				if (rc == null || rc.getRoutingContexts()[0] != info) {
					return false;
				}

				return true;

			}
			return false;
		} else {
			return true;
		}

	}

	class TestAssociation implements Association {

		private AssociationListener associationListener = null;
		private String name = null;
		private LinkedList<M3UAMessage> messageRxFromUserPart = new LinkedList<M3UAMessage>();

		TestAssociation(String name) {
			this.name = name;
		}

		M3UAMessage txPoll() {
			return messageRxFromUserPart.poll();
		}

		@Override
		public AssociationListener getAssociationListener() {
			return this.associationListener;
		}

		@Override
		public String getHostAddress() {
			return null;
		}

		@Override
		public int getHostPort() {
			return 0;
		}

		@Override
		public String getName() {
			return null;
		}

		@Override
		public String getPeerAddress() {
			return null;
		}

		@Override
		public int getPeerPort() {
			return 0;
		}

		@Override
		public String getServerName() {
			return null;
		}

		@Override
		public boolean isStarted() {
			return false;
		}

		@Override
		public void send(PayloadData payloadData) throws Exception {
			M3UAMessage m3uaMessage = messageFactory.createSctpMessage(payloadData.getData());
			this.messageRxFromUserPart.add(m3uaMessage);
		}

		@Override
		public void setAssociationListener(AssociationListener associationListener) {
			this.associationListener = associationListener;
		}

		public void signalCommUp() {
			this.associationListener.onCommunicationUp(this);
		}

		public void signalCommLost() {
			this.associationListener.onCommunicationLost(this);
		}

	}

	class TransportManagement implements Management {

		private FastMap<String, Association> associations = new FastMap<String, Association>();

		@Override
		public Association addAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort,
				String assocName) throws Exception {
			TestAssociation testAssociation = new TestAssociation(assocName);
			this.associations.put(assocName, testAssociation);
			return testAssociation;
		}

		@Override
		public Server addServer(String serverName, String hostAddress, int port) throws Exception {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Association addServerAssociation(String peerAddress, int peerPort, String serverName, String assocName)
				throws Exception {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Association getAssociation(String assocName) throws Exception {
			return this.associations.get(assocName);
		}

		@Override
		public Map<String, Association> getAssociations() {
			return associations.unmodifiable();
		}

		@Override
		public int getConnectDelay() {
			return 0;
		}

		@Override
		public String getName() {
			return null;
		}

		@Override
		public List<Server> getServers() {
			return null;
		}

		@Override
		public int getWorkerThreads() {
			return 0;
		}

		@Override
		public boolean isSingleThread() {
			return false;
		}

		@Override
		public void removeAssociation(String assocName) throws Exception {

		}

		@Override
		public void removeServer(String serverName) throws Exception {

		}

		@Override
		public void setConnectDelay(int connectDelay) {

		}

		@Override
		public void setSingleThread(boolean arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void setWorkerThreads(int arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void start() throws Exception {
			// TODO Auto-generated method stub

		}

		@Override
		public void startAssociation(String arg0) throws Exception {
			// TODO Auto-generated method stub

		}

		@Override
		public void startServer(String arg0) throws Exception {
			// TODO Auto-generated method stub

		}

		@Override
		public void stop() throws Exception {
			// TODO Auto-generated method stub

		}

		@Override
		public void stopAssociation(String arg0) throws Exception {
			// TODO Auto-generated method stub

		}

		@Override
		public void stopServer(String arg0) throws Exception {
			// TODO Auto-generated method stub

		}

	}
}