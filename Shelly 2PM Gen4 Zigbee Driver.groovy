/**
 *  Shelly 2PM Gen4 Zigbee Driver - Ultimate Edition with Energy Support
 *
 *  Copyright 2025 
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
 *  IMPORTANT SETUP INSTRUCTIONS:
 *  1. The Shelly 2PM Gen4 comes with Matter firmware by default
 *  2. To switch to Zigbee: Press the button 5 consecutive times
 *  3. To enter inclusion mode: Press the button 3 consecutive times  
 *  4. The device will stay in inclusion mode for 2 minutes
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2025-08-17  0.0.1          Clean version with proper number formatting
 *    2025-08-17  0.0.2          Added complete energy monitoring support
 *    2025-08-17  0.0.3          Optimized logging and child device persistence
 *    2025-08-24  0.1.0          First Beta release
 * 
 */

import hubitat.zigbee.zcl.DataType

metadata {
    definition(
        name: "Shelly 2PM Gen4 Zigbee Driver",
        namespace: "shelly",
        author: "Joao+Claude",
        importUrl: ""
    ) {
        capability "Switch"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "VoltageMeasurement"
        capability "CurrentMeter" 
        capability "Refresh"
        capability "Configuration"
        capability "Initialize"
        
        // Dual relay attributes (device has 2 functional endpoints)
        attribute "switch1", "string"
        attribute "switch2", "string"
        attribute "power1", "number"
        attribute "power2", "number"
        attribute "current1", "number"
        attribute "current2", "number"
        attribute "voltage", "number"
        attribute "energy1", "number"
        attribute "energy2", "number"
        
        // *** ALTERNATIVE: Calculate power factor from available data ***
        attribute "powerFactor", "number"
        attribute "powerFactor1", "number"
        attribute "powerFactor2", "number"
        attribute "apparentPower", "number"
        attribute "apparentPower1", "number"
        attribute "apparentPower2", "number"
        
        // Device health
        attribute "healthStatus", "enum", ["offline", "online"]
        
        // Commands for individual relay control
        command "on1"
        command "off1"
        command "on2" 
        command "off2"
        command "toggle1"
        command "toggle2"
        
        // Additional commands for testing
        command "testBasicComm"
        command "discoverEndpoints"
        command "testEndpoint2"
        
        // Energy and diagnostic commands
        command "diagnoseEnergy"
        command "configureEnergyReporting"
        command "resetEnergy"
        command "diagnosePower"
        command "fixPowerFormatting"
        command "syncChildDevices"
        command "clearLogState"
        command "maintainChildStates"
        command "debugPower2"
        command "fixPower2Now"
        command "diagnoseElectricalAll"
        command "calculateAllPowerFactors"
        command "deepScanAllClusters"
        command "testShellySpecificAttrs"
        
        // Fingerprint for device identification
        fingerprint profileId: "0104", 
                    inClusters: "0000,0003,0004,0005,0006,0B04,0702", 
                    outClusters: "0019", 
                    manufacturer: "Shelly", 
                    model: "2PM", 
                    deviceJoinName: "Shelly 2PM Gen4"
    }
    
    preferences {
        section("Device Configuration") {
            input name: "powerReportDelta", type: "number", title: "Power Report Delta (W)", 
                  description: "Minimum power change to trigger report", defaultValue: 1, range: "1..100"
            input name: "reportingInterval", type: "number", title: "Reporting Interval (seconds)", 
                  description: "Maximum time between reports", defaultValue: 300, range: "10..3600"
            input name: "energyReportInterval", type: "number", title: "Energy Report Interval (seconds)", 
                  description: "Maximum time between energy reports", defaultValue: 300, range: "60..3600"
        }
        section("Advanced") {
            input name: "createChildDevices", type: "bool", title: "Create Child Devices for Individual Relays", 
                  description: "Creates separate devices for Relay 1 and Relay 2", defaultValue: true
            input name: "logEnable", type: "bool", title: "Enable Debug Logging", defaultValue: false
            input name: "txtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: true
        }
    }
}

// Zigbee cluster definitions
private getCLUSTER_BASIC() { 0x0000 }
private getCLUSTER_IDENTIFY() { 0x0003 }
private getCLUSTER_GROUPS() { 0x0004 }
private getCLUSTER_SCENES() { 0x0005 }
private getCLUSTER_ON_OFF() { 0x0006 }
private getCLUSTER_SIMPLE_METERING() { 0x0702 }
private getCLUSTER_ELECTRICAL_MEASUREMENT() { 0x0B04 }

def installed() {
    logInfo "Installing Shelly 2PM Gen4..."
    sendEvent(name: "energy", value: 0, unit: "kWh")
    sendEvent(name: "energy1", value: 0, unit: "kWh")
    sendEvent(name: "energy2", value: 0, unit: "kWh")
    sendEvent(name: "powerFactor", value: 1.0)
    sendEvent(name: "powerFactor1", value: 1.0)
    sendEvent(name: "powerFactor2", value: 1.0)
    sendEvent(name: "apparentPower", value: 0, unit: "VA")
    sendEvent(name: "apparentPower1", value: 0, unit: "VA")
    sendEvent(name: "apparentPower2", value: 0, unit: "VA")
    initialize()
}

def updated() {
    logInfo "Updating Shelly 2PM Gen4..."
    unschedule()
    initialize()
}

def initialize() {
    logInfo "Initializing Shelly 2PM Gen4..."
    
    unschedule()
    
    if (logEnable) {
        runIn(1800, logsOff)
    }
    
    runIn(3, configure)
    
    if (createChildDevices) {
        createChildDevices()
        runIn(5, "syncChildDevices")
    } else {
        removeChildDevices()  
    }
    
    runEvery5Minutes(refresh)
    
    if (createChildDevices) {
        runEvery1Minute("maintainChildStates")
    }
    
    sendEvent(name: "healthStatus", value: "online")
    logInfo "Initialization complete"
}

def configure() {
    logInfo "Configuring Shelly 2PM Gen4..."
    
    List<String> cmds = []
    
    // Bind clusters for both endpoints
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0B04 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0B04 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0702 {${device.zigbeeId}} {}"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0702 {${device.zigbeeId}} {}"
    
    // Configure power reporting
    def powerInterval = reportingInterval ?: 300
    cmds += "he cr 0x${device.deviceNetworkId} 0x01 0x0B04 0x050B 0x29 10 ${powerInterval} 1 {}"
    cmds += "he cr 0x${device.deviceNetworkId} 0x02 0x0B04 0x050B 0x29 10 ${powerInterval} 1 {}"
    
    // Configure energy reporting
    def energyInterval = energyReportInterval ?: 300
    cmds += "he cr 0x${device.deviceNetworkId} 0x01 0x0702 0x0000 0x25 60 ${energyInterval} 1 {}"
    cmds += "he cr 0x${device.deviceNetworkId} 0x02 0x0702 0x0000 0x25 60 ${energyInterval} 1 {}"
    
    cmds += refresh()
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    return cmds
}

def calculateAllPowerFactors() {
    logInfo "=== CALCULANDO FATORES DE POTÊNCIA ==="
    
    // Force calculation for both endpoints
    calculatePowerFactorForEndpoint(1)
    calculatePowerFactorForEndpoint(2)
    
    // Display current values
    def voltage = device.currentValue("voltage") ?: 0
    def current1 = device.currentValue("current1") ?: 0
    def current2 = device.currentValue("current2") ?: 0
    def power1 = device.currentValue("power1") ?: 0
    def power2 = device.currentValue("power2") ?: 0
    def apparentPower1 = device.currentValue("apparentPower1") ?: 0
    def apparentPower2 = device.currentValue("apparentPower2") ?: 0
    def powerFactor1 = device.currentValue("powerFactor1") ?: 1.0
    def powerFactor2 = device.currentValue("powerFactor2") ?: 1.0
    def overallPF = device.currentValue("powerFactor") ?: 1.0
    
    logInfo "Voltage: ${voltage}V"
    logInfo "Relay 1: I=${current1}A, P=${power1}W, S=${apparentPower1}VA, PF=${powerFactor1}"
    logInfo "Relay 2: I=${current2}A, P=${power2}W, S=${apparentPower2}VA, PF=${powerFactor2}"
    logInfo "Overall Power Factor: ${overallPF}"
    
    logInfo "✅ Power factors calculated and updated!"
}

