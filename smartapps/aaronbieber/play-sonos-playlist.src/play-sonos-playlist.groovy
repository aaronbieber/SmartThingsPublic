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
    name: "Play Sonos Playlist",
    namespace: "aaronbieber",
    author: "Aaron Bieber",
    description: "Use the Node.js Sonos HTTP server to play a Sonos playlist on one speaker when the mode changes.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
  section("Configure") {
    input(name: "speaker", type: "text", title: "Which speaker? Lowercase, please")
    input(name: "volume", type: "number", title: "Volume (1-100)")
    input(name: "playlist", type: "text", title: "Sonos playlist to play")
    input(name: "modes", type: "mode", title: "Play for which modes?", multiple: true)
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
  subscribe(location, "mode", modeChangeHandler)
}

def modeChangeHandler(event) {
  def mode = event.value
  log.debug "Mode changed to ${mode}"
  if (settings.modes.contains(mode)) {
    log.debug "Mode ${mode} is in the list of modes ${settings.modes}; playing ${settings.playlist} at vol. ${settings.volume} on ${settings.speaker}"
    sendSonosRequest(settings.speaker, "volume", settings.volume)
    sendSonosRequest(settings.speaker, "shuffle", "on")
    sendSonosRequest(settings.speaker, "repeat", "on")
    sendSonosRequest(settings.speaker, "playlist", settings.playlist)
  } else {
    log.debug "Not a trigger mode; doing nothing"
  }
}

def sendSonosRequest(speaker, action, value) {
  def speakerName = speaker.replace(" ", "%20")
  def actionName = action.replace(" ", "%20")
  def valueName = value.toString().replace(" ", "%20")
  def command = new physicalgraph.device.HubAction(
  method: "GET",
  path: "/${speakerName}/${actionName}/${valueName}",
  headers: [
                                                   HOST: "192.168.10.10:5005"
  ]
  )
  log.debug "Sending command: ${command}"
  sendHubCommand(command)
}
