/*
    GE Smart Plug / Zemismart Power Meter Smart Plug

    Changes:
        - 09/26/19 Update nname and fingerprint from @aruffell
        - 09/27/19 Fix typo for reporting, and update fingerprint order, fix whitespace
        - Adapted to Zemismart Power Smart Plug by Joao
        -
*/

import groovy.transform.Field

@Field static Map zigbeeSwitch = [
    cluster: [
        name: "Switch Cluster",
        hexString: "0006",
        hexValue: 0x0006,
    ],
    onOffAttr : [
        name: "On/Off",
        hexString: "0000",
        hexValue: 0x0000,
    ],
]

@Field static Map zigbeeSimpleMonitoring = [
    cluster: [
        name: "Simple Monitoring Cluster",
        hexString: "0702",
        hexValue: 0x0702,
    ],
    energyAttr : [
        name: "Accumulated Energy Used",
        hexString: "0000",
        hexValue: 0x0000,
        divisor: 200, //10000
    ],
    energyDayAttr : [
        name: "Current Day Energy Used",
        hexString: "0001",        //0401
        hexValue: 0x0001,         //0x0401
        divisor: 200, //10000
    ],
    powerAttr : [
        name: "Instantaneous Power Use",
        hexString: "0006",  //0400
        hexValue: 0x0006,
        divisor: 1,  //10
    ],
    unitAttr : [
        name: "Unit of Measure",
        hexString: "0300",  //0400
        hexValue: 0x0300,
        divisor: 1,  //10
    ],
    energyMultiplierAttr : [
        name: "Energy Multiplier",
        hexString: "0301",  
        hexValue: 0x0301,
        divisor: 1,  //10
    ],
    energyDivisorAttr : [
        name: "Energy Divisor",
        hexString: "0302",  
        hexValue: 0x0302,
        divisor: 1,  //10
    ],
]

@Field static Map zigbeeElectricalMeasurement = [
    cluster: [
        name: "Electrical Measurement Cluster",
        hexString: "0B04",
        hexValue: 0x0B04,
    ],
    powerrAttr : [
        name: "Power",
        hexString: "050B",
        hexValue: 0x050B,
        divisor: 2,
    ],
    voltageAttr : [
        name: "Voltage",
        hexString: "0505",
        hexValue: 0x0505,
        divisor: 1,
    ],
    currentAttr : [
        name: "Current",
        hexString: "0508",
        hexValue: 0x0508,
        divisor: 2000,
    ],
    currentMultAttr : [
        name: "Current Mult",
        hexString: "0602",
        hexValue: 0x0602,
        divisor: 1,
    ],
    currentDivAttr : [
        name: "Current Div",
        hexString: "0603",
        hexValue: 0x0603,
        divisor: 1,
    ],
]

@Field static List currentChangeOptions = [
    "No Reports",
    "0.1 A",
    "0.5 A",
    "1.0 A",
    "2.0 A",
    "3.0 A",
    "4.0 A",
    "5.0 A",
    "6.0 A",
    "7.0 A",
    "8.0 A",
    "9.0 A",
    "10.0 A",
    "20.0 A",
]

@Field static List voltageChangeOptions = [
    "No Reports",
    "1 Volt",
    "2 Volts",
    "3 Volts",
    "4 Volts",
    "5 Volts",
    "10 Volts",
    "25 Volts",
    "50 Volts",
]

@Field static List powerrChangeOptions = [
    "No Reports",
    "1 Watt",
    "2 Watts",
    "3 Watts",
    "4 Watts",
    "5 Watts",
    "10 Watts",
    "25 Watts",
    "50 Watts",
    "100 Watts",
    "200 Watts",
    "500 Watts",
    "1000 Watts",
]

@Field static List powerChangeOptions = [
    "No Reports",
    "1 Watt",
    "2 Watts",
    "3 Watts",
    "4 Watts",
    "5 Watts",
    "10 Watts",
    "25 Watts",
    "50 Watts",
    "100 Watts",
    "200 Watts",
    "500 Watts",
    "1000 Watts",
]

