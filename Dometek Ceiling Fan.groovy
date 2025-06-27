/**
 *  Dometek Ceiling Fan with Light Switch and Wind Direction
 *  
 *  Copyright 2025 Manus AI
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
 *  Based on zigbee2mqtt external converter for Dometek Ceiling Fan:
 *  https://github.com/Koenkk/zigbee2mqtt/issues/26836
 * version 1.0.0 2025-06-26 Joao - Initial version
 */

static String version()   { '1.0.0' }
static String timeStamp() { '2025/06/26 11:11 PM' }


metadata {
    definition (
        name: "Dometek Ceiling Fan", 
        namespace: "manus-ai", 
        author: "Manus AI",
        importUrl: ""
    ) {
        capability "Switch"
        capability "SwitchLevel"
        capability "FanControl"
        capability "Light"
        capability "Refresh"

        fingerprint profileId: "0104", deviceId: "0100", inClusters: "0000,0004,0005,EF00", outClusters: "0019,000A", manufacturer: "_TZE284_rcke0jsu", model: "TS0601"
        
        attribute "fanSwitch", "string"
        attribute "fanMode", "string"
        attribute "fanDirection", "string"
        attribute "lightCountdown", "number"
        attribute "fanCountdown", "number"
        attribute "statusIndication", "string"
        attribute "powerOutageMemory", "string"
    }

    // Commands for the driver
    command "setFanSpeed", [[name:"speed", type:"NUMBER", description:"Fan speed (1-4)"]]
    command "setFanMode", [[name:"mode", type:"ENUM", description:"Fan mode (nature, fresh)", constraints:["nature", "fresh"]]]
    command "setFanDirection", [[name:"direction", type:"ENUM", description:"Fan direction (forward, reverse)", constraints:["forward", "reverse"]]]
    command "setLightCountdown", [[name:"minutes", type:"NUMBER", description:"Light countdown in minutes (0-540)"]]
    command "setFanCountdown", [[name:"minutes", type:"NUMBER", description:"Fan countdown in minutes (0-540)"]]
    command "setStatusIndication", [[name:"status", type:"ENUM", description:"Status indication (on, off, always)", constraints:["on", "off", "always"]]]
    command "setPowerOutageMemory", [[name:"memory", type:"ENUM", description:"Power outage memory (off, on, restore)", constraints:["off", "on", "restore"]]]
    command "fanOn"
    command "fanOff"
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing description: ${description}"
    def map = zigbee.parseDescriptionAsMap(description)

    if (map) {
        def result = []
        def cluster = map.clusterId
        def command = map.command
        def data = map.data

        // Handle Tuya specific commands (DPs)
        if (cluster == "EF00" && command == "01") { // Tuya Data Point Response
            if (data && data.size() >= 4) {
                def dpId = zigbee.convertHexToInt(data[2])
                def dpType = zigbee.convertHexToInt(data[3])
                def dpLen = zigbee.convertHexToInt(data[4]) * 256 + zigbee.convertHexToInt(data[5])
                def dpValue = parseTuyaDataPoint(data.drop(6).take(dpLen), dpType)

                log.debug "Tuya DP: ${dpId}, Type: ${dpType}, Value: ${dpValue}"

                switch (dpId) {
                    case 1: // Light Switch
                        result << createEvent(name: "switch", value: dpValue == 1 ? "on" : "off", descriptionText: "Light is ${dpValue == 1 ? 'on' : 'off'}")
                        break
                    case 51: // Fan Switch
                        result << createEvent(name: "fanSwitch", value: dpValue == 1 ? "on" : "off", descriptionText: "Fan is ${dpValue == 1 ? 'on' : 'off'}")
                        break
                    case 52: // Fan Mode
                        def mode = "unknown"
                        if (dpValue == 0) mode = "nature"
                        else if (dpValue == 1) mode = "fresh"
                        result << createEvent(name: "fanMode", value: mode, descriptionText: "Fan mode set to ${mode}")
                        break
                    case 53: // Fan Speed
                        result << createEvent(name: "speed", value: dpValue + 1, descriptionText: "Fan speed set to ${dpValue + 1}") // zigbee2mqtt maps 0-3 to 1-4
                        result << createEvent(name: "level", value: (dpValue + 1) * 25, descriptionText: "Fan level set to ${(dpValue + 1) * 25}%")
                        break
                    case 54: // Fan Direction
                        def direction = "unknown"
                        if (dpValue == 0) direction = "forward"
                        else if (dpValue == 1) direction = "reverse"
                        result << createEvent(name: "fanDirection", value: direction, descriptionText: "Fan direction set to ${direction}")
                        break
                    case 7: // Light Countdown
                        result << createEvent(name: "lightCountdown", value: dpValue, descriptionText: "Light countdown set to ${dpValue} minutes")
                        break
                    case 55: // Fan Countdown
                        result << createEvent(name: "fanCountdown", value: dpValue, descriptionText: "Fan countdown set to ${dpValue} minutes")
                        break
                    case 102: // Status Indication
                        def status = "unknown"
                        if (dpValue == 0) status = "off"
                        else if (dpValue == 1) status = "on"
                        else if (dpValue == 2) status = "always"
                        result << createEvent(name: "statusIndication", value: status, descriptionText: "Status indication set to ${status}")
                        break
                    case 103: // Power Outage Memory
                        def memory = "unknown"
                        if (dpValue == 0) memory = "off"
                        else if (dpValue == 1) memory = "on"
                        else if (dpValue == 2) memory = "restore"
                        result << createEvent(name: "powerOutageMemory", value: memory, descriptionText: "Power outage memory set to ${memory}")
                        break
                    default:
                        log.warn "Unhandled Tuya DP: ${dpId}"
                        break
                }
            }
        }
        return result
    }
    return null
}