def deepScanAllClusters() {
    logInfo "=== VARREDURA PROFUNDA DE TODOS OS CLUSTERS ==="
    
    List<String> cmds = []
    
    // Test common clusters only - simplified
    def clustersToTest = [
        "0000": "Basic",
        "0702": "Simple Metering",
        "0B04": "Electrical Measurement",
        "0B05": "Diagnostics"
    ]
    
    logInfo "Testing ${clustersToTest.size()} clusters..."
    
    clustersToTest.each { clusterHex, description ->
        // Try some common attributes in each cluster
        for (int attr = 0; attr <= 0x10; attr++) {
            def attrHex = String.format("%04X", attr)
            cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x${clusterHex} 0x${attrHex} {}"
        }
    }
    
    logInfo "Generated ${cmds.size()} discovery commands - sending immediately"
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    return cmds
}

def testShellySpecificAttrs() {
    logInfo "=== TESTANDO ATRIBUTOS ESPECÍFICOS SHELLY ==="
    
    List<String> cmds = []
    
    // Focus on electrical cluster extended ranges where Shelly might hide data
    def ranges = [
        [0x0600, 0x0610], // Range where frequency might be
        [0x0700, 0x0710], // Range where power factor might be
        [0x0800, 0x0810]  // Other possible range
    ]
    
    ranges.each { range ->
        for (int attr = range[0]; attr <= range[1]; attr++) {
            def attrHex = String.format("%04X", attr)
            cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x${attrHex} {}"
            cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x${attrHex} {}"
        }
    }
    
    logInfo "Testing ${cmds.size()} Shelly-specific attributes - sending immediately"
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    return cmds
}

def sendCommandsInBatches(List<String> commands, int batchSize, String description) {
    logInfo "Sending ${commands.size()} commands in batches of ${batchSize} (${description})"
    
    for (int i = 0; i < commands.size(); i += batchSize) {
        def batch = commands.subList(i, Math.min(i + batchSize, commands.size()))
        
        if (i == 0) {
            // Send first batch immediately
            sendHubCommand(new hubitat.device.HubMultiAction(batch, hubitat.device.Protocol.ZIGBEE))
            logInfo "Sent first batch, size: ${batch.size()}"
        } else {
            // Schedule subsequent batches with delays
            def delay = (i / batchSize) * 2 + 1
            def batchNumber = (i / batchSize) + 1
            
            // Store batch data in state for the scheduled method
            state["delayedBatch_${batchNumber}"] = [
                commands: batch,
                batchNumber: batchNumber
            ]
            
            runIn(delay, "sendDelayedBatch${batchNumber}")
            logInfo "Scheduled batch #${batchNumber} for ${delay} seconds, size: ${batch.size()}"
        }
    }
}

// Create individual methods for each batch to avoid parameter issues
def sendDelayedBatch1() {
    def data = state["delayedBatch_1"]
    if (data) {
        sendHubCommand(new hubitat.device.HubMultiAction(data.commands, hubitat.device.Protocol.ZIGBEE))
        logInfo "Sent delayed batch #${data.batchNumber}, size: ${data.commands.size()}"
        state.remove("delayedBatch_1")
    }
}

def sendDelayedBatch2() {
    def data = state["delayedBatch_2"]
    if (data) {
        sendHubCommand(new hubitat.device.HubMultiAction(data.commands, hubitat.device.Protocol.ZIGBEE))
        logInfo "Sent delayed batch #${data.batchNumber}, size: ${data.commands.size()}"
        state.remove("delayedBatch_2")
    }
}

def sendDelayedBatch3() {
    def data = state["delayedBatch_3"]
    if (data) {
        sendHubCommand(new hubitat.device.HubMultiAction(data.commands, hubitat.device.Protocol.ZIGBEE))
        logInfo "Sent delayed batch #${data.batchNumber}, size: ${data.commands.size()}"
        state.remove("delayedBatch_3")
    }
}

def sendDelayedBatch4() {
    def data = state["delayedBatch_4"]
    if (data) {
        sendHubCommand(new hubitat.device.HubMultiAction(data.commands, hubitat.device.Protocol.ZIGBEE))
        logInfo "Sent delayed batch #${data.batchNumber}, size: ${data.commands.size()}"
        state.remove("delayedBatch_4")
    }
}

def sendDelayedBatch5() {
    def data = state["delayedBatch_5"]
    if (data) {
        sendHubCommand(new hubitat.device.HubMultiAction(data.commands, hubitat.device.Protocol.ZIGBEE))
        logInfo "Sent delayed batch #${data.batchNumber}, size: ${data.commands.size()}"
        state.remove("delayedBatch_5")
    }
}

// *** HELPER FUNCTIONS TO DETECT FREQUENCY AND POWER FACTOR ***
def checkIfFrequency(long rawValue, int attrId) {
    def candidates = [
        rawValue,           // Already in Hz
        rawValue / 10.0,    // In deciHz (dHz)
        rawValue / 100.0,   // In centiHz (cHz)  
        rawValue / 1000.0,  // In milliHz (mHz)
        rawValue / 10000.0  // In 0.1 mHz
    ]
    
    for (candidate in candidates) {
        if (candidate >= 45.0 && candidate <= 65.0) {
            return Math.round(candidate * 10) / 10.0
        }
    }
    
    return 0.0  // Not a valid frequency
}

def checkIfPowerFactor(long rawValue, int attrId) {
    def candidates = [
        rawValue / 1.0,      // Already as decimal (0-1)
        rawValue / 10.0,     // As tenths (0-10)
        rawValue / 100.0,    // As percentage (0-100)
        rawValue / 1000.0,   // As thousandths (0-1000)
        rawValue / 10000.0   // As ten-thousandths (0-10000)
    ]
    
    for (candidate in candidates) {
        if (candidate >= 0.0 && candidate <= 1.0) {
            return Math.round(candidate * 1000) / 1000.0
        }
    }
    
    return -1.0  // Not a valid power factor
}

def refresh() {
    logInfo "Refreshing Shelly 2PM Gen4..."
    
    List<String> cmds = []
    
    // Read current switch states
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000 {}"
    
    // Read electrical measurements
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x050B {}"  // Current EP1
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x050B {}"  // Current EP2
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x0505 {}"  // Voltage
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x0300 {}"  // AC Frequency
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x0510 {}"  // Power Factor EP1
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x0510 {}"  // Power Factor EP2
    
    // Read energy measurements
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0702 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0000 {}"
    
    // Read basic device information
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0000 0x0004 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0000 0x0005 {}"
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    return cmds
}

// Switch control commands
def on() {
    logInfo "Turning on both relays"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x01 {}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    
    sendEvent(name: "switch1", value: "on")
    sendEvent(name: "switch2", value: "on")
    sendEvent(name: "switch", value: "on")
    updateChildDevice(1, "switch", "on")
    updateChildDevice(2, "switch", "on")
    
    return cmds
}

def off() {
    logInfo "Turning off both relays"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x00 {}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    
    sendEvent(name: "switch1", value: "off")
    sendEvent(name: "switch2", value: "off")
    sendEvent(name: "switch", value: "off")
    updateChildDevice(1, "switch", "off")
    updateChildDevice(2, "switch", "off")
    
    return cmds
}

