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
  section("Configuration") {
    paragraph "This SmartApp requires the Node Sonos HTTP API server to be running on your network; tap the link below to visit the project on Github."
    href(
      name: "node-sonos-http-api",
      title: "Node Sonos HTTP API",
      style: "external",
      url: "https://github.com/jishi/node-sonos-http-api")

    input(name: "nodeServer", type: "text", title: "Your Sonos HTTP API server address, like 192.168.1.1.:5005")
    input(name: "speaker", type: "capability.musicPlayer", title: "Play on which speaker?")
    input(name: "volume", type: "number", range: "1..100", title: "Set volume to (percent)")
    input(name: "shuffle", type: "bool", title: "Shuffle?")
    input(name: "repeat", type: "bool", title: "Repeat?")
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

    sendSonosRequest(
      settings.speaker,
      "shuffle",
      settings.shuffle ? "on" : "off"
    )

    sendSonosRequest(
      settings.speaker,
      "repeat",
      settings.repeat ? "on" : "off"
    )

    sendSonosRequest(settings.speaker, "playlist", settings.playlist)
  } else {
    log.debug "Not a trigger mode; doing nothing"
  }
}

def sendSonosRequest(speaker, action, value) {
  def speakerName = speaker.toLowerCase().replace(" ", "%20")
  def actionName = action.replace(" ", "%20")
  def valueName = value.toString().replace(" ", "%20")
  def command = new physicalgraph.device.HubAction(
    method: "GET",
    path: "/${speakerName}/${actionName}/${valueName}",
    headers: [HOST: settings.nodeServer])

  log.debug "Sending command: ${command}"
  sendHubCommand(command)
}