def on() {
    log.debug "Executing on()"
    return sendTuyaCommand(1, 1, 1) // DP 1, BOOL type, value 1 (on)
}

def off() {
    log.debug "Executing off()"
    return sendTuyaCommand(1, 1, 0) // DP 1, BOOL type, value 0 (off)
}

def fanOn() {
    log.debug "Executing fanOn()"
    return sendTuyaCommand(51, 1, 1) // DP 51, BOOL type, value 1 (on)
}

def fanOff() {
    log.debug "Executing fanOff()"
    return sendTuyaCommand(51, 1, 0) // DP 51, BOOL type, value 0 (off)
}

def setLevel(level) {
    log.debug "Executing setLevel(${level})"
    // Mapping 0-100 to 1-4 fan speeds
    def speed = Math.round(level / 25) + 1
    if (speed < 1) speed = 1
    if (speed > 4) speed = 4
    setFanSpeed(speed)
}

def setSpeed(speed) {
    log.debug "Executing setSpeed(${speed})"
    setFanSpeed(speed)
}

def setFanSpeed(speed) {
    log.debug "Executing setFanSpeed(${speed})"
    // Tuya DP 53 for fan speed, mapping 1-4 to 0-3 for Tuya
    def tuyaSpeed = speed - 1
    if (tuyaSpeed < 0) tuyaSpeed = 0
    if (tuyaSpeed > 3) tuyaSpeed = 3
    return sendTuyaCommand(53, 2, tuyaSpeed) // DP 53, VALUE type
}

def setFanMode(mode) {
    log.debug "Executing setFanMode(${mode})"
    def tuyaMode
    switch (mode) {
        case "nature": tuyaMode = 0; break
        case "fresh": tuyaMode = 1; break
        default: 
            log.warn "Unknown fan mode: ${mode}"
            return
    }
    return sendTuyaCommand(52, 4, tuyaMode) // DP 52, ENUM type
}

def setFanDirection(direction) {
    log.debug "Executing setFanDirection(${direction})"
    def tuyaDirection
    switch (direction) {
        case "forward": tuyaDirection = 0; break
        case "reverse": tuyaDirection = 1; break
        default: 
            log.warn "Unknown fan direction: ${direction}"
            return
    }
    return sendTuyaCommand(54, 4, tuyaDirection) // DP 54, ENUM type
}

def setLightCountdown(minutes) {
    log.debug "Executing setLightCountdown(${minutes})"
    return sendTuyaCommand(7, 2, minutes) // DP 7, VALUE type
}

