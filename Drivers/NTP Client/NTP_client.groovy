/*****************************************************************************************************************
 *  Copyright Daniel Terryn
 *
 *  Name: NTP Client to retrieve Date/Time from local NTP server....
 *
 *  Date: 2020-04-27
 *
 *  Version: 1.21-bab
 *
 *  Author: Daniel Terryn / Barry Burke
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
 *    2019-09-22  Daniel Terryn  Original Creation
 *    2019-09-23  Daniel Terryn  Send event when time set, fixed NTP time calculation, more choices for time difference configuration, refactoring, show NTP Server Date as Event, add force command
 *    2020-04-27  Barry Burke    Optimizations to reduce delta/drift when setting time
 *                               - change time conversion math to BigDecimal
 *                               - declare all variable and function types
 *                               - update the actual time BEFORE logger() & sendEvents()
 *                               - enable corrections at 1/2 second of drift
 *                               - allow checks to be scheduled every 1/2/3/5...minutes
 *                               - randomize the seconds & minutes offset of schedule, so multiple instances(on multiple hubs) don't hit the timeserver at the same time
 *                               - optimize calls to logger; convert remaining double math to BigDecimal
 * 
 */

def SECONDS_1900_TO_EPOCH() {return 2208988800.0 as BigDecimal}

metadata {
    definition (name: "NTP Client", author: "dan.t", namespace: "dan.t", importUrl: "https://raw.githubusercontent.com/danTapps/Hubitat/master/Drivers/NTP%20Client/NTP_client.groovy") {
        capability "Actuator"
        capability "Initialize"
        capability "Refresh"
        
        attribute "lastNTPdate", "string"
        attribute "lastHubDate", "string"
        attribute "lastDiffMS", "number"
        attribute "updateHubTimeTo", "string"
        
        command "force"

    }
    preferences {
        input ( name: "ntpIP", type: "text", title: "NTP Server IP Address", description: "IP Address in form 192.168.1.226", required: true, displayDuringSetup: true )
        input ( name: "ntpPort", type: "text", title: "NTP Server Port", description: "port in form of 123", required: true, displayDuringSetup: true, default: 123 )
        input ( name: "minTimeDiff", title: "Minimum time difference between Hub and NTP Server to update time.", type: "enum",
            options: [
				500 : "1/2 Second",
				1000 : "1 Second",
				10000 : "10 Seconds",
				30000 : "30 Seconds",
				60000 : "1 Minutes",
                180000 : "3 Minutes",
                300000 : "5 Minutes",
                600000 : "10 Minutes",
                1200000 : "20 Minutes",
                1800000 : "30 Minutes",
                2400000 : "40 Minutes",
                3000000 : "50 Minutes",
                3600000 : "1 Hour",
                7200000 : "2 Hours",
                10800000 : "3 Hours"
            ],
            displayDuringSetup: true, required: true )   
        input ( name: 'pollInterval', type: 'enum', title: 'Update interval (in minutes)', options: ['1', '2', '3', '4', '5', '10', '15', '30', '60', '120', '180', '240', '360', '720'], required: true, displayDuringSetup: true )

        input ( name: "configLoggingLevel", title: "Live Logging Level:\nMessages with this level and higher will be logged.", type: "enum",
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
}

def configure() {
    logger("Executing 'configure()'", "info")
    state.loggingLevel = (configLoggingLevel) ? configLoggingLevel.toInteger() : 3
    updateDeviceNetworkID()

    unschedule()
	Integer interval = Integer.parseInt(settings.pollInterval)
	switch (interval) {
		case 1: 
			runEvery1Minute(refresh)
			break;
		case 5:
			runEvery5Minutes(refresh)
			break;
		case 10:
			runEvery10Minutes(refresh)
			break;
		case 15:
			runEvery15Minutes(refresh)
			break;
		case 30:
			runEvery30Minutes(refresh)
			break;
		case 60:
			runEvery1Hour(refresh)
			break;
		case 180:
			runEvery3Hours(refresh)
			break;
		default:
			Random rand = new Random()
			int randomSeconds = rand.nextInt(59)
            int startMinutes = rand.nextInt( interval < 60 ? interval : 59)
			if ( interval < 61)
                schedule("${randomSeconds} ${startMinutes}/${interval} * * * ?", refresh)
			else
                schedule("${randomSeconds} ${startMinutes} 0/${interval/60} * * ?", refresh)
			break;
	}
    refresh()
}

def parse(description) {
    if (state.loggingLevel <= 4) logger("Executing 'parse() ${description}'", "debug")
    try {
        def encrResponse = parseLanMessage(description).payload
        byte[] rawBytes = hubitat.helper.HexUtils.hexStringToByteArray(encrResponse);
        long hubTimeMS = now() 
        long newTimeMS = getNTPTimeMS(rawBytes, hubTimeMS)
		
		// update time as soon as possible to reduce the drift
		long timeDiff = newTimeMS - hubTimeMS as long
        if (timeDiff < 0) timeDiff = timeDiff * -1
		if ((timeDiff >= Long.parseLong(minTimeDiff)) || (state?.force == true))
        {
			location.hub.updateSystemTime(new Date(newTimeMS.toLong()))
            if (state?.force == true)
                if (state.loggingLevel <= 4) logger("Force Update Hub Time to ${(new Date(newTimeMS.toLong())).toString()}", "info")
            else
                logger("Update Hub Time to ${(new Date(newTimeMS.toLong())).toString()}", "info")
            sendEvent(name: "updateHubTimeTo", value: (new Date(newTimeMS.toLong())).toString())
        }
        state.force = false
        
		if (state.loggingLevel <= 3) {
        	logger("NTP is ${newTimeMS} ms aka ${new Date(newTimeMS.toLong())}", "info")
        	logger("Hub is ${hubTimeMS} ms aka ${new Date(hubTimeMS)}", "info")
		}
		if (state.loggingLevel <= 4) {
        	logger("Diff is ${timeDiff} ms", "debug")
        	logger("minTimeDiff is ${minTimeDiff} ms", "debug")
		}
        sendEvent(name: "lastNTPdate", value: (new Date(newTimeMS.toLong())).toString())
        sendEvent(name: "lastHubDate", value: (new Date(hubTimeMS)).toString())
        sendEvent(name: "lastDiffMS", value: timeDiff)
    } catch (error) {
        logger("<font color=red>${error}</font>", "warn")
    }
}

def refresh() {
    //def SECONDS_1900_TO_EPOCH = 2208988800.0 as double

    if (state.loggingLevel <= 4) logger("Executing 'refresh()'", "debug")
    byte[] rawBytes = [0x1b, 0x0,  0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0]
    rawBytes = encodeTimestamp(rawBytes, 40, (now()/1000)+SECONDS_1900_TO_EPOCH())

    String stringBytes = hubitat.helper.HexUtils.byteArrayToHexString(rawBytes)
    def myHubAction = new hubitat.device.HubAction(stringBytes, 
                           hubitat.device.Protocol.LAN, 
                           [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, 
                            destinationAddress: "${ntpIP}:${ntpPort}",
                            encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
    sendHubCommand(myHubAction)
}

def force()
{
    if (state.loggingLevel <= 4) logger("Executing 'force()'", "debug")
    state.force = true
    logger("<b>Forcing</b> Date/Time update in 2 seconds", "info")
    runIn(2, "refresh", [overwrite: true])

}

def getTimestamp(byte[] array, int pointer)
{
    if (state.loggingLevel <= 4) logger("Executing 'getTimestamp()'", "debug")
    def r = 0.0 // as double
        
    for(int i=0; i<8; i++)
    {
        r += unsignedByteToShort(array[pointer+i]) * Math.pow(2, (3-i)*8)
    }
        
    return r
}

short unsignedByteToShort(byte b)
{
    if((b & 0x80)==0x80) return (short) (128 + (b & 0x7f))
    else return (short) b
}

long getNTPTimeMS(byte[] array, destinationTimestamp) 
{
    if (state.loggingLevel <= 4) logger("Executing 'getNTPTimeMS()'", "debug")
    // See the packet format diagram in RFC 2030 for details 
    
    /* 
    byte[] referenceIdentifier = [0, 0, 0, 0]
    def leapIndicator = (byte) ((array[0] >> 6) & 0x3)
    def version = (byte) ((array[0] >> 3) & 0x7)
    def mode = (byte) (array[0] & 0x7)
    def stratum = unsignedByteToShort(array[1])
    def pollInterval = array[2]
    def precision = array[3]

    def rootDelay = (array[4] * 256.0) + 
        unsignedByteToShort(array[5]) +
        (unsignedByteToShort(array[6]) / 256.0) +
        (unsignedByteToShort(array[7]) / 65536.0)

    def rootDispersion = (unsignedByteToShort(array[8]) * 256.0) + 
        unsignedByteToShort(array[9]) +
        (unsignedByteToShort(array[10]) / 256.0) +
        (unsignedByteToShort(array[11]) / 65536.0)

    referenceIdentifier[0] = array[12];
    referenceIdentifier[1] = array[13];
    referenceIdentifier[2] = array[14];
    referenceIdentifier[3] = array[15];
    */

    def referenceTimestamp = getTimestamp(array, 16)
    def originateTimestamp = getTimestamp(array, 24)
    def receiveTimestamp = getTimestamp(array, 32)
    def transmitTimestamp = getTimestamp(array, 40)

    return (now() + (((receiveTimestamp - originateTimestamp) + (transmitTimestamp - ((destinationTimestamp/1000) + SECONDS_1900_TO_EPOCH()))) / 2)*1000)
}

def encodeTimestamp(array,pointer, timestamp)
{
    if (state.loggingLevel <= 4) logger("Executing 'encodeTimestamp()'", "debug")
    // Converts a double into a 64-bit fixed point
    for(i=0; i<8; i++) {
        // 2^24, 2^16, 2^8, .. 2^-32
        def base = Math.pow(2, (3-i)*8) as BigDecimal
               
        // Capture byte value
        array[pointer+i] = (byte) (timestamp / base);

        // Subtract captured value from remaining total
        timestamp = timestamp - (unsignedByteToShort(array[pointer+i]) * base);
    }

    array[7+pointer] = 0x0;
    return array;
}


def getHostAddress() {
    logger("Executing 'getHostAddress()'", "debug")
    logger("Using ip: ${ntpIP} and port: ${ntpPort} for device: ${device.id}", "debug")
    return ntpIP + ":" + ntpPort
}

def updateDeviceNetworkID() {
    logger("Executing 'updateDeviceNetworkID'", "debug")
    
    if(device.deviceNetworkId!=getDNIfromIPandPort(ntpIP, ntpPort)) {
        logger("setting deviceNetworkID = ${getDNIfromIPandPort(ntpIP, ntpPort)}", "debug")
        device.setDeviceNetworkId(getDNIfromIPandPort(ntpIP, ntpPort))
    }
}

def getDNIfromIPandPort(ipAddress, port)
{
    logger("Executing 'getDNIfromIPandPort'", "debug")
    def iphex = convertIPtoHex(ipAddress)
    def porthex = convertPortToHex(port)

    return "${iphex}:${porthex}"
}
def convertIPtoHex(ipAddress) { 
    logger("Executing 'convertIPtoHex'", "debug")
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}
def convertPortToHex(port) {
    logger("Executing 'convertPortToHex'", "debug")
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
            if (state.loggingLevel >= 1) log.error msg
            break

        case "warn":
            if (state.loggingLevel >= 2) log.warn msg
            break

        case "info":
            if (state.loggingLevel >= 3) log.info msg
            break

        case "debug":
            if (state.loggingLevel >= 4) log.debug msg
            break

        case "trace":
            if (state.loggingLevel >= 5) log.trace msg
            break

        default:
            log.debug msg
            break
    }
}



