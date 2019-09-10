/*****************************************************************************************************************
 *  Copyright Daniel Terryn
 *
 *  Name: The NFL Scorer App
 *
 *  Date: 2019-09-10
 *
 *  Version: 0.01
 *
 *  Author: Daniel Terryn
 *
 *  Description: A SmartApp to send notifications and actions when NFL Team Scores
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
 *****************************************************************************************************************/
import groovy.json.JsonSlurper
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat 

definition(
    name: "NFL Scorer ALPHA",
    namespace: "dan.t",
    author: "Daniel Terryn",
    description: "NFL Scroing App",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {

    section("Configuration:") {
        //input "RunMe", "button", title: "Click This Button"

        input ( name: "configTeam", title: "NFL team to follow.", type: "enum",
            options: [
                "ARI":"ARI",
                "ATL":"ATL",
                "BAL":"BAL",
                "BUF":"BUF",
                "CAR":"CAR",
                "CIN":"CIN",
                "CLE":"CLE",
                "DAL":"DAL",
                "DET":"DET",
                "IND":"IND",
                "JAC":"JAC",
                "KC":"KC",
                "LA":"LA",
                "LAC":"LAC",
                "MIA":"MIA",
                "MIN":"MIN",
                "NE":"NE",
                "NYG":"NYG",
                "NYJ":"NYJ",
                "PHI":"PHI",
                "PIT":"PIT",
                "SEA":"SEA",
                "SF":"SF",
                "TB":"TB",
                "TEN":"TEN",
                "WAS":"WAS"
            ],
            defaultValue: "", displayDuringSetup: true, required: true )    
        
        input "myNotificationDevice", "capability.notification", title: "Notification Device", multiple: true, required: true

        input ( name: "refreshTime", title: "Refresh time:", type: "enum",
            options: [
                "1" : "Every 1 Minute",
                "2" : "Every 2 Minutes",
                "3" : "Every 3 Minutes",
                "4" : "Every 4 Minutes",
                "5" : "Every 5 Minutes"
            ],
            defaultValue: "2", displayDuringSetup: true, required: true )    
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


/*****************************************************************************************************************
 *  SmartThings System Commands:
 *****************************************************************************************************************/

/**
 *  installed()
 *
 *  Runs when the app is first installed.
 **/
def installed() {
    state.loggingLevelIDE = 3
    
    logger("${app.label}: Installed" , "info")
    state.lastTotalScore = 0

    updated()
}

def appButtonHandler(btn) {
    switch(btn) {
        case "RunMe":   //do some stuff
        state.lastTotalScore = 0
               updateNFLScores()
            break
    }
}

def updated() {
    state.loggingLevelIDE = (settings.configLoggingLevelIDE) ? settings.configLoggingLevelIDE.toInteger() : 3
    state.team = settings.configTeam
    logger("updated()", "info")
    if (!(settings.configTeam))
	    logger("configTeam not set")
    unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
    logger("initialize()", "info")
    schedule("0 0/${refreshTime} * 1/1 * ? *", updateNFLScores)

}
/**
 *  uninstalled()
 *
 *  Runs when the app is uninstalled.
 **/
def uninstalled() {
    logger("uninstalled()", "info")
}

def updateNFLScores()
{
    def requestParams =
    [
        uri:  "http://www.nfl.com/liveupdate/scores/scores.json",
        requestContentType: "application/json",
    ]
    asynchttpGet("responseNFLScores", requestParams)
}

def responseNFLScores(response, data)
{
    logger("responseNFLScores()", "info")
    def status = response.status          // => http status code of the response
    if (status != 200)
    {
        logger("responseNFLScores(): received invalid status ${status}. Abort", "info")
        return
    }
	def jsonData = parseJson(response.getData())
	def dateO = timeToday('00:00')
    def now = new Date()
    def tf = new java.text.SimpleDateFormat("yyyyMMdd")
    def today = "${tf.format(now)}" as String

    def gameCounter = 0
    def foundGame = true
    def totalScoreSet = false
    def gameKey = today + String.format('%02d',gameCounter) 
    logger("looking for game key: ${gameKey}", "debug")
    def game = jsonData[gameKey]
    while (game)
    {              
        if ( (state.team == game.away.abbr) || (state.team == game.home.abbr) )
        {
            logger("game: ${game}")
            totalScoreSet = true
            def newScore = 0
            if (state.team == game.away.abbr)
            newScore = game.away.score.T
            if (state.team == game.home.abbr)
            newScore = game.home.score.T
            if (newScore != state.lastTotalScore)
            {
                logger("${state.team} scored ${newScore - state.lastTotalScore} points. Game is ${game.away.score.T} - ${game.home.score.T}", "info")
                settings.myNotificationDevice.deviceNotification("${state.team} scored ${newScore - state.lastTotalScore}. Game is ${game.away.score.T} - ${game.home.score.T}")
            }
            state.lastTotalScore = newScore
        }
        
        gameCounter = gameCounter + 1
        gameKey = today + String.format('%02d',gameCounter) 
        logger("looking for game key: ${gameKey}", "debug")
        game = jsonData[gameKey]
    }
    if (totalScoreSet == false)
        state.lastTotalScore = 0
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