@Field static List energyChangeOptions = [
    "No Reports",
    "0.001 kWh",
    "0.005 kWh",
    "0.010 kWh",
    "0.025 kWh",
    "0.050 kWh",
    "0.100 kWh",
    "0.250 kWh",
    "0.500 kWh",
    "1 kWh",
    "2 kWh",
    "5 kWh",
    "10 kWh",
    "20 kWh",
]

@Field static List timeReportOptions = [
    "No Reports",
    "5 Seconds",
    "10 Seconds",
    "30 Seconds",
    "1 Minute",
    "5 Minutes",
    "15 Minutes",
    "30 Minutes",
    "1 Hour",
    "6 Hours",
    "12 Hours",
    "24 Hours",
]

metadata {
    definition (name: "GE/Jasco Smart Switch", namespace: "asj", author: "asj") {
        capability "Configuration"
        capability "Refresh"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "CurrentMeter"
        capability "Sensor"
        capability "Outlet"
        capability "Switch"
        capability "VoltageMeasurement"
        
        command "resetEnergy"
        attribute "current", "number"
        attribute "energyYesterDay", "number"
        attribute "energyToday", "number"
        attribute "energyMonth", "number"

        // GE/Jasco
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,0702", outClusters: "0003,000A,0019", manufacturer: "Jasco", model: "45853", deviceJoinName: "GE ZigBee Plug-In Switch"
        fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B05,0702", outClusters: "000A,0019", manufacturer: "Jasco", model: "45856", deviceJoinName: "GE ZigBee In-Wall Switch"
        fingerprint profileId: "0104", inClusters: "0000,0004,0005,0006,0702,0B04", outClusters: "0019,000A", manufacturer: "_TZ3000_rdtixbnu", model: "TS0121", deviceJoinName: "Zemismart Outlet"

    }

    preferences {
        //standard logging options
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "powerChange", type: "enum", title: "Power Report Value Change:", defaultValue: powerChangeOptions[0], options: powerChangeOptions
        input name: "powerReport", type: "enum", title: "Power Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
        input name: "energyChange", type: "enum", title: "Energy Report Value Change:", defaultValue: energyChangeOptions[0], options: energyChangeOptions
        input name: "energyReport", type: "enum", title: "Energy Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
        input name: "energyDayChange", type: "enum", title: "Energy Day Report Value Change:", defaultValue: energyChangeOptions[0], options: energyChangeOptions
        input name: "energyDayReport", type: "enum", title: "Energy Day Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
        
        
        input name: "powerrChange", type: "enum", title: "Powerr Report Value Change:", defaultValue: powerrChangeOptions[0], options: powerrChangeOptions
        input name: "powerrReport", type: "enum", title: "Powerr Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
        input name: "voltageChange", type: "enum", title: "Voltage Report Value Change:", defaultValue: voltageChangeOptions[0], options: voltageChangeOptions
        input name: "voltageReport", type: "enum", title: "Voltage Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions

        input name: "currentChange", type: "enum", title: "Current Report Value Change:", defaultValue: currentChangeOptions[0], options: currentChangeOptions
        input name: "currentReport", type: "enum", title: "Current Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
 
        input name: "currentMultChange", type: "enum", title: "Current Mult Report Value Change:", defaultValue: currentChangeOptions[0], options: currentChangeOptions
        input name: "currentMultReport", type: "enum", title: "Current Mult Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
        
        input name: "currentDivChange", type: "enum", title: "Current Div Report Value Change:", defaultValue: currentChangeOptions[0], options: currentChangeOptions
        input name: "currentDivReport", type: "enum", title: "Current Div Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions

        input name: "energyMultChange", type: "enum", title: "Energy Mult Report Value Change:", defaultValue: energyChangeOptions[0], options: energyChangeOptions
        input name: "energyMultReport", type: "enum", title: "Energy Mult Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
        
        input name: "energyDivChange", type: "enum", title: "Energy Div Report Value Change:", defaultValue: energyChangeOptions[0], options: energyChangeOptions
        input name: "energyDivReport", type: "enum", title: "Energy Div Reporting Interval:", defaultValue: timeReportOptions[0], options: timeReportOptions
 
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def parse(String description) {
    if (logEnable) log.debug "description is ${description}"
    def descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "descMap:${descMap}"
log.debug "ex1: $description"    
    if (description.startsWith("catchall")) return

    def cluster = descMap.clusterId ?: descMap.cluster
    def hexValue = descMap.value
    def attrId = descMap.attrId
    def encoding = descMap.encoding
    def size = descMap.size
log.debug "ex2: $cluster"
    switch (cluster){
        case zigbeeSwitch.cluster.hexString: //switch
            switch (attrId) {
                case zigbeeSwitch.onOffAttr.hexString:
                    getSwitchResult(hexValue)
                    break
            }
            break
        case zigbeeSimpleMonitoring.cluster.hexString:
            //if (hexValue) log.info "power cluster: ${cluster} ${attrId} encoding: ${encoding} size: ${size} value: ${hexValue} int: ${hexStrToSignedInt(hexValue)}"
            switch (attrId) {
                case zigbeeSimpleMonitoring.energyAttr.hexString:
                    getEnergyResult(hexValue)
                    break
                case zigbeeSimpleMonitoring.energyDayAttr.hexString:
                    getEnergyDayResult(hexValue)
                    break
                case zigbeeSimpleMonitoring.powerAttr.hexString:
                    getPowerResult(hexValue)
                    break
                case zigbeeSimpleMonitoring.energyMultiplierAttr.hexString:
                    getEnergyMultiplierResult(hexValue)
                    break
                case zigbeeSimpleMonitoring.energyDivisorAttr.hexString:
                    getEnergyDivisorResult(hexValue)
                    break
            }
            break
        case zigbeeElectricalMeasurement.cluster.hexString:
            switch (attrId) {
                case zigbeeElectricalMeasurement.voltageAttr.hexString:
                    getVoltageResult(hexValue)
                    break
                case zigbeeElectricalMeasurement.powerrAttr.hexString:
                    getPowerrResult(hexValue)
                    break
                case zigbeeElectricalMeasurement.currentAttr.hexString:
                    getCurrentResult(hexValue)
                    break
                case zigbeeElectricalMeasurement.currentMultAttr.hexString:
                    getCurrentMultResult(hexValue)
                    break
                case zigbeeElectricalMeasurement.currentDivAttr.hexString:
                    getCurrentDivResult(hexValue)
                    break
            }
            break
        default :
            if (hexValue) log.info "unknown cluster: ${cluster} ${attrId} encoding: ${encoding} size: ${size} value: ${hexValue} int: ${hexStrToSignedInt(hexValue)}"
            break
    }
    return
}



//event methods
def getEnergyMultiplierResult(hex) {
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeElectricalMeasurement.energyMultiplierAttr.divisor
    def name = "energy multiplier"
    def unit = ""
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)      
//  if (state.currentMultiplier && state.currentDivisor) {
//    return (state.currentMultiplier / state.currentDivisor)
//  } else {
//    return 0.001831
//  }
}

def getEnergyDivisorResult(hex) {
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeElectricalMeasurement.energyDivisorAttr.divisor
    def name = "energy divisor"
    def unit = ""
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)      
//  if (state.currentMultiplier && state.currentDivisor) {
//    return (state.currentMultiplier / state.currentDivisor)
//  } else {
//    return 0.001831
//  }
}

