/**
 *  Copyright 2019 G.Brown (MorkOz) - Adaptado para o Terncy Smart Dial por Joao M Ferreira em maio 2021
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
 *  Adapted and modified from code written by at9, motley74, sticks18 and AnotherUser 
 *  Original sources: 
 *  https://github.com/at-9/hubitat/blob/master/Drivers/at-9-Zemismart-3-Gang-Sticker-Switch
 *  https://github.com/motley74/SmartThingsPublic/blob/master/devicetypes/motley74/osram-lightify-dimming-switch.src/osram-lightify-dimming-switch.groovy
 *  https://github.com/AnotherUser17/SmartThingsPublic-1/blob/master/devicetypes/AnotherUser/osram-lightify-4x-switch.src/osram-lightify-4x-switch.groovy
 *
 *  Version Author              Note
 *  0.1     G.Brown (MorkOz)    Initial release
 *  0.1     Joao.MF (joaomf)    Terncy Smart Dial version
 */

import hubitat.zigbee.zcl.DataType

metadata {
    definition (name: "Terncy Smart Dial", namespace: "joaomf", author: "Joao.MF") {
    capability "Actuator"
    capability "PushableButton"
    capability "HoldableButton"
    capability "DoubleTapableButton"
    capability "Configuration"
    capability "Refresh"
    capability "Sensor"  
    capability "Battery"  
    capability "Switch Level"    
    capability "ChangeLevel"    

        fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,FCCC", outClusters: "0019", manufacturer: "", model: "TERNCY-SD01", deviceJoinName: "Terncy Smart Dial"
        
        attribute "batteryLastReplaced", "String"
        command "resetBatteryReplacedDate"
        attribute "lastLevel", "integer"
    }

	preferences {
        input name: "debugEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        
        //Battery Voltage Range
		input name: "voltsmin", title: "Min Volts (0% battery = ___ volts, range 2.0 to 2.7). Default = 2.5 Volts", description: "", type: "decimal", range: "2..2.7"
		input name: "voltsmax", title: "Max Volts (100% battery = ___ volts, range 2.8 to 3.4). Default = 3.0 Volts", description: "", type: "decimal", range: "2.8..3.4"
        input(name: "minLevel", type: "integer", title: "Level (minimum)", description: "Minimum dimming level to send before sending 0, below this level it will be reported as 0. (default: 2)", defaultValue: "2", range: "0..50")
        input(name: "maxLevel", type: "integer", title: "Level (maximum)", description: "Maximum dimming level to send, at and above this level it will be reported as 100. (default: 97)", defaultValue: "97", range: "51..100")
        input(name: "multiplier", type: "integer", title: "Multiplier", description: "Multiplier factor for the dimmer returning level. (default: 10)", defaultValue: "10", range: "0..1000")


	}

}

private sendButtonNumber() {
    def remoteModel = device.getDataValue("model")
    switch(remoteModel){
        case "TS0041":
            sendEvent(name: "numberOfButtons", value: 1, isStateChange: true)
            break
        case "TS0042":
            sendEvent(name: "numberOfButtons", value: 2, isStateChange: true)
            break
        case "TS0043":
            sendEvent(name: "numberOfButtons", value: 3, isStateChange: true)
            break
        case "TS0044":
            sendEvent(name: "numberOfButtons", value: 4, isStateChange: true)
            break
        case "TERNCY-SD01":
            sendEvent(name: "numberOfButtons", value: 1, isStateChange: true)
            break
    }
}

def installed() {
    sendButtonNumber
    state.start = now()
}

def updated() {
    sendButtonNumber
}

def refresh() {
   //deleteAllScheduledJobs
   // read battery level attributes
      return zigbee.readAttribute(0x0001, 0x0020) + zigbee.configureReporting(0x0001, 0x0020, 0x20, 3600, 21600, 0x01)
  // this device doesn't support 0021
      zigbee.readAttribute(0x0001, 0x0021)
}

def configure() {
    sendButtonNumber
    state.lastLevel = 0
    def configCmds = []
  for (int endpoint=1; endpoint<=3; endpoint++) {
    def list = ["0006", "0001", "0000"]
    // the config to the switch
    for (cluster in list) {
      configCmds.add("zdo bind 0x${device.deviceNetworkId} 0x0${endpoint} 0x01 0x${cluster} {${device.zigbeeId}} {}")
    }
  }
  return configCmds
}

