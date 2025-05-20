/**
 * Shelly 1PM Gen 4 Zigbee Driver para Hubitat Elevation
 *
 * Este driver permite controlar e monitorar o dispositivo Shelly 1PM Gen 4 através do protocolo Zigbee.
 * O dispositivo é um relé com medição de energia que suporta as seguintes funcionalidades:
 * - Ligar/desligar (switch)
 * - Medição de potência (power em W)
 * - Medição de tensão (voltage em V)
 * - Medição de frequência AC (ac_frequency em Hz)
 * - Medição de corrente (current em A)
 * - Medição de energia produzida (produced_energy em kWh)
 *
 * Baseado na documentação do Zigbee2MQTT: https://www.zigbee2mqtt.io/devices/S4SW-001P16EU.html
 * Modelo: S4SW-001P16EU
 * Fabricante: Shelly
 * Descrição: 1PM Gen 4
 *
 * Copyright 2025
 * Licenciado sob a Licença Apache, Versão 2.0
 * version 1.0.0 2025-04-17 Joao - Initial version
 * version 1.1.0 2025-04-02 kkossev - Small improvements
 * version 1.1.1 2025-05-19 Joao - Translation to English
 */

import groovy.transform.Field
import groovy.json.JsonOutput
import hubitat.zigbee.zcl.DataType

/**
 * Driver definition with capabilities and attributes
 */
metadata {
    definition(
        name: "Shelly 1PM Gen 4 Zigbee Driver",
        namespace: "hubitat",
        author: "Manus",
        importUrl: "https://raw.githubusercontent.com/joaomf/hubitat/refs/heads/master/Shelly%201PM%20Gen%204%20Zigbee%20Driver.groovy"
    ) {
        // Basic capabilities
        capability "Actuator"        // Allows the device to be controlled
        capability "Switch"          // On/off support
        capability "Refresh"         // Allows you to manually update the values
        capability "Configuration"   // Allows you to configure the device
        
        // Capabilities for energy measurement
        capability "PowerMeter"        // For power measurement (W)
        capability "VoltageMeasurement" // For voltage measurement (V)
        capability "CurrentMeter"      // For current measurement (A)
        capability "EnergyMeter"       // For energy measurement (kWh)
        
        // Custom attributes
        attribute "acFrequency", "number" // For AC frequency (Hz)
        attribute "producedEnergy", "number" // For energy produced (kWh)
        
        // Device fingerprint
        fingerprint profileId: "0104", 
                    inClusters: "0000,0003,0004,0005,0006,0B04,0702", 
                    outClusters: "0019", 
                    manufacturer: "Shelly", 
                    model: "1PM", 
                    deviceJoinName: "Shelly 1PM Gen 4"
    }
    
    /**
     * User configurable preferences
     */
    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logs", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable text logs", defaultValue: true
        
        // Report settings
        input name: "reportingInterval", type: "number", title: "Report interval (seconds)", defaultValue: 60, range: "10..3600"
        
        // Calibration settings
        input name: "powerCalibration", type: "decimal", title: "Power calibration (%)", defaultValue: 0
        input name: "voltageCalibration", type: "decimal", title: "Voltage calibration (%)", defaultValue: 0
        input name: "currentCalibration", type: "decimal", title: "Current calibration (%)", defaultValue: 0
        input name: "acFrequencyCalibration", type: "decimal", title: "AC frequency calibration (offset)", defaultValue: 0
    }
}

// Constants for Zigbee clusters
@Field static final Integer CLUSTER_BASIC = 0x0000
@Field static final Integer CLUSTER_ON_OFF = 0x0006
@Field static final Integer CLUSTER_SIMPLE_METERING = 0x0702
@Field static final Integer CLUSTER_ELECTRICAL_MEASUREMENT = 0x0B04

// Constants for attributes
@Field static final Integer ATTR_ON_OFF = 0x0000
@Field static final Integer ATTR_PRESENT_VALUE = 0x0055
@Field static final Integer ATTR_ACTIVE_POWER = 0x050B
@Field static final Integer ATTR_RMS_VOLTAGE = 0x0505
@Field static final Integer ATTR_RMS_CURRENT = 0x0508
@Field static final Integer ATTR_AC_FREQUENCY = 0x0300
@Field static final Integer ATTR_ENERGY_DELIVERED = 0x0000
@Field static final Integer ATTR_ENERGY_PRODUCED = 0x0001
@Field static final Integer ATTR_POWER_FACTOR = 0x0510

