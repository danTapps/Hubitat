/*
 * Virtual Checkmark with Switch
 *
 *  Copyright 2019 Daniel Terryn
 *
 *  Licensed Virtual Image Switch the Apache License, Version 2.0 (the "License"); you may not use this file except
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
 *    2018-05-21  Daniel Terryn  Original Creation
 * 
 */

metadata {
    definition (name: "Virtual Image Switch", namespace: "dan.t", author: "Daniel Terryn") {
        capability "Sensor"
        capability "Switch"
        
        attribute "image", "string"
        command "setOnImage", ["imageHtmlTag"]
        command "setOffImage", ["imageHtmlTag"]
        
    }   
}

def on() {
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "image", value: state.onImage)
}

def off() {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "image", value: state.offImage)
}

def updated() {
    if (state.onImage == null)
        setOnImage('<img src=https://raw.githubusercontent.com/danTapps/Hubitat/master/switch-on.png width=30 height=30>')
    if (state.offImage == null)
        setOffImage('<img src=https://raw.githubusercontent.com/danTapps/Hubitat/master/switch-off.png width=30 height=30>')
    
}
def installed() {
    setOnImage('<img src=https://raw.githubusercontent.com/danTapps/Hubitat/master/switch-on.png width=30 height=30>')
    setOffImage('<img src=https://raw.githubusercontent.com/danTapps/Hubitat/master/switch-off.png width=30 height=30>')
    off()   
}

def setOnImage(newImage)
{
    state.onImage = newImage
}

def setOffImage(newImage)
{
    state.offImage = newImage
}
