/*****************************************************************************************************************
 *  Copyright Daniel Terryn
 *
 *  Name: Driver to communicate with Hue Sync Box....
 *
 *  Date: 2020-02-07
 *
 *  Version: 1.20
 *
 *  Author: Daniel Terryn
 *
 *  Description: A driver to retrieve the current time from an NTP server and update the hub....
 *
 *  License:
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *   for the specific language governing permissions and limitations under the License.
 *****************************************************************************************************************
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2020-02-07  Daniel Terryn  Original Creation
 *
 * 
 */

metadata {
    definition (name: "Hue Sync Box", author: "dan.t", namespace: "dan.t", importUrl: "https://raw.githubusercontent.com/danTapps/Hubitat/master/Drivers/Hue%20Sync%20Box/hue_sync_box.groovy") {
        capability "Refresh"
        capability "Switch"
        capability "Initialize"
        capability "Presence Sensor"

		command "setMode", [[name:"Set Mode*", type: "ENUM", description: "Set Mode", constraints: ["passthrough", "video", "music", "game"] ] ]
		command "setInput", [[name:"Set Input*", type: "ENUM", description: "Set Input", constraints: ["input1", "input2", "input3", "input3"] ] ]
		command "setItensity", [[name:"Mode*", type: "ENUM", description: "Mode", constraints: ["video", "music", "game"] ],[name:"Intensity*", type: "ENUM", description: "Intensity", constraints: ["subtle", "moderate", "high", "intense"] ] ]
        //command "toggleMode"
        command "checkForUpdates"
        command "registerHueSyncBox"
        
        attribute "brightness", "number"
        attribute "mode", "string"
        attribute "hdmiSource", "string"
        attribute "lastSyncMode", "string"
        attribute "firmwareVersion", "string"
        attribute "lastCheckedUpdate", "string"
        attribute "updateAvailable", "string"
        attribute "intensity", "string"

        
    }
    preferences {
        input ( name: "ip", type: "text", title: "HUE Sync Box IP Address", description: "IP Address in form 192.168.1.226", required: true, displayDuringSetup: true )
        input ( name: "api_key", type: "text", title: "API Key", required: false, displayDuringSetup: true )
        input ( name: 'pollInterval', type: 'enum', title: 'Update interval (in seconds)', options: ['10', '30', '60', '300'], required: true, displayDuringSetup: true, defaultValue: "60" )
        input ( name: "configLoggingLevelIDE", title: "IDE Live Logging Level:\nMessages with this level and higher will be logged to the IDE.", type: "enum",
            options: [
                "0" : "None",
                "1" : "Error",
                "2" : "Warning",
                "3" : "Info",
                "4" : "Debug",
                "5" : "Trace"
            ],
            defaultValue: "3", displayDuringSetup: true, required: false )      
    }
}


def installed() {
    logger("Executing 'installed()'", "info")
    initialize()
}

def initialize() {
    logger("Executing 'initialize()'", "debug")
    updated()
}

def updated() {
    logger("Executing 'updated()'", "debug")
    configure()
    state.authTryCount = 0
}

def configure() {
    logger("Executing 'configure()'", "info")
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3

    unschedule()
    def pollInt = settings["pollInterval"]?.toInteger()
    // If change polling options in UI, may need to modify some of these cases:
    switch (pollInt ?: 0) {
        case 0:
            logger("Polling disabled; not scheduling")
            break
        case 1..59:
            logger("Scheduling polling every ${pollInt} seconds")
            schedule("${Math.round(Math.random() * pollInt)}/${pollInt} * * ? * * *", "refresh")
            break
        case 60..259:
            logger("Scheduling polling every 1 minute")
            runEvery1Minute("refresh")
            break
        case 300..1800:
            logger("Schedulig polling every 5 minutes")
            runEvery5Minutes("refresh")
            break
        default:
            logger("Scheduling polling every hour")
            runEvery1Hour("refresh")                
    }
    refresh()
}

def registerHueSyncBox()
{
    if (settings.api_key != null) {
        log.error "Hue Sync Box is already regsitered, please clear the API key setting to re-register your sync box"
        return
    }
    if (settings.ip == null) {
        log.error "Hue Sync Box IP has to be set"
        return
    }
    if (state.authTryCount > 19) {
        log.debug "I tried 20 times to register, I quit now. Check your settings."
        return
    }
    log.debug "Running registration, press the power button for 2 seconds on the Hue Sync Box and make sure the TV is on, attempt ${state.authTryCount+1}"

    unschedule()
    def body = [:]
    body.appName = "HueSyncBoxDriver"
    body.instanceName = "HueSyncBoxDriver"
    body.appSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
    
    def requestParams =
    [
        uri:  "https://${settings["ip"]}/api/v1/registrations",
        contentType: "application/json",
        ignoreSSLIssues:  true,
        body : toJson(body)
    ]
    try{
        httpPost(requestParams)
        {
            response ->
        	    if (response?.status == 200)
	            {
                    if (response?.data?.accessToken) {
                        device.updateSetting("api_key", [value:response?.data?.accessToken, type:"text"])
                        configure()
                    }
                    else {
                        log.error("Unknown error adding Hue Sync Box ${response?.data}")
                        runIn(10, "registerHueSyncBox")
                        state.authTryCount = state.authTryCount + 1                        
                    }
                }
	            else
	            {
                    log.error "Error in registration Response ${response?.status}"
                    runIn(10, "registerHueSyncBox")
                    state.authTryCount = state.authTryCount + 1
                }
        }
    } catch (groovyx.net.http.HttpResponseException hre) {
        log.error "Error:${hre.getResponse()?.getData()}"
        runIn(10, "registerHueSyncBox")
        state.authTryCount = state.authTryCount + 1
    } catch (Exception e) {	
        log.error "Something went wrong when posting: ${e}"
        runIn(10, "registerHueSyncBox")
        state.authTryCount = state.authTryCount + 1
    }      
}

