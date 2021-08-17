/**
 *  Zemismart six Button DH (v.0.0.1) Adaptado por Joao Melo Ferreira (@joaomf) para o Hubitat (17/09/2021)
 *
 * MIT License
 *
 * Copyright (c) 2018
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
*/

import groovy.json.JsonOutput
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import hubitat.zigbee.zcl.DataType

metadata 
{
   definition (name: "Zemismart Button 6", namespace: "Onaldo", author: "Onaldo", ocfDeviceType: "x.com.st.d.remotecontroller", mcdSync: true)
   {
      capability "Actuator"
      capability "Battery"
      //capability "Button"
      capability "PushableButton"
      capability "HoldableButton"
      capability "DoubleTapableButton"   
      capability "Refresh"
      capability "Sensor"
      capability "Health Check"
 
      command "configure"
      command "refresh" 
      //command "doubleTap"
	  //command "hold"
	  //command "pushReset"
	  command "resetBatteryReplacedDate" 
       
      fingerprint inClusters: "0000, 0004, 0005", outClusters: "0019, 000A", manufacturer: "_TZE200_zqtiam4u", model: "TS0601", deviceJoinName: "Zemismart B 6", mnmn: "SmartThings", vid: "generic-6-button"

       
// 상보이님 DTH를 참고했습니다. 

   }

   tiles(scale: 2)
   {  
      multiAttributeTile(name: "button", type: "generic", width: 2, height: 2) 
      {
         tileAttribute("device.button", key: "PRIMARY_CONTROL") 
         {
            attributeState "pushed", label: "Pressed", icon:"st.Weather.weather14", backgroundColor:"#53a7c0"
            attributeState "double", label: "Pressed Twice", icon:"st.Weather.weather11", backgroundColor:"#53a7c0"
            attributeState "held", label: "Held", icon:"st.Weather.weather13", backgroundColor:"#53a7c0"
         }
      }
      valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) 
      {
         state "battery", label: '${currentValue}% battery', unit: ""
      }
      standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) 
      {
         state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
      }

      main(["button"])
      details(["button","battery", "refresh"])
   }
}

private getCLUSTER_ID() { 0xEF00 }


private channelNumber(String dni) 
{
   dni.split(":")[-1] as Integer
}

def parse(String description) 
{
   if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
      Map descMap = zigbee.parseDescriptionAsMap(description)      
      log.debug descMap
      if (descMap?.clusterInt==CLUSTER_ID) {
         if ( descMap?.command == "01") {
            def buttonNumber = zigbee.convertHexToInt(descMap?.data[2])
                def buttonState = null
            log.debug "buttonNumber=${buttonNumber}" 
                if( buttonNumber == 10) {
               def batCap = zigbee.convertHexToInt(descMap?.data[9])
               log.debug "batCap=${batCap}" 
                   sendEvent(name:"battery", value: batCap)
                } else { 
               buttonState = zigbee.convertHexToInt(descMap?.data[6])
               log.debug "type=${buttonState}"      
               switch (buttonState) {    
                  case 0x00: 
                     buttonState = "pushed"
                     break
                  case 0x01:
                     buttonState = "doubleTapped"
                     break
                  case 0x02: 
                     buttonState = "held"
                     break
               }
//          def descriptionText = "button $buttonNumber was $buttonState"
//             result = [name: "button", value: buttonState, data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true]
             //sendButtonEvent(buttonNumber, buttonState)
             def descriptionText = "button $buttonNumber was $buttonState"
             //createEvent(name: "button", value: buttonState, data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true)     
             createEvent(name: buttonState, value: buttonNumber, descriptionText: descriptionText, isStateChange: true)          
                }
            }
      }
   }
}

private sendButtonEvent(buttonNumber, buttonState) 
{
   def child = childDevices?.find { channelNumber(it.deviceNetworkId) == buttonNumber }
   if (child)
   {
      def descriptionText = "$child.displayName was $buttonState" // TODO: Verify if this is needed, and if capability template already has it handled
      log.debug "child $child"
      child?.sendEvent([name: "button", value: buttonState, data: [buttonNumber: 1], descriptionText: descriptionText, isStateChange: true])
   } 
   else 
   {
      log.debug "Child device $buttonNumber not found!"
   }
}

