/**
 *  Stop All Players
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
  name: "Stop All Players",
  namespace: "aaronbieber",
  author: "Aaron Bieber",
  description: "Stop all music players.",
  category: "Mode Magic",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
  page(name: "pageOne") {
    section {
      input(name: "players", type: "capability.musicPlayer", title: "Stop which players", multiple: true)
      input(name: "modes", type: "mode", title: "Select mode(s)", multiple: true)
      label(name: "label", title: "Assign a name")
    }
  }
}

def installed() {
  log.debug "Installed with settings: ${settings}"

  initialize()
}

def updated() {
  log.debug "Updated with settings: ${settings}"

  unsubscribe()
  initialize()
}

def initialize() {
  subscribe(location, "mode", modeChangeHandler)
}

def modeChangeHandler(event) {
  def mode = event.value
  log.debug "Mode changed to ${mode}"
  if (settings.modes.contains(mode)) {
    log.debug "Mode is in the list (${settings.modes}); stopping all players"
    players.each{ player ->
      log.debug "Stopping ${player}"
      player.stop()
    }
  }
}