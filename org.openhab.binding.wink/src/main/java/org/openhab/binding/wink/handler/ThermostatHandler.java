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
package org.openhab.binding.wink.handler;

import static org.openhab.binding.wink.WinkBindingConstants.*;

import java.util.Map;
import java.util.HashMap;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.wink.client.IWinkDevice;
import org.openhab.binding.wink.client.WinkSupportedDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link ThermostatHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Joel Shuman - Initial contribution
 */
//@NonNullByDefault
public class ThermostatHandler extends WinkBaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(ThermostatHandler.class);

    public ThermostatHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleWinkCommand(ChannelUID channelUID, Command command) {
        logger.debug("Thermostat handleWinkCommand ChannelUID: {}", channelUID.getId());
        logger.debug("Thermostat handleWinkCommand command instanceof: {}", command.getClass().getName());
        if (command instanceof RefreshType) {
            logger.debug("Refreshing state");
            updateDeviceState(getDevice());
        }
        if (channelUID.getId().equals(CHANNEL_THERMOSTAT_CURRENTSETPOINT)) {
            if (command instanceof Number) {
                logger.debug("Setting desired temperature {}", command);
                float temperature = ((Number) command).floatValue();
                setDesiredTemperature(temperature);
            }
        } else if (channelUID.getId().equals(CHANNEL_THERMOSTAT_CURRENTMODE)) {
            if (command instanceof StringType) {
                logger.debug("Setting desired mode {}", command);
                String mode = ((StringType) command).toString();
                setDesiredMode(mode);
            }
        }
    }

    private void setDesiredTemperature(float temperature) {
        Map<String, String> updatedState = new HashMap<String, String>();
        IWinkDevice device = getDevice();
        Map<String, String> jsonData = device.getCurrentState();

        String units = "f";
        if (jsonData.get("units") != null && !jsonData.get("units").equals("null")) {
            units = getTemperatureUnits(jsonData.get("units"));
        }

        float desiredTemperature = temperature;
        if (units.equals("f")) {
            // Convert to Celcius before setting new temp.
            desiredTemperature = (desiredTemperature - 32) / 1.8f;
        }

        if (jsonData.get("mode") != null && !jsonData.get("mode").equals("null")) {
            String currentOperationMode = jsonData.get("mode");
            if (currentOperationMode.equals("cool_only")) {
                updatedState.put("max_set_point", String.valueOf(desiredTemperature));
            } else if (currentOperationMode.equals("heat_only")) {
                updatedState.put("min_set_point", String.valueOf(desiredTemperature));
            } else { // auto
                // Set them both the same.
                updatedState.put("min_set_point", String.valueOf(desiredTemperature));
                updatedState.put("max_set_point", String.valueOf(desiredTemperature));
            }

            logger.debug("Setting new temperature to {}", desiredTemperature);
            bridgeHandler.setDesiredState(device, updatedState);
        }
    }

    private void setDesiredMode(String mode) {
        Map<String, String> updatedState = new HashMap<String, String>();
        if (mode.equals("Cool")) {
            updatedState.put("mode", String.valueOf("cool_only"));
        } else if (mode.equals("Heat")) {
            updatedState.put("mode", String.valueOf("heat_only"));
        } else if (mode.equals("Auto")) {
            updatedState.put("mode", String.valueOf("auto"));
        } else {
            // unknown mode.
            logger.warn("Detected unknown wink:thermostat mode '{}'", mode);
        }

        logger.debug("Setting new mode to {}", mode);
        bridgeHandler.setDesiredState(getDevice(), updatedState);
    }

    @Override
    protected WinkSupportedDevice getDeviceType() {
        return WinkSupportedDevice.THERMOSTAT;
    }

    @Override
    protected void updateDeviceState(IWinkDevice device) {
        Map<String, String> jsonData = device.getCurrentState();
        Map<String, String> jsonDataDesired = device.getDesiredState();

        String units = "f";
        if (jsonData.get("units") != null && !jsonData.get("units").equals("null")) {
            units = getTemperatureUnits(jsonData.get("units"));
        }

        if (jsonData.get("temperature") != null && !jsonData.get("temperature").equals("null")) {
            Float temperature = Float.valueOf(jsonData.get("temperature"));
            if ("f".equals(units)) {
                temperature = temperature * 1.8f + 32;
            }
            updateState(CHANNEL_THERMOSTAT_CURRENTTEMPERATURE, new DecimalType(temperature));
            logger.debug("Updated CHANNEL_THERMOSTAT_CURRENTTEMPERATURE");
        }

        if (jsonData.get("mode") != null && !jsonData.get("mode").equals("null")) {
            // When setting the mode or set temp, the pubnub will return the desired_state data.
            // Usually the pubnub returns an empty string for the desired_state data.
            String desiredOperationMode = jsonData.get("mode");
            String desiredMaxSetPoint = jsonData.get("max_set_point");
            String desiredMinSetPoint = jsonData.get("min_set_point");

            if (!jsonDataDesired.isEmpty()){
                if (jsonDataDesired.containsKey("mode") && !jsonDataDesired.get("mode").isEmpty()){
                    desiredOperationMode = jsonDataDesired.get("mode");
                }

                if (jsonDataDesired.containsKey("max_set_point") && !jsonDataDesired.get("max_set_point").isEmpty()){
                    desiredMaxSetPoint = jsonDataDesired.get("max_set_point");
                }

                if (jsonDataDesired.containsKey("min_set_point") && !jsonDataDesired.get("min_set_point").isEmpty()){
                    desiredMinSetPoint = jsonDataDesired.get("min_set_point");
                }
            }
            
            String currentOperationModePretty = "";
            float currentSetPoint = 0.0f;
            float minSet = Float.valueOf(desiredMinSetPoint);
            float maxSet = Float.valueOf(desiredMaxSetPoint);

            if (desiredOperationMode.equals("cool_only")) {
                currentSetPoint = maxSet;
                currentOperationModePretty = "Cool";
            } else if (desiredOperationMode.equals("heat_only")) {
                currentSetPoint = minSet;
                currentOperationModePretty = "Heat";
            } else { // auto
                // return the lower of the two.
                currentSetPoint = maxSet < minSet ? maxSet : minSet;
                currentOperationModePretty = "Auto";
            }

            if ("f".equals(units)) {
                currentSetPoint = currentSetPoint * 1.8f + 32;
            }

            updateState(CHANNEL_THERMOSTAT_CURRENTSETPOINT, new DecimalType(currentSetPoint));
            updateState(CHANNEL_THERMOSTAT_CURRENTMODE, new StringType(currentOperationModePretty));
        }

        if (jsonData.get("smart_temperature") != null && !jsonData.get("smart_temperature").equals("null")) {
            Float smartTemperature = Float.valueOf(jsonData.get("smart_temperature"));
            if ("f".equals(units)) {
                smartTemperature = smartTemperature * 1.8f + 32;
            }
            updateState(CHANNEL_THERMOSTAT_SMARTTEMPERATURE, new DecimalType(smartTemperature));
        }

        if (jsonData.get("external_temperature") != null && !jsonData.get("external_temperature").equals("null")) {
            Float externalTemperature = Float.valueOf(jsonData.get("external_temperature"));
            if ("f".equals(units)) {
                externalTemperature = externalTemperature * 1.8f + 32;
            }
            updateState(CHANNEL_THERMOSTAT_EXTERNALTEMPERATURE, new DecimalType(externalTemperature));
        }

        if (jsonData.get("humidity") != null && !jsonData.get("humidity").equals("null")) {
            // pubnub response: '0.62'
            // polling response: '62'  Need to handle both.
            Float humidity = Float.valueOf(jsonData.get("humidity"));
            if (1.0 > humidity){
                humidity *= 100;
            }
            updateState(CHANNEL_THERMOSTAT_HUMIDITY, new DecimalType(humidity));
        }

        if (jsonData.get("connection") != null && !jsonData.get("connection").equals("null")) {
            final boolean connectionState = Boolean.valueOf(jsonData.get("connection"));
            updateState(CHANNEL_THERMOSTAT_ONLINE, connectionState ? OnOffType.ON : OnOffType.OFF);
        }

        if (jsonData.get("has_fan") != null && !jsonData.get("has_fan").equals("null")) {
            final boolean hasFan = Boolean.valueOf(jsonData.get("has_fan"));
            updateState(CHANNEL_THERMOSTAT_HASFAN, hasFan ? OnOffType.ON : OnOffType.OFF);
        }

        if (jsonData.get("eco_target") != null && !jsonData.get("eco_target").equals("null")) {
            final boolean ecoMode = Boolean.valueOf(jsonData.get("eco_target"));
            updateState(CHANNEL_THERMOSTAT_ECOMODE, ecoMode ? OnOffType.ON : OnOffType.OFF);
        }

        if (jsonData.get("technician_name") != null && !jsonData.get("technician_name").equals("null")) {
            updateState(CHANNEL_THERMOSTAT_TECHNAME, new StringType(jsonData.get("technician_name")));
        }

        if (jsonData.get("technician_phone") != null && !jsonData.get("technician_phone").equals("null")) {
            updateState(CHANNEL_THERMOSTAT_TECHPHONE, new StringType(jsonData.get("technician_phone")));
        }

        if (jsonData.get("fan_active") != null && !jsonData.get("fan_active").equals("null")) {
            final boolean fanState = Boolean.valueOf(jsonData.get("fan_active"));
            updateState(CHANNEL_THERMOSTAT_FANACTIVE, fanState ? OnOffType.ON : OnOffType.OFF);
        }

        if (jsonData.get("last_error") != null && !jsonData.get("last_error").equals("null")) {
            updateState(CHANNEL_THERMOSTAT_LASTERROR, new StringType(jsonData.get("last_error")));
        }

        if (jsonData.get("occupied") != null && !jsonData.get("occupied").equals("null")) {
            final boolean occupied = Boolean.valueOf(jsonData.get("occupied"));
            updateState(CHANNEL_THERMOSTAT_OCCUPIED, occupied ? OnOffType.ON : OnOffType.OFF);
        }

        final boolean auxActive  = Boolean.valueOf(jsonData.get("aux_active"));
        final boolean coolActive = Boolean.valueOf(jsonData.get("cool_active"));
        final boolean heatActive = Boolean.valueOf(jsonData.get("heat_active"));

        String runningMode = null;
        if (auxActive && !coolActive && !heatActive) {
            runningMode = "Aux";
        } else if (!auxActive && coolActive && !heatActive) {
            runningMode = "Cool";
        } else if (!auxActive && !coolActive && heatActive) {
            runningMode = "Heat";
        } else if (!auxActive && !coolActive && !heatActive) {
            runningMode = "Idle";
        } else {
            runningMode = "Unknown";
        }

        updateState(CHANNEL_THERMOSTAT_RUNNINGMODE, new StringType(runningMode));
    }

    private String getTemperatureUnits(String rawUnits){
        // Wink API sends units in two formats. 'f', or '{temperature=f}'
        logger.debug("units string contents: {}", rawUnits);

        String units = rawUnits;
        // If it is larger than 1 char, parse the json to get the temperature.
        if (units.length() > 1){
            JsonElement jsonElement = new JsonParser().parse(units);
            if (!jsonElement.isJsonNull()){
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                if (!jsonObject.isJsonNull() && !jsonObject.get("temperature").isJsonNull()){
                    units = jsonObject.get("temperature").getAsString();
                }
            }
        }
        return units;
    }
}
