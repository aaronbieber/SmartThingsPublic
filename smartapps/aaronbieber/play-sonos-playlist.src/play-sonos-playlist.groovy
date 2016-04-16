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
  page name: "pageStart", nextPage: "pageTriggers"
  page name: "pageTriggers", nextPage: "pageControlType"
  page name: "pageControlType"
  page name: "pagePlaylist"
  page name: "pagePreset"
}

def pageStart() {
  dynamicPage(name: "pageStart", title: "Basic Configuration", uninstall: true, install: false) {
    section {
      paragraph "This SmartApp requires the Node Sonos HTTP API server to be running on your network; tap the link below to visit the project on Github."
      href(
          name: "node-sonos-http-api",
          title: "Node Sonos HTTP API",
          description: "Tap to visit the website",
          style: "external",
          url: "https://github.com/jishi/node-sonos-http-api")

      input(name: "nodeServer", type: "text", title: "Your Sonos HTTP API server address, like 192.168.1.1:5005")
    }

    section {
      input(name: "modes", type: "mode", title: "Play for which mode(s)?", multiple: true, required: true)
    }

    section {
      label(title: "Assign a name")
    }
  }
}

def pageTriggers() {
  dynamicPage(name: "pageTriggers", title: "Trigger settings", uninstall: true, install: false) {

    section("Quiet hours") {
      paragraph(
        "If the mode change is triggered between these hours, do not play. This is useful " +
        "if you often come home late and don't want your 'welcome home' playlist to wake the house. " +
        "Note that if the start time is after the end time, we will assume that quiet hours cross " +
        "the midnight boundary.")

      input(name: "quietStart", type: "time", title: "Quiet from", required: false)
      input(name: "quietEnd", type: "time", title: "Quiet until", required: false)
    }

    section("Notifications") {
      input(name: "notifyOnPlay", type: "bool", title: "Notify on play?",
            description: "Send a push notification when this playlist is triggered.")
      input(name: "notifyOnQuiet", type: "bool", title: "Notify on quiet?",
            description: "If you're using quiet hours, send a push notification if this playlist is triggered during that time.")
    }
  }
}

def pageControlType() {
  dynamicPage(name: "pageControlType", title: "Speaker setup", uninstall: true, install: true) {
    section {
      input(
          name: "controlType",
          title: "Control Type",
          type: "enum",
          options: [["Playlist": "Sonos Playlist"], ["Preset": "Sonos HTTP API Preset"]],
          submitOnChange: true,
          multiple: false,
          required: false
      )
      if (controlType) {
        href(
            name: "href${controlType}",
            page: "page${controlType}",
            title: "${controlType} Settings",
            description: controlTypeDesc()
        )
      }
    }
  }
}

def controlTypeDesc() {
  def desc = ""
  if (controlType == "Playlist") {
  	if (playlist && speaker && volume) {
      desc = "Play ${playlist} on ${speaker} at volume ${volume}"
    } else {
      desc = "Tap to configure"
    }
  }
  
  if (controlType == "Preset") {
    if (preset) {
      desc = "Launch preset ${preset}"
    } else {
      desc = "Tap to configure"
    }
  }
  
  desc
}

def pagePlaylist() {
  dynamicPage(name: "pagePlaylist", title: "Playlist Settings", install: false, uninstall: false) {
    section {
      input(name: "speaker", type: "capability.musicPlayer", title: "Play on which speaker?")
      input(name: "volume", type: "number", range: "1..100", title: "Set volume to (percent)")
      input(name: "shuffle", type: "bool", title: "Shuffle?")
      input(name: "repeat", type: "bool", title: "Repeat?")
      input(name: "playlist", type: "text", title: "Sonos playlist to play")
    }
  }
}

def pagePreset() {
  dynamicPage(name: "pagePreset", title: "Preset Settings", install: false, uninstall: false) {
    section {
      input(name: "preset", type: "text", title: "Preset name")
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
    
    if (settings.notifyOnQuiet) {
      sendPush("Your \"${settings.playlist}\" playlist would have played on \"${settings.speaker}\", but it is quiet time.")
    }
    
    return false
  }

  // Playing will begin!
  if (controlType == "Playlist") {
    log.debug "Mode ${mode} is in the list of modes ${settings.modes}, playing ${settings.playlist} at vol. ${settings.volume} on ${settings.speaker}."

    if (settings.notifyOnPlay) {
      sendPush("Your \"${settings.playlist}\" playlist is playing on \"${settings.speaker}.\"")
    }
    runPlaylist()
    return true
  }

  if (controlType == "Preset") {
    log.debug "Mode ${mode} is in the list of modes ${settings.modes}, running preset ${settings.preset}."
    runPreset()
    return true
  }
}

def runPlaylist() {
  sendSonosSpeakerRequest(settings.speaker, "volume", settings.volume)

  sendSonosSpeakerRequest(
    settings.speaker,
    "shuffle",
    settings.shuffle ? "on" : "off"
  )

  sendSonosSpeakerRequest(
    settings.speaker,
    "repeat",
    settings.repeat ? "on" : "off"
  )

  sendSonosSpeakerRequest(settings.speaker, "playlist", settings.playlist)
}

def runPreset() {
  sendSonosPresetRequest(settings.preset)
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
 *
 * @return void
 */
def sendSonosSpeakerRequest(speaker, action, value) {
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

/* Send a command to the local Node Sonos HTTP API server to start a "preset."
 *
 * @param string preset The name of the preset in presets.json on the Node server.
 *
 * @return void
 */
def sendSonosPresetRequest(preset) {
  def presetName = preset.replace(" ", "%20")
  def command = new physicalgraph.device.HubAction(
      method: "GET",
      path: "/preset/${presetName}",
      headers: [HOST: settings.nodeServer])

  log.debug "Sending command: ${command}"
  sendHubCommand(command)
}