// Constants for dividers
@Field static final Integer DIVISOR_POWER = 100
@Field static final Integer DIVISOR_VOLTAGE = 100
@Field static final Integer DIVISOR_CURRENT = 100
@Field static final Integer DIVISOR_FREQUENCY = 100
@Field static final Integer DIVISOR_ENERGY = 1000000

/**
 * Method called when the device is installed
 */
def installed() {
    logDebug "Installed"
    initialize()
}

/**
 * Method called when device settings are updated
 */
def updated() {
    logDebug "Updated"
    initialize()
}

/**
 * Initializes the device and configures the reports
 */
def initialize() {
    logDebug "Initializing"
    unschedule()
    
    if (logEnable) {
        runIn(1800, "logsOff")  // Disables debug logs after 30 minutes
    }
    
    // Configure reports
    configure()
}

/**
 * Disables debugging logs
 */
def logsOff() {
    log.warn "Debug logs disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

/**
 * Configures the device's periodic reports
 * @return List of Zigbee commands for configuration
 */
def configure() {
    logDebug "Configuring periodic reports"
    
    def interval = reportingInterval ? reportingInterval : 60
    def minInterval = 10
    def maxInterval = interval as Integer //Explicitly converting to Integer
    
    def cmds = []
    
    // Configure report for on/off state
    cmds += zigbee.configureReporting(CLUSTER_ON_OFF, ATTR_ON_OFF, DataType.BOOLEAN, 0, maxInterval, 1)
    
    // Configure report for power
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_ACTIVE_POWER, DataType.INT16, minInterval, maxInterval, 1)
    
    // Configure report for voltage
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_VOLTAGE, DataType.UINT16, minInterval, maxInterval, 1)
    
    // Set up report for current
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_CURRENT, DataType.UINT16, minInterval, maxInterval, 1)
    
    // Configure report for AC frequency
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_AC_FREQUENCY, DataType.UINT16, minInterval, maxInterval, 1)
    
    // Set up report for energy
    cmds += zigbee.configureReporting(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_DELIVERED, DataType.UINT48, minInterval, maxInterval, 1)
    cmds += zigbee.configureReporting(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_PRODUCED, DataType.UINT48, minInterval, maxInterval, 1)
    
    logDebug "Sending configuration commands: ${cmds}"
    return cmds
}

/**
 * Manually updates device values
 * @return List of Zigbee commands for reading attributes
 */
def refresh() {
    logDebug "Updating values"
    
    def cmds = []
    
    // Read on/off status
    cmds += zigbee.readAttribute(CLUSTER_ON_OFF, ATTR_ON_OFF)
    
    // Read power
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_ACTIVE_POWER)
    
    // Read voltage
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_VOLTAGE)
    
    // Read current
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_CURRENT)
    
    // Read AC frequency
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_AC_FREQUENCY)
    
    // Read energy
    cmds += zigbee.readAttribute(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_DELIVERED)
    cmds += zigbee.readAttribute(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_PRODUCED)
    
    logDebug "Sending update commands: ${cmds}"
    return cmds
}

/**
 * Turn on the device
 * @return Zigbee command to turn on
 */
def on() {
    logDebug "Turning on"
    return zigbee.on()
}

/**
 * Turn off the device
 * @return Zigbee command to turn off
 */
def off() {
    logDebug "Turning off"
    return zigbee.off()
}

/**
 * Processes Zigbee messages received from the device
 * @param Description String containing the description of the Zigbee message
 * @return null
 */
def parse(String description) {
    logDebug "Received: ${description}"
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    logDebug "Description map: ${descMap}"
    
    if (descMap.cluster == "0006" && descMap.attrId == "0000") {
        // Processando estado on/off
        processSwitchState(descMap)
    } else if (descMap.cluster == "0B04") {
        // Processando medições elétricas
        processElectricalMeasurement(descMap)
    } else if (descMap.cluster == "0702") {
        // Processando medições de energia
        processEnergyMeasurement(descMap)
    }
    
    return null
}

