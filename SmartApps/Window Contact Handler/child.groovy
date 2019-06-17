/*
 * Window Contact Handler - Instance
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
 *    2019-06-17  Daniel Terryn  Original Creation
 * 
 */

definition(
    name: "Window Contact Handler - Instance",
    namespace: "dan.t",
    author: "Daniel Terryn",
    description: "",
    category: "Convenience",
        
    parent: "dan.t:Window Contact Handler",
    
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    )


preferences {
  page name: "mainPage", title: "", install: true, uninstall: true // ,submitOnChange: true      
}

def mainPage() {
    dynamicPage(name: "mainPage") {
      section ("<b>Which Contact Sensors to act on?</b>") {
          input "myContacts", "capability.contactSensor", multiple: true, required: true
      }
      section ("<b>Which Switch to set?</b>") {
          input "mySwitch", "capability.switch", multiple: false, required: true
      }
      section ("<b>Which AC switch to check?</b>") {
          input "myAcSwitch", "capability.switch", multiple: false, required: false
      }
      section ("<b>Which Contacts to ignore when AC switch is on switch to check?</b>") {
          input "myAcContacts", "capability.contactSensor", multiple: true, required: false
      }
  
      section (title: "<b>Name/Rename</b>") {
        label title: "This child app's Name (optional)", required: false
      }
   }
}

def installed() {
  initialize()
  log.info "Installed with settings: ${settings}"
}

def updated() {
  unsubscribe()
  initialize()
  log.info "Updated with settings: ${settings}"
}

def initialize() {
  subscribe(myContacts, "contact", handler)
  if (myAcSwitch)
      subscribe(myAcSwitch, "switch", handler)
}

def handler(evt) {
    def setSwitchStateOn = false;
    log.info "handler: ${evt.deviceId}"

    for(contact in myContacts)
    {
        def currentContact = contact.currentValue("contact")
        log.debug "hanlder: checking contact ${contact} with value ${currentContact}"
        if (myAcSwitch)
        {
            if (myAcSwitch.currentValue("switch") == "on")
            {
                def inAcContacts = myAcContacts?.find{it.deviceId == contact.deviceId}
                if (inAcContacts != null)
                    log.info "Ignoring contact ${inAcContacts}"
                else
                    if (currentContact == "open")
                        setSwitchStateOn = true;
            }
            else
                if (currentContact == "open")
                    setSwitchStateOn = true;            
        }
        else
        {
            if (currentContact == "open")
                setSwitchStateOn = true;
        }
    }
    log.info "hanlder: set Switch state: ${setSwitchStateOn} ${mySwitch.currentValue("switch")}"
    
    if (setSwitchStateOn == true) {
        if (mySwitch.currentValue("switch") != "on") {
            log.debug "handler: set switch ${mySwitch} to on"
            mySwitch.on()
        }
    }
    else {
        if (mySwitch.currentValue("switch") != "off") {
            log.debug "handler: set switch ${mySwitch} to off"
            mySwitch.off()
        }
    }
}


