/*****************************************************************************************************************
 *  Copyright Daniel Terryn
 *
 *  Name: The NFL Scorer App
 *
 *  Date: 2019-09-10
 *
 *  Version: 0.02
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
    name: "NFL Scorer ALPHA 2",
    namespace: "dan.t",
    author: "Daniel Terryn",
    description: "NFL Scoring App",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

 preferences {
 	section() {
 		page name: "mainPage", title: "", install: true, uninstall: true
 	}
 }
 def mainPage() {
 	dynamicPage(name: "mainPage") {
 		//preCheck()
 		section() {
        input "RunMe", "button", title: "Click This Button"
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
        }
        
        section() {
            input "myNotificationDevice", "capability.notification", title: "Send scoring notifications to notification device", multiple: true, required: false
            input "myRedzoneSwitch", "capability.switch", title: "Turn switch on/off when team is in RedZone", multiple: false, required: false
            input "myScoringButton", "capability.pushableButton", title: "Push button when team scores", multiple: false, required: false, submitOnChange: true
 			if(myScoringButton){
                    input "myScoringButtonNumber", "number", title: "Enter Button Number", required: true, multiple: false
 		            input "myScoringButtonAction", "enum", title: "Select Action", options: ["1":"push","2":"hold","3":"release"], defaultValue: "", displayDuringSetup: true, required: true
            }
        }
        
        section() {
            input ( name: "gameTimeRefreshTime", title: "Refresh time during Game:", type: "enum",
                options: [
                    "3" : "Every 3 Seconds",
                    "4" : "Every 5 Seconds",
                    "5" : "Every 10 Seconds",
                    "15" : "Every 15 Seconds",
                    "30" : "Every 30 Seconds",
                    "45" : "Every 45 Seconds"
                ],
                defaultValue: "3", displayDuringSetup: true, required: true )
            
            input ( name: "refreshTime", title: "Refresh time on Game Day:", type: "enum",
                options: [
                    "2" : "Every 2 Minutes",
                    "3" : "Every 3 Minutes",
                    "4" : "Every 4 Minutes",
                    "5" : "Every 5 Minutes",
                    "10" : "Every 10 Minutes"
                ],
                defaultValue: "2", displayDuringSetup: true, required: true )
            
            input ( name: "noGameRefreshTime", title: "Refresh time every other day:", type: "enum",
                options: [
                    "3" : "Every 3 Hour",
                    "4" : "Every 6 Hours",
                    "5" : "Every 9 Hours"
                ],
                defaultValue: "3", displayDuringSetup: true, required: true )
        }
        
        section() {
            input ( name: "configLoggingLevel", title: "Live Logging Level:\nMessages with this level and higher will be logged to the IDE.", type: "enum",
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
    state.updateSchedule = -1
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
    state.loggingLevelIDE = (settings.configLoggingLevel) ? settings.configLoggingLevel.toInteger() : 3
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
    if (state.updateSchedule == null)
        state.updateSchedule = -1
    scheduleQuick()    
}
/**
 *  uninstalled()
 *
 *  Runs when the app is uninstalled.
 **/
def uninstalled() {
    logger("uninstalled()", "info")
    unschedule()
    
}

