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

package org.mobicents.protocols.ss7.cap.api.service.circuitSwitchedCall;

import org.mobicents.protocols.ss7.cap.api.primitives.AChChargingAddress;
import org.mobicents.protocols.ss7.cap.api.primitives.CAPExtensions;
import org.mobicents.protocols.ss7.cap.api.primitives.SendingSideID;
import org.mobicents.protocols.ss7.cap.api.service.circuitSwitchedCall.primitive.CAMELAChBillingChargingCharacteristics;

/**
*

applyCharging {PARAMETERS-BOUND : bound} OPERATION ::= { 
 ARGUMENT  ApplyChargingArg {bound} 
 RETURN RESULT FALSE 
 ERRORS   {missingParameter | 
     unexpectedComponentSequence | 
     unexpectedParameter | 
     unexpectedDataValue | 
     parameterOutOfRange | 
     systemFailure | 
     taskRefused | 
     unknownLegID | 
     unknownCSID} 
 CODE   opcode-applyCharging} 
-- Direction: gsmSCF -> gsmSSF, Timer: Tac
-- This operation is used for interacting from the gsmSCF with the gsmSSF charging mechanisms. 
-- The ApplyChargingReport operation provides the feedback from the gsmSSF to the gsmSCF. 

ApplyChargingArg {PARAMETERS-BOUND : bound} ::= SEQUENCE {
aChBillingChargingCharacteristics [0] AChBillingChargingCharacteristics {bound},
partyToCharge [2] SendingSideID DEFAULT sendingSideID : leg1,
extensions [3] Extensions {bound} OPTIONAL,
aChChargingAddress [50] AChChargingAddress {bound}
DEFAULT legID:sendingSideID:leg1,
...
}

* 
* @author sergey vetyutnev
* 
*/
public interface ApplyChargingRequest extends CircuitSwitchedCallMessage {

	public CAMELAChBillingChargingCharacteristics getAChBillingChargingCharacteristics();

	public SendingSideID getPartyToCharge();

	public CAPExtensions getExtensions();

	public AChChargingAddress getAChChargingAddress();

}

