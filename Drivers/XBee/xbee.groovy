/*
 * XBee Presence
 *
 *  Copyright 2019 Daniel Terryn
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2019-05-21  Daniel Terryn  Original Creation
 * 
 */
metadata {
    definition (name: "XBee Presence", namespace: "dan.t", author: "Daniel Terryn") {
        capability "Sensor"
        capability "Configuration"
        capability "Battery"
        capability "Presence Sensor"
		capability "Switch"
		command "disable"
		command "enable"
    }

    preferences {

        input "fullVoltageValue", "enum", title:"Battery 100% mV:", required:true, defaultValue:3300, options:[3000:"3000 mV",3300:"3300 mV",3600:"3600 mV"]
        input "checkInterval", "enum", title:"Minutes elapsed until sensor is not present", required:true, defaultValue:3, options:[1:"1 Minute",2:"2 Minutes",3:"3 Minutes", 4:"4 Minutes",5:"5 Minutes"]
		input "disabledMode", "enum", title: "When disabling sensor, set presence to:", description: "Tap to set",
                    defaultValue:"auto", options: ["auto", "present", "not present"], displayDuringSetup: false
        input "enabledMode", "enum", title: "When enabling sensor, set presence to:", description: "Tap to set",
                    defaultValue:"auto", options: ["auto", "present", "not present"], displayDuringSetup: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def updated() {
    stopTimer()
    startTimer()
}

def installed() {
    // Arrival sensors only goes OFFLINE when Hub is off
}
def configure() {
    log.warn "configure..."
    return []
}
def parse(String description) {
    state.lastCheckin = now()

    handlePresenceEvent(true)
    if (description?.startsWith('catchall')) {
        parseCatchAllMessage(description)
    }

    return []
}

private Map parseCatchAllMessage(String description) {
    Map resultMap = [:]
    def cluster = zigbee.parse(description)
    if (cluster.clusterId == 0x0011 && cluster.command == 0x01){
        handleBatteryEvent(cluster)
    }
    
    return resultMap
}

/**
 * Create battery event from reported battery voltage.
 *
 * @param volts Battery voltage in mV
 */
private handleBatteryEvent(cluster) {
    def descriptionText

    def battery_string = ""
    for (element in cluster.data) {
        battery_string = battery_string + Integer.toString(element,16).padLeft(2, '0')
    }
    battery_mV = Integer.parseInt(battery_string)
    log.debug "Battery mV: ${battery_mV}"
    def value = 100
    if (battery_mV <= 2100) {
            value = 0
    }
    else {
        /* Formula
            Minimum Voltage = 2100mV
            Divider = (100% Voltage in mV - 2100) (max and default is 3600)
        */
        def offset = battery_mV - 2100
        value = Math.round((offset / (Integer.parseInt(fullVoltageValue)-2100)) * 100)
        if (value > 100)
            value = 100
    }
    def linkText = getLinkText(device)
    def currentPercentage = device.currentState("battery")?.value
    if (currentPercentage && (Integer.parseInt(currentPercentage, 10) == value)) {
        return
    }
    descriptionText = '{{ linkText }} battery was {{ value }}'
    def eventMap = [
                name: 'battery',
                value: value,
                descriptionText: descriptionText,
                translatable: true
            ]
    log.debug "Creating battery event for voltage=${battery_mV/1000}V: ${linkText} ${eventMap.name} is ${eventMap.value}%"
    sendEvent(eventMap)
    
}

private handlePresenceEvent(present) {
    def wasPresent = device.currentState("presence")?.value == "present"
    if (!wasPresent && present) {
        log.debug "Sensor is present"
        startTimer()
    } else if (!present) {
        log.debug "Sensor is not present"
        stopTimer()
    } else if (wasPresent && present) {
        log.debug "Sensor already present"
        return
    }
    def linkText = getLinkText(device)
    def descriptionText
	def enabledStatus = ""
    if ( present )
        descriptionText = "{{ linkText }} has arrived"
    else
        descriptionText = "{{ linkText }} has left"
	if ((device.currentValue("enabled") == "disabled-present") || (device.currentValue("enabled") == "disabled-not present")) {
    	// Device is disabled so we won't generate a presence event but instead just track the status behind the scenes by generating an enabled event
    	if (logEnable) log.debug "${linkText} is ${device.currentValue("enabled")}: not creating presence event"
        enabledStatus = "disabled-"
        enabledStatus = enabledStatus.concat(present ? "present" : "not present")
		if (device.currentValue("enabled") != enabledStatus) sendEvent(name: "enabled", value: enabledStatus, isStateChange: true)
	}
    else {
    	def eventMap = [
			name: "presence",
        	value: present ? "present" : "not present",
        	linkText: linkText,
        	descriptionText: descriptionText,
        	translatable: true
    	]
    enabledStatus = "enabled-"
        enabledStatus = enabledStatus.concat(present ? "present" : "not present")
		if (device.currentValue("enabled") != enabledStatus) sendEvent(name: "enabled", value: enabledStatus, isStateChange: true)
	   	if (logEnable)log.debug "Creating presence event: ${device.displayName} ${eventMap.name} is ${eventMap.value} with status ${device.currentValue("enabled")}"
    	sendEvent(eventMap)
    }
}

private startTimer() {
    log.debug "Scheduling periodic timer"
    runEvery1Minute("checkPresenceCallback")
}

private stopTimer() {
    log.debug "Stopping periodic timer"
    // Always unschedule to handle the case where the DTH was running in the cloud and is now running locally
    unschedule("checkPresenceCallback")
}

def checkPresenceCallback() {
    def timeSinceLastCheckin = (now() - state.lastCheckin ?: 0) / 1000
    def theCheckInterval = Integer.parseInt(checkInterval) * 60
    log.debug "Sensor checked in ${timeSinceLastCheckin} seconds ago"
    if (timeSinceLastCheckin >= theCheckInterval) {
        handlePresenceEvent(false)
    }
}

def enable() {
    // Force presence per user settings
    if (logEnable)log.debug "Setting sensor presence to ${settings.enabledMode}"
	sendEvent(name: "switch", value: "on", isStateChange: true)
	if (settings.enabledMode && (settings.enabledMode != "auto")) {
    	stopTimer()
    	sendEvent(name: "presence", value: settings.enabledMode, translatable: true)
        }
    else if (settings.enabledMode && (settings.enabledMode == "auto"))
	    startTimer()
	// Enable the device and update the enabled status to reflect the new status
	if (logEnable)log.debug "Enabling ${getLinkText(device)}"
    if (device.currentValue("presence") == "present")
		sendEvent(name: "enabled", value: "enabled-present", isStateChange: true)
    else if (device.currentValue("presence") == "not present")
		sendEvent(name: "enabled", value: "enabled-not present", isStateChange: true)
}

def disable() {
    // Force presence per user settings
    if (logEnable)log.debug "Setting sensor presence to ${settings.disabledMode}"
	sendEvent(name: "switch", value: "off", isStateChange: true)
	if (settings.disabledMode && (settings.disabledMode != "auto")) {
    	stopTimer()
    	sendEvent(name: "presence", value: settings.disabledMode, translatable: true)
        }
    else if (settings.disabledMode && (settings.disabledMode == "auto"))
	    startTimer()
	// Disable the device and update the enabled status to reflect the new status
	if (logEnable)log.debug "Disabling ${getLinkText(device)}"
    state.updatePresence = false
    if (device.currentValue("presence") == "present")
		sendEvent(name: "enabled", value: "disabled-present", isStateChange: true)
    else if (device.currentValue("presence") == "not present")
		sendEvent(name: "enabled", value: "disabled-not present", isStateChange: true)
}

def toggle() {
	// Button pressed, toggle the enabled state (which also tracks the current presence)
	if ((device.currentValue("enabled") == "enabled-present") || (device.currentValue("enabled") == "enabled-not present"))
    	disable()
    else
    	enable()
}

def on(){
	enable()
}

def off(){
	disable()
}
	