private sendUsernameRequest() {
    def userDesc = "HueSyncBox"
    def body = [:]
    body.appName = userDesc
    body.instanceName = userDesc
    body.appSecret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="

    def requestParams =
    [
        uri:  "https://${settings["syncBoxIP"]}/api/v1/registrations",
        contentType: "application/json",
        ignoreSSLIssues:  true,
        body : toJson(body)
    ]
    //asynchttpPost("parseUsernameResponse", requestParams)
    log.debug "${requestParams}"
    try{
        httpPost(requestParams)
        {
            response ->
        	    if (response?.status == 200)
	            {
                    if (response?.data?.accessToken) {
                        state["accessToken"] = response?.data?.accessToken
                        state["syncBoxAuthorized"] = true
                    }
                    else {
                        log.error("Unknown error adding Hue Sync Box ${response?.data}")
                    }
                }
	            else
	            {
                    log.error "Error in registration Response ${response?.status}"
                }
        }
    } catch (groovyx.net.http.HttpResponseException hre) {
          log.error "Error:${hre.getResponse()?.getData()}"
    } catch (Exception e) {	
          log.error "Something went wrong when posting: ${e}"
    }          
}


def refresh() {
    if (settings.api_key == null) {
        log.error "Hue Sync Box is not registered, please register the Hue Sync Box with the registration flow"
        return
    }
    if (settings.ip == null) {
        log.error "Hue Sync Box IP is not set, please updated your setting"
        return
    }
    logger("Executing 'refresh()'", "debug")
    try{
        def data = sendAsyncHttpGet("")
        def switchState = "off"
        if (data?.execution?.syncActive == true) {
            switchState = "on"
            if (data?.execution?.mode)
                if (data?.execution[data?.execution?.mode]?.intensity) 
                    valueChangeEvent("intensity", data?.execution[data?.execution?.mode]?.intensity, "")
        } else {
            valueChangeEvent("intensity", "off", "")
        }
        valueChangeEvent("switch", switchState, "")
        if (data?.execution?.brightness)
            valueChangeEvent("brightness", data?.execution?.brightness, "")
        if (data?.execution?.mode)
            valueChangeEvent("mode", data?.execution?.mode, "")
        if (data?.execution?.hdmiSource)
            valueChangeEvent("hdmiSource", data?.execution?.hdmiSource, "")
        if (data?.execution?.lastSyncMode)
            valueChangeEvent("lastSyncMode", data?.execution?.lastSyncMode, "")
        if (data?.device?.firmwareVersion)
            valueChangeEvent("firmwareVersion", data?.device?.firmwareVersion, "")
        if (data?.device?.lastCheckedUpdate)
            valueChangeEvent("lastCheckedUpdate", data?.device?.lastCheckedUpdate, "")
        if (data?.device?.updatableFirmwareVersion)
            valueChangeEvent("updateAvailable", "true", "")
        else
            valueChangeEvent("updateAvailable", "false", "")
        valueChangeEvent("presence", "present", "")
    } catch (Exception e){
        logger("refresh() exception ${e}", "error")    
        valueChangeEvent("presence", "not present", "")
    }      
}

def toggleMode()
{
    try{
        def currentMode = state?.data_points["mode"]
        setMode(state?.data_points["lastSyncMode"])
        valueChangeEvent("lastSyncMode", currentMode, "")        

    } catch (Exception e){
        logger("toggleMode() exception ${e}", "error")    
    }
}

def checkForUpdates()
{
    try{
        sendAsyncHttpPut("/device", "{\"action\": \"checkForFirmwareUpdates\" }")
    } catch (Exception e){
        logger("checkForUpdates() exception ${e}", "error")    
    }

}

def setInput(input)
{
    logger("Receive \"setInput(\"${input}\")\" command", "info")

    try{
        sendAsyncHttpPut("/execution", "{\"hdmiSource\":\"${input}\"}")
        valueChangeEvent("hdmiSource", input, "")        
    } catch (Exception e){
        logger("setInput() exception ${e}", "error")    
    }
}

def setMode(mode)
{
    logger("Receive \"setMode(\"${mode}\")\" command", "info")
    try{
        sendAsyncHttpPut("/execution", "{\"mode\": \"${mode}\"}")
        valueChangeEvent("mode", mode, "")
        if (mode == "passthrough")
            valueChangeEvent("intensity", "", "")

    } catch (Exception e){
        logger("setMode() exception ${e}", "error")    
    }
}

