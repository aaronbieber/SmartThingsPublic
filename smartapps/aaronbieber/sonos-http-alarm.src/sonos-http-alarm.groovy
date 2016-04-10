/**
 *  Sonos HTTP Alarm
 *
 *  Copyright 2016 Aaron Bieber
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
 */
definition(
    name: "Sonos HTTP Alarm",
    namespace: "aaronbieber",
    author: "Aaron Bieber",
    description: "v1.0: Call a local Sonos HTTP Node.js server to play a playlist at a certain time.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Configure") {
		input(name: "triggerAt", type: "time", title: "Trigger at this time")
        input(name: "days", type: "enum", title: "Trigger on these days", required: false, multiple: true, options: weekdays() + weekends())
        input(name: "room", type: "text", title: "Which room? Lowercase, please")
        input(name: "volume", type: "number", title: "Volume (1-100)")
        input(name: "playlist", type: "text", title: "Sonos playlist to play")
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unschedule()
	initialize()
}

def initialize() {
	log.debug "Initializing the schedule"
	schedule(triggerAt, handleEvent)
}

def handleEvent() {
	log.debug "Handling event..."
    if (canTriggerToday()) {
    	log.debug "This is an acceptable trigger time. Triggering."
        sendSonosRequest(room, "volume", volume)
        sendSonosRequest(room, "shuffle", "on")
        sendSonosRequest(room, "repeat", "on")
		sendSonosRequest(room, "playlist", playlist)
    } else {
    	log.debug "Not a valid trigger day; doing nothing."
    }
}

def sendSonosRequest(room, action, value) {
	def roomName = room.replace(" ", "%20")
    def actionName = action.replace(" ", "%20")
    def valueName = value.toString().replace(" ", "%20")
	def command = new physicalgraph.device.HubAction(
		method: "GET",
		path: "/${roomName}/${actionName}/${valueName}",
		headers: [
			HOST: "192.168.10.10:5005"
		]
	)
    log.debug "Sending command: ${command}"
	sendHubCommand(command)
}

// See if today's day name is in the "days" list, or the "days" list is empty.
def canTriggerToday() {
	def today = new Date().format("EEEE")
	log.debug "Checking if we can run ${today}"
	return (!days || days.contains(today))
}

def weekdays() {
	["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]
}

def weekends() {
	["Saturday", "Sunday"]
}