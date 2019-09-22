metadata {
    definition (name: "NTP Client", author: "dan.t", namespace: "dan.t") {
        capability "Actuator"
        capability "Refresh"
        
    }
    preferences {
        input ( name: "ip", type: "text", title: "NTP Server IP Address", description: "IP Address in form 192.168.1.226", required: true, displayDuringSetup: true )
        input ( name: "port", type: "text", title: "NTP Server Port", description: "port in form of 123", required: true, displayDuringSetup: true, default: 123 )
        input ( name: "minTimeDiff", title: "Minimum time difference between Hub and NTP Server to update time.", type: "enum",
            options: [
                "3600000" : "1 Hour",
                "7200000" : "2 Hours",
                "10800000" : "3 Hours"
            ],
            displayDuringSetup: true, required: true )   
        input ( name: 'pollInterval', type: 'enum', title: 'Update interval (in minutes)', options: ['10', '30', '60', '120', '300'], required: true, displayDuringSetup: true )

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
}

def configure() {
    logger("Executing 'configure()'", "info")
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
    updateDeviceNetworkID()

    unschedule()
    if (Integer.parseInt(settings.pollInterval) < 61)
        schedule("0 0/${settings.pollInterval} * 1/1 * ? *", refresh)
    else
        schedule("0 0 0/${Integer.parseInt(settings.pollInterval)/60} 1/1 * ? *", refresh)
    refresh()
}

def parse(description) {
    logger("Executing 'parse() ${description}'", "debug")
    try {
        def encrResponse = parseLanMessage(description).payload
        byte[] rawBytes = hubitat.helper.HexUtils.hexStringToByteArray(encrResponse);
        def newTimeMS = getNTPTimeMS(rawBytes)
        def newDate = new Date(newTimeMS.toLong())
        
        logger("NTP Server returned time of ${newTimeMS}", "info")
        logger("Hub is ${now()}", "info")
        def timeDiff = newTimeMS - now()
        logger("Diff is ${timeDiff}")
        if (timeDiff < 0)
            timeDiff = timeDiff * -1
        if (timeDiff >= minTimeDiff)
        {
            logger("Update Hub Time to ${newDate}", "info")
            location.hub.updateSystemTime(new Date(newTimeMS.toLong()))
        }

    } catch (error) {
        logger("${error}", "warn")
    }
}

def decodeTimestamp(byte[] array, int pointer)
{
    def r = 0.0 as double
        
    for(int i=0; i<8; i++)
    {
        r += unsignedByteToShort(array[pointer+i]) * Math.pow(2, (3-i)*8)
    }
        
    return r
}

def unsignedByteToShort(byte b)
{
    if((b & 0x80)==0x80) return (short) (128 + (b & 0x7f))
    else return (short) b
}

def debugDate(epoch)
{
    long longDate = Long.valueOf(epoch).longValue()
    parseDate = new Date(longDate).format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)
    logger("Date: ${parseDate}")
}

def timestampToString (timestamp) {
    return millisToDate (timestampToMS(timestamp))
}

def timestampToMS (timestamp) {

    def DAYS = 25567 as long; // 1 Jan 1900 to 1 Jan 1970
    def SECS = 60 * 60 * 24 * DAYS as long
    if(timestamp==0) return 0
    
    // timestamp is relative to 1900, utc is used by Java and is relative to 1970 
    return Math.round (1000.0*(timestamp-SECS))
}

def millisToDate (ms) {
    long longDate = Long.valueOf(ms).longValue()
    parseDate = new Date(longDate).format("yyyy-MM-dd'T'HH:mm:ss.SSSZ", location.timeZone)
    return parseDate
}

def getNTPTimeMS(byte[] array) {
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
    
    referenceTimestamp = decodeTimestamp(array, 16)
    logger("referenceTimestamp: ${referenceTimestamp} ${timestampToString(referenceTimestamp)}", "debug")

    /*
    originateTimestamp = decodeTimestamp(array, 24)
    receiveTimestamp = decodeTimestamp(array, 32)
    transmitTimestamp = decodeTimestamp(array, 40)
    */
    return timestampToMS(referenceTimestamp)
}

def refresh() {
    logger("Executing 'refresh()'", "debug")
    byte[] rawBytes = [0x1b, 0x0,  0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 
                       0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0]
    String stringBytes = hubitat.helper.HexUtils.byteArrayToHexString(rawBytes)
    def myHubAction = new hubitat.device.HubAction(stringBytes, 
                           hubitat.device.Protocol.LAN, 
                           [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT, 
                            destinationAddress: "${settings.ip}:${settings.port}",
                            encoding: hubitat.device.HubAction.Encoding.HEX_STRING])
    sendHubCommand(myHubAction)
}

def getHostAddress() {
    def ip = settings.ip
    def port = settings.port
    
    logger("Using ip: ${ip} and port: ${port} for device: ${device.id}", "debug")
    return ip + ":" + port
}

def updateDeviceNetworkID() {
    logger("Executing 'updateDeviceNetworkID'", "debug")
    
    if(device.deviceNetworkId!=getDNIfromIPandPort(settings.ip, settings.port)) {
        logger("setting deviceNetworkID = ${getDNIfromIPandPort(settings.ip, settings.port)}", "debug")
        device.setDeviceNetworkId(getDNIfromIPandPort(settings.ip, settings.port))
    }
}

def getDNIfromIPandPort(ipAddress, port)
{
    def iphex = convertIPtoHex(ipAddress)
    def porthex = convertPortToHex(port)

    return "${iphex}:${porthex}"
}
def convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex
}
def convertPortToHex(port) {
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


