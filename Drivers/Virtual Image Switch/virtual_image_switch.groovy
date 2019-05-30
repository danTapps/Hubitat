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
 *    2019-05-21  Daniel Terryn  Original Creation
 *	  2019-05-30  Daniel Terryn	 Updated to be able to set image url, width and height of image via commands
 * 
 */

metadata {
    definition (name: "Virtual Image Switch", namespace: "dan.t", author: "Daniel Terryn") {
        capability "Sensor"
        capability "Switch"
        
        attribute "image", "string"
		command "setOnImageUrl", ["URL"]
		command "setOffImageUrl", ["URL"]
		command "setImageWidth", ["Width"]
		command "setImageHeight", ["Height"]
    }   
}

def updated() {
    if (state.imgWidth == null)
		state.imgWidth = 30
	if (state.imgHeight == null)
		state.imgHeight = 30
    if (state.onImage == null)
		setOnImageUrl("https://tinyurl.com/yy7j4ao8")
    if (state.offImage == null)
        setOffImageUrl("https://tinyurl.com/yyg465bu")
}

def on() {
    sendEvent(name: "switch", value: "on")
	if (state.onImage == null)
		updated()
    sendEvent(name: "image", value: state.onImage)
}

def off() {
    sendEvent(name: "switch", value: "off")
	if (state.offImage == null)
		updated()
    sendEvent(name: "image", value: state.offImage)
}

def installed() {
	updated()
    off()   
}

def setOnImageTag(url, width, heigth)
{
	state.onImage = "<img src=" + url + " width="+width+" height="+heigth+">"
	if (device.currentValue("switch") == "on")
		sendEvent(name: "image", value: state.onImage)
}

def setOffImageTag(url, width, heigth)
{
	state.offImage = "<img src=" + url + " width="+width+" height="+heigth+">"
	if (this.device.currentValue("switch") == "off")
		sendEvent(name: "image", value: state.offImage)
}

def setOnImageUrl(newImage)
{
    state.onImageUrl = newImage
	setOnImageTag(state.onImageUrl, state.imgWidth, state.imgHeight)
}

def setOffImageUrl(newImage)
{
    state.offImageUrl = newImage
	setOffImageTag(state.offImageUrl, state.imgWidth, state.imgHeight)
}

def setImageHeight(height)
{
	state.imgHeight = height
	setOnImageTag(state.onImageUrl, state.imgWidth, state.imgHeight)
	setOffImageTag(state.offImageUrl, state.imgWidth, state.imgHeight)
}

def setImageWidth(width)
{
	state.imgWidth = width
	setOnImageTag(state.onImageUrl, state.imgWidth, state.imgHeight)
	setOffImageTag(state.offImageUrl, state.imgWidth, state.imgHeight)
}
