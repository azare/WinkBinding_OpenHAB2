/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.wink.internal;

import static org.openhab.binding.wink.WinkBindingConstants.*;

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.openhab.binding.wink.handler.BinarySwitchHandler;
import org.openhab.binding.wink.handler.DoorBellHandler;
import org.openhab.binding.wink.handler.LightBulbHandler;
import org.openhab.binding.wink.handler.LockHandler;
import org.openhab.binding.wink.handler.RemoteHandler;
import org.openhab.binding.wink.handler.ThermostatHandler;
import org.openhab.binding.wink.handler.WinkHub2BridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WinkHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Sebastien Marchand - Initial contribution
 */
//@NonNullByDefault
public class WinkHandlerFactory extends BaseThingHandlerFactory {

    private Logger logger = LoggerFactory.getLogger(WinkHandlerFactory.class);

    public static final Set<ThingTypeUID> DISCOVERABLE_DEVICE_TYPES_UIDS = new HashSet<>(Arrays.asList(THING_TYPE_LIGHT_BULB,
            THING_TYPE_REMOTE, THING_TYPE_BINARY_SWITCH, THING_TYPE_LOCK, THING_TYPE_DOORBELL, THING_TYPE_THERMOSTAT));

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS =  new HashSet<>(Arrays.asList(THING_TYPE_WINK_HUB_2));

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        logger.debug("Checking if the factory supports {}", thingTypeUID.toString());
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID)
                || DISCOVERABLE_DEVICE_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(THING_TYPE_WINK_HUB_2)) {
            return new WinkHub2BridgeHandler((Bridge) thing);
        } else if (thingTypeUID.equals(THING_TYPE_LIGHT_BULB)) {
            return new LightBulbHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_REMOTE)) {
            return new RemoteHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_BINARY_SWITCH)) {
            return new BinarySwitchHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_LOCK)) {
            return new LockHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_DOORBELL)) {
            return new DoorBellHandler(thing);
        } else if (thingTypeUID.equals(THING_TYPE_THERMOSTAT)) {
            return new ThermostatHandler(thing);
        }

        return null;
    }
}
