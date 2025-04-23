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
 * version 1.0.0 2025-04-17 Joao - Versão inicial
 * version 1.1.0 2025-04-02 kkossev - pequenas melhorias
 */

import groovy.transform.Field
import groovy.json.JsonOutput
import hubitat.zigbee.zcl.DataType

/**
 * Definição do driver com capabilities e atributos
 */
metadata {
    definition(
        name: "Shelly 1PM Gen 4 Zigbee Driver",
        namespace: "hubitat",
        author: "Manus",
        importUrl: "https://raw.githubusercontent.com/joaomf/hubitat/refs/heads/master/Shelly%201PM%20Gen%204%20Zigbee%20Driver.groovy"
    ) {
        // Capabilities básicas
        capability "Actuator"        // Permite que o dispositivo seja controlado
        capability "Switch"          // Suporte para ligar/desligar
        capability "Refresh"         // Permite atualizar manualmente os valores
        capability "Configuration"   // Permite configurar o dispositivo
        
        // Capabilities para medição de energia
        capability "PowerMeter"        // Para medição de potência (W)
        capability "VoltageMeasurement" // Para medição de tensão (V)
        capability "CurrentMeter"      // Para medição de corrente (A)
        capability "EnergyMeter"       // Para medição de energia (kWh)
        
        // Atributos personalizados
        attribute "acFrequency", "number" // Para frequência AC (Hz)
        attribute "producedEnergy", "number" // Para energia produzida (kWh)
        
        // Fingerprint do dispositivo
        fingerprint profileId: "0104", 
                    inClusters: "0000,0003,0004,0005,0006,0B04,0702", 
                    outClusters: "0019", 
                    manufacturer: "Shelly", 
                    model: "1PM", 
                    deviceJoinName: "Shelly 1PM Gen 4"
    }
    
    /**
     * Preferências configuráveis pelo usuário
     */
    preferences {
        input name: "logEnable", type: "bool", title: "Habilitar logs de depuração", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Habilitar logs de texto", defaultValue: true
        
        // Configurações de relatórios
        input name: "reportingInterval", type: "number", title: "Intervalo de relatórios (segundos)", defaultValue: 60, range: "10..3600"
        
        // Configurações de calibração
        input name: "powerCalibration", type: "decimal", title: "Calibração de potência (%)", defaultValue: 0
        input name: "voltageCalibration", type: "decimal", title: "Calibração de tensão (%)", defaultValue: 0
        input name: "currentCalibration", type: "decimal", title: "Calibração de corrente (%)", defaultValue: 0
        input name: "acFrequencyCalibration", type: "decimal", title: "Calibração de frequência AC (offset)", defaultValue: 0
    }
}

// Constantes para clusters Zigbee
@Field static final Integer CLUSTER_BASIC = 0x0000
@Field static final Integer CLUSTER_ON_OFF = 0x0006
@Field static final Integer CLUSTER_SIMPLE_METERING = 0x0702
@Field static final Integer CLUSTER_ELECTRICAL_MEASUREMENT = 0x0B04

// Constantes para atributos
@Field static final Integer ATTR_ON_OFF = 0x0000
@Field static final Integer ATTR_PRESENT_VALUE = 0x0055
@Field static final Integer ATTR_ACTIVE_POWER = 0x050B
@Field static final Integer ATTR_RMS_VOLTAGE = 0x0505
@Field static final Integer ATTR_RMS_CURRENT = 0x0508
@Field static final Integer ATTR_AC_FREQUENCY = 0x0300
@Field static final Integer ATTR_ENERGY_DELIVERED = 0x0000
@Field static final Integer ATTR_ENERGY_PRODUCED = 0x0001
@Field static final Integer ATTR_POWER_FACTOR = 0x0510

// Constantes para divisores
@Field static final Integer DIVISOR_POWER = 100
@Field static final Integer DIVISOR_VOLTAGE = 100
@Field static final Integer DIVISOR_CURRENT = 100
@Field static final Integer DIVISOR_FREQUENCY = 100
@Field static final Integer DIVISOR_ENERGY = 1000000

/**
 * Método chamado quando o dispositivo é instalado
 */