def getCurrentMultResult(hex) {
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeElectricalMeasurement.currentMultAttr.divisor
    def name = "current multiplier"
    def unit = "A"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)      
//  if (state.currentMultiplier && state.currentDivisor) {
//    return (state.currentMultiplier / state.currentDivisor)
//  } else {
//    return 0.001831
//  }
}

def getCurrentDivResult(hex) {
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeElectricalMeasurement.currentDivAttr.divisor
    def name = "current divisor"
    def unit = "A"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)    
//  if (state.currentMultiplier && state.currentDivisor) {
//    return (state.currentMultiplier / state.currentDivisor)
//  } else {
//    return 0.001831
//  }
}

private getCurrentResult(hex){
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeElectricalMeasurement.currentAttr.divisor
    def name = "current"
    def unit = "A"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    state.current = value
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getVoltageResult(hex){
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeElectricalMeasurement.voltageAttr.divisor
    def name = "voltage"
    def unit = "V"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getPowerrResult(hex){
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeElectricalMeasurement.powerrAttr.divisor
    def name = "power"
    def unit = "W"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getPowerResult(hex){
    def value = hexStrToSignedInt(hex)
    value = value / zigbeeSimpleMonitoring.powerAttr.divisor
    def name = "power"
    def unit = "W"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getEnergyDayResult(hex){
    def value = hexStrToSignedInt(hex)
    if (state.energyDayReset) {
        state.energyDayReset = null
        state.energyDayResetValue = value
    }
    if (state.energyDayResetValue) {
        if (value < state.energyDayResetValue) {
            state.energyDayResetValue = null
        } else {
            value -= state.energyDayResetValue
        }
    }

    value = value / zigbeeSimpleMonitoring.energyDayAttr.divisor
    def name = "energy day"
    def unit = "kWh"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
}

private getEnergyResult(hex){
    def value = hexStrToSignedInt(hex)
    if (state.energyReset) {
        state.energyReset = null
        state.energyResetValue = value
    }
    if (state.energyResetValue) {
        if (value < state.energyResetValue) {
            state.energyResetValue = null
        } else {
            value -= state.energyResetValue
        }
    }

    value = value / zigbeeSimpleMonitoring.energyAttr.divisor
    def name = "energy"
    def unit = "kWh"
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit)
    setEnergyData(value)
}

private getSwitchResult(hex){
    def value = hexStrToSignedInt(hex) == 1 ? "on" : "off"
    def name = "switch"
    def unit = ""
    def descriptionText = "${device.displayName} ${name} is ${value}${unit}"
    if (txtEnable) log.info "${descriptionText} [physical]"
    sendEvent(name: name,value: value,descriptionText: descriptionText,unit: unit, type: "physical")
}

private setEnergyData(value){
    
    def hoje = LocalDate.now()
    
    state.energyYesterDay = value
    state.energyToday = value
    state.energyMonth = value
    
    def name = "energyYesterDay"
    def unit = "kWh"
    def descriptionText = "atributo ${name} is ${state.energyYesterDay}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: state.energyYesterDay,descriptionText: descriptionText,unit: unit)
    
    name = "energyToday"
    unit = "kWh"
    descriptionText = "atributo ${name} is ${state.energyToday}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: state.energyToday,descriptionText: descriptionText,unit: unit)
    
    name = "energyMonth"
    unit = "kWh"
    descriptionText = "atributo ${name} is ${state.energyMonth}${unit}"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: name,value: state.energyMonth,descriptionText: descriptionText,unit: unit)
}    

private getChangeValue(change) {
    def changeValue
    def prMatch = (change =~ /([0-9.]+) /)
    if (prMatch) changeValue = prMatch[0][1]
    if (changeValue.isInteger()) {
        changeValue = changeValue.toInteger()
    } else if(changeValue.isDouble()) {
        changeValue = changeValue.toDouble()
    }
    return changeValue
}

private getReportValue(report) {
    def reportValue
    def prMatch = (report =~ /(\d+) Seconds/)
    if (prMatch) reportValue = prMatch[0][1].toInteger()
    prMatch = (report =~ /(\d+) Minute/)
    if (prMatch) reportValue = prMatch[0][1].toInteger() * 60
    prMatch = (report =~ /(\d+) Hour/)
    if (prMatch) reportValue = prMatch[0][1].toInteger() * 3600

    return reportValue
}

//capability and device methods
def off() {
    def descriptionText = "switch is off [digital]"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "switch",value: "off",descriptionText: descriptionText, type: "digital")

    zigbee.off()
}

def on() {
    def descriptionText = "switch is on [digital]"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "switch",value: "on",descriptionText: descriptionText, type: "digital")

    zigbee.on()
}