/**
 * Processes the state of the switch (on/off)
 * @param descMap Map containing the attribute data
 */
private void processSwitchState(Map descMap) {
    if (descMap.value == null) return
    
    def value = descMap.value == "01" ? "on" : "off"
    logInfo "Switch state: ${value}"
    
    sendEvent(name: "switch", value: value)

}

/**
 * Processes electrical measurements (power, voltage, current, frequency)
 * @param descMap Map containing the attribute data
 */
private void processElectricalMeasurement(Map descMap) {
    if (descMap.value == null) return
    
    String attrId = descMap.attrId
    Integer rawValue = Integer.parseInt(descMap.value, 16)
    if (rawValue == null) return // Ignores null values
    
    switch (attrId) {
        case "050B": // Active power
            BigDecimal powerDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_POWER), 2, BigDecimal.ROUND_HALF_UP)
            if (powerCalibration) {
                powerDouble = powerDouble.multiply(new BigDecimal(1 + (powerCalibration / 100)))
            }
            String powerString = String.format("%.2f", powerDouble)
            logInfo "Power: ${powerString} W"
            sendEvent(name: "power", value: powerString, unit: "W")
            break
            
        case "0505": // RMS voltage
            BigDecimal voltageDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_VOLTAGE), 1, BigDecimal.ROUND_HALF_UP)
            if (voltageCalibration) {
                voltageDouble = voltageDouble.multiply(new BigDecimal(1 + (voltageCalibration / 100)))
            }
            String voltageString = String.format("%.1f", voltageDouble)
            logInfo "Voltage: ${voltageString} V"
            sendEvent(name: "voltage", value: voltageString, unit: "V")
            break
            
        case "0508": // RMS current
            BigDecimal currentDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_CURRENT), 3, BigDecimal.ROUND_HALF_UP)
            if (currentCalibration) {
                currentDouble = currentDouble.multiply(new BigDecimal(1 + (currentCalibration / 100)))
            }
            String currentString = String.format("%.3f", currentDouble)
            logInfo "Current: ${currentString} A"
            sendEvent(name: "amperage", value: currentString, unit: "A")
            break
            
        case "0300": // AC Frequency
            BigDecimal frequencyDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_FREQUENCY), 2, BigDecimal.ROUND_HALF_UP)
            if (acFrequencyCalibration) {
                frequencyDouble = frequencyDouble.add(new BigDecimal(acFrequencyCalibration))
            }
            String frequencyString = String.format("%.2f", frequencyDouble)
            logInfo "AC Frequency: ${frequencyString} Hz"
            sendEvent(name: "acFrequency", value: frequencyString, unit: "Hz")
            break
    }
}

/**
 * Processes energy measurements (consumed and produced)
 * @param descMap Map containing the attribute data
 */
private void processEnergyMeasurement(Map descMap) {
    if (descMap.value == null) return
    
    String attrId = descMap.attrId
    Long rawValue = Long.parseLong(descMap.value, 16)
    if (rawValue == null) return // Ignores null values
    
    switch (attrId) {
        case "0000": // Energy delivered (consumed)
            BigDecimal energyDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_ENERGY), 3, BigDecimal.ROUND_HALF_UP)
            String energyString = String.format("%.3f", energyDouble)
            logInfo "Energy: ${energyString} kWh"
            sendEvent(name: "energy", value: energyString, unit: "kWh")
            break
            
        case "0001": // Energy produced
            BigDecimal producedEnergyDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_ENERGY), 3, BigDecimal.ROUND_HALF_UP)
            String producedEnergyString = String.format("%.3f", producedEnergyDouble)
            logInfo "Produced Energy: ${producedEnergyString} kWh"
            sendEvent(name: "producedEnergy", value: producedEnergyString, unit: "kWh")
            break
    }
}

/**
 * Registers debugging message if enabled
 * @param msg Message to be registered
 */
private void logDebug(String msg) {
    if (logEnable) {
        log.debug "${device.displayName}: ${msg}"
    }
}

/**
 * Register informative message if enabled
 * @param msg Message to be registered
 */
private void logInfo(String msg) {
    if (txtEnable) {
        log.info "${device.displayName}: ${msg}"
    }
}
