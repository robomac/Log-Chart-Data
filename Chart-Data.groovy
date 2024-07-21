/*
    Child app to Event Grabber. 
        Accepts from Event Grabber:
            - Attributes for which there are devices
            - Devices within those Attributes we are grabbing data for.
        (Really a list of Device Display Name / Attribute, but this is the way it's used.)
    */

    import groovy.transform.Field
    import java.util.regex.Matcher
    import java.lang.Math
    import java.util.Calendar
    import org.apache.commons.lang3.time.DateUtils
    import groovy.time.TimeCategory

    public static String version()      {  return "1.0.0"  }
    def getThisCopyright(){"&copy; 2024 robomac Tony McNamara"}

    /** No more than six things may be graphed at a time. That's the limit of colors selectable currently. */
    @Field static maxDevices = 6
    /** Names for numbers of the various color map entries. Background may be omitted for transparency. */
    @Field static colorEnum = ["Series1", "Series2", "Series3", "Series4", "Series5", "Series6", "Axis", "Labels", "Title", "Background"]
    /** Colors for dark theme */
    @Field static chartDarkColors = """["deeppink", "aqua", "Chartreuse", "magenta", "orange", "red", "white","LightCyan","#C0C0C0","#303030"]"""

    // At 12pt, Upper case O = 18, 7 = 11, "Sensorama" = 106 = 12 px per char. "Bedroom" = 90 = 13px per char
    // Obviously also font-dependent
    @Field final Integer PixPerChar12Pt = 14

    @Field final Integer UNINITIALIZED = -999
    // Shows on Add User App page
    definition(
        name: "Chart Data",
        namespace: "robomac",
        author: "Tony McNamara",
        description: "Builds charts and graphs from Log Data data.",
        category: "", // Not currently used
        importUrl: "", // Github link, but not currently used.
        documentationLink: "", // URL - if present, a ? in app page sends users to it.
        singleInstance: false, // One instance per chart.
        singleThreaded: true, // Only run one copy of this one instance at a time.
        parent: "robomac:Log Data",
        iconUrl: "", // Not currently used
        iconX2Url: "", // Not currently used
        iconX3Url: "" // Not currently used
        )

    preferences {
        // install == true to be final page (provides Done button). These can be defined inline or separately.
        page(name: "mainPage", title: "Chart Data Setup", install: true, uninstall: true) 
    }
        
    def mainPage() {
        if (state.initialized == null) initialize()
        String tooltipStyle = "<style> /* The icon */ .help-tip{     /* HE styling overrides */     box-sizing: content-box;     white-space: collapse;          display: inline-block;     margin: auto;     vertical-align: text-top;     text-align: center;     border: 2px solid white;     border-radius: 50%;     width: 16px;     height: 16px;     font-size: 12px;          cursor: default;     color: white;     background-color: #2f4a9c; } /* Add the icon text, e.g. question mark */ .help-tip:before{     white-space: collapse;     content:'?';     font-family: sans-serif;     font-weight: normal;     color: white;     z-index: 10; } /* When hovering over the icon, display the tooltip */ .help-tip:hover p{     display:block;     transform-origin: 100% 0%;     -webkit-animation: fadeIn 0.5s ease;     animation: fadeIn 0.5s ease; } /* The tooltip */ .help-tip p {     /* HE styling overrides */     box-sizing: content-box;          /* initially hidden */     display: none;          position: relative;     float: right;     width: 178px;     height: auto;     left: 50%;     transform: translate(204px, -90px);     border-radius: 3px;     box-shadow: 0 0px 20px 0 rgba(0,0,0,0.1);         background-color: #FFFFFF;     padding: 12px 16px;     z-index: 999;          color: #37393D;          text-align: center;     line-height: 18px;     font-family: sans-serif;     font-size: 12px;     text-rendering: optimizeLegibility;     -webkit-font-smoothing: antialiased;      } .help-tip p a {     color: #067df7;     text-decoration: none;     z-index: 100; } .help-tip p a:hover {     text-decoration: underline; } .help-tip-header {     font-weight: bold;     color: #6482de; } /* CSS animation */ @-webkit-keyframes fadeIn {     0% { opacity:0; }     100% { opacity:100%; } } @keyframes fadeIn {     0% { opacity:0; }     100% { opacity:100%; } } </style>";
        String pageTitlePreface = (thisChartName == null? "" : "${thisChartName} ")
        dynamicPage(name: "mainPage", title: "<center><h2>${pageTitlePreface}Chart Data</h2></center>", uninstall: true, install: true, singleThreaded:true) {
            section {
                paragraph "${tooltipStyle}"
                if (thisChartName != null) {
                    paragraph "<b>Chart URL:</b> <a href=\"http://${location.hub.localIP}/local/${parent.cleanseFileName(thisChartName)}.svg\" target=\"_blank\">\"http://${location.hub.localIP}/local/${parent.cleanseFileName(thisChartName)}.svg\"</a> "
                }
                input "thisChartName", "text", title: "Name this chart", submitOnChange: true, width:4

                if(thisChartName) app.updateLabel("$thisChartName")
                // Select attribute to chart
                def allowedAttributes = parent.listAttributes()
                input (name:"deviceCategory", title:"<b>Attribute to Graph</b>${getZindexToggle('deviceCategory')} ${getTooltipHTML('Attribute to Chart','Each chart (instance) can only chart one attribute, but <i>can</i> chart many devices of that attribute. This is because attributes and units are inextricably linked.</br>Only attributes tracked in the parent Logger app are listed here.')}", type:"enum", options: allowedAttributes, required:true, state: selectOk?.devicePage ? "complete" : null, submitOnChange:true, width:3)
                devicesAttr = []
                if (parent != null) {
                    devicesAttr = parent.getSelectedDevicesForAttribute(deviceCategory)
                }
                input (name:"deviceList", title:"<b>Devices measuring ${deviceCategory}</b>${getZindexToggle('enableEvents')} ${getTooltipHTML('Devices to Chart','Only devices that the parent Logger app is tracking for the selected attribute are listed. To chart an unlisted device, first track this attribute for that device in the parent Logger.')}", type:"enum", options:devicesAttr, multiple: true, required: false, defaultValue: null, width:3)
            }        

            section {
                input (name: "chartHours", type: "number", title: "<b>Hours Time-Length</b>:${getZindexToggle('chartHours')} ${getTooltipHTML('Chart Time-Length','Currently charts always end at now(). They can start as early as the parent Logger data retention, but more time means longer processing and more clutter.')}", defaultValue: 24, submitOnChange: true, width: 3)
                input (name: "rebuildFrequency", type: "enum", title: "<b>Refresh (Minutes, 0 == off)</b>:", multiple: false, defaultValue: 30, options: [0,5,10,15,30,60,120], submitOnChange: true, width: 3, newLineAfter:true)
                
                input (name: "autoscaleChart",  type: "bool", title: "<b>Auto-Scale Chart?</b>${getZindexToggle('autoscaleChart')} ${getTooltipHTML('Chart Auto-Scale','Sets the chart minimum and maximum dynamically or to the values you choose.<br/>Auto-Scale is easier if you don\'t know the range.<br/>Setting them manually will run <i>much</i> faster and provide more visual consistency across chart instances.')}", defaultValue: false, submitOnChange: true, width: 2)
                if (autoscaleChart == false) {
                    input (name: "chartMinValue", type:"number", title: "<b>Chart Min</b>", defaultValue: 0, submitOnChange: true, width:2)
                    input (name: "chartMaxValue", type:"number", title: "<b>Chart Max</b>", defaultValue: 100, submitOnChange: true, width:2)
                }
                input (name: "includeMinMax", type:"bool", title: "<b>Include min/max values in legend?</b>${getZindexToggle('includeMinMax')} ${getTooltipHTML('Min/Max on the Chart','If On, the low and high values for each device will be listed after their name on the legend on the chart.')}", defaultValue: true)
                input (name: "chartWidth", type: "number", title:"<b>Width of chart</b>", defaultValue: 200)
                input (name: "chartHeight", type: "number", title:"<b>Height of chart, excluding legend/labels</b>", defaultValue: 200)
                input (name: "labelHeight", type: "number", title:"<b>Space for legend/labels. (~15 per device or 60 for a timestamp axis label.)", defaultValue: 60)
            }
            section(hideable:true, hidden:false, "${thisChartName} Color Settings") {
                paragraph "<h2>Configuring Chart Colors</h><p>The current color map is always displayed in hex. An equivalent list can be pasted into the \"Load Colors\" input, including color names, and then \"Load Colors\" pressed; if names are found, they will be converted to hex.<br/>Colors may also be selected via the drop-down and then chosen with the picker or input box; to save those, click \"Save\" and the color map will change to reflect it.</p>"
                if (logDebug) log.debug("colorHex is ${labelColorHex}, labelColor is ${labelColor}, lastColor is ${state.LastColor}")
                /* state.colorTheme is of the format ["#xxxxxx","#xxxxxx"....] */
                if ((state.colorTheme == null) || (state.colorTheme.length() < 10)) {
                    state.colorTheme = colorValidateString(chartDarkColors)
                }

                paragraph "<b>Current Color/Theme Map:</b> ${state.colorTheme}"
                input (name: "themeColors", title: "Color Theme Input", required: false, width: 4)
                input (name: "loadThemeColors", type: "button", title: "Load Colors", backgroundColor: "#27ae61", textColor: "white", submitOnChange: true, width: 2)
                input (name: "themeColorTextInput", type: "text", title: "",  submitOnChange: false, width: 5)
                paragraph "<br/>"
                // Track previous colorToSet, to know what's changed.
                input (name: "colorToSet", type: "enum", title: "", options: colorEnum, required: false, submitOnChange: true,  width: 2)
                input (name: "saveColor", type: "button", title: "Save", width: "1", submitOnChange: true)
                // These two are linked and process via state.LastColor to ensure a change to one changes the other.
                input (name: "labelColor", type:"color", title: "", required: false, submitOnChange: true, width:1, defaultValue:state.LastColor)
                input (name: "labelColorHex", type:"COLOR_MAP", title: "", required: false, submitOnChange: true, width: 1, defaultValue:state.LastColor)
                
                if (colorToSet == "Title") {
                    input (name: "writeChartName",  type: "bool", title: "<b>Write Chart Name at top of chart?</b>", defaultValue: true, submitOnChange: true, width: 2)
                }

                if (colorToSet == "Background") {
                    input (name: "transparentBackground",  type: "bool", title: "<b>Transparent Chart Background?</b>", defaultValue: true, submitOnChange: true, width: 2)
                }
                log.debug("Previous was ${state.previousColorToSet}, current is ${colorToSet}")
                if (state.previousColorToSet != colorToSet) {
                    // Selected color entry has changed; update labelColor and labelColorHex
                    // set LastColor to this value.
                    if (logDebug) log.debug("Changing color to set from ${state.previousColorToSet} to ${colorToSet}")
                    state.LastColor = colorFromTheme(colorToSet)
                    app.updateSetting("labelColorHex", [type:"COLOR_MAP", value:state.LastColor])
                    app.updateSetting("labelColor", [type:"color", value:state.LastColor])
                    state.previousColorToSet = colorToSet
                }
                // These app.updateSetting()s might crap out if the app isn't yet installed! Can change with initialize() perhaps.
                if (state.LastColor != labelColorHex) {
                    state.LastColor = labelColorHex
                    app.updateSetting("labelColor", [type:"color", value:state.LastColor])
                    log.debug("Updated lastcolor to color hex. Now should be equal: ${state.LastColor}, ${labelColorHex}, ${labelColor}.")
                } else if (state.LastColor != labelColor) {
                    state.LastColor = labelColor
                    app.updateSetting("labelColorHex", [type:"COLOR_MAP", value:state.LastColor])
                    log.debug("Updated lastcolor to color. Now should be equal: ${state.LastColor}, ${labelColorHex}, ${labelColor}.")
                }
            }
            section {
                input (name: "rebuildChart", type: "button", title: "Rebuild Chart", backgroundColor: "#27ae61", textColor: "white", submitOnChange: true, width: 12)
                paragraph "<hr />"
                }
            section(hideable:true, hidden:false, "${thisChartName} Chart Preview") {
                paragraph "<img src=\"http://${location.hub.localIP}/local/${parent.cleanseFileName(thisChartName)}.svg\" />\n"
            }
            section {
                input (name: "logTrace", type: "bool", title: "<b>Enable trace logging?</b>", defaultValue: false, submitOnChange: true, width: 2)
                input (name: "logDebug", type: "bool", title: "<b>Enable debug logging?</b>", defaultValue: false, submitOnChange: true, width: 2)
            }
        }
    }        

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
            return
        }
        //Set the flag so that this should only ever run once.
        state.initialized = true
        state.colorTheme = ""
        state.previousColorToSet = ""
        app.updateSetting("chartRebuildIntervalSeconds", 120)
        app.updateSetting("time24H", true)
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
        if (logTrace) log.trace "${thisChartName} installed(). Scheduling rebuild."
        if (logTrace) log.trace "chartHours is type  ${typeOf(chartHours)}"
        chartHours = (long)chartHours
        buildWriteSVGFile()
        // Build the schedule to fire these off.
        unschedule(buildWriteSVGFile)
        if (rebuildFrequency > 0) {
            schedule("0 */${rebuildFrequency} * ? * *", buildWriteSVGFile)
        }
    }

    // Called when user presses "Done" button in app
    def updated() {
        if (logTrace) log.trace "${thisChartName}: updated()"
        installed()
    }

    // Called when app uninstalled
    def uninstalled() {
        unsubscribe()
        unschedule(buildWriteSVGFile)
    if (logTrace) log.trace "${thisChartName} uninstalled()"
    // Most apps would not need to do anything here

    }

    def logEvent(evt) {
        // Omitting source (usually DEVICE) and evt.description (seems to always be null)
        log.info("${thisChartName}:\n\tDesc: ${evt.descriptionText}\n\tAttribute ${evt.name}\n\tAttribute Value ${evt.value}\n\tUnit: ${evt.unit}\n\tDisplay Name: ${evt.getDisplayName()}\n\tDate: ${evt.getDate()}")
    }

    // Handle Event - When a published event - e.g. temp change - comes in.
    def handler(evt) {
        if (logDebug) logEvent(evt)
        saveEvent(evt)
    }

    def appButtonHandler(buttonName) {    
        switch(buttonName) {
            case "rebuildChart":
                buildWriteSVGFile()
                break;
            case "loadThemeColors":
                String validatedInput = colorValidateString(themeColorTextInput)
                if (validatedInput == null) {
                    log.warn("${thisChartName}: ${themeColorTextInput} failed color validation.")
                    return
                }
                themeColorTextInput = validatedInput
                state.colorTheme = validatedInput
                if (logTrace) log.trace("${thisChartName}: ${themeColorTextInput} set.")
                // And load up the individual values...
                break;
            // Called when the user has changed an individual component color (e.g. Series4 or Title) in the current theme using the picker.
            case "saveColor":
                colorIntoTheme(colorToSet, labelColor)
                break;
        }
    }
    // End Event Subscription Management

    // Return the type of the passed in object, as string.
    static String typeOf(obj){
        if(obj instanceof String){return 'String'}
        else if(obj instanceof Map){return 'Map'}
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

    // ******* File Management https://docs2.hubitat.com/en/developer/interfaces/file-manager-api ****
    //***** Access at http://<ip>/local/<fname>

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
    float toFloat(def val) {
        try {
            if(val instanceof String){return Float.parseFloat(val) }
            return (float)val
            } catch(Exception ex) {
                log.error("${thisChartName}: Error parsing Event Value ${val} into float. ${ex}")
            }
        return (float)UNINITIALIZED    
    }

    // Returns a tuple of min, max. (Technically a list)
    def calcJSONStat(def fname, Date startTime){
        Date endTime = new Date()
        def jsonMapList = readDeviceHistoryFromFile(fname)
        for (mapItem in jsonMapList) {
            eventDate = new Date(mapItem["time"])
            if (timeOfDayIsBetween(startTime, endTime, eventDate)) {
                if ((state.dataMax == UNINITIALIZED) || (mapItem["val"] > state.dataMax ) || (state.dataMax == null)) {
                    state.dataMax = mapItem["val"]
                }
                if ((state.dataMin == UNINITIALIZED) || (mapItem["val"] < state.dataMin ) || (state.dataMin == null)) {
                    state.dataMin = mapItem["val"]
                }
            }
        }
        if (logDebug) { 
            log.debug("${thisChartName}: Set data max to ${state.dataMax} and data min to ${state.dataMin}")
        }
        return [state.dataMin, state.dataMax]
    }

    def buildWriteSVGFile() {
        String svgFileName = "${thisChartName}.svg"
        String svgData = buildSVG((int)chartWidth, (int)chartHeight, (int)labelHeight, new Date(), chartHours)
        if (logDebug) log.debug"${thisChartName}: Uploading file per rebuild request: ${svgFileName} as ${parent.cleanseFileName(svgFileName)}"
        uploadHubFile(parent.cleanseFileName(svgFileName), svgData.getBytes())
    }

    // Returns the pixel row (hence the Int) for this data point on the chart. For SVG, (0,0) is the top left.
    Integer normalizeDataHeight(def dataSize, Integer chartHeight, float dataMin, float dataMax){
        return chartHeight - (int)(Math.round((chartHeight/(dataMax - dataMin)) * (dataSize - dataMin)))
    }

    /** If rotate is True, anchorMiddle is ignored, assumed to be false. */
    String buildSVGLabel(int x, int y, String text, int fontsize, String color, boolean rotate = false, boolean anchorMiddle = false) {
        String rotation = ""
        if (anchorMiddle) {
            rotation = " text-anchor=\"middle\"  "
        }
        if (rotate) {
            rotation = " text-anchor=\"end\" alignment-baseline=\"middle\" transform=\"rotate(-90,${x}, ${y})\" "
        }
        return "<text x=\"${x}\" y=\"${y}\" font-size=\"${fontsize}\" fill=\"${color}\" ${rotation} >${text}</text>\n"
    }


    // Builds a chart starting from endTime - hours. new Date()
    // https://developer.mozilla.org/en-US/docs/Web/SVG
    // Attribute: deviceCategory
    // Devices: deviceList
    String buildSVG(Integer width, Integer height, Integer heightOfLabelArea, Date endTime, long lhours) {
        def functionStartTime = now()
        Integer fontSize = 12
        Integer hours = (int)lhours
        int dataSourceCount = 0
        Date startTime = DateUtils.addHours(endTime, -1*hours)
        Long millisPerPixel = (endTime.getTime() - startTime.getTime()) / width

        StringBuilder svgFile = new StringBuilder()
        StringBuilder legends = new StringBuilder()
        Integer totalChartHeight = height + heightOfLabelArea
        String bg = ""
        if (!transparentBackground) {
            bg = " style=\"background-color:${colorFromTheme("Background")}\""
        }

        String base = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n
            <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
            <svg width="${width}" height="${totalChartHeight}" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" ${bg} >\n"""
        svgFile.append(base)
        // Iterate through the data for the min and max. Need to build multipliers to keep those inside width/height.    
        // Need a min and max to normalize data TO. 
        // Ensure some buffer and rounding for the min/max values that fit on-chart.
        // These are overridden by chart settings if the user set any.
        Integer maxDataVal = UNINITIALIZED
        Integer minDataVal = UNINITIALIZED
        if (autoscaleChart == false) {
            maxDataVal = chartMaxValue
            minDataVal = chartMinValue
        } else {
            for (dev in deviceList) {
                def fname = parent.createDataFilename(dev, deviceCategory)
                calcJSONStat(fname, startTime)
            }
            maxDataVal = Math.ceil(state.dataMax +1 )
            minDataVal = Math.floor(state.dataMin - 1)
        }
        if (logDebug) log.debug("${thisChartName}: Max Chart: ${maxDataVal}. Min Chart: ${minDataVal}")
        for (dev in deviceList) {
            if (logDebug) log.debug("${thisChartName}: Charting device ${dev} for attribute ${deviceCategory}")
            def fname = parent.createDataFilename(dev, deviceCategory)
            StringBuilder line = new StringBuilder()
            line.append("<polyline points=\"")
            def jsonMapList = readDeviceHistoryFromFile(fname)
            boolean minMaxInited = false
            def minV, maxV
            for (mapItem in jsonMapList) {
                eventDate = new Date(mapItem["time"])
                if (timeOfDayIsBetween(startTime, endTime, eventDate)) {
                    if ((!minMaxInited) || (maxV < mapItem["val"])) { maxV = mapItem["val"]}
                    if ((!minMaxInited) || (minV > mapItem["val"])) { minV = mapItem["val"]}
                    minMaxInited = true
                    Integer xPos = (int)((mapItem["time"] - startTime.getTime()) / millisPerPixel)
                    Integer yPos = normalizeDataHeight(mapItem["val"], height, minDataVal, maxDataVal)
                    line.append(" ${xPos},${yPos}")
                }
            }
            // Finish polyline with color etc.
            String colorPlaceName = "Series${dataSourceCount + 1}"
            def lineColor = colorFromTheme(colorPlaceName)
            line.append("\" fill=\"none\" stroke=\"${lineColor}\" />\n")
            svgFile.append(line)
            String label = dev
            if (includeMinMax == true) { label += " - ${minV} to ${maxV}"}
            legends.append(buildSVGLabel(20, height + (dataSourceCount + 1) * 15, label, fontSize, lineColor))
            dataSourceCount++
        }
        // Append axis floor
        def axisColor = colorFromTheme("Axis")
        svgFile.append("<polyline points=\"0,${height} ${width},${height}\" fill=\"none\" stroke=\"${axisColor}\" />\n")
        svgFile.append(legends)
        Integer halfHeight = height/2 // Only due to VSCode screwup.
        def midPointValue = (maxDataVal + minDataVal)/2
        String labelColor = colorFromTheme("Labels")
        if (writeChartName) {   // Only write chart name if it's set to.
            def chartNameColor =  colorFromTheme("Title")
            svgFile.append(buildSVGLabel((int)(width/2), fontSize + 6, "${thisChartName}", fontSize + 3, chartNameColor, false, true))
        }
        svgFile.append(buildSVGLabel(0, 10, "${maxDataVal}", fontSize, labelColor))
        svgFile.append(buildSVGLabel(0, halfHeight, "${midPointValue}", fontSize, labelColor))
        svgFile.append(buildSVGLabel(0, height-10, "${minDataVal}", fontSize, labelColor))
        // Add start and end date-time
        svgFile.append(buildSVGLabel((int)(fontSize/2), height, "${startTime.format('HH:mm MMM dd')}", fontSize, labelColor, true))
        svgFile.append(buildSVGLabel(width - 10, height, "${endTime.format('HH:mm MMM dd')}", fontSize, labelColor, true))
        svgFile.append("</svg>\n")
        def functionRunTime = now() - functionStartTime

        if (logDebug) log.debug("${thisChartName}: buildSvg() took ${functionRunTime}ms. ")
        return svgFile.toString()
    }

    @Field static colorNames = [
        ALICEBLUE:"F0F8FF",
        ANTIQUEWHITE:"FAEBD7",
        AQUA:"00FFFF",
        AQUAMARINE:"7FFFD4",
        AZURE:"F0FFFF",
        BEIGE:"F5F5DC",
        BISQUE:"FFE4C4",
        BLACK:"000000",
        BLANCHEDALMOND:"FFEBCD",
        BLUE:"0000FF",
        BLUEVIOLET:"8A2BE2",
        BROWN:"A52A2A",
        BURLYWOOD:"DEB887",
        CADETBLUE:"5F9EA0",
        CHARTREUSE:"7FFF00",
        CHOCOLATE:"D2691E",
        CORAL:"FF7F50",
        CORNFLOWERBLUE:"6495ED",
        CORNSILK:"FFF8DC",
        CRIMSON:"DC143C",
        CYAN:"00FFFF",
        DARKBLUE:"00008B",
        DARKCYAN:"008B8B",
        DARKGOLDENROD:"B8860B",
        DARKGRAY:"A9A9A9",
        DARKGREEN:"006400",
        DARKGREY:"A9A9A9",
        DARKKHAKI:"BDB76B",
        DARKMAGENTA:"8B008B",
        DARKOLIVEGREEN:"556B2F",
        DARKORANGE:"FF8C00",
        DARKORCHID:"9932CC",
        DARKRED:"8B0000",
        DARKSALMON:"E9967A",
        DARKSEAGREEN:"8FBC8F",
        DARKSLATEBLUE:"483D8B",
        DARKSLATEGRAY:"2F4F4F",
        DARKSLATEGREY:"2F4F4F",
        DARKTURQUOISE:"00CED1",
        DARKVIOLET:"9400D3",
        DEEPPINK:"FF1493",
        DEEPSKYBLUE:"00BFFF",
        DIMGRAY:"696969",
        DIMGREY:"696969",
        DODGERBLUE:"1E90FF",
        FIREBRICK:"B22222",
        FLORALWHITE:"FFFAF0",
        FORESTGREEN:"228B22",
        FUCHSIA:"FF00FF",
        GAINSBORO:"DCDCDC",
        GHOSTWHITE:"F8F8FF",
        GOLD:"FFD700",
        GOLDENROD:"DAA520",
        GRAY:"808080",
        GREEN:"008000",
        GREENYELLOW:"ADFF2F",
        GREY:"808080",
        HONEYDEW:"F0FFF0",
        HOTPINK:"FF69B4",
        INDIANRED:"CD5C5C",
        INDIGO:"4B0082",
        IVORY:"FFFFF0",
        KHAKI:"F0E68C",
        LAVENDER:"E6E6FA",
        LAVENDERBLUSH:"FFF0F5",
        LAWNGREEN:"7CFC00",
        LEMONCHIFFON:"FFFACD",
        LIGHTBLUE:"ADD8E6",
        LIGHTCORAL:"F08080",
        LIGHTCYAN:"E0FFFF",
        LIGHTGOLDENRODYELLOW:"FAFAD2",
        LIGHTGRAY:"D3D3D3",
        LIGHTGREEN:"90EE90",
        LIGHTGREY:"D3D3D3",
        LIGHTPINK:"FFB6C1",
        LIGHTSALMON:"FFA07A",
        LIGHTSEAGREEN:"20B2AA",
        LIGHTSKYBLUE:"87CEFA",
        LIGHTSLATEGRAY:"778899",
        LIGHTSLATEGREY:"778899",
        LIGHTSTEELBLUE:"B0C4DE",
        LIGHTYELLOW:"FFFFE0",
        LIME:"00FF00",
        LIMEGREEN:"32CD32",
        LINEN:"FAF0E6",
        MAGENTA:"FF00FF",
        MAROON:"800000",
        MEDIUMAQUAMARINE:"66CDAA",
        MEDIUMBLUE:"0000CD",
        MEDIUMORCHID:"BA55D3",
        MEDIUMPURPLE:"9370DB",
        MEDIUMSEAGREEN:"3CB371",
        MEDIUMSLATEBLUE:"7B68EE",
        MEDIUMSPRINGGREEN:"00FA9A",
        MEDIUMTURQUOISE:"48D1CC",
        MEDIUMVIOLETRED:"C71585",
        MIDNIGHTBLUE:"191970",
        MINTCREAM:"F5FFFA",
        MISTYROSE:"FFE4E1",
        MOCCASIN:"FFE4B5",
        NAVAJOWHITE:"FFDEAD",
        NAVY:"000080",
        OLDLACE:"FDF5E6",
        OLIVE:"808000",
        OLIVEDRAB:"6B8E23",
        ORANGE:"FFA500",
        ORANGERED:"FF4500",
        ORCHID:"DA70D6",
        PALEGOLDENROD:"EEE8AA",
        PALEGREEN:"98FB98",
        PALETURQUOISE:"AFEEEE",
        PALEVIOLETRED:"DB7093",
        PAPAYAWHIP:"FFEFD5",
        PEACHPUFF:"FFDAB9",
        PERU:"CD853F",
        PINK:"FFC0CB",
        PLUM:"DDA0DD",
        POWDERBLUE:"B0E0E6",
        PURPLE:"800080",
        REBECCAPURPLE:"663399",
        RED:"FF0000",
        ROSYBROWN:"BC8F8F",
        ROYALBLUE:"4169E1",
        SADDLEBROWN:"8B4513",
        SALMON:"FA8072",
        SANDYBROWN:"F4A460",
        SEAGREEN:"2E8B57",
        SEASHELL:"FFF5EE",
        SIENNA:"A0522D",
        SILVER:"C0C0C0",
        SKYBLUE:"87CEEB",
        SLATEBLUE:"6A5ACD",
        SLATEGRAY:"708090",
        SLATEGREY:"708090",
        SNOW:"FFFAFA",
        SPRINGGREEN:"00FF7F",
        STEELBLUE:"4682B4",
        TAN:"D2B48C",
        TEAL:"008080",
        THISTLE:"D8BFD8",
        TOMATO:"FF6347",
        TURQUOISE:"40E0D0",
        VIOLET:"EE82EE",
        WHEAT:"F5DEB3",
        WHITE:"FFFFFF",
        WHITESMOKE:"F5F5F5",
        YELLOW:"FFFF00",
        YELLOWGREEN:"9ACD32",    ]

    /** Converts a color name, if present, to the hex code. Returns null if neither is legal. */
    def colorNameToHexCode(String colorName) {
        def cn = colorName.toUpperCase()
        if (logTrace) log.trace("Looking up color ${cn}")
        if (colorNames.containsKey(cn)) {
            if (logTrace) log.trace("Returning ${colorNames[cn]}")
            return colorNames[cn]
        }
        if (logTrace) log.trace("Failed to find color ${cn}; trying hex.")
        if (colorName.size() != 6) {
            return null
        }
        for (int i = 0; i < colorName.length(); i++) {
            def c = cn.charAt(i)
            if (!"0123456789ABCDEF".contains(String.valueOf(c))) {
                return null
            }
        }
        return cn
    }

    /** Add this new color into the color them at themeUnit location. */
    def colorIntoTheme(def themeUnit, def newColor) {
        def indexOfColor = colorEnum.indexOf(themeUnit)
        def originalColors = state.colorTheme[1..-2].replace("\"","").split(",")
        StringBuilder sb = new StringBuilder()
        sb.append("[")
        for (int i = 0; i < originalColors.size(); i++) {
            if (i > 0) {
                sb.append(",")
            }
            if (i == indexOfColor) {
                sb.append("\"${newColor}\"")
            } else {
                sb.append("\"${originalColors[i]}")
            }
        }
        sb.append("]")
        if (logDebug) {
            log.debug("${thisChartName}: Inserting new color ${newColor} at position ${themeUnit} into ${state.colorTheme}, resulting in ${sb.toString()}")
        }
        state.colorTheme = sb.toString()
    }

    /** Returns the color for the specified enum in colorEnum from state.colorTheme */
    String colorFromTheme(def themeUnit) { 
        def indexOfColor = colorEnum.indexOf(themeUnit)
        def colors = state.colorTheme[1..-2].replace("\"","").split(",")
        if (logDebug) log.debug("${thisChartName}: Extracting ${themeUnit} from ${state.colorTheme}. Got ${colors[indexOfColor]}")
        return colors[indexOfColor]
   } 

    /** Returns a converted color string if valid, or null/empty if invalid */
    def colorValidateString(String colorString) {
        // Strip out all quotes, brackets, parens
        String cs = colorString.replace("\"","").replace("[","").replace("]","").replace("#","").replace(" ","")
        // Split on commas. 
        def colorList = cs.split(",")
        if (colorList.size() < 7) {
            log.warn("${thisChartName}: Invalid color-string input ${colorString} - insufficient color count.")
            return null
        }
        StringBuilder formattedString = new StringBuilder()
        formattedString.append("[")
        def currentIndex = 0
        for ( colorEntry in colorList) {
            def retColor = colorNameToHexCode(colorEntry)
            if (retColor == null) {
                log.warn("${thisChartName}: Invalid color-string input ${colorString} - could not parse ${colorEntry}.")
                return null
            }
            if (currentIndex > 0) {
                formattedString.append(",")
            }
            formattedString.append("\"#${retColor}\"")
            currentIndex++
        }
        formattedString.append("]")
        return formattedString.toString()
    }