def refresh() {
   log.debug "Refresh"
    
   List cmds =  []
   def attrs = [zigbeeSimpleMonitoring.energyAttr.hexValue, 0x200, zigbeeSimpleMonitoring.energyDayAttr.hexValue] //, 0x200, zigbeeSimpleMonitoring.powerAttr.hexValue, 0x200, zigbeeSimpleMonitoring.unitAttr.hexValue, 0x200, zigbeeSimpleMonitoring.energyMultiplierAttr.hexValue, 0x200, zigbeeSimpleMonitoring.energyDivisorAttr.hexValue
   attrs.each { it ->
        cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,it,[:],200) 
   }
   def attrss = [zigbeeElectricalMeasurement.powerrAttr.hexValue, 0x200, zigbeeElectricalMeasurement.voltageAttr.hexValue, 0x200, zigbeeElectricalMeasurement.currentAttr.hexValue, 0x200, zigbeeElectricalMeasurement.currentMultAttr.hexValue, 0x200, zigbeeElectricalMeasurement.currentDivAttr.hexValue]
   attrss.each { it ->
        cmds +=  zigbee.readAttribute(zigbeeElectricalMeasurement.cluster.hexValue,it,[:],200) 
   } 
   // Ask for units of measure, divisor, multiplier, and type
   for (def base = 0x300; base < 0x30F; base++) {
       cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,base,[:],200) 
   }
   return cmds
}

