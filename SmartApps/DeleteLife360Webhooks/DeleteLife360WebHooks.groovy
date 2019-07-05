

definition(
    name: "Delete Life360 Webhooks",
    namespace: "dan.t",
    author: "dan.t",
    description: "",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {

    section("Devices To Refresh:") {

        input "username", "string", title: "Username", required: true
        input "password", "string", title: "Password", required: true

    }
}


def installed() {
    state.installedAt = now()
    log.debug "${app.label}: Installed" 
    initialize()
    state.specialBunnyFlag = false
}


def updated() {
    log.trace "updated()"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    login()
    def circles = getCircles()
    for (circle in circles) {
        deleteWebHooks(circle.id)
    }
    log.info "Deleted all Webhooks"
}

def login() {
    
    // Base 64 encode the credentials

    def basicCredentials = "${oauthClientId}:${oauthClientSecret}"
    def encodedCredentials = basicCredentials.bytes.encodeBase64().toString()

    def url = "https://api.life360.com/v3/oauth2/token.json"
   
    def postBody =  "grant_type=password&" +
                    "username=${username}&"+
                    "password=${password}"

    def result = null
    
    try {
       
        httpPost(uri: url, body: postBody, headers: ["Authorization": "Basic cFJFcXVnYWJSZXRyZTRFc3RldGhlcnVmcmVQdW1hbUV4dWNyRUh1YzptM2ZydXBSZXRSZXN3ZXJFQ2hBUHJFOTZxYWtFZHI0Vg==" ]) {response -> 
            result = response
        }
        if (result.data.access_token) {
            state.life360AccessToken = result.data.access_token
            return true;
        }
        log.info "Life360 initializeLife360Connection, response=${result.data}"
        return false;
        
    }
    catch (e) {
       log.error "Life360 initializeLife360Connection, error: $e"
       return false;
    }
}

def getCircles()
{
    def url = "https://api.life360.com/v3/circles.json"
 
        def result = null
       
        httpGet(uri: url, headers: ["Authorization": "Bearer ${state.life360AccessToken}" ]) {response -> 
            result = response
        }

        log.debug "Circles=${result.data}"
        return result.data.circles
}

def deleteWebHooks(circleid)
{
    def deleteUrl = "https://api.life360.com/v3/circles/${circleid}/webhook.json"
    
    try { // ignore any errors - there many not be any existing webhooks
            httpDelete (uri: deleteUrl, headers: ["Authorization": "Bearer ${state.life360AccessToken}" ]) {response -> 
                result = response}
        }
    
    catch (e) {
    
        log.debug (e)
    }
}