def setFanCountdown(minutes) {
    log.debug "Executing setFanCountdown(${minutes})"
    return sendTuyaCommand(55, 2, minutes) // DP 55, VALUE type
}

def setStatusIndication(status) {
    log.debug "Executing setStatusIndication(${status})"
    def tuyaStatus
    switch (status) {
        case "off": tuyaStatus = 0; break
        case "on": tuyaStatus = 1; break
        case "always": tuyaStatus = 2; break
        default: 
            log.warn "Unknown status indication: ${status}"
            return
    }
    return sendTuyaCommand(102, 4, tuyaStatus) // DP 102, ENUM type
}

def setPowerOutageMemory(memory) {
    log.debug "Executing setPowerOutageMemory(${memory})"
    def tuyaMemory
    switch (memory) {
        case "off": tuyaMemory = 0; break
        case "on": tuyaMemory = 1; break
        case "restore": tuyaMemory = 2; break
        default: 
            log.warn "Unknown power outage memory: ${memory}"
            return
    }
    return sendTuyaCommand(103, 4, tuyaMemory) // DP 103, ENUM type
}

def refresh() {
    log.debug "Executing refresh()"
    // Send Tuya query command to request current status
    def cmds = []
    cmds << "he cmd 0x${device.deviceNetworkId} 0x01 0xEF00 0x03 {}"
    return cmds
}

// Helper methods for Tuya data parsing and sending

def parseTuyaDataPoint(data, type) {
    switch (type) {
        case 0: // RAW
            return data.collect { String.format("%02X", zigbee.convertHexToInt(it)) }.join()
        case 1: // BOOL
            return zigbee.convertHexToInt(data[0])
        case 2: // VALUE (4-byte integer)
            if (data.size() >= 4) {
                return (zigbee.convertHexToInt(data[0]) << 24) + 
                       (zigbee.convertHexToInt(data[1]) << 16) + 
                       (zigbee.convertHexToInt(data[2]) << 8) + 
                       zigbee.convertHexToInt(data[3])
            }
            return 0
        case 3: // STRING
            return new String(data.collect { (char)zigbee.convertHexToInt(it) } as char[])
        case 4: // ENUM
            return zigbee.convertHexToInt(data[0])
        case 5: // BITMAP
            return zigbee.convertHexToInt(data[0])
        default:
            return null
    }
}

def sendTuyaCommand(dpId, dataType, value) {
    def cmds = []
    def payload = []
    
    // Command header
    payload << "00" // Status (0x00)
    payload << "00" // Transaction sequence number
    payload << String.format("%02X", dpId) // Data Point ID
    payload << String.format("%02X", dataType) // Data Type

    def dataBytes = []
    switch (dataType) {
        case 1: // BOOL
        case 4: // ENUM
        case 5: // BITMAP
            dataBytes << String.format("%02X", value & 0xFF)
            break
        case 2: // VALUE (4-byte integer)
            dataBytes << String.format("%02X", (value >> 24) & 0xFF)
            dataBytes << String.format("%02X", (value >> 16) & 0xFF)
            dataBytes << String.format("%02X", (value >> 8) & 0xFF)
            dataBytes << String.format("%02X", value & 0xFF)
            break
        case 3: // STRING
            value.getBytes("UTF-8").each { dataBytes << String.format("%02X", it & 0xFF) }
            break
        case 0: // RAW
            if (value instanceof String) {
                for (int i = 0; i < value.length(); i += 2) {
                    dataBytes << value.substring(i, Math.min(i + 2, value.length()))
                }
            }
            break
    }

    payload << String.format("%02X", (dataBytes.size() >> 8) & 0xFF) // Data length high byte
    payload << String.format("%02X", dataBytes.size() & 0xFF) // Data length low byte
    payload.addAll(dataBytes)

    def payloadStr = payload.join("")
    cmds << "he cmd 0x${device.deviceNetworkId} 0x01 0xEF00 0x00 {${payloadStr}}"
    
    log.debug "Sending Tuya command: ${cmds}"
    return cmds
}