def configure() {
    log.debug "Configuring Reporting and Bindings."
    //runIn(1800,logsOff)

    List cmds = zigbee.onOffConfig()
    cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
                                            zigbeeSimpleMonitoring.powerAttr.hexValue,
                                            DataType.INT24,
                                            5,
                                            getReportValue(powerReport),
                                            getChangeValue(powerChange) * zigbeeSimpleMonitoring.powerAttr.divisor)
    cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
                                            zigbeeSimpleMonitoring.energyAttr.hexValue,
                                            DataType.INT8, //UINT48 ---- int24
                                            5,
                                            getReportValue(energyReport),
                                            (getChangeValue(energyChange) * zigbeeSimpleMonitoring.energyAttr.divisor).toInteger())
    cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
                                            zigbeeSimpleMonitoring.energyDayAttr.hexValue,
                                            DataType.INT8, //UINT48 ---- uint24
                                            5,
                                            getReportValue(energyDayReport),
                                            (getChangeValue(energyDayChange) * zigbeeSimpleMonitoring.energyDayAttr.divisor).toInteger())
   // cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
   //                                         zigbeeSimpleMonitoring.unitAttr.hexValue,
   //                                         DataType.STRING, 
   //                                         5,
   //                                         getReportValue(unitReport),
   //                                         (getChangeValue(unitChange))
    cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
                                            zigbeeSimpleMonitoring.energyMultiplierAttr.hexValue,
                                            DataType.UINT48, 
                                            5,
                                            getReportValue(energyMultiplierReport),
                                            (getChangeValue(energyMultiplierChange) * zigbeeSimpleMonitoring.energyMultiplierAttr.divisor).toInteger())
    
    cmds = cmds + zigbee.configureReporting(zigbeeSimpleMonitoring.cluster.hexValue,
                                            zigbeeSimpleMonitoring.energyDivisorAttr.hexValue,
                                            DataType.UINT48, 
                                            5,
                                            getReportValue(energyDivisorReport),
                                            (getChangeValue(energyDivisorChange) * zigbeeSimpleMonitoring.energyDivisorAttr.divisor).toInteger())
   
    cmds = cmds + zigbee.configureReporting(zigbeeElectricalMeasurement.cluster.hexValue,
                                            zigbeeElectricalMeasurement.powerrAttr.hexValue,
                                            DataType.INT24, 
                                            5,
                                            getReportValue(powerrReport),
                                            (getChangeValue(powerrChange) * zigbeeElectricalMeasurement.powerrAttr.divisor).toInteger())
    
    cmds = cmds + zigbee.configureReporting(zigbeeElectricalMeasurement.cluster.hexValue,
                                            zigbeeElectricalMeasurement.voltageAttr.hexValue,
                                            DataType.INT24, 
                                            5,
                                            getReportValue(voltageReport),
                                            (getChangeValue(voltageChange) * zigbeeElectricalMeasurement.voltageAttr.divisor).toInteger())
    
    cmds = cmds + zigbee.configureReporting(zigbeeElectricalMeasurement.cluster.hexValue,
                                            zigbeeElectricalMeasurement.currentAttr.hexValue,
                                            DataType.INT24, 
                                            5,
                                            getReportValue(currentReport),
                                            (getChangeValue(currentChange) * zigbeeElectricalMeasurement.currentAttr.divisor).toInteger())
    cmds = cmds + zigbee.configureReporting(zigbeeElectricalMeasurement.cluster.hexValue,
                                            zigbeeElectricalMeasurement.currentMultAttr.hexValue,
                                            DataType.INT24, 
                                            5,
                                            getReportValue(currentMultReport),
                                            (getChangeValue(currentChange) * zigbeeElectricalMeasurement.currentMultAttr.divisor).toInteger())
    cmds = cmds + zigbee.configureReporting(zigbeeElectricalMeasurement.cluster.hexValue,
                                            zigbeeElectricalMeasurement.currentDivAttr.hexValue,
                                            DataType.INT24, 
                                            5,
                                            getReportValue(currentDivReport),
                                            (getChangeValue(currentChange) * zigbeeElectricalMeasurement.currentDivAttr.divisor).toInteger())
    
    cmds = cmds + refresh()
    if (logEnabled) log.info "cmds:${cmds}"
    return cmds
}