def installed() {
    logDebug "Instalado"
    initialize()
}

/**
 * Método chamado quando as configurações do dispositivo são atualizadas
 */
def updated() {
    logDebug "Atualizado"
    initialize()
}

/**
 * Inicializa o dispositivo e configura os relatórios
 */
def initialize() {
    logDebug "Inicializando"
    unschedule()
    
    if (logEnable) {
        runIn(1800, "logsOff")  // Desativa logs de depuração após 30 minutos
    }
    
    // Configurar relatórios
    configure()
}

/**
 * Desativa os logs de depuração
 */
def logsOff() {
    log.warn "Logs de depuração desativados"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

/**
 * Configura os relatórios periódicos do dispositivo
 * @return Lista de comandos Zigbee para configuração
 */
def configure() {
    logDebug "Configurando relatórios periódicos"
    
    def interval = reportingInterval ? reportingInterval : 60
    def minInterval = 10
    def maxInterval = interval
    
    def cmds = []
    
    // Configurar relatório para estado on/off
    cmds += zigbee.configureReporting(CLUSTER_ON_OFF, ATTR_ON_OFF, DataType.BOOLEAN, 0, maxInterval, 1)
    
    // Configurar relatório para potência
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_ACTIVE_POWER, DataType.INT16, minInterval, maxInterval, 1)
    
    // Configurar relatório para tensão
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_VOLTAGE, DataType.UINT16, minInterval, maxInterval, 1)
    
    // Configurar relatório para corrente
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_CURRENT, DataType.UINT16, minInterval, maxInterval, 1)
    
    // Configurar relatório para frequência AC
    cmds += zigbee.configureReporting(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_AC_FREQUENCY, DataType.UINT16, minInterval, maxInterval, 1)
    
    // Configurar relatório para energia
    cmds += zigbee.configureReporting(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_DELIVERED, DataType.UINT48, minInterval, maxInterval, 1)
    cmds += zigbee.configureReporting(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_PRODUCED, DataType.UINT48, minInterval, maxInterval, 1)
    
    logDebug "Enviando comandos de configuração: ${cmds}"
    return cmds
}

/**
 * Atualiza manualmente os valores do dispositivo
 * @return Lista de comandos Zigbee para leitura de atributos
 */
def refresh() {
    logDebug "Atualizando valores"
    
    def cmds = []
    
    // Ler estado on/off
    cmds += zigbee.readAttribute(CLUSTER_ON_OFF, ATTR_ON_OFF)
    
    // Ler potência
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_ACTIVE_POWER)
    
    // Ler tensão
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_VOLTAGE)
    
    // Ler corrente
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_RMS_CURRENT)
    
    // Ler frequência AC
    cmds += zigbee.readAttribute(CLUSTER_ELECTRICAL_MEASUREMENT, ATTR_AC_FREQUENCY)
    
    // Ler energia
    cmds += zigbee.readAttribute(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_DELIVERED)
    cmds += zigbee.readAttribute(CLUSTER_SIMPLE_METERING, ATTR_ENERGY_PRODUCED)
    
    logDebug "Enviando comandos de atualização: ${cmds}"
    return cmds
}

/**
 * Liga o dispositivo
 * @return Comando Zigbee para ligar
 */
def on() {
    logDebug "Ligando"
    return zigbee.on()
}

/**
 * Desliga o dispositivo
 * @return Comando Zigbee para desligar
 */
def off() {
    logDebug "Desligando"
    return zigbee.off()
}

/**
 * Processa mensagens Zigbee recebidas do dispositivo
 * @param description String contendo a descrição da mensagem Zigbee
 * @return null
 */