def refresh() 
{
    //log.debug "Refreshing Battery"
    updated()
    //return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, getAttrid_Battery()) 
}

def configure() 
{
    log.debug "Configuring Reporting, IAS CIE, and Bindings."
    
    init()
    
    def cmds = []

    return
           zigbee.enrollResponse() +
           readDeviceBindingTable() // Need to read the binding table to see what group it's using            
           cmds
}

private getButtonName(buttonNum) 
{
   return "${device.displayName} " + buttonNum
}

private void createChildButtonDevices(numberOfButtons) 
{
   state.oldLabel = device.label
   log.debug "Creating $numberOfButtons"
   log.debug "Creating $numberOfButtons children"
   
   for (i in 1..numberOfButtons) 
   {
      log.debug "Creating child $i"
      def child = addChildDevice("hubitat", "Child Button", "${device.deviceNetworkId}:${i}", device.hubId,[completedSetup: true, label: getButtonName(i),
             isComponent: true, componentName: "button$i", componentLabel: "buttton ${i}"])
      child.sendEvent(name: "supportedButtonValues",value: ["pushed","held","double"].encodeAsJSON(), displayed: false)
      child.sendEvent(name: "numberOfButtons", value: 1, displayed: false)
      child.sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], displayed: false)
   }
}

def installed() 
{
    def numberOfButtons = 6
        createChildButtonDevices(numberOfButtons) //Todo
        sendEvent(name: "supportedButtonValues", value: ["pushed","held","double"].encodeAsJSON(), displayed: false)
    sendEvent(name: "numberOfButtons", value: numberOfButtons , displayed: false)
    // Initialize default states
    numberOfButtons.times 
    {
        sendEvent(name: "button", value: "pushed", data: [buttonNumber: it+1], displayed: false)
    }
    // These devices don't report regularly so they should only go OFFLINE when Hub is OFFLINE
    sendEvent(name: "DeviceWatch-Enroll", value: JsonOutput.toJson([protocol: "zigbee", scheme:"untracked"]), displayed: false)    
}

def updated() 
{
   log.debug "childDevices $childDevices"
   if (childDevices && device.label != state.oldLabel) 
   {
      childDevices.each 
      {
         def newLabel = getButtonName(channelNumber(it.deviceNetworkId))
    it.setLabel(newLabel)
      }
      state.oldLabel = device.label
    }
}


private Integer getGroupAddrFromBindingTable(description) 
{
   log.info "Parsing binding table - '$description'"
   def btr = zigbee.parseBindingTableResponse(description)
   def groupEntry = btr?.table_entries?.find { it.dstAddrMode == 1 }
   if (groupEntry != null) 
   {
      log.info "Found group binding in the binding table: ${groupEntry}"
      Integer.parseInt(groupEntry.dstAddr, 16)
   } 
   else 
   {
      log.info "The binding table does not contain a group binding"
      null
    }
}

private List addHubToGroup(Integer groupAddr) 
{
   ["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}","delay 200"]
}

private List readDeviceBindingTable() 
{
   ["zdo mgmt-bind 0x${device.deviceNetworkId} 0","delay 200"]
}

//Simulate a button push
def push() {
	sendEvent(parseButtonMessage("80"))
}

//Simulate a button doubleTap
def doubleTap() {
	sendEvent(parseButtonMessage("81"))
}

//Simulate a button hold
def hold() {
	sendEvent(parseButtonMessage("82"))
}

//Simulate a reset button push
def pushReset() {
	sendEvent(parseButtonMessage("CD"))
}

//Reset the batteryLastReplaced date to current date
def resetBatteryReplacedDate(paired) {
	def newlyPaired = paired ? " for newly paired sensor" : ""
	sendEvent(name: "batteryLastReplaced", value: new Date().toLocaleString(), descriptionText: "Set battery last replaced date")
	displayInfoLog("Setting Battery Last Replaced to current date${newlyPaired}")
}

def init() {
	//if (!device.currentState('batteryLastReplaced')?.value)
	//	resetBatteryReplacedDate(true)
    
    if (!state.numberOfButtons) {
		sendEvent(name: "numberOfButtons", value: 6)
		displayInfoLog("Number of buttons set to 6")
		state.numberOfButtons = 6
	}
	//sendEvent(name: "numberOfButtons", value: 6)
}