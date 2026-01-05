/**
 *  Tuya QAFZ200 Dimmable Power Supply with Circadian Rhythm
 *
 *  Device: QAFZ200 (_TZE204_zenj4lxv)
 *  Manufacturer: _TZE204_zenj4lxv
 *  Model: TS0601
 *
 *  Features:
 *  - On/Off control
 *  - Brightness dimming (0-100%)
 *  - Color temperature control (2700K-6500K)
 *  - Circadian rhythm automation
 *
 *  Author: João
 *  Date: 2026-01-05
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    
 *    
 *    
 *    
 
 */

metadata {
    definition(
        name: "Tuya QAFZ200 Dimmable Power Supply",
        namespace: "joao",
        author: "João",
        importUrl: "https://raw.githubusercontent.com/..."
    ) {
        capability "Switch"
        capability "SwitchLevel"
        capability "ColorTemperature"
        capability "Configuration"
        capability "Refresh"
        
        attribute "circadianStatus", "enum", ["active", "inactive"]
        
        command "enableCircadianRhythm"
        command "disableCircadianRhythm"

        fingerprint profileId: "0104", 
                    inClusters: "0000,0004,0005,EF00", 
                    outClusters: "0019,000A", 
                    manufacturer: "_TZE204_zenj4lxv", 
                    model: "TS0601", 
                    deviceJoinName: "Tuya QAFZ200"
    }

    preferences {
        input name: "circadianEnabled", type: "bool", title: "Enable Circadian Rhythm", defaultValue: false
        input name: "minColorTemp", type: "number", title: "Minimum Color Temperature (K)", defaultValue: 2700, range: "2700..6500"
        input name: "maxColorTemp", type: "number", title: "Maximum Color Temperature (K)", defaultValue: 6500, range: "2700..6500"
        input name: "startTime", type: "time", title: "Circadian Start Time", defaultValue: "06:00"
        input name: "endTime", type: "time", title: "Circadian End Time", defaultValue: "18:00"
        input name: "transitionTime", type: "number", title: "Transition Time (seconds)", defaultValue: 5, range: "0..3600"
        input name: "updateInterval", type: "number", title: "Update Interval (minutes)", defaultValue: 15, range: "1..60"
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

// Parse incoming messages
def parse(String description) {
    logDebug("parse: ${description}")
    
    Map descMap = zigbee.parseDescriptionAsMap(description)
    logDebug("Parsed descMap: ${descMap}")
    
    if (descMap?.clusterInt == 0x0006) {
        // On/Off cluster
        return parseOnOffCluster(descMap)
    } else if (descMap?.clusterInt == 0x0008) {
        // Level Control cluster
        return parseLevelCluster(descMap)
    } else if (descMap?.clusterInt == 0x0300) {
        // Color Control cluster
        return parseColorCluster(descMap)
    } else {
        logDebug("Unhandled cluster: ${descMap}")
    }
    
    return null
}

// Parse On/Off cluster
def parseOnOffCluster(Map descMap) {
    if (descMap.attrInt == 0x0000) {
        def value = descMap.value == "01" ? "on" : "off"
        logInfo("is ${value}")
        sendEvent(name: "switch", value: value)
        
        // Se ligou e tinha circadian pausado, retomar
        if (value == "on" && state.circadianPaused && settings.circadianEnabled) {
            state.circadianPaused = false
            runIn(2, "applyCircadianTemperature")
        }
    }
}

// Parse Level cluster
def parseLevelCluster(Map descMap) {
    if (descMap.attrInt == 0x0000) {
        def rawValue = Integer.parseInt(descMap.value, 16)
        // Evitar divisão por zero ou valores inválidos
        def levelValue = rawValue > 0 ? Math.round(rawValue / 2.54) : 0
        levelValue = Math.max(0, Math.min(100, levelValue)) // Garantir 0-100
        logInfo("level is ${levelValue}%")
        sendEvent(name: "level", value: levelValue, unit: "%")
    }
}

// Parse Color cluster
def parseColorCluster(Map descMap) {
    if (descMap.attrInt == 0x0007) {
        // Color temperature in mireds
        def mireds = Integer.parseInt(descMap.value, 16)
        if (mireds > 0) {
            def kelvin = Math.round(1000000 / mireds)
            logInfo("color temperature is ${kelvin}K")
            sendEvent(name: "colorTemperature", value: kelvin, unit: "K")
        }
    }
}

// Switch commands
def on() {
    logInfo("turning on")
    List<Map<String, String>> cmds = []
    cmds += zigbee.command(0x0006, 0x01)
    
    // Se circadian estava pausado, aplicar temperatura após ligar
    if (state.circadianPaused && settings.circadianEnabled) {
        state.circadianPaused = false
        runIn(2, "applyCircadianTemperature")
    } else if (!settings.circadianEnabled) {
        // Se não tem circadian, aplicar última temperatura conhecida
        def lastCT = device.currentValue("colorTemperature") ?: 4000
        cmds += setColorTemperatureCommands(lastCT)
    }
    
    sendZigbeeCommands(cmds)
}

def off() {
    logInfo("turning off")
    // Apenas pausar circadian, não desativar completamente
    if (settings.circadianEnabled && state.circadianActive) {
        state.circadianPaused = true
        logDebug("Circadian paused (light turned off)")
    }
    
    List<Map<String, String>> cmds = []
    cmds += zigbee.command(0x0006, 0x00)
    sendZigbeeCommands(cmds)
}

// Apply circadian temperature after turning on
def applyCircadianTemperature() {
    if (device.currentValue("switch") == "on" && settings.circadianEnabled && !state.circadianPaused) {
        logDebug("Applying circadian temperature after turning on")
        updateCircadianRhythm()
    }
}

// Level commands
def setLevel(level, duration = null) {
    def transitionTime = duration != null ? duration : (settings.transitionTime ?: 0)
    logInfo("setting level to ${level}% over ${transitionTime}s")
    
    def scaledLevel = Math.round(level * 2.54)
    def transitionTimeHex = zigbee.convertToHexString((transitionTime * 10) as Integer, 4)
    
    List<Map<String, String>> cmds = []
    cmds += zigbee.command(0x0008, 0x04, zigbee.convertToHexString(scaledLevel, 2) + transitionTimeHex)
    sendZigbeeCommands(cmds)
}

// Color temperature commands
def setColorTemperature(kelvin, level = null, duration = null) {
    kelvin = Math.max(2700, Math.min(6500, kelvin as Integer))
    logInfo("setting color temperature to ${kelvin}K")
    
    List<Map<String, String>> cmds = []
    
    if (level != null) {
        cmds += setLevelCommands(level, duration)
    }
    
    cmds += setColorTemperatureCommands(kelvin, duration)
    sendZigbeeCommands(cmds)
}

def setColorTemperatureCommands(kelvin, duration = null) {
    def transitionTime = duration != null ? duration : (settings.transitionTime ?: 0)
    def mireds = Math.round(1000000 / kelvin)
    def miredsHex = zigbee.convertToHexString(mireds, 4)
    def transitionTimeHex = zigbee.convertToHexString((transitionTime * 10) as Integer, 4)
    
    return zigbee.command(0x0300, 0x0A, miredsHex + transitionTimeHex)
}

def setLevelCommands(level, duration = null) {
    def transitionTime = duration != null ? duration : (settings.transitionTime ?: 0)
    def scaledLevel = Math.round(level * 2.54)
    def transitionTimeHex = zigbee.convertToHexString((transitionTime * 10) as Integer, 4)
    
    return zigbee.command(0x0008, 0x04, zigbee.convertToHexString(scaledLevel, 2) + transitionTimeHex)
}

// Circadian rhythm functions
def enableCircadianRhythm() {
    logInfo("enabling circadian rhythm")
    device.updateSetting("circadianEnabled", true)
    state.circadianActive = true
    state.circadianPaused = false
    sendEvent(name: "circadianStatus", value: "active")
    startCircadianSchedule()
}

def disableCircadianRhythm() {
    logInfo("disabling circadian rhythm")
    device.updateSetting("circadianEnabled", false)
    state.circadianActive = false
    state.circadianPaused = false
    sendEvent(name: "circadianStatus", value: "inactive")
    unschedule(updateCircadianRhythm)
}

def startCircadianSchedule() {
    logDebug("Starting circadian rhythm schedule")
    def intervalMinutes = settings.updateInterval ?: 15
    schedule("0 */${intervalMinutes} * * * ?", updateCircadianRhythm)
    updateCircadianRhythm() // Run immediately
}

def updateCircadianRhythm() {
    // Continuar calculando mesmo com luz apagada
    // para que a temperatura esteja pronta quando ligar
    
    if (!settings.circadianEnabled) {
        return
    }
    
    def now = new Date()
    def currentMinutes = now.hours * 60 + now.minutes
    
    // Parse start and end times
    def startMinutes = parseTimeToMinutes(settings.startTime)
    def endMinutes = parseTimeToMinutes(settings.endTime)
    
    // Validar que os tempos foram parseados corretamente
    if (startMinutes == null || endMinutes == null) {
        logDebug("Error parsing time settings")
        return
    }
    
    // Evitar divisão por zero se start == end
    if (startMinutes == endMinutes) {
        logDebug("Start and end times are the same, skipping circadian update")
        return
    }
    
    logDebug("Current time: ${now.hours}:${now.minutes} = ${currentMinutes} minutes")
    logDebug("Start time: ${startMinutes} minutes, End time: ${endMinutes} minutes")
    
    def minTemp = settings.minColorTemp ?: 2700
    def maxTemp = settings.maxColorTemp ?: 6500
    
    def targetTemp
    
    if (currentMinutes < startMinutes || currentMinutes > endMinutes) {
        // Outside circadian period - use minimum (warm) temperature
        targetTemp = minTemp
    } else {
        // Inside circadian period - calculate temperature
        def totalMinutes = endMinutes - startMinutes
        def elapsedMinutes = currentMinutes - startMinutes
        def progress = elapsedMinutes / totalMinutes
        
        // Parabolic curve: starts at minTemp, peaks at maxTemp at midday, returns to minTemp
        def parabolicProgress = 1 - Math.pow((progress * 2) - 1, 2)
        targetTemp = minTemp + ((maxTemp - minTemp) * parabolicProgress)
        targetTemp = Math.round(targetTemp)
    }
    
    logDebug("Circadian calculated temperature: ${targetTemp}K")
    
    // Só aplicar se a luz estiver ligada e não estiver pausada
    if (device.currentValue("switch") == "on" && !state.circadianPaused) {
        logDebug("Applying circadian temperature: ${targetTemp}K")
        setColorTemperature(targetTemp, null, settings.transitionTime)
    } else {
        // Guardar temperatura calculada para quando ligar
        state.lastCalculatedTemp = targetTemp
        logDebug("Light is off/paused - temperature ${targetTemp}K ready for next turn on")
    }
}

def parseTimeToMinutes(timeString) {
    if (!timeString) {
        logDebug("parseTimeToMinutes: timeString is null or empty")
        return null
    }
    
    try {
        // Hubitat retorna time input no formato ISO: "2026-01-05T06:00:00.000-03:00"
        // Precisamos extrair apenas HH:mm
        
        def timeStr = timeString.toString()
        logDebug("parseTimeToMinutes input: ${timeStr}")
        
        // Se já está no formato simples HH:mm
        if (timeStr.contains(":") && !timeStr.contains("T")) {
            def parts = timeStr.split(":")
            def hours = parts[0] as Integer
            def minutes = parts[1] as Integer
            def result = hours * 60 + minutes
            logDebug("Simple format parsed: ${hours}h${minutes}m = ${result} minutes")
            return result
        }
        
        // Se está no formato ISO com "T"
        if (timeStr.contains("T")) {
            // Extrair a parte do tempo depois do "T"
            def timePart = timeStr.split("T")[1]
            // Pegar apenas HH:mm (primeiros 5 caracteres)
            def timeOnly = timePart.substring(0, 5)
            def parts = timeOnly.split(":")
            def hours = parts[0] as Integer
            def minutes = parts[1] as Integer
            def result = hours * 60 + minutes
            logDebug("ISO format parsed: ${hours}h${minutes}m = ${result} minutes")
            return result
        }
        
        logDebug("parseTimeToMinutes: Unrecognized format")
        return null
        
    } catch (Exception e) {
        log.error "Error parsing time '${timeString}': ${e.message}"
        return null
    }
}

// Configuration
def configure() {
    logDebug("Configuring device...")
    List<Map<String, String>> cmds = []
    
    // Bind clusters
    cmds += zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 3600, null) // On/Off
    cmds += zigbee.configureReporting(0x0008, 0x0000, 0x20, 1, 3600, 0x01) // Level
    cmds += zigbee.configureReporting(0x0300, 0x0007, 0x21, 1, 3600, 0x01) // Color temp
    
    sendZigbeeCommands(cmds)
}

def refresh() {
    logDebug("Refreshing device...")
    List<Map<String, String>> cmds = []
    
    cmds += zigbee.readAttribute(0x0006, 0x0000) // On/Off state
    cmds += zigbee.readAttribute(0x0008, 0x0000) // Current level
    cmds += zigbee.readAttribute(0x0300, 0x0007) // Color temperature
    
    sendZigbeeCommands(cmds)
}

// Lifecycle methods
def installed() {
    logInfo("installed()")
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "level", value: 100)
    sendEvent(name: "colorTemperature", value: 4000)
    sendEvent(name: "circadianStatus", value: "inactive")
    state.circadianActive = false
    state.circadianPaused = false
}

def updated() {
    logInfo("Preferences updated...")
    unschedule()
    
    if (settings.circadianEnabled) {
        state.circadianActive = true
        state.circadianPaused = false
        sendEvent(name: "circadianStatus", value: "active")
        startCircadianSchedule()
    } else {
        state.circadianActive = false
        state.circadianPaused = false
        sendEvent(name: "circadianStatus", value: "inactive")
    }
    
    if (logEnable) runIn(3600, logsOff)
}

// Helper methods
def sendZigbeeCommands(List<Map<String, String>> cmds) {
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmds.each {
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}

// Logging
def logsOff() {
    log.warn "Debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def logInfo(msg) {
    if (txtEnable) log.info "${device.displayName} ${msg}"
}

def logDebug(msg) {
    if (logEnable) log.debug "${device.displayName} ${msg}"
}
