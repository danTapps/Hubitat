/*
 * Window Contact Handler - Parent
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
    name: "Window Contact Handler",
    namespace: "dan.t",
    author: "Daniel Terryn",
    description: "",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    )


preferences {
     page name: "mainPage", title: "", install: true, uninstall: true // ,submitOnChange: true      
} 

def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unschedule()
    unsubscribe()
    initialize()
}

def initialize() {
    log.info "There are ${childApps.size()} child smartapps"
    childApps.each {child ->
        log.info "Child app: ${child.label}"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        section {    
            paragraph title: "<Window Contact Handler",
            "<b>This parent app is a container for all:</b><br> Window Contact Handler - child apps"
        }
        section (){app(name: "WCHchild", appName: "Window Contact Handler - Instance", namespace: "dan.t", title: "Window Contact Handler - Instance", multiple: true)}    
        
        section (title: "<b>Name/Rename</b>") {label title: "Enter a name for this parent app (optional)", required: false}
    } 
}