def updateNFLScores()
{
    logger("updateNFLScores()", "info")
    def requestParams =
    [
        uri:  "http://www.nfl.com/liveupdate/scores/scores.json",
        requestContentType: "application/json",
        timeout: 3
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
    //logger("responseNFLScores() data: ${jsonData}", "trace")
	def dateO = timeToday('00:00')
    def now = new Date()
    def tf = new java.text.SimpleDateFormat("yyyyMMdd")
    def today = "${tf.format(now)}" as String
    //def today = "20190915"

    def gameCounter = 0
    def foundGame = false
    def totalScoreSet = false
    def gameRunning = false
    def gameKey = today + String.format('%02d',gameCounter) 
    //logger("looking for game key: ${gameKey}", "debug")
    def game = jsonData[gameKey]
    if ((!(game)) && (state.updateSchedule == 1))
    {
        logger("EXIT: jsonData: ${jsonData}, got response with no data, ignore during game", "debug")
        return
    }

    while (game)
    {              
        if ( (state.team == game.away.abbr) || (state.team == game.home.abbr) )
        {
            logger("game: ${game}")
            totalScoreSet = true
            def newScore = 0
            if ( (state.team == game.away.abbr) || (state.team == game.home.abbr) )
            {
                foundGame = true
                if (state.team == game.away.abbr)
                    newScore = game.away.score.T.toInteger()
                if (state.team == game.home.abbr)
                    newScore = game.home.score.T.toInteger()
                logger("before newScore: ${newScore} lastTotalScore ${state.lastTotalScore}", "debug")
                if ((newScore != state.lastTotalScore) && ((game.away.score.T) || (game.home.score.T)))
                {
                    logger("${state.team} scored ${newScore - state.lastTotalScore} points. Game is ${game.away.score.T} - ${game.home.score.T}", "info")
                    if (myNotificationDevice)
                        myNotificationDevice.deviceNotification("${state.team} scored ${newScore - state.lastTotalScore}. Game is ${game.away.score.T} - ${game.home.score.T}")
                    if (myScoringButton)
                    {
                        switch(myScoringButtonAction)
                        {
                            case 1:
                                myScoringButton.push(myScoringButtonNumber)
                                break
                            case 2:
                                myScoringButton.hold(myScoringButtonNumber)
                                break
                            case 3:
                                myScoringButton.release(myScoringButtonNumber)
                                break
                        }
                    }
                    valueChangeEvent("nflscorealert", "${newScore - state.lastTotalScore}")
                }
                logger("after newScore: ${newScore} lastTotalScore ${state.lastTotalScore}", "debug")
                state.lastTotalScore = newScore
                if (newScore != state.lastTotalScore)
                {
                    logger("Reset lastTotalScore", "debug")
                    state.remove('lastTotalScore')
                    state.lastTotalScore = newScore
                }
                if (game.posteam)
                {
                    if ((game.posteam == state.team) && (game?.redzone == true))
                    {
                        valueChangeEvent("nflredzonealert", "on")
                        if (myRedzoneSwitch)
                            if (myRedzoneSwitch.currentValue('switch') == 'off')
                                myRedzoneSwitch.on()
                    }
                    else
                    {
                        valueChangeEvent("nflredzonealert", "off")
                        if (myRedzoneSwitch)
                            if (myRedzoneSwitch.currentValue('switch') == 'on')
                                myRedzoneSwitch.off()
                    }
                }
                if (game.qtr)
                {
                    if (game.qtr != 'Final')
                        gameRunning = true
                    else
                        if (myRedzoneSwitch.currentValue('switch') == 'on')
                            myRedzoneSwitch.off()
                }
            }

        }
        
        gameCounter = gameCounter + 1
        gameKey = today + String.format('%02d',gameCounter) 
        //logger("looking for game key: ${gameKey}", "debug")
        game = jsonData[gameKey]
    }
    if (totalScoreSet == false)
        state.lastTotalScore = 0
    if (gameRunning == false)
    {
        if (foundGame == true)
        {
            scheduleGameDay()
        }
        else
        {
            scheduleNoGameDay()
        }
        valueChangeEvent("nflredzonealert", "off")
    }
    else
    {
        scheduleQuick()
    }
}

def scheduleQuick()
{
    if (state.updateSchedule != 1)
    {
        logger("set quick update schedule", "info")

        unschedule()
        schedule("0/${gameTimeRefreshTime} * * * * ? *", updateNFLScores)
        state.updateSchedule = 1
    }
}

def scheduleGameDay()
{
    if (state.updateSchedule != 0)
    {
        logger("set scheduleGameDay schedule", "info")

        unschedule()
        schedule("0 0/${refreshTime} * 1/1 * ? *", updateNFLScores)
        state.updateSchedule = 0
    }
}

def scheduleNoGameDay()
{
    if (state.updateSchedule != 2)
    {
        logger("set scheduleNoGameDay schedule", "info")
        unschedule()
        schedule("0 0 0/${noGameRefreshTime} 1/1 * ? *", updateNFLScores)
        state.updateSchedule = 2
    }
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
    
    logger("----> Send new ${deviceAttribute} state, old: ${oldValue}, new: ${newValue}", "debug")
    sendEvent(name: deviceAttribute, value: newValue)

    def nowDay = new Date().format("MMM dd", location.timeZone)
    def nowTime = new Date().format("h:mm a", location.timeZone)
    
    return true;
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