def setItensity(mode, intensity)
{
    logger("Receive \"setItensity(\"${mode}\", \"${intensity}\")\" command", "info")

    try{
        sendAsyncHttpPut("/execution", "{\"${mode}\":{ \"intensity\": \"${intensity}\"}}")
        valueChangeEvent("intensity", intensity, "")
    } catch (Exception e){
        logger("setItensity() exception ${e}", "error")    
    }    
}

def on() {
    logger("Receive \"on()\" command", "info")

    try{
        setMode("video")
        valueChangeEvent("switch", "on", "")
    } catch (Exception e){
        logger("on() exception ${e}", "error")    
    }      
}

def off() {
    logger("Receive \"off()\" command", "info")    
    try{
        setMode("passthrough")
        valueChangeEvent("switch", "off", "")
    } catch (Exception e){
        logger("off() exception ${e}", "error")    
    }      
}

def valueChangeEvent(def deviceAttribute, def newValue, def newUnit = "")
{
    def success = false
    def oldValue = null
    if (newValue == null)
        return false
    if (state?.data_points)
    {
        if (state?.data_points["${deviceAttribute}"])
        {
            oldValue = state?.data_points["${deviceAttribute}"]
            if (state?.data_points["${deviceAttribute}"] == newValue)
                return false
            else if (state?.data_points["${deviceAttribute}"].toString().equals(newValue.toString()))
                return false
        }
    }
    else
        state.data_points = [:]
    
    state.data_points["${deviceAttribute}"] = newValue
    
    logger("Send new ${deviceAttribute} state, old: ${oldValue}, new: ${newValue}", "info")
    def descriptionText = "${device.displayName} ${deviceAttribute} is ${newValue}"
    sendEvent(name: deviceAttribute, value: newValue, unit:  newUnit, descriptionText: descriptionText)
    
    return true;
}

def sendAsyncHttpPut(message, body)
{
    def requestParams =
    [
        uri:  "https://${settings.ip}/api/v1"+message,
        contentType: "application/json",
        headers: ["Authorization": "Bearer ${settings.api_key}"],
        timeout: 200, 
        ignoreSSLIssues:  true,
		body : body
    ]
        
    logger("sendAsyncHttpPut: ${requestParams}", "debug")
    
    try{
        httpPut(requestParams)  //change to httpGet for the get test.
        {
          response ->
	        if (response?.status == 200)
	        {
                logger(response.data, "debug")
		        return response.data
	        }
	        else
	        {
                logger("httpPut ${response?.status}", "warn")
	        }
        }
    } catch (org.apache.http.conn.HttpHostConnectException e) {
        logger("httpPut HttpHostConnectException ${e}", "error")
        valueChangeEvent("presence", "not present", "")
    } catch (org.apache.http.NoHttpResponseException e) {
        logger("httpPut NoHttpResponseException ${e}", "error")
        valueChangeEvent("presence", "not present", "")
    } catch (Exception e){
        logger("httpPut Exception ${e}", "error")
        valueChangeEvent("presence", "not present", "")
    }    
}

def sendAsyncHttpGet(message)
{
    def result = null
    def requestParams =
    [
        uri:  "https://${settings.ip}/api/v1"+message,
        contentType: "application/json",
        headers: ["Authorization": "Bearer ${settings.api_key}"],
        timeout: 200, 
        ignoreSSLIssues:  true
    ]
    logger("sendAsyncHttpGet: ${requestParams}", "debug")
 
    try{
        httpGet(requestParams)  //change to httpGet for the get test.
        {
          response ->
	        if (response?.status == 200)
	        {
                logger(response.data, "debug")
		        result = response.data
	        }
	        else
	        {
		        log.warn "${response?.status}"
	        }
        }
    } catch (org.apache.http.conn.HttpHostConnectException e) {
        logger("httpGet HttpHostConnectException ${e}", "error")
        valueChangeEvent("presence", "not present", "")
    } catch (org.apache.http.NoHttpResponseException e) {
        logger("httpGet NoHttpResponseException ${e}", "error")
        valueChangeEvent("presence", "not present", "")
    } catch (Exception e){
        logger("httpGet Exception ${e}", "error")
        valueChangeEvent("presence", "not present", "")
    }   
    return result
}

/**
 *  logger()
 *
 *  Wrapper function for all logging.
 **/
private logger(msg, level = "debug") {

    switch(level) {
        case "error":
            if (state.loggingLevelIDE >= 1) log.error msg
            break

        case "warn":
            if (state.loggingLevelIDE >= 2) log.warn msg
            break

        case "info":
            if (state.loggingLevelIDE >= 3) log.info msg
            break

        case "debug":
            if (state.loggingLevelIDE >= 4) log.debug msg
            break

        case "trace":
            if (state.loggingLevelIDE >= 5) log.trace msg
            break

        default:
            log.debug msg
            break
    }
}

def toJson(Map m)
{
    return new groovy.json.JsonBuilder(m).toString()
}