def on1() {
    logInfo "Turning on relay 1 (endpoint 1)"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x01 {}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    sendEvent(name: "switch1", value: "on")
    updateMainSwitchState()
    updateChildDevice(1, "switch", "on")
    return cmds
}

def off1() {
    logInfo "Turning off relay 1 (endpoint 1)"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0x00 {}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    sendEvent(name: "switch1", value: "off")
    updateMainSwitchState()
    updateChildDevice(1, "switch", "off")
    return cmds
}

def on2() {
    logInfo "Turning on relay 2 (endpoint 2)"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x01 {}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    sendEvent(name: "switch2", value: "on")
    updateMainSwitchState()
    updateChildDevice(2, "switch", "on")
    return cmds
}

def off2() {
    logInfo "Turning off relay 2 (endpoint 2)"
    def cmds = []
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x00 {}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    sendEvent(name: "switch2", value: "off")
    updateMainSwitchState()
    updateChildDevice(2, "switch", "off")
    return cmds
}

private updateMainSwitchState() {
    def switch1State = device.currentValue("switch1") ?: "off"
    def switch2State = device.currentValue("switch2") ?: "off"
    def mainState = (switch1State == "on" || switch2State == "on") ? "on" : "off"
    sendEvent(name: "switch", value: mainState)
}

def toggle1() {
    def currentState = device.currentValue("switch1")
    if (currentState == "on") {
        return off1()
    } else {
        return on1()
    }
}

def toggle2() {
    def currentState = device.currentValue("switch2")
    if (currentState == "on") {
        return off2()
    } else {
        return on2()
    }
}

def refreshAfterCommand() {
    logInfo "Refreshing state after command..."
    def cmds = []
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0006 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000 {}"
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

// Test commands
def testBasicComm() {
    logInfo "Testing communication with real device endpoints..."
    def cmds = []
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0000 0x0004 {}"
    cmds += "delay 1000"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0000 0x0005 {}"
    cmds += "delay 1000"
    
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    return cmds
}

def testEndpoint2() {
    logInfo "Testing endpoint 2 communication specifically..."
    def cmds = []
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000 {}"
    cmds += "delay 1000"
    cmds += "he cmd 0x${device.deviceNetworkId} 0x02 0x0006 0x02 {}"
    cmds += "delay 1000"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0006 0x0000 {}"
    
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    return cmds
}

def discoverEndpoints() {
    logInfo "Discovering device endpoints and clusters..."
    def cmds = []
    
    for (int ep = 0; ep <= 5; ep++) {
        cmds += "he rattr 0x${device.deviceNetworkId} 0x0${ep} 0x0000 0x0004 {}"
        cmds += "delay 500"
    }
    
    cmds += "zdo active 0x${device.deviceNetworkId}"
    cmds += "delay 1000"
    
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    return cmds
}

// Energy diagnostic commands
def diagnoseEnergy() {
    logInfo "=== DIAGNÓSTICO DE ENERGIA INICIADO ==="
    
    List<String> cmds = []
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0702 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0702 0x0301 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0301 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0702 0x0302 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0302 {}"
    
    logInfo "Enviando ${cmds.size()} comandos de diagnóstico..."
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    return cmds
}

def configureEnergyReporting() {
    logInfo "Configurando reporte automático de energia..."
    
    List<String> cmds = []
    def energyInterval = energyReportInterval ?: 300
    
    cmds += "he cr 0x${device.deviceNetworkId} 0x01 0x0702 0x0000 0x25 60 ${energyInterval} 1 {}"
    cmds += "he cr 0x${device.deviceNetworkId} 0x02 0x0702 0x0000 0x25 60 ${energyInterval} 1 {}"
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    return cmds
}

def resetEnergy() {
    logInfo "Resetando contadores de energia no Hubitat..."
    
    sendEvent(name: "energy", value: 0, unit: "kWh")
    sendEvent(name: "energy1", value: 0, unit: "kWh")
    sendEvent(name: "energy2", value: 0, unit: "kWh")
    
    updateChildDevice(1, "energy", 0)
    updateChildDevice(2, "energy", 0)
    
    logInfo "Contadores de energia resetados"
}

def fixPowerFormatting() {
    logInfo "=== CORRIGINDO FORMATAÇÃO DE POTÊNCIA ==="
    
    def power1 = device.currentValue("power1")
    def power2 = device.currentValue("power2")
    
    logInfo "Current power1: ${power1} (${power1?.class})"
    logInfo "Current power2: ${power2} (${power2?.class})"
    
    if (power1 != null) {
        def cleanPower1 = String.format("%.1f", power1 as Double) as Double
        sendEvent(name: "power1", value: cleanPower1, unit: "W")
        logInfo "Fixed power1: ${cleanPower1}"
    }
    
    if (power2 != null) {
        def cleanPower2 = String.format("%.1f", power2 as Double) as Double
        sendEvent(name: "power2", value: cleanPower2, unit: "W")
        logInfo "Fixed power2: ${cleanPower2}"
    }
    
    def totalPower = String.format("%.1f", (power1 ?: 0) + (power2 ?: 0)) as Double
    sendEvent(name: "power", value: totalPower, unit: "W")
    logInfo "Fixed total power: ${totalPower}"
}

def diagnosePower() {
    logInfo "=== DIAGNÓSTICO DE POTÊNCIA INICIADO ==="
    
    List<String> cmds = []
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0702 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0702 0x0001 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0001 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x050B {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x050B {}"  // Current EP2
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x050D {}"  // Power EP1
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x050D {}"  // Power EP2
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x0300 {}"  // AC Frequency
    cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x0510 {}"  // Power Factor EP1
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x0510 {}"  // Power Factor EP2
    
    logInfo "Enviando ${cmds.size()} comandos de diagnóstico de potência..."
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    return cmds
}

def debugPower2() {
    logInfo "=== DEBUG POWER2 ==="
    
    def power2Current = device.currentValue("power2")
    def power2State = state["lastLoggedPower2"]
    
    logInfo "Current Power2 value: ${power2Current}"
    logInfo "Last logged Power2: ${power2State}"
    logInfo "Switch2 state: ${device.currentValue('switch2')}"
    
    List<String> cmds = []
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0000 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0702 0x0001 {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x050B {}"
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x050D {}"
    
    logInfo "Sending ${cmds.size()} commands specifically for endpoint 2..."
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    runIn(5, "checkPower2Results")
    return cmds
}

def checkPower2Results() {
    def power2 = device.currentValue("power2") ?: 0
    def current2 = device.currentValue("current2") ?: 0
    def switch2 = device.currentValue("switch2") ?: "off"
    
    logInfo "=== POWER2 RESULTS CHECK ==="
    logInfo "Power2: ${power2}W"
    logInfo "Current2: ${current2}A"
    logInfo "Switch2: ${switch2}"
    
    if (power2 == 0 && current2 > 0) {
        logInfo "WARNING: Current2 shows ${current2}A but Power2 is 0W - possible parsing issue"
    }
    
    if (switch2 == "on" && power2 == 0 && current2 == 0) {
        logInfo "WARNING: Relay 2 is ON but shows no power/current - checking communication"
    }
}

def fixPower2Now() {
    logInfo "=== MANUAL POWER2 FIX ==="
    
    def current2 = device.currentValue("current2") ?: 0
    def voltage = device.currentValue("voltage") ?: 220
    def switch2 = device.currentValue("switch2") ?: "off"
    
    logInfo "Current state: switch2=${switch2}, current2=${current2}A, voltage=${voltage}V"
    
    if (switch2 == "on" && current2 > 0.001) {
        def calculatedPower = Math.round((voltage * current2) * 10) / 10.0
        
        logInfo "Calculating: ${voltage}V × ${current2}A = ${calculatedPower}W"
        
        if (calculatedPower >= 0.1) {
            sendEvent(name: "power2", value: calculatedPower, unit: "W")
            updateChildDevice(2, "power", calculatedPower)
            
            // Update total power
            def power1 = device.currentValue("power1") ?: 0
            def totalPower = Math.round((power1 + calculatedPower) * 10) / 10.0
            sendEvent(name: "power", value: totalPower, unit: "W")
            
            logInfo "✅ FIXED: Power2 = ${calculatedPower}W, Total = ${totalPower}W"
        } else {
            logInfo "❌ Calculated power too low: ${calculatedPower}W"
        }
    } else {
        logInfo "❌ Cannot fix: switch2=${switch2}, current2=${current2}A"
    }
}

def diagnoseElectricalAll() {
    logInfo "=== DIAGNÓSTICO ELÉTRICO COMPLETO ==="
    
    List<String> cmds = []
    
    // Test common electrical measurement attributes  
    def electricalAttrs = [
        "0300": "AC Frequency",
        "0301": "Power Multiplier", 
        "0302": "Power Divisor",
        "0303": "Current Multiplier",
        "0304": "Current Divisor",
        "0400": "AC Voltage Multiplier",
        "0401": "AC Voltage Divisor",
        "0500": "Line Current",
        "0501": "Active Current",
        "0502": "Reactive Current", 
        "0503": "RMS Voltage",
        "0504": "RMS Voltage Min",
        "0505": "RMS Voltage Max",
        "0506": "RMS Current",
        "0507": "RMS Current Min",
        "0508": "RMS Current Max",
        "0509": "Active Power",
        "050A": "Active Power Min",
        "050B": "Active Power Max",
        "050C": "Reactive Power",
        "050D": "Apparent Power",
        "050E": "Power Factor",
        "050F": "Average RMS Voltage Period",
        "0510": "Average RMS Under Voltage Counter"
    ]
    
    logInfo "Testing ${electricalAttrs.size()} electrical attributes..."
    
    // Test on both endpoints - simplified version
    electricalAttrs.each { attr, description ->
        cmds += "he rattr 0x${device.deviceNetworkId} 0x01 0x0B04 0x${attr} {}"
        cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0B04 0x${attr} {}"
    }
    
    logInfo "Generated ${cmds.size()} commands - sending immediately"
    
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    }
    
    return cmds
}

def sendNextBatch(data) {
    // Legacy method - now using individual batch methods
    logInfo "Legacy sendNextBatch called - using new batch system"
}

// *** ADDED: Calculate power factor from available data ***
def calculatePowerFactorForEndpoint(int endpoint) {
    def voltage = device.currentValue("voltage") ?: 220
    def current = device.currentValue("current${endpoint}") ?: 0
    def activePower = device.currentValue("power${endpoint}") ?: 0
    
    if (voltage > 0 && current > 0.001 && activePower > 0.1) {
        // Calculate apparent power: S = V × I
        def apparentPower = voltage * current
        apparentPower = Math.round(apparentPower * 10) / 10.0
        
        // Calculate power factor: PF = P / S
        def powerFactor = activePower / apparentPower
        powerFactor = Math.round(powerFactor * 1000) / 1000.0
        
        // Ensure within valid range
        if (powerFactor > 1.0) powerFactor = 1.0
        if (powerFactor < 0.0) powerFactor = 0.0
        
        // Update individual values
        sendEvent(name: "apparentPower${endpoint}", value: apparentPower, unit: "VA")
        sendEvent(name: "powerFactor${endpoint}", value: powerFactor)
        
        // Update child device
        updateChildDevice(endpoint, "apparentPower", apparentPower)
        updateChildDevice(endpoint, "powerFactor", powerFactor)
        
        // Calculate total values
        def apparentPower1 = endpoint == 1 ? apparentPower : (device.currentValue("apparentPower1") ?: 0)
        def apparentPower2 = endpoint == 2 ? apparentPower : (device.currentValue("apparentPower2") ?: 0)
        def totalApparentPower = apparentPower1 + apparentPower2
        sendEvent(name: "apparentPower", value: totalApparentPower, unit: "VA")
        
        // Calculate overall power factor (weighted average)
        def activePower1 = device.currentValue("power1") ?: 0
        def activePower2 = device.currentValue("power2") ?: 0
        def totalActivePower = activePower1 + activePower2
        
        def overallPF = 1.0
        if (totalApparentPower > 0) {
            overallPF = totalActivePower / totalApparentPower
            overallPF = Math.round(overallPF * 1000) / 1000.0
            if (overallPF > 1.0) overallPF = 1.0
        }
        sendEvent(name: "powerFactor", value: overallPF)
        
        // Log significant changes
        def lastLoggedPF = state["lastLoggedPF${endpoint}"] ?: 0
        if (Math.abs(powerFactor - lastLoggedPF) >= 0.01) {
            logInfo "Relay ${endpoint}: PF=${powerFactor}, S=${apparentPower}VA, P=${activePower}W"
            state["lastLoggedPF${endpoint}"] = powerFactor
        }
        
        if (logEnable) logDebug "Endpoint ${endpoint}: V=${voltage}V, I=${current}A, P=${activePower}W, S=${apparentPower}VA, PF=${powerFactor}"
    }
}

def clearLogState() {
    logInfo "Clearing log state - will reset logging thresholds"
    
    state.remove("lastLoggedEnergy1")
    state.remove("lastLoggedEnergy2") 
    state.remove("lastLoggedPower1")
    state.remove("lastLoggedPower2")
    state.remove("lastLoggedCurrent1")
    state.remove("lastLoggedCurrent2")
    state.remove("lastLoggedVoltage")
    state.remove("lastLoggedFrequency")
    state.remove("lastLoggedPF1")
    state.remove("lastLoggedPF2")
    
    logInfo "Log state cleared - next readings will be logged"
}

// Parse incoming messages
def parse(String description) {
    if (logEnable) logDebug "Parsing: ${description}"
    
    def result = []
    
    if (!description) {
        if (logEnable) logDebug "Empty description received"
        return result
    }
    
    try {
        if (description.startsWith('read attr')) {
            def descMap = zigbee.parseDescriptionAsMap(description)
            if (descMap && descMap.cluster && descMap.cluster != "null") {
                if (logEnable) logDebug "Received response: cluster=${descMap?.cluster}, endpoint=${descMap?.endpoint}, attr=${descMap?.attrId}, value=${descMap?.value}"
                result = processZigbeeMessage(descMap)
            }
        } else if (description.startsWith('catchall')) {
            if (logEnable) logDebug "Received catchall: ${description}"
            def descMap = zigbee.parseDescriptionAsMap(description)
            if (descMap && descMap.cluster && descMap.cluster != "null") {
                result = processZigbeeMessage(descMap)
            }
        } else if (description.startsWith('zdo-info')) {
            if (logEnable) logDebug "ZDO info: ${description}"
        } else {
            if (logEnable) logDebug "Received message: ${description}"
        }
    } catch (Exception e) {
        if (logEnable) logDebug "Error parsing message: ${e.message}, description: ${description}"
    }
    
    return result
}

private processZigbeeMessage(Map descMap) {
    def result = []
    
    if (!descMap || !descMap.cluster || descMap.cluster == "null" || descMap.cluster == "") {
        if (logEnable) logDebug "Invalid or empty descMap: ${descMap}"
        return result
    }
    
    def clusterInt = null
    try {
        if (descMap.clusterInt != null) {
            clusterInt = descMap.clusterInt
        } else if (descMap.cluster && descMap.cluster != "null" && descMap.cluster != "") {
            clusterInt = Integer.parseInt(descMap.cluster, 16)
        } else {
            if (logEnable) logDebug "No valid cluster in descMap: ${descMap}"
            return result
        }
    } catch (Exception e) {
        if (logEnable) logDebug "Error parsing cluster: ${descMap.cluster}, error: ${e.message}"
        return result
    }
    
    def attrInt = null
    try {
        if (descMap.attrInt != null) {
            attrInt = descMap.attrInt
        } else if (descMap.attrId && descMap.attrId != "null" && descMap.attrId != "") {
            attrInt = Integer.parseInt(descMap.attrId, 16)
        }
    } catch (Exception e) {
        if (logEnable) logDebug "Error parsing attribute: ${descMap.attrId}, error: ${e.message}"
    }
    
    switch (clusterInt) {
        case CLUSTER_BASIC:
            if (attrInt == 0x0004 && descMap.value && descMap.value != "null") {
                try {
                    def manufacturer = new String(descMap.value.decodeHex())
                    logInfo "Manufacturer: ${manufacturer}"
                } catch (Exception e) {
                    logInfo "Manufacturer (raw): ${descMap.value}"
                }
            } else if (attrInt == 0x0005 && descMap.value && descMap.value != "null") {
                try {
                    def model = new String(descMap.value.decodeHex())
                    logInfo "Model: ${model}"
                } catch (Exception e) {
                    logInfo "Model (raw): ${descMap.value}"
                }
            }
            break
            
        case CLUSTER_ON_OFF:
            result = processOnOffMessage(descMap)
            break
            
        case CLUSTER_SIMPLE_METERING:
            if (attrInt != null && descMap.value && descMap.value != "null") {
                result = processPowerAndEnergyMessage(descMap)
            } else {
                if (logEnable) logDebug "Skipping metering cluster - insufficient data"
            }
            break
            
        case CLUSTER_ELECTRICAL_MEASUREMENT:
            if (attrInt != null && descMap.value && descMap.value != "null") {
                result = processElectricalMessage(descMap)
            } else {
                if (logEnable) logDebug "Skipping electrical cluster - insufficient data"
            }
            break
            
        default:
            if (logEnable) logDebug "Unhandled cluster: ${descMap.cluster} (${clusterInt}), attr: ${descMap.attrId}, value: ${descMap.value}"
            break
    }
    
    sendEvent(name: "healthStatus", value: "online")
    return result
}

private processOnOffMessage(Map descMap) {
    def endpoint = 1
    def attrInt = null
    
    try {
        endpoint = descMap.endpoint ? Integer.parseInt(descMap.endpoint, 16) : 1
    } catch (Exception e) {
        endpoint = 1
    }
    
    try {
        attrInt = descMap.attrInt ?: (descMap.attrId ? Integer.parseInt(descMap.attrId, 16) : null)
    } catch (Exception e) {
        attrInt = null
    }
    
    if (logEnable) logDebug "ON/OFF cluster: endpoint=${endpoint}, attr=${attrInt}, value=${descMap.value}"
    
    if (attrInt == 0x0000 && descMap.value && descMap.value != "null") {
        def switchState = descMap.value == "01" ? "on" : "off"
        
        sendEvent(name: "switch${endpoint}", value: switchState)
        
        def switch1State = device.currentValue("switch1") ?: "off"
        def switch2State = device.currentValue("switch2") ?: "off"
        def mainState = (switch1State == "on" || switch2State == "on") ? "on" : "off"
        sendEvent(name: "switch", value: mainState)
        
        updateChildDevice(endpoint, "switch", switchState)
        
        if (txtEnable) logInfo "Relay ${endpoint} is ${switchState}"
        
        return [createEvent(name: "switch${endpoint}", value: switchState)]
    }
    
    if (descMap.command && descMap.data) {
        if (logEnable) logDebug "ON/OFF command response: endpoint=${endpoint}, command=${descMap.command}"
        
        if (descMap.command == "0B" && descMap.data) {
            if (logEnable) logDebug "Device acknowledged ON/OFF command for endpoint ${endpoint}"
            runIn(1, "refreshAfterCommand")
        }
    }
    
    if (descMap.data && descMap.data.size() > 0) {
        try {
            def lastByte = descMap.data[-1]
            if (logEnable) logDebug "Catchall last byte: ${lastByte}"
            
            if (lastByte == "00" || lastByte == "01") {
                def switchState = lastByte == "01" ? "on" : "off"
                sendEvent(name: "switch${endpoint}", value: switchState)
                
                def switch1State = device.currentValue("switch1") ?: "off"
                def switch2State = device.currentValue("switch2") ?: "off"
                def mainState = (switch1State == "on" || switch2State == "on") ? "on" : "off"
                sendEvent(name: "switch", value: mainState)
                
                updateChildDevice(endpoint, "switch", switchState)
                if (txtEnable) logInfo "Relay ${endpoint} turned ${switchState} (from catchall)"
                return [createEvent(name: "switch${endpoint}", value: switchState)]
            }
        } catch (Exception e) {
            if (logEnable) logDebug "Error parsing ON/OFF catchall data: ${e.message}"
        }
    }
    
    return []
}

private processPowerAndEnergyMessage(Map descMap) {
    if (!descMap || !descMap.endpoint || !descMap.cluster || 
        !descMap.attrId || descMap.attrId == "null" || descMap.attrId == "") {
        if (logEnable) logDebug "Skipping metering message - insufficient data"
        return []
    }
    
    def endpoint = 1
    def attrInt = null
    
    try {
        endpoint = descMap.endpoint ? Integer.parseInt(descMap.endpoint, 16) : 1
    } catch (Exception e) {
        endpoint = 1
    }
    
    try {
        if (descMap.attrInt != null) {
            attrInt = descMap.attrInt
        } else if (descMap.attrId && descMap.attrId != "null" && descMap.attrId != "") {
            attrInt = Integer.parseInt(descMap.attrId, 16)
        } else {
            return []
        }
    } catch (Exception e) {
        if (logEnable) logDebug "Error parsing metering attribute: ${descMap.attrId}"
        return []
    }
    
    if (descMap.value && descMap.value != "null" && descMap.value != "") {
        try {
            def rawValue = Long.parseLong(descMap.value, 16)
            if (logEnable) logDebug "Metering raw: ${descMap.value} hex = ${rawValue} decimal, attr: ${attrInt}, endpoint: ${endpoint}"
            
            switch (attrInt) {
                case 0x0000:
                    if (logEnable) logDebug "Processing metering attr 0x0000: rawValue=${rawValue}, endpoint=${endpoint}"
                    
                    if (rawValue > 100000) {
                        // Energy processing
                        if (logEnable) logDebug "Detected ENERGY data: ${rawValue}"
                        def energy = rawValue / 1000000.0
                        energy = Math.round(energy * 1000000) / 1000000.0
                        
                        sendEvent(name: "energy${endpoint}", value: energy, unit: "kWh")
                        
                        def energy1 = endpoint == 1 ? energy : (device.currentValue("energy1") ?: 0)
                        def energy2 = endpoint == 2 ? energy : (device.currentValue("energy2") ?: 0)
                        def totalEnergy = Math.round((energy1 + energy2) * 1000000) / 1000000.0
                        sendEvent(name: "energy", value: totalEnergy, unit: "kWh")
                        
                        updateChildDevice(endpoint, "energy", energy)
                        
                        def lastLoggedEnergy = state["lastLoggedEnergy${endpoint}"] ?: 0
                        if (Math.abs(energy - lastLoggedEnergy) >= 0.001) {
                            if (txtEnable) logInfo "Relay ${endpoint} energy: ${energy} kWh"
                            state["lastLoggedEnergy${endpoint}"] = energy
                        }
                        return [createEvent(name: "energy${endpoint}", value: energy, unit: "kWh")]
                    } else {
                        // Power processing
                        if (logEnable) logDebug "Detected POWER data: ${rawValue}"
                        def powerDouble = rawValue / 1000.0
                        def power = Double.parseDouble(String.format("%.1f", powerDouble))
                        
                        if (logEnable) logDebug "Calculated power: final=${power}"
                        
                        sendEvent(name: "power${endpoint}", value: power, unit: "W")
                        
                        def power1 = endpoint == 1 ? power : (device.currentValue("power1") ?: 0)
                        def power2 = endpoint == 2 ? power : (device.currentValue("power2") ?: 0)
                        def totalPowerDouble = power1 + power2
                        def totalPower = Double.parseDouble(String.format("%.1f", totalPowerDouble))
                        
                        sendEvent(name: "power", value: totalPower, unit: "W")
                        
                        updateChildDevice(endpoint, "power", power)
                        
                        if (endpoint == 2) {
                            logInfo "DEBUG: Power2 updated - raw=${rawValue}, calculated=${power}W, total=${totalPower}W"
                        }
                        
                        def lastLoggedPower = state["lastLoggedPower${endpoint}"] ?: 0
                        if (Math.abs(power - lastLoggedPower) >= 1.0) {
                            if (txtEnable) logInfo "Relay ${endpoint} power: ${power}W"
                            state["lastLoggedPower${endpoint}"] = power
                        }
                        return [createEvent(name: "power${endpoint}", value: power, unit: "W")]
                    }
                    break
                    
                case 0x0001:
                    def powerDouble = rawValue / 1000.0
                    def power = Double.parseDouble(String.format("%.1f", powerDouble))
                    
                    sendEvent(name: "power${endpoint}", value: power, unit: "W")
                    
                    def power1 = endpoint == 1 ? power : (device.currentValue("power1") ?: 0)
                    def power2 = endpoint == 2 ? power : (device.currentValue("power2") ?: 0)
                    def totalPowerDouble = power1 + power2
                    def totalPower = Double.parseDouble(String.format("%.1f", totalPowerDouble))
                    sendEvent(name: "power", value: totalPower, unit: "W")
                    
                    updateChildDevice(endpoint, "power", power)
                    
                    if (endpoint == 2) {
                        logInfo "DEBUG: Power2 (attr 0x0001) updated - raw=${rawValue}, calculated=${power}W"
                    }
                    
                    def lastLoggedPower = state["lastLoggedPower${endpoint}"] ?: 0
                    if (Math.abs(power - lastLoggedPower) >= 1.0) {
                        if (txtEnable) logInfo "Relay ${endpoint} power: ${power}W"
                        state["lastLoggedPower${endpoint}"] = power
                    }
                    return [createEvent(name: "power${endpoint}", value: power, unit: "W")]
                    break
                    
                // *** UPDATED: More flexible frequency detection ***
                case 0x0300: // AC Frequency (or other electrical attributes)
                    def frequency = 0.0
                    if (rawValue > 0) {
                        // Try different scaling factors
                        if (rawValue > 10000) {
                            frequency = rawValue / 1000.0  // If in mHz
                        } else if (rawValue > 1000) {
                            frequency = rawValue / 100.0   // If in cHz  
                        } else if (rawValue > 100) {
                            frequency = rawValue / 10.0    // If in dHz
                        } else {
                            frequency = rawValue           // If already in Hz
                        }
                        
                        // Validate reasonable frequency range (45-65 Hz)
                        if (frequency >= 45 && frequency <= 65) {
                            frequency = Math.round(frequency * 10) / 10.0
                            sendEvent(name: "frequency", value: frequency, unit: "Hz")
                            
                            def lastLoggedFrequency = state["lastLoggedFrequency"] ?: 0
                            if (Math.abs(frequency - lastLoggedFrequency) >= 0.1) {
                                logInfo "AC Frequency: ${frequency}Hz (raw=${rawValue})"
                                state["lastLoggedFrequency"] = frequency
                            }
                            return [createEvent(name: "frequency", value: frequency, unit: "Hz")]
                        } else {
                            if (logEnable) logDebug "Frequency out of range: ${frequency}Hz (raw=${rawValue})"
                        }
                    }
                    break
                    
                // *** UPDATED: More flexible power factor detection ***
                case 0x050E: // Power Factor (different attribute)
                case 0x0510: // Alternative Power Factor
                    def powerFactor = 0.0
                    if (rawValue >= 0) {
                        // Try different scaling factors
                        if (rawValue > 1000) {
                            powerFactor = rawValue / 10000.0  // If in ten-thousandths
                        } else if (rawValue > 100) {
                            powerFactor = rawValue / 1000.0   // If in thousandths
                        } else if (rawValue > 1) {
                            powerFactor = rawValue / 100.0    // If in hundredths
                        } else {
                            powerFactor = rawValue / 1.0      // If already as decimal
                        }
                        
                        // Ensure within valid range
                        if (powerFactor > 1.0) powerFactor = 1.0
                        if (powerFactor < 0.0) powerFactor = 0.0
                        
                        powerFactor = Math.round(powerFactor * 1000) / 1000.0
                        
                        sendEvent(name: "powerFactor${endpoint}", value: powerFactor)
                        
                        // Calculate weighted average
                        def pf1 = endpoint == 1 ? powerFactor : (device.currentValue("powerFactor1") ?: 1.0)
                        def pf2 = endpoint == 2 ? powerFactor : (device.currentValue("powerFactor2") ?: 1.0)
                        def power1 = device.currentValue("power1") ?: 0
                        def power2 = device.currentValue("power2") ?: 0
                        def totalPower = power1 + power2
                        
                        def overallPF = 1.0
                        if (totalPower > 0) {
                            overallPF = ((pf1 * power1) + (pf2 * power2)) / totalPower
                            overallPF = Math.round(overallPF * 1000) / 1000.0
                        }
                        
                        sendEvent(name: "powerFactor", value: overallPF)
                        
                        def lastLoggedPF = state["lastLoggedPF${endpoint}"] ?: 0
                        if (Math.abs(powerFactor - lastLoggedPF) >= 0.01) {
                            logInfo "Relay ${endpoint} power factor: ${powerFactor} (raw=${rawValue}, attr=0x${Integer.toHexString(attrInt).toUpperCase()})"
                            state["lastLoggedPF${endpoint}"] = powerFactor
                        }
                        
                        updateChildDevice(endpoint, "powerFactor", powerFactor)
                        return [createEvent(name: "powerFactor${endpoint}", value: powerFactor)]
                    }
                    break
                    
                default:
                    if (logEnable) logDebug "Unhandled metering attribute: ${attrInt}, value: ${rawValue}"
                    break
            }
        } catch (Exception e) {
            if (logEnable) logDebug "Error parsing metering value: ${descMap.value}, error: ${e.message}"
        }
    }
    
    return []
}

private processElectricalMessage(Map descMap) {
    if (!descMap || !descMap.endpoint || !descMap.cluster || 
        !descMap.attrId || descMap.attrId == "null" || descMap.attrId == "") {
        if (logEnable) logDebug "Skipping electrical message - insufficient data"
        return []
    }
    
    def endpoint = 1
    def attrInt = null
    
    try {
        endpoint = descMap.endpoint ? Integer.parseInt(descMap.endpoint, 16) : 1
    } catch (Exception e) {
        endpoint = 1
    }
    
    try {
        if (descMap.attrInt != null) {
            attrInt = descMap.attrInt
        } else if (descMap.attrId && descMap.attrId != "null" && descMap.attrId != "") {
            attrInt = Integer.parseInt(descMap.attrId, 16)
        } else {
            return []
        }
    } catch (Exception e) {
        if (logEnable) logDebug "Error parsing electrical attribute: ${descMap.attrId}"
        return []
    }
    
    if (descMap.value && descMap.value != "null" && descMap.value != "") {
        try {
            def rawValue = Integer.parseInt(descMap.value, 16)
            if (logEnable) logDebug "Electrical raw: ${descMap.value} hex = ${rawValue} decimal, attr: ${attrInt}"
            
            switch (attrInt) {
                case 0x050B: // AC Current
                    def current = 0.0
                    if (rawValue > 0) {
                        current = rawValue / 1000.0
                        current = Math.round(current * 1000) / 1000.0
                    }
                    
                    sendEvent(name: "current${endpoint}", value: current, unit: "A")
                    
                    def current1 = endpoint == 1 ? current : (device.currentValue("current1") ?: 0)
                    def current2 = endpoint == 2 ? current : (device.currentValue("current2") ?: 0)
                    def totalCurrent = Math.round((current1 + current2) * 1000) / 1000.0
                    sendEvent(name: "current", value: totalCurrent, unit: "A")
                    
                    updateChildDevice(endpoint, "current", current)
                    
                    // AUTO-FIX: If endpoint 2 has current but no power, calculate it
                    if (endpoint == 2 && current > 0.001) {
                        def currentPower2 = device.currentValue("power2") ?: 0
                        if (currentPower2 <= 0.1) {
                            def voltage = device.currentValue("voltage") ?: 220
                            def calculatedPower = Math.round((voltage * current) * 10) / 10.0
                            if (calculatedPower >= 0.1) {
                                logInfo "AUTO-FIX: Power2 calculated = ${calculatedPower}W (current=${current}A, voltage=${voltage}V)"
                                sendEvent(name: "power2", value: calculatedPower, unit: "W")
                                updateChildDevice(2, "power", calculatedPower)
                                
                                // Update total power
                                def power1 = device.currentValue("power1") ?: 0
                                def totalPower = Math.round((power1 + calculatedPower) * 10) / 10.0
                                sendEvent(name: "power", value: totalPower, unit: "W")
                            }
                        }
                    }
                    
                    // *** CALCULATE APPARENT POWER AND POWER FACTOR ***
                    calculatePowerFactorForEndpoint(endpoint)
                    
                    def lastLoggedCurrent = state["lastLoggedCurrent${endpoint}"] ?: 0
                    if (Math.abs(current - lastLoggedCurrent) >= 0.01) {
                        if (txtEnable) logInfo "Relay ${endpoint} current: ${current}A"
                        state["lastLoggedCurrent${endpoint}"] = current
                    }
                    return [createEvent(name: "current${endpoint}", value: current, unit: "A")]
                    break
                    
                case 0x0505: // AC Voltage
                    def voltage = 0.0
                    if (rawValue > 0) {
                        voltage = rawValue / 100.0
                        voltage = Math.round(voltage * 10) / 10.0
                    }
                    
                    sendEvent(name: "voltage", value: voltage, unit: "V")
                    
                    def lastLoggedVoltage = state["lastLoggedVoltage"] ?: 0
                    if (Math.abs(voltage - lastLoggedVoltage) >= 1.0) {
                        if (txtEnable) logInfo "Voltage: ${voltage}V"
                        state["lastLoggedVoltage"] = voltage
                    }
                    return [createEvent(name: "voltage", value: voltage, unit: "V")]
                    break
                    
                case 0x050D: // Active Power
                    def power = 0.0
                    if (rawValue > 0) {
                        power = rawValue / 1000.0
                        power = Math.round(power * 10) / 10.0
                    }
                    
                    sendEvent(name: "power${endpoint}", value: power, unit: "W")
                    
                    def power1 = endpoint == 1 ? power : (device.currentValue("power1") ?: 0)
                    def power2 = endpoint == 2 ? power : (device.currentValue("power2") ?: 0)
                    def totalPower = Math.round((power1 + power2) * 10) / 10.0
                    sendEvent(name: "power", value: totalPower, unit: "W")
                    
                    updateChildDevice(endpoint, "power", power)
                    
                    if (endpoint == 2) {
                        logInfo "DEBUG: Power2 (electrical cluster) updated - raw=${rawValue}, calculated=${power}W"
                    }
                    
                    def lastLoggedPower = state["lastLoggedPower${endpoint}"] ?: 0
                    if (Math.abs(power - lastLoggedPower) >= 1.0) {
                        if (txtEnable) logInfo "Relay ${endpoint} power: ${power}W"
                        state["lastLoggedPower${endpoint}"] = power
                    }
                    return [createEvent(name: "power${endpoint}", value: power, unit: "W")]
                    break
                    
                default:
                    if (logEnable) logDebug "Unhandled electrical attribute: ${attrInt}"
                    break
            }
        } catch (Exception e) {
            if (logEnable) logDebug "Error parsing electrical value: ${descMap.value}, error: ${e.message}"
        }
    }
    
    return []
}

// Child device management
private createChildDevices() {
    if (!createChildDevices) return
    
    for (int i = 1; i <= 2; i++) {
        def childDni = "${device.deviceNetworkId}-${i}"
        def child = getChildDevice(childDni)
        
        if (!child) {
            logInfo "Creating child device for Relay ${i}"
            try {
                child = addChildDevice("hubitat", "Generic Component Switch", childDni, [
                    name: "Shelly 2PM Gen4 - Relay ${i}",
                    label: "Shelly 2PM Gen4 - Relay ${i}",
                    isComponent: true
                ])
                
                def switchState = device.currentValue("switch${i}") ?: "off"
                def power = device.currentValue("power${i}") ?: 0
                def current = device.currentValue("current${i}") ?: 0
                def energy = device.currentValue("energy${i}") ?: 0
                
                child.sendEvent(name: "switch", value: switchState, isStateChange: true)
                if (power > 0) child.sendEvent(name: "power", value: power, unit: "W")
                if (current > 0) child.sendEvent(name: "current", value: current, unit: "A")
                if (energy > 0) child.sendEvent(name: "energy", value: energy, unit: "kWh")
                
                // *** ADDED: Initialize power factor if available ***
                def powerFactor = device.currentValue("powerFactor${i}") ?: 1.0
                if (powerFactor != 1.0) child.sendEvent(name: "powerFactor", value: powerFactor)
                
                logInfo "Child device ${i} created and initialized"
            } catch (Exception e) {
                if (logEnable) logDebug "Error creating child device: ${e.message}"
            }
        }
    }
}

private removeChildDevices() {
    getChildDevices().each { child ->
        try {
            logInfo "Removing child device: ${child.label}"
            deleteChildDevice(child.deviceNetworkId)
        } catch (Exception e) {
            if (logEnable) logDebug "Error removing child device: ${e.message}"
        }
    }
}

private updateChildDevice(int endpoint, String attribute, value) {
    if (!createChildDevices) return
    
    def childDni = "${device.deviceNetworkId}-${endpoint}"
    def child = getChildDevice(childDni)
    
    if (child) {
        try {
            state["child${endpoint}_${attribute}"] = value
            
            child.sendEvent(name: attribute, value: value, isStateChange: true, displayed: true)
            
            if (attribute == "switch") {
                child.sendEvent(name: "switch", value: value, isStateChange: true, displayed: true, descriptionText: "Relay ${endpoint} is ${value}")
                runIn(1, "forceChildUpdate${endpoint}")
            }
            
            if (logEnable) logDebug "Updated child ${endpoint}: ${attribute} = ${value}"
        } catch (Exception e) {
            if (logEnable) logDebug "Error updating child device ${endpoint}: ${e.message}"
        }
    }
}

def forceChildUpdate1() {
    forceChildUpdateEndpoint(1)
}

def forceChildUpdate2() {
    forceChildUpdateEndpoint(2)
}

private forceChildUpdateEndpoint(int endpoint) {
    def childDni = "${device.deviceNetworkId}-${endpoint}"
    def child = getChildDevice(childDni)
    
    if (child) {
        def switchState = state["child${endpoint}_switch"]
        def power = state["child${endpoint}_power"]
        def current = state["child${endpoint}_current"] 
        def energy = state["child${endpoint}_energy"]
        
        if (switchState) {
            child.sendEvent(name: "switch", value: switchState, isStateChange: true, displayed: true)
        }
        if (power != null && power > 0) {
            child.sendEvent(name: "power", value: power, unit: "W", isStateChange: true)
        }
        if (current != null && current > 0) {
            child.sendEvent(name: "current", value: current, unit: "A", isStateChange: true)
        }
        if (energy != null && energy > 0) {
            child.sendEvent(name: "energy", value: energy, unit: "kWh", isStateChange: true)
        }
        
        if (logEnable) logDebug "Forced update child ${endpoint} with stored states"
    }
}

def maintainChildStates() {
    if (!createChildDevices) return
    
    if (logEnable) logDebug "=== MAINTAINING CHILD STATES ==="
    
    def switch1State = device.currentValue("switch1") ?: "off"
    def switch2State = device.currentValue("switch2") ?: "off"
    def power1 = device.currentValue("power1") ?: 0
    def power2 = device.currentValue("power2") ?: 0
    def current1 = device.currentValue("current1") ?: 0
    def current2 = device.currentValue("current2") ?: 0
    def energy1 = device.currentValue("energy1") ?: 0
    def energy2 = device.currentValue("energy2") ?: 0
    
    // AUTOMATIC POWER2 CALCULATION FIX
    if (switch2State == "on" && power2 <= 0.1 && current2 > 0.001) {
        def voltage = device.currentValue("voltage") ?: 220
        if (current2 > 0 && voltage > 0) {
            def calculatedPower = Math.round((voltage * current2) * 10) / 10.0
            if (calculatedPower >= 0.1) {
                logInfo "AUTO-FIX: Power2 calculated from V×I = ${calculatedPower}W (was ${power2}W)"
                sendEvent(name: "power2", value: calculatedPower, unit: "W")
                power2 = calculatedPower
                
                // Recalculate total power
                def totalPower = Math.round((power1 + power2) * 10) / 10.0
                sendEvent(name: "power", value: totalPower, unit: "W")
                
                // Update child device too
                updateChildDevice(2, "power", power2)
            }
        }
    }
    
    // Store in state for persistence
    state["child1_switch"] = switch1State
    state["child2_switch"] = switch2State
    state["child1_power"] = power1
    state["child2_power"] = power2
    state["child1_current"] = current1
    state["child2_current"] = current2
    state["child1_energy"] = energy1
    state["child2_energy"] = energy2
    
    // Force update each child
    for (int i = 1; i <= 2; i++) {
        def childDni = "${device.deviceNetworkId}-${i}"
        def child = getChildDevice(childDni)
        
        if (child) {
            def switchState = i == 1 ? switch1State : switch2State
            def power = i == 1 ? power1 : power2
            def current = i == 1 ? current1 : current2
            def energy = i == 1 ? energy1 : energy2
            
            def childSwitchState = child.currentValue("switch")
            if (!childSwitchState || childSwitchState != switchState) {
                if (logEnable) logDebug "Child ${i} lost switch state (${childSwitchState} vs ${switchState}), restoring..."
                child.sendEvent(name: "switch", value: switchState, isStateChange: true, displayed: true, descriptionText: "Restored: Relay ${i} is ${switchState}")
            }
            
            if (power > 0) {
                def childPower = child.currentValue("power")
                if (!childPower || Math.abs(childPower - power) > 0.1) {
                    child.sendEvent(name: "power", value: power, unit: "W", isStateChange: true)
                }
            }
            
            if (current > 0) {
                def childCurrent = child.currentValue("current")
                if (!childCurrent || Math.abs(childCurrent - current) > 0.001) {
                    child.sendEvent(name: "current", value: current, unit: "A", isStateChange: true)
                }
            }
            
            if (energy > 0) {
                def childEnergy = child.currentValue("energy")
                if (!childEnergy || Math.abs(childEnergy - energy) > 0.000001) {
                    child.sendEvent(name: "energy", value: energy, unit: "kWh", isStateChange: true)
                }
            }
        }
    }
    
    if (logEnable) logDebug "Child states maintenance complete"
}

def syncChildDevices() {
    logInfo "=== SINCRONIZANDO CHILD DEVICES ==="
    
    if (!createChildDevices) {
        logInfo "Child devices disabled"
        return
    }
    
    def switch1State = device.currentValue("switch1") ?: "off"
    def switch2State = device.currentValue("switch2") ?: "off"
    def power1 = device.currentValue("power1") ?: 0
    def power2 = device.currentValue("power2") ?: 0
    def current1 = device.currentValue("current1") ?: 0
    def current2 = device.currentValue("current2") ?: 0
    def energy1 = device.currentValue("energy1") ?: 0
    def energy2 = device.currentValue("energy2") ?: 0
    
    logInfo "Parent states: switch1=${switch1State}, switch2=${switch2State}"
    logInfo "Parent power: power1=${power1}, power2=${power2}"
    
    for (int i = 1; i <= 2; i++) {
        def childDni = "${device.deviceNetworkId}-${i}"
        def child = getChildDevice(childDni)
        
        if (child) {
            if (logEnable) logDebug "Syncing child ${i}..."
            
            def switchState = i == 1 ? switch1State : switch2State
            def power = i == 1 ? power1 : power2
            def current = i == 1 ? current1 : current2
            def energy = i == 1 ? energy1 : energy2
            
            child.sendEvent(name: "switch", value: switchState, isStateChange: true, displayed: true)
            
            if (power > 0) {
                child.sendEvent(name: "power", value: power, unit: "W", isStateChange: true)
            }
            if (current > 0) {
                child.sendEvent(name: "current", value: current, unit: "A", isStateChange: true)
            }
            if (energy > 0) {
                child.sendEvent(name: "energy", value: energy, unit: "kWh", isStateChange: true)
            }
            
            if (logEnable) logDebug "Child ${i} synced: switch=${switchState}, power=${power}W"
        } else {
            if (logEnable) logDebug "Child device ${i} not found"
        }
    }
    
    logInfo "Child device sync complete"
}

// Child device command handlers
void componentOn(cd) {
    if (!cd) return
    def endpoint = cd.deviceNetworkId.split("-")[-1] as Integer
    logInfo "Child device ${endpoint} on command"
    if (endpoint == 1) {
        on1()
    } else if (endpoint == 2) {
        on2()
    }
    runIn(1, "maintainChildStates")
    runIn(3, "maintainChildStates")
}

void componentOff(cd) {
    if (!cd) return
    def endpoint = cd.deviceNetworkId.split("-")[-1] as Integer
    logInfo "Child device ${endpoint} off command"
    if (endpoint == 1) {
        off1()
    } else if (endpoint == 2) {
        off2()
    }
    runIn(1, "maintainChildStates")
    runIn(3, "maintainChildStates")
}

void componentRefresh(cd) {
    refresh()
    runIn(2, "maintainChildStates")
}

// Logging functions
private logInfo(msg) {
    if (txtEnable) log.info "${device.label} ${msg}"
}

private logDebug(msg) {
    if (logEnable) log.debug "${device.label} ${msg}"
}

def logsOff() {
    logInfo "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
