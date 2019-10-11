
metadata {
    definition (name: "NFL Team Watcher Device", namespace: "dan.t", author: "Daniel Terryn",    importUrl: "https://raw.githubusercontent.com/danTapps/Hubitat/master/SmartApps/NFL%20Scorer/NFL_Scorer_device.groovy") {
        capability "Sensor"
    }   
    
        attribute "awayTeam", "string"
        attribute "awayScore", "string"
        attribute "homeTeam", "string"
        attribute "homeScore", "number"
        attribute "tv", "string"
        attribute "down", "number"
        attribute "qtr", "number"
        attribute "yardLine", "string"
        attribute "togo", "number"
        attribute "stadium", "string"
        attribute "possessionTeam", "string"
        attribute "gameDate", "string"
        attribute "redzone", "string"
}


public void  setValue()
{
    log.trace("----> Set Value")
}


def installed() {
    log.trace "Executing 'installed()'"
    initialize()
}

def initialize() {
    log.trace "Executing 'initialize()'"
    updated()
}

def updated() {
    log.trace "Executing 'updated()'"
    configure()
}

def configure() {
    log.trace "Executing 'configure()'"
}


def valueChangeEvent(def deviceAttribute, def newValue)
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
    
    log.trace("----> Send new ${deviceAttribute} state, old: ${oldValue}, new: ${newValue}")
    sendEvent(name: deviceAttribute, value: newValue)

    return true;
}

//def updateValues( gameData, gameDay)
def updateValues( gameData, gameDay )
{
    log.trace("---->  updateValues ${gameData}, ${gameDay}")

    valueChangeEvent("awayTeam", gameData.away.abbr ? gameData.away.abbr : " ")
    valueChangeEvent("awayScore", gameData.away.score.T ? gameData.away.score.T.toInteger() : "0")
    valueChangeEvent("homeTeam", gameData.home.abbr ? gameData.home.abbr : " ")
    valueChangeEvent("homeScore", gameData.home.score.T ? gameData.home.score.T.toInteger() : "0")
    valueChangeEvent("tv", gameData.media.tv ? gameData.media.tv : " ")
    valueChangeEvent("down", gameData.down ? gameData.down.toInteger() : "0")
    valueChangeEvent("qtr", gameData.qtr ? gameData.qtr : " ")
    valueChangeEvent("yardLine", gameData.yl ? gameData.yl : " " )
    valueChangeEvent("togo", gameData.togo ? gameData.togo.toInteger() : "0")
    valueChangeEvent("stadium", gameData.stadium ? gameData.stadium : " ")
    valueChangeEvent("possessionTeam", gameData.posteam ? gameData.posteam : " ")
    valueChangeEvent("redzone", gameData.redzone ? "true" : "false")
    valueChangeEvent("gameDate", gameDay)

}

