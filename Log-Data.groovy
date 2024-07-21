    /** Log-Data (and Chart-Data)
     *  
     *
     * Tool Tip code thanks to SBurke,  https://community.hubitat.com/t/tooltips-for-app-input-switches/137414
     *
     *        Roughly 20KB per day for decently-frequent (~8 min avg) updates.
    */

    import groovy.transform.Field
    import java.util.regex.Matcher
    import java.lang.Math
    import java.util.Calendar
    import org.apache.commons.lang3.time.DateUtils

    // Slurper isn't working with Hubitat. Using native call.
    // import groovy.json.JsonSlurper  // https://docs.groovy-lang.org/latest/html/gapi/groovy/json/JsonSlurper.html
    import groovy.json.JsonOutput   // https://docs.groovy-lang.org/latest/html/gapi/groovy/json/JsonOutput.html

    public static String version()      {  return "1.0.0"  }
    def getThisCopyright(){"&copy; 2024 robomac Tony McNamara"}

    @Field final String NameAttrDelim = '%'

    @Field final Integer UNINITIALIZED = -999

    // Shows on Add User App page
    definition(
        name: "Log Data",
        namespace: "robomac",
        author: "Tony McNamara",
        description: "Captures selected device/attribute events as JSON.",
        category: "", // Not currently used
        importUrl: "", // Github link, but not currently used.
        documentationLink: "", // URL - if present, a ? in app page sends users to it.
        singleInstance: false, // One instance per chart. Might be a bad idea.
        singleThreaded: true, // Only run one copy of this one instance at a time.
        iconUrl: "", // Not currently used
        iconX2Url: "", // Not currently used
        iconX3Url: "" // Not currently used
        )   

    preferences {
        // install == true to be final page (provides Done button). These can be defined inline or separately.
        page(name: "mainPage", title: "HACharter Setup", install: true, uninstall: true) 
    }
        