def parse(String description) {
    Map map = [:]
    if ((description?.startsWith("catchall:")) || (description?.startsWith("read attr -"))) {
        def parsedMap = zigbee.parseDescriptionAsMap(description)
        if (debugEnable){
            log.debug("Message Map: '$parsedMap'")
        }
        
        switch(parsedMap.sourceEndpoint) {
            case "04": 
                button = "4"
                break
            case "03": 
                button = "3"
                break
            case "02": 
                button = "2"
                break
            case "01": 
                button = "1"
                break
        }
        //log.debug(parsedMap.command)
        //log.debug(parsedMap.data[-1])
        switch(parsedMap.command) {                
            case "00": 
                //log.debug("Entrou 00")
                switch(parsedMap.data[-1]) {
                    case "01":
                        name = "pushed" 
                        //log.debug("Comando Push")
                        break
                    case "02":
                        name = "doubleTapped" 
                        //log.debug("Comando Double Tap")
                        break
                }    
                break

            case "14": 
                //log.debug("Entrou 14")
                switch(parsedMap.data) {
                    case "[01]":
                        name = "held" 
                        //log.debug("Comando Hold")
                        break
                }
                break
            
            case "0A":
                //log.debug("Girou")
                //log.debug("Nivel: '$state.lastLevel'")
                short valueParsed = zigbee.convertHexToInt(parsedMap.value)
                //log.debug("Valor: '$valueParsed'")
                def lado = {0 < valueParsed ? "Direita" : "Esquerda"}
                                                                    //(multiplier as integer) -> se der erro na linha abaixo
                def pontos = state.lastLevel + (valueParsed / 12) * multiplier 
                             //(minLevel as integer) -> se der erro na linha abaixo
                if (pontos < minLevel) { 
                    pontos = minLevel
                             //(maxLevel as integer) -> se der erro na linha abaixo
                } else if (pontos > maxLevel) {
                    pontos = maxLevel
                }    
                
                setLevel(pontos)
                
                //if (valueParsed > 0) {
                //    log.debug("Girou para a direita")
                //} else {
                //    log.debug("Girou para a esquerda")
                //}
                break
            
            default:
                log.debug("Comando desconhecido")
                break
            
        }   
        //log.debug(name)        
        
        sendEvent(name: name, value: button, descriptionText: "Button $button was $name",isStateChange:true)
    //}  
    
    if (cluster == "0001" & attrId == "0020")
	// Parse battery level from hourly announcement message
		map = parseBattery(valueHex)
	else
		//displayDebugLog("Unable to parse message")
        log.debug("Unable to parse message")
    if (map != [:]) {
		//displayDebugLog("Creating event $map")
        log.debug("Creating event $map")
		return createEvent(map)
	} //else
	  //return [:]
    
    }
    return
}

def setLevel(value) {
	log.debug "setLevel >> value: $value"
	def level = Math.max(Math.min(value as Integer, maxLevel), minLevel)
	//if (level > 0) {
	//	sendEvent(name: "switch", value: "on")
	//} else {
	//	sendEvent(name: "switch", value: "off")
	//}
    state.lastLevel = level
	sendEvent(name: "level", value: level, unit: "%")
}

// Convert 2-byte hex string to voltage
// 0x0020 BatteryVoltage -  The BatteryVoltage attribute is 8 bits in length and specifies the current actual (measured) battery voltage, in units of 100mV.
private parseBattery(valueHex) {
	//displayDebugLog("Battery parse string = ${valueHex}")
    log.debug("Battery parse string = ${valueHex}")
	def rawVolts = hexStrToSignedInt(valueHex) / 10
	def minVolts = voltsmin ? voltsmin : 2.5
	def maxVolts = voltsmax ? voltsmax : 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
	displayInfoLog(descText)
	def result = [
			name           : 'battery',
			value          : roundedPct,
			unit           : "%",
			isStateChange  : true,
			descriptionText: descText
	]
	return result
}

//Reset the batteryLastReplaced date to current date
def resetBatteryReplacedDate(paired) {
	def newlyPaired = paired ? " for newly paired sensor" : ""
	sendEvent(name: "batteryLastReplaced", value: new Date().toLocaleString(), descriptionText: "Set battery last replaced date")
	displayInfoLog("Setting Battery Last Replaced to current date${newlyPaired}")
}