def parse(String description) {
    logDebug "Recebido: ${description}"
    
    def descMap = zigbee.parseDescriptionAsMap(description)
    logDebug "Mapa de descrição: ${descMap}"
    
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
 * Processa o estado do switch (on/off)
 * @param descMap Mapa contendo os dados do atributo
 */
private void processSwitchState(Map descMap) {
    if (descMap.value == null) return
    
    def value = descMap.value == "01" ? "on" : "off"
    logInfo "Estado do switch: ${value}"
    
    sendEvent(name: "switch", value: value)

}

/**
 * Processa medições elétricas (potência, tensão, corrente, frequência)
 * @param descMap Mapa contendo os dados do atributo
 */
private void processElectricalMeasurement(Map descMap) {
    if (descMap.value == null) return
    
    String attrId = descMap.attrId
    Integer rawValue = Integer.parseInt(descMap.value, 16)
    if (rawValue == null) return // Ignora valores nulos
    
    switch (attrId) {
        case "050B": // Potência ativa
            BigDecimal powerDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_POWER), 2, BigDecimal.ROUND_HALF_UP)
            if (powerCalibration) {
                powerDouble = powerDouble.multiply(new BigDecimal(1 + (powerCalibration / 100)))
            }
            String powerString = String.format("%.2f", powerDouble)
            logInfo "Potência: ${powerString} W"
            sendEvent(name: "power", value: powerString, unit: "W")
            break
            
        case "0505": // Tensão RMS
            BigDecimal voltageDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_VOLTAGE), 1, BigDecimal.ROUND_HALF_UP)
            if (voltageCalibration) {
                voltageDouble = voltageDouble.multiply(new BigDecimal(1 + (voltageCalibration / 100)))
            }
            String voltageString = String.format("%.1f", voltageDouble)
            logInfo "Tensão: ${voltageString} V"
            sendEvent(name: "voltage", value: voltageString, unit: "V")
            break
            
        case "0508": // Corrente RMS
            BigDecimal currentDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_CURRENT), 3, BigDecimal.ROUND_HALF_UP)
            if (currentCalibration) {
                currentDouble = currentDouble.multiply(new BigDecimal(1 + (currentCalibration / 100)))
            }
            String currentString = String.format("%.3f", currentDouble)
            logInfo "Corrente: ${currentString} A"
            sendEvent(name: "amperage", value: currentString, unit: "A")
            break
            
        case "0300": // Frequência AC
            BigDecimal frequencyDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_FREQUENCY), 2, BigDecimal.ROUND_HALF_UP)
            if (acFrequencyCalibration) {
                frequencyDouble = frequencyDouble.add(new BigDecimal(acFrequencyCalibration))
            }
            String frequencyString = String.format("%.2f", frequencyDouble)
            logInfo "Frequência AC: ${frequencyString} Hz"
            sendEvent(name: "acFrequency", value: frequencyString, unit: "Hz")
            break
    }
}

/**
 * Processa medições de energia (consumida e produzida)
 * @param descMap Mapa contendo os dados do atributo
 */
private void processEnergyMeasurement(Map descMap) {
    if (descMap.value == null) return
    
    String attrId = descMap.attrId
    Long rawValue = Long.parseLong(descMap.value, 16)
    if (rawValue == null) return // Ignora valores nulos
    
    switch (attrId) {
        case "0000": // Energia entregue (consumida)
            BigDecimal energyDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_ENERGY), 3, BigDecimal.ROUND_HALF_UP)
            String energyString = String.format("%.3f", energyDouble)
            logInfo "Energia consumida: ${energyString} kWh"
            sendEvent(name: "energy", value: energyString, unit: "kWh")
            break
            
        case "0001": // Energia produzida
            BigDecimal producedEnergyDouble = new BigDecimal(rawValue).divide(new BigDecimal(DIVISOR_ENERGY), 3, BigDecimal.ROUND_HALF_UP)
            String producedEnergyString = String.format("%.3f", producedEnergyDouble)
            logInfo "Energia produzida: ${producedEnergyString} kWh"
            sendEvent(name: "producedEnergy", value: producedEnergyString, unit: "kWh")
            break
    }
}

/**
 * Registra mensagem de depuração se habilitado
 * @param msg Mensagem a ser registrada
 */
private void logDebug(String msg) {
    if (logEnable) {
        log.debug "${device.displayName}: ${msg}"
    }
}

/**
 * Registra mensagem informativa se habilitado
 * @param msg Mensagem a ser registrada
 */
private void logInfo(String msg) {
    if (txtEnable) {
        log.info "${device.displayName}: ${msg}"
    }
}