def mainPage() {
    // Take care of one-time basics.  https://docs2.hubitat.com/en/developer/app/preferences
    if (state.initialized == null) initialize()
    
    String tooltipStyle = "<style> /* The icon */ .help-tip{     /* HE styling overrides */     box-sizing: content-box;     white-space: collapse;          display: inline-block;     margin: auto;     vertical-align: text-top;     text-align: center;     border: 2px solid white;     border-radius: 50%;     width: 16px;     height: 16px;     font-size: 12px;          cursor: default;     color: white;     background-color: #2f4a9c; } /* Add the icon text, e.g. question mark */ .help-tip:before{     white-space: collapse;     content:'?';     font-family: sans-serif;     font-weight: normal;     color: white;     z-index: 10; } /* When hovering over the icon, display the tooltip */ .help-tip:hover p{     display:block;     transform-origin: 100% 0%;     -webkit-animation: fadeIn 0.5s ease;     animation: fadeIn 0.5s ease; } /* The tooltip */ .help-tip p {     /* HE styling overrides */     box-sizing: content-box;          /* initially hidden */     display: none;          position: relative;     float: right;     width: 178px;     height: auto;     left: 50%;     transform: translate(204px, -90px);     border-radius: 3px;     box-shadow: 0 0px 20px 0 rgba(0,0,0,0.1);         background-color: #FFFFFF;     padding: 12px 16px;     z-index: 999;          color: #37393D;          text-align: center;     line-height: 18px;     font-family: sans-serif;     font-size: 12px;     text-rendering: optimizeLegibility;     -webkit-font-smoothing: antialiased;      } .help-tip p a {     color: #067df7;     text-decoration: none;     z-index: 100; } .help-tip p a:hover {     text-decoration: underline; } .help-tip-header {     font-weight: bold;     color: #6482de; } /* CSS animation */ @-webkit-keyframes fadeIn {     0% { opacity:0; }     100% { opacity:100%; } } @keyframes fadeIn {     0% { opacity:0; }     100% { opacity:100%; } } </style>";
    String pageTitlePreface = (thisChartName == null? "" : "${thisChartName} ")
    dynamicPage(name: "mainPage", title: "<center><h2>${pageTitlePreface} Event Logger</h2></center>", uninstall: true, install: true, singleThreaded:true) {
        section{
            paragraph "${tooltipStyle}"
            input "thisChartName", "text", title: "Event Grabber Instance Name", submitOnChange: true
            if(thisChartName) app.updateLabel("$thisChartName")

            input (name:"chartAttribute", title:"<b>Attribute to Chart</b>${getZindexToggle('chartAttribute')} ${getTooltipHTML('Attribute Selection', 'Charting and Logging is grouped by Attributes. The same device may be selected more than once, once per attribute.')}", type:"enum", options: [1:"Temperature", 2:"Battery Level", 3:"Humidity", 4:"Luminance"], required:true, submitOnChange:true, width:3)

            input (name:"daysKeepData", title:"Time to keep data${getZindexToggle('daysKeepData')} ${getTooltipHTML('Data Retention', 'Data is stored in File Manager in JSON files. Over time it will get very long. At least for now, the entire file is loaded and parsed both to add events and to chart it, so smaller is better.')}", type:"enum", options: [0: "Forever - don't purge", 7:"One Week", 14:"Two Weeks", 21:"Three Weeks", 31:"A Month"], required:true, submitOnChange:true, width:3)

            switch (chartAttribute?chartAttribute.toInteger():0) {
                case 1: 
                    input "selectedTempDevices", "capability.temperatureMeasurement", title: "Temperature Sensors to be monitored" , multiple: true, required: false, defaultValue: null, width: 6
                    break;
                case 2: 
                    input "selectedBattDevices", "capability.battery", title: "Battery Devices to be Monitored" , multiple: true, required: false, defaultValue: null, width: 6
                    break;
                case 3: 
                    input "selectedHumidDevices", "capability.relativeHumidityMeasurement", title: "Humidity Devices to be Monitored" , multiple: true, required: false, defaultValue: null, width: 6
                    break;
                case 4: 
                    input "selectedLightDevices", "capability.illuminanceMeasurement", title: "Light Devices to be Monitored" , multiple: true, required: false, defaultValue: null, width: 6
                    break;
            }
            // Empty the devices map. It will be of the form: DisplayName - Attribute (because that's what comes in with an Event): { DataMin, DataMax}
            state.selectedDevices = [:]
            for (dev in selectedTempDevices) {
                state.selectedDevices << [(deviceNameAndAttributeToVar(dev.getDisplayName(), "temperature")):["dataMin":UNINITIALIZED, "dataMax":UNINITIALIZED,"attribute":"temperature"]]
            }
            for (dev in selectedBattDevices) {
                state.selectedDevices << [(deviceNameAndAttributeToVar(dev.getDisplayName(), "battery")):["dataMin":UNINITIALIZED, "dataMax":UNINITIALIZED,"attribute":"battery"]]
            }
            for (dev in selectedHumidDevices) {
                state.selectedDevices << [(deviceNameAndAttributeToVar(dev.getDisplayName(), "humidity")):["dataMin":UNINITIALIZED, "dataMax":UNINITIALIZED,"attribute":"humidity"]]
            }
            for (dev in selectedLightDevices) {
                state.selectedDevices << [(deviceNameAndAttributeToVar(dev.getDisplayName(), "illuminance")):["dataMin":UNINITIALIZED, "dataMax":UNINITIALIZED,"attribute":"illuminance"]]
            }
            if (logInfo) log.info ("${thisChartName} Selected Devices: ${state.selectedDevices}")
        }       
        section(hideable:true, hidden:true, "Debugging/Advanced") { 
            paragraph "<div style='background:#FFFFFF; height: 1px; margin-top:0em; margin-bottom:0em ; border: 0;'></div>"    //Horizontal Line
            paragraph "Subscribing and Unsubscribing (Pausing) Events${getZindexToggle('enableEvents')} ${getTooltipHTML('Subscribing to Events','This is done automatically on installation. The only reason to do this would be if you Disabled them to pause activity.')}<br/>"
            input (name: "enableEvents", type: "button", title: "Enable Events (Subscribe)", backgroundColor: "#27ae61", textColor: "white", submitOnChange: true, width: 2)
            input (name: "unsubscribeEvents", type: "button", title: "Disable Events (Unsubscribe)", backgroundColor: "#27ae61", textColor: "white", submitOnChange: true, width: 3)
            input (name: "logInfo",  type: "bool", title: "<b>Enable info logging?</b>", defaultValue: false, submitOnChange: true, width: 2)
            input (name: "logTrace", type: "bool", title: "<b>Enable trace logging?</b>", defaultValue: false, submitOnChange: true, width: 2)
            input (name: "logDebug", type: "bool", title: "<b>Enable debug logging?</b>", defaultValue: false, submitOnChange: true, width: 2)
        }

        section { // child apps
            app name: "myChildApps", appName: "Chart Data", namespace: "robomac", title: "Create New Chart...", multiple: true
        }
    }   // Dynamic Page
} // mainPage()

    /** Tool Tip notes: Requires the style be defined early on, plugged in via paragraph, and then the headers getting tooltips call both functions below
     *  in their title, to get the index and plug it into the created tooltip code. */
    // Tool Tip Code
    String getZindexToggle(String setting, int low = 10, int high = 50) {
       return "<style> div:has(label[for^='settings[${setting}]']) { z-index: ${low}; } div:has(label):has(div):has(span):hover { z-index: ${high}; } </style>";
    }

    /** Creates the HTML to invoke the tooltiop. URL/Label are optional; if included, appear at the bottom as a link. 
     *  Note that tooltips don't work well for buttons because the device is inside a boundary.
     */
    String getTooltipHTML(String heading, String tooltipText, String hrefURL="", String hrefLabel='View Documentation'){
        String docLink = ""
        if (hrefURL.size()>0) {
            docLink = "<br/> <a href='${hrefURL}' target='_blank'>${hrefLabel}</a>"
        }
        return "<span class='help-tip'> <p> <span class='help-tip-header'>${heading}</span> <br/>${tooltipText}${docLink} </p> </span>";
    }

    // Set up basics we rely on for later, including defaults.
    def initialize(){
        if ( state.initialized == true ){
            if (logDebug) log.debug ("initialize: Initialize has already been run. Exiting")
            //return
        }
        
        //Set the flag so that this should only ever run once.
        state.initialized = true
        app.updateSetting("chartRebuildIntervalSeconds", 120)
        app.updateSetting("time24H", true)
        app.updateSetting("defaultWidth", 200)
        app.updateSetting("defaultHeight", 190)
        app.updateSetting("defaultMaxDays", 10)
        // The min and max in the JSON files. This is recalculated when purging, updated-as-needed when 
        app.updateSetting("chartMin", UNINITIALIZED)
        app.updateSetting("chartMax", UNINITIALIZED)
        
        // Aggregate metrics down every two hours
        app.updateSetting("summarizePeriodMinutes", 120)
    }

    // Called when app first installed
    def installed() {
    // for now, just write entry to "Logs" when it happens:
        log.trace "${thisChartName} installed(). Subscribing to events."
        subscribeToEvents()
        updateJSONStats()
    }

    // Called when user presses "Done" button in app
    def updated() {
        log.trace "updated()"
        installed()
    }

    // Called when app uninstalled
    def uninstalled() {
        unsubscribe()
        log.trace "uninstalled()"
    // Most apps would not need to do anything here

    }

    def getSelectedDevicesForAttribute(String attr) {
        def devices = []
        for (devItem in state.selectedDevices) {
            def devName=""
            def attribute = ""
            (devName, attribute) = varDeviceToNameAttr(devItem.key)
            if (attr == attribute)
            {
                devices.add(devName)
            }
        }
        return devices
    }

    // Returns a List of attributes selected.
    def listAttributes() {
        def retval = []
        for (devItem in state.selectedDevices) {
            def devName=""
            def attribute = ""
            (devName, attribute) = varDeviceToNameAttr(devItem.key)
            if (!retval.contains(attribute))
            {
                retval.add(attribute)
            }
        }
        return retval
    }

    def logEvent(evt) {
        // Omitting source (usually DEVICE) and evt.description (seems to always be null)
        log.info("${thisChartName}:\n\tDesc: ${evt.descriptionText}\n\tAttribute ${evt.name}\n\tAttribute Value ${evt.value}\n\tUnit: ${evt.unit}\n\tDisplay Name: ${evt.getDisplayName()}\n\tDate: ${evt.getDate()}")
    }

    def logDevice(dvc) {
        if (dvc == null) 
        { 
            log.warn("${thisChartName}: logDevice: No device specified")
            return
        }
        log.info("${thisChartName}: Device: ${dvc.getName()} \tDisplay Name: ${dvc.getDisplayName()}")
    }

    /** Handle Event - When a published event - e.g. temp change - comes in. */
    def handler(evt) {
        if (logEvent) logEvent(evt)
        saveEvent(evt)
    }

    // Save one event - load JSON, add, save JSON
    def saveEvent(def evt) {
        def device = evt.getDevice()
        def attributeName = evt.name
        String fname = createDataFilename(device.getDisplayName(), attributeName)
        def deviceHistory = readDeviceHistoryFromFile(fname) // Should be a list
        if (deviceHistory == null) {
            log.warn("${thisChartName}: Device History ${fname} for ${device.getDisplayName()} not found.")
            deviceHistory = []
        }
        def devEvent = [:]
        devEvent["time"] = evt.getUnixTime()
        devEvent["val"] = toFloat(evt.value)
        devEvent["dateStr"] = evt.getDate()
        deviceHistory.add(devEvent)
        writeToFile(fname, deviceHistory)
        if (logDebug) log.debug("${thisChartName}: Added event to file ${fname}")
    }


    /** From state.selectedDevices key, give device display name and attribute.
     *  It's really just break on %, unlike filename which is _
     */
    def varDeviceToNameAttr(String deviceString) {
        return deviceString.tokenize(NameAttrDelim)
    }

    /** From deviceName and Attribute create var label. Just inserts NameAttrDelim */
    String deviceNameAndAttributeToVar(String devName, String attr) {
        return "${devName}${NameAttrDelim}${attr}"
    }

    def subscribeToEvents() {
        unsubscribe()
        if (logDebug) log.debug("${thisChartName}: Subscribing to events.")
        // Work-around due to problems with the map...
        for (dev in selectedTempDevices) {
            if (logDebug) log.debug("${thisChartName}: Subscribing to ${dev.getDisplayName()}-temperature.")
            subscribe(dev, "temperature", handler)
            retrievePastEvents(dev, "temperature")                    
        }
        for (dev in selectedBattDevices) {
            if (logDebug) log.debug("${thisChartName}: Subscribing to ${dev.getDisplayName()}-battery.")
            subscribe(dev, "battery", handler)
            retrievePastEvents(dev, "battery")
        }
        for (dev in selectedHumidDevices) {
            if (logDebug) log.debug("${thisChartName}: Subscribing to ${dev.getDisplayName()}-humidity.")
            subscribe(dev, "humidity", handler)
            retrievePastEvents(dev, "humidity")
        }
        for (dev in selectedLightDevices) {
            if (logDebug) log.debug("${thisChartName}: Subscribing to ${dev.getDisplayName()}-illuminance.")
            subscribe(dev, "illuminance", handler)
            retrievePastEvents(dev, "illuminance")
        }
    }

    // Goes through the list of devices and creates JSON of their past events.
    // Should only be done after initial install.
    def retrievePastEvents(def device, String attributeName) {
        String fname = createDataFilename(device.getDisplayName(), attributeName)
        // Check to see if it exists. If it does, for now, don't load interim events.
        def pastList = readDeviceHistoryFromFile(fname)
        if ((pastList != null) && (pastList.size() > 1)) {
            if (logInfo)
                log.info("${thisChartName}: Found ${pastList.size()} past events for ${device.getDisplayName()}. Skipping loading event history.")
            return
        }
        def devEventList = []
        if (logEvent) logDevice(device)
        def oldEvents = device.events([max:2000])   // Max that the U.I. supports setting.
        if (logDebug) log.info("${thisChartName}: Found ${oldEvents.size()} events for ${device.getDisplayName()}")
        for (evt in oldEvents) {
            // Need to filter to only events we actually wanted.
            if (evt.name == attributeName) {
                def devEvent = [:]
                devEvent["time"] = evt.getUnixTime()
                devEvent["val"] = toFloat(evt.value)
                devEvent["dateStr"] = evt.getDate()
                devEventList.add(devEvent)
            }
        }
        if (devEventList.size() > 0) {
            // These default to the reverse sort of what I want. I always want oldest on top, newest append to bottom.
            // Fun fact: <=> is called the spaceship operator (<=>), which calls the compareTo method.
            devEventList.sort { a, b -> a.time <=> b.time }
            writeToFile(fname, devEventList)
        } else {
            if (logDebug) log.debug("${thisChartName}: No historical events found for ${device.getDisplayName()}")
        }
    }


    // Per https://docs2.hubitat.com/en/developer/app/preferences, default button handler
    def appButtonHandler(buttonName) {    
        switch(buttonName) {
            case "enableEvents":    //Setup a subscription to the currently selected device list and the attribute type relevant to that list.
                subscribeToEvents()
                break;
            case "unsubscribeEvents":
                unsubscribe()
                break
        }
    }

    // End Event Subscription Management

    // Return the type of the passed in object, as string.
    static String typeOf(obj){
        if(obj instanceof String){return 'String'}
        else if(obj instanceof com.hubitat.app.ParentDeviceWrapper){return 'ParentDeviceWrapper'}
        else if(obj instanceof com.hubitat.app.ChildDeviceWrapper){return 'ChildDeviceWrapper'}
        else if(obj instanceof com.hubitat.app.DeviceWrapper){return 'DeviceWrapper'}
        else if(obj instanceof List){return 'List'}
        else if(obj instanceof ArrayList){return 'ArrayList'}
        else if(obj instanceof Integer){return 'Int'}
        else if(obj instanceof BigInteger){return 'BigInt'}
        else if(obj instanceof Long){return 'Long'}
        else if(obj instanceof Boolean){return 'Bool'}
        else if(obj instanceof BigDecimal){return 'BigDec'}
        else if(obj instanceof Float){return 'Float'}
        else if(obj instanceof Byte){return 'Byte'}
        else{ return 'unknown'}
    }

    /** Hub Filenames must be only alpha-numeric + _-. Anything else will silently fail. Spaces are not allowed. */
    String cleanseFileName(String filename) {
        return filename.replaceAll(/[^A-Za-z0-9\._-]/, "")
    }

    // One data file per device/attribute
    String createDataFilename(String deviceName, String attributeName) {
        String createdName = deviceName + '_' + attributeName+".json"
        createdName = cleanseFileName(createdName)
        return createdName
    }

    // Loop through each device in the list, for the specified attribute, and purge the file. 
    // If the same device is being logged with different attributes, those are distinct calls.
    def purgePastDeviceAttribute(def devices, def attribute, Date cutoff) {
        for (dev in devices) {
            if (logDebug) log.debug("${thisChartName}: Purging ${dev} ${attribute} to ${cutoff}.")
            def retainedEvents = []
            def fname = parent.createDataFilename(dev, deviceCategory)
            def jsonMapList = readDeviceHistoryFromFile(fname)
            for (mapItem in jsonMapList) {
                eventDate = new Date(mapItem["time"])
                if (eventDate.after(cutoff)) { // write to output 
                    retainedEvents << mapItem
                }
            }
            writeToFile(fname, retainedEvents)
       }
    }

    /** This could be made more efficient by treating it as text file regions, but the time taken doesn't seem worth saving. */
    def purgePastData(){
        if (daysKeepData > 0) {
            Date startTime = DateUtils.addDays(now, -1*daysKeepData)
            purgePastDeviceAttribute()
            // read and copy
            purgePastDeviceAttribute(selectedTempDevices, "temperature", cutoff)
            purgePastDeviceAttribute(selectedBattDevices, "battery", cutoff)
            purgePastDeviceAttribute(selectedHumidDevices, "humidity", cutoff)
            purgePastDeviceAttribute(selectedLightDevices, "illuminance", cutoff)
        }
    }

    /** Pushes object to hub as fname. */
    def writeToFile(String fname, def obj){
        uploadHubFile(fname, groovy.json.JsonOutput.toJson(obj).getBytes())
    }

    /** Returns a list of maps. Currently the map elements are: val (float), dateStr (string) and time (long). 
     *  time and dateStr overlap; dateStr is included to make it easier to read as a human.    */
    def readDeviceHistoryFromFile(String fname) { // Should be a list
        byte[] fdata 
        try {
            fdata = downloadHubFile(fname)
        } catch (Exception ex) {
            log.error("${thisChartName}: File ${fname} not found. Not loaded. ${ex}")
            return []
        }

        if (fdata == null) {
            log.error("${thisChartName}: File ${fname} was empty.")
            return []
        }
        def retObj = parseJson(new String(fdata))
        if (retObj == null) {
            log.error("${thisChartName}: File ${fname} could not be parsed.")
            return []
        }
        return retObj
    }

    // ****************  SVG BUILD ******************
    // Chart all files from timestamps specified, for width specified.
    List<String> getAllDataFilenames() {
        def retval =[]
        state.selectedDevices.each{ devItem, val -> 
            def devName=""
            def attribute = ""
            (devName, attribute) = varDeviceToNameAttr(devItem)
            retval.add(createDataFilename(devName, attribute))
        }
        if (logDebug) log.debug("${thisChartName}: all filenames: ${retval}")
        return retval
    }


    float toFloat(def val) {
        try {
            if(val instanceof String){return Float.parseFloat(val) }
            return (float)val
            } catch(Exception ex) {
                log.error("${thisChartName}: Error parsing Event Value ${val} into float. ${ex}")
            }
        return (float)UNINITIALIZED    
    }

    def updateJSONStats(){
        // TODO - This should iterate through the selectedDevices map to do this.
        def filenames = getAllDataFilenames()
        for (fname in filenames) {
            def jsonMapList = readDeviceHistoryFromFile(fname)
            for (mapItem in jsonMapList) {
                // if (logTrace) log.trace("In ${fname} found value ${mapItem["val"]} time ${mapItem["dateStr"]}")
                if ((state.dataMax == UNINITIALIZED) || (mapItem["val"] > state.dataMax )) {
                    state.dataMax = mapItem["val"]
                }
                if ((state.dataMin == UNINITIALIZED) || (mapItem["val"] < state.dataMin )) {
                    state.dataMin = mapItem["val"]
                }
            }
        }
        if (logDebug) { 
            log.debug("${thisChartName}: Set data max to ${state.dataMax} and data min to ${state.dataMin}")
        }
    }

