/*
 * TED Pro Home
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
    definition (name: "TED Pro Home", author: "dan.t", namespace: "dan.t") {
        capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Power Meter"
        capability "Voltage Measurement"
        capability "Configuration"
        capability "Initialize"

        command  "clearAlertMessage"

    }
    preferences {
        input ( name: "ip", type: "text", title: "TED IP Address", description: "IP Address in form 192.168.1.226", required: true, displayDuringSetup: true )
        input ( name: "port", type: "text", title: "TED Port", description: "port in form of 8090", required: true, displayDuringSetup: true )
        input ( name: "username", type: "text", title: "Username", required: false, displayDuringSetup: true )
        input ( name: "password", type: "password", title: "Password", required: false, displayDuringSetup: true )
        input ( name: 'pollInterval', type: 'enum', title: 'Update interval (in seconds)', options: ['10', '30', '60', '120', '300'], required: true, displayDuringSetup: true )
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
    state.hasSpyder = false
    if ( device.deviceNetworkId != getDNIfromIPandPort(settings.ip, settings.port))
    {
        state.alertMessage = "TED Parent Device has not yet been fully configured. Set IP address, port, username and password and click configure"
    }
    initialize()
}

def initialize() {
    logger("Executing 'initialize()'", "debug")
    updated()
}

def updated() {
    logger("Executing 'updated()'", "debug")
    configure()
}

def configure() {
    logger("Executing 'configure()'", "info")
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
    updateDeviceNetworkID()

    unschedule()
    schedule("0/${settings.pollInterval} * * * * ? *", refresh)
    
    refresh()
    childDevices.each {
        try
        {
            it.setLoggingLevel(state.loggingLevelIDE)
        }
        catch (e) {}    
    }
}

def poll() {
    logger('Executing poll()', "debug")
    refresh()
}

def refresh() {
    logger("Executing 'refresh()'", "debug")
    sendAsyncHttp("api/SystemOverview.xml", "parse_SystemOverview")
}


def parse_SystemSettings(response, data) {
    logger("parse_SystemSettings", "info")
    def status = response.status          // => http status code of the response
    def xml = parseXML(response.getData())
    
    def maxAddDevicePerSession = 4
    def hasSpyder = false
    if (status.toInteger() == 200)
    {
        try
        {
            for (spyder in 0..3)
            {
              if (xml.Spyders.Spyder[spyder].Enabled.toInteger() ==1)
              {
                logger( "Spyder ${spyder} Enabled", "trace")
                for (group in 0..7)
                {
                  if (xml.Spyders.Spyder[spyder].Group[group].UseCT.toInteger() != 0)
                  {
                      hasSpyder = true
                    def dni = "${device.deviceNetworkId}-sp${spyder}.${group}"
                    def childDevice = null
                    try 
                    {
                      childDevices.each {
                        try
                        {
                          if (it.deviceNetworkId == dni) {
                            childDevice = it
                          }
                        }
                        catch (e) {}    
                      }
                    }
                    catch (e) {}
                    if (childDevice != null) {
                      logger("parse_SystemSettings: child dni ${dni} was found", "debug")
                    }            
                    else
                    {
                      if (maxAddDevicePerSession > 0)
                      {
                        logger("parse_SystemSettings: child dni ${dni} was not found, create it with label TED ${xml.Spyders.Spyder[spyder].Group[group].Description}", "info")
                        def device = createChildDevice("TED ${xml.Spyders.Spyder[spyder].Group[group].Description.toString()}", spyder.toString(), group.toString())
                        try
                        {
                            device.setLoggingLevel(state.loggingLevelIDE)
                        }
                        catch (ex)
                        {
                            logger("Error setting loglevel ${ex}", "error")
                        }
                        maxAddDevicePerSession--
                      }
                    }
                  }
                }
              }
            }
        }
        catch (e) {}
        sendAsyncHttp("api/SpyderData.xml", "parse_SpyderData")
        
    }
    else
    {
        logger("parse_SystemSettings: invalid response status: ${status}", "warn")
        state.alertMessage = "invalid response status: ${status}"
    }
    state.hasSpyder = hasSpyder
}

def valueChangeEvent(def deviceAttribute, def newValue, def newUnit)
{
    def success = false
    def oldValue = null
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
    
    logger("----> Send new ${deviceAttribute} state, old: ${oldValue}, new: ${newValue}", "debug")
    sendEvent(name: deviceAttribute, value: newValue, unit:  newUnit)

    def nowDay = new Date().format("MMM dd", location.timeZone)
    def nowTime = new Date().format("h:mm a", location.timeZone)
    
    return true;
}

def parse_SpyderData(response, data) {
    logger("parse_SpyderData", "info")
    def status = response.status          // => http status code of the response
    def xml = parseXML(response.getData())

    if (status.toInteger() == 200)
    {
        def mainPower = xml.DashData.Now
        valueChangeEvent("power", mainPower.toInteger(), "W")
        for (spyder in 0..3)
        {
            logger("Spyder ${spyder} Enabled", "trace")
            for (group in 0..7)
            {
                def dni = "${device.deviceNetworkId}-sp${spyder}.${group}"
                def childDevice = null
                try 
                {
                    childDevices.each {
                        try
                        {
                            if (it.deviceNetworkId == dni) {
                                childDevice = it
                            }
                        }
                        catch (e) {}    
                    }
                }
                catch (e) {}
                if (childDevice != null) {
                    logger("parse_SpyderData: child dni ${dni} was found", "trace")
                    childDevice.parse("power ${xml.Spyder[spyder].Group[group].Now.toString()}")
                }            
            }
        }
    }
    else
    {
        logger("parse_SpyderData: invalid response status: ${status}", "warn")
        state.alertMessage = "invalid response status: ${status}"
    }
}

def parse_SystemOverview(response, data) {
    logger("parse_SystemOverview", "info")
    def status = response.status          // => http status code of the response
    def xml = parseXML(response.getData())

    if (status.toInteger() == 200)
    {
        // only send power value if there is no spyder, otherwise use spyder data to have them match better
        if (state?.hasSpyer == false)
            valueChangeEvent("power", (xml.MTUVal.MTU1.Value).toInteger(), unit:"W")

        valueChangeEvent("voltage", ((xml.MTUVal.MTU1.Voltage).toDouble() / 10.0), "V")
        valueChangeEvent("power_factor", ((xml.MTUVal.MTU1.PF).toDouble() / 10.0), "%")
        sendAsyncHttp("api/SystemSettings.xml", "parse_SystemSettings")
    }
    else
    {
        logger("parse_SpyderData: invalid response status: ${status}", "warn")
        state.alertMessage = "invalid response status: ${status}"
    }
}

private getHostAddress() {
    def ip = settings.ip
    def port = settings.port
    
    logger("Using ip: ${ip} and port: ${port} for device: ${device.id}", "debug")
    return ip + ":" + port
}

def sendAsyncHttp(message, handler)
{
    def requestParams =
    [
        uri:  "http://"+getHostAddress()+"/"+message,
        requestContentType: "application/x-www-form-urlencoded",
    ]
    if ((settings.username != null) && (settings.password != null)) 
    {
        def headers = [:]
        headers.put("Authorization", encodeCredentialsBasic(settings.username, settings.password))  
        requestParams.put("headers", headers)
    }
    asynchttpGet(handler, requestParams)
    
}
def clearAlertMessage()
{
    state.remove('alertMessage')
}

private encodeCredentialsBasic(username, password) {
    return "Basic " + "${username}:${password}".bytes.encodeBase64()
}

def updateDeviceNetworkID() {
    logger("Executing 'updateDeviceNetworkID'", "debug")
    
    if(device.deviceNetworkId!=getDNIfromIPandPort(settings.ip, settings.port)) {
        logger("setting deviceNetworkID = ${getDNIfromIPandPort(settings.ip, settings.port)}", "debug")
        device.setDeviceNetworkId(getDNIfromIPandPort(settings.ip, settings.port))
    }
}

private void createChildDevice(String deviceName, String spyder, String group) {
    
    if ( device.deviceNetworkId == getDNIfromIPandPort(settings.ip, settings.port)) {
    
        def dni = "${device.deviceNetworkId}-sp${spyder}.${group}"

        logger("createChildDevice:  Creating Child Device '${deviceName}'", "info")
        
        try {
            def newDevice = addChildDevice("TED Pro Home Spyder", "${device.deviceNetworkId}-sp${spyder}.${group}",
                [label: "${deviceName}", 
                isComponent: true, name: "sp${spyder}.${group}"])
            newDevice.setLoggingLevel(state.loggingLevelIDE)
        } catch (e) {
            logger("Child device creation failed with error = ${e}", "error")
            state.alertMessage = "Child device creation failed. Please make sure that the '${deviceHandlerName}' is installed and published."
        }
    } else 
    {
        state.alertMessage = "TED Parent Device has not yet been fully configured. Click the 'Gear' icon, enter data for all fields, and click 'Done'"
        logger("sendAlert createChildDevice ${state.alertMessage}!", "error") 
    }
}

private String getDNIfromIPandPort(ipAddress, port)
{
    def iphex = convertIPtoHex(ipAddress)
    def porthex = convertPortToHex(port)

    return "${iphex}:${porthex}"
}
private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}
private String convertPortToHex(port) {
    String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
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


