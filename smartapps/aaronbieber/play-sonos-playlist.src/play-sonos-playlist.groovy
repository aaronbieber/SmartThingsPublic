/**
 *  Play Sonos Playlist
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
 *  ---
 *
 *  This SmartApp allows you to play a Sonos playlist by name on a specific Sonos speaker using the Node Sonos HTTP
 *  API server (https://github.com/jishi/node-sonos-http-api) when one of a list of modes is entered.
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
  page(name: "pageOne", title: "Speaker setup", nextPage: "pageTwo", uninstall: true) {
    section {
      paragraph "This SmartApp requires the Node Sonos HTTP API server to be running on your network; tap the link below to visit the project on Github."
      href(
        name: "node-sonos-http-api",
        title: "Node Sonos HTTP API",
        style: "external",
        url: "https://github.com/jishi/node-sonos-http-api")

      input(name: "nodeServer", type: "text", title: "Your Sonos HTTP API server address, like 192.168.1.1:5005")
      input(name: "speaker", type: "capability.musicPlayer", title: "Play on which speaker?")
      input(name: "volume", type: "number", range: "1..100", title: "Set volume to (percent)")
      input(name: "shuffle", type: "bool", title: "Shuffle?")
      input(name: "repeat", type: "bool", title: "Repeat?")
      input(name: "playlist", type: "text", title: "Sonos playlist to play")
    }
  }

  page(name: "pageTwo", title: "Trigger settings", uninstall: true, install: true) {
    section("Mode triggers") {
      input(name: "modes", type: "mode", title: "Play for which mode(s)?", multiple: true, required: true)
    }

    section("Quiet hours") {
      paragraph(
        "If the mode change is triggered between these hours, do not play. This is useful " +
        "if you often come home late and don't want your 'welcome home' playlist to wake the house. " +
        "Note that if the start time is after the end time, we will assume that quiet hours cross " +
        "the midnight boundary.")

      input(name: "quietStart", type: "time", title: "Quiet from", required: false)
      input(name: "quietEnd", type: "time", title: "Quiet until", required: false)
      
      label(title: "Assign a name")
    }
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
  log.debug "Attaching mode change handler"
  subscribe(location, "mode", modeChangeHandler)
}

def duringQuietHours() {
  if (!settings.quietStart || !settings.quietEnd) {
    log.debug "Quiet hours are not set; passing"
    return false
  }

  // All of this insanity is thanks to the way Java handles dates and times.
  // I would never wish this on any other human.
  def quietStartTimeString = "1970-01-01T" + settings.quietStart.substring(11)
  def quietEndTimeString = "1970-01-01T" + settings.quietEnd.substring(11)
  def nowString = "1970-01-01T" + new java.text.SimpleDateFormat("HH:mm:ss.SSSZ").format(new Date())

  def quietStartTimeDate = new java.text.SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss.SSSZ").parse(quietStartTimeString)
  def quietEndTimeDate = new java.text.SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss.SSSZ").parse(quietEndTimeString)
  def nowDate = new java.text.SimpleDateFormat("yyyy-mm-dd'T'HH:mm:ss.SSSZ").parse(nowString)

  // If the start time is before midnight and the end time is after midnight,
  // the comparisons will be exclusive. Otherwise, the comparisons are inclusive.
  def crossesMidnight = quietStartTimeDate.compareTo(quietEndTimeDate) > 0
  
  return ((   crossesMidnight
           && (   nowDate.after(quietStartTimeDate)
               || nowDate.before(quietEndTimeDate)))
           || (   nowDate.after(quietStartTimeDate)
               && nowDate.before(quietEndTimeDate)))
}

def modeChangeHandler(event) {
  def mode = event.value
  log.debug "Mode changed to ${mode}"
  
  if (!settings.modes.contains(mode)) {
    log.debug "Mode ${mode} is not in the list of modes ${settings.modes}; doing nothing"
	return false
  }
  
  if (duringQuietHours()) {
    log.debug "Quiet hours are in effect; doing nothing"
    return false
  }

  log.debug "Mode ${mode} is in the list of modes ${settings.modes} playing ${settings.playlist} at vol. ${settings.volume} on ${settings.speaker}"
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
}

/* Get the Node Sonos HTTP API version of a capability.musicPlayer name.
 * This is acquired by coercing the musicPlayer to a string to get its
 * SmartThings name, removing "Sonos" from the end, and lowercasing it.
 *
 * @param capability.musicPlayer player A music player object
 * 
 * @return string The music player name recognized by Node Sonos HTTP API
 */
def getMusicPlayerName(player) {
  return player.toString().toLowerCase().replace(" sonos", "").replace(" ", "%20")
}

/* Send a command to the local Node Sonos HTTP API server. This is accomplished
 * by proxying an HTTP GET request through your SmartThings hub using
 * sendHubCommand().
 *
 * @param capability.musicPlayer speaker The musicPlayer to play on
 * @param string                 action  The action to take (one of "shuffle", "repeat", or "playlist")
 * @param string                 value   The action's value ("on", "off", or a playlist name)
 */
def sendSonosRequest(speaker, action, value) {
  def speakerName = getMusicPlayerName(speaker)
  def actionName = action.replace(" ", "%20")
  def valueName = value.toString().replace(" ", "%20")
  def command = new physicalgraph.device.HubAction(
    method: "GET",
    path: "/${speakerName}/${actionName}/${valueName}",
    headers: [HOST: settings.nodeServer])

  log.debug "Sending command: ${command}"
  sendHubCommand(command)
}