def getReadings() {
    List cmds = []
    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,
                                  zigbeeSimpleMonitoring.powerAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,
                                  zigbeeSimpleMonitoring.energyAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,
                                  zigbeeSimpleMonitoring.energyDayAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,
                                  zigbeeSimpleMonitoring.energyMultiplierAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeSimpleMonitoring.cluster.hexValue,
                                  zigbeeSimpleMonitoring.energyDivisorAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeElectricalMeasurement.cluster.hexValue,
                                  zigbeeElectricalMeasurement.powerrAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeElectricalMeasurement.cluster.hexValue,
                                  zigbeeElectricalMeasurement.voltageAttr.hexValue,
                                  [:],20) 
    cmds +=  zigbee.readAttribute(zigbeeElectricalMeasurement.cluster.hexValue,
                                  zigbeeElectricalMeasurement.currentAttr.hexValue,
                                  [:],20)
    cmds +=  zigbee.readAttribute(zigbeeElectricalMeasurement.cluster.hexValue,
                                  zigbeeElectricalMeasurement.currentMultAttr.hexValue,
                                  [:],20)
    cmds +=  zigbee.readAttribute(zigbeeElectricalMeasurement.cluster.hexValue,
                                  zigbeeElectricalMeasurement.currentDivAttr.hexValue,
                                  [:],20)

    return cmds
}

def updated() {
    log.trace "Updated()"
    log.trace "powerChangeValue: " + getChangeValue(powerChange)
    log.trace "powerReportvalue: " + getReportValue(powerReport)
    log.trace "energyChangeValue: " + getChangeValue(energyChange)
    log.trace "energyReportvalue: " + getReportValue(energyReport)
    log.trace "energyDayChangeValue: " + getChangeValue(energyDayChange)
    log.trace "energyDayReportvalue: " + getReportValue(energyDayReport)
    
    log.trace "energyMultiplierChangeValue: " + getChangeValue(energyMultiplierChange)
    log.trace "energyDivisorReportvalue: " + getReportValue(energyDivisorReport)
    
    log.trace "powerrChangeValue: " + getChangeValue(powerrChange)
    log.trace "powerrReportvalue: " + getReportValue(powerrReport)
    log.trace "voltageChangeValue: " + getChangeValue(voltageChange)
    log.trace "voltageReportvalue: " + getReportValue(voltageReport)
    
    log.trace "currentChangeValue: " + getChangeValue(currentChange)
    log.trace "currentReportvalue: " + getReportValue(currentReport)
    
    log.trace "currentMultChangeValue: " + getChangeValue(currentMultChange)
    log.trace "currentDivReportvalue: " + getReportValue(currentDivReport)
    
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff) 
    return configure()
}


def resetEnergy() {
    state.energyReset = true
    return getReadings()
}
