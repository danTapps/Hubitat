# TED Pro Home Energy Monitor

This driver code allows you to create a power meter device in Hubitat to interface with the TED Pro Home energy monitor (see: http://www.theenergydetective.com/tedprohome.html). It reports "power" and "voltage" that allows you to create rules for notifications or actions.<br>
The driver supports TED Spyders. TED Spyders monitor individual circuits and child devices will be automatically created for each configured Spyder. Spyder circuits report on "power" only

Here is an example view of a TED device with additional Spyders:

![example view](https://raw.githubusercontent.com/danTapps/Hubitat/master/Drivers/TED%20Pro%20Home/images/ted_view.png)

# Installation
* Under the Hubitat Web Interface, Click on <u><b>```Drivers code```</b></u> in the left side menu.
* Click on the button <u><b>```+New Driver```</b></u>
* copy the content of the <u>parent.groovy<u> file into the editor and hit save
* Under the Hubitat Web Interface, Click on <u><b>```Drivers code```</b></u> in the left side menu.
* Click on the button <u><b>```+New Driver```</b></u>
* copy the content of the <u>child.groovy<u> file into the editor and hit save
* Under the Hubitat Web Interface, Click on <u><b>```Devices```</b></u> in the left side menu.
* Click on the button <u><b>```+Add Virtual Device```</b></u>
* Add a new device and select <u><b>```TED Pro Home```</b></u> as type 

# Configuration
- Set the IP address of the TED Pro Home device (a static IP or DHCP reservation is required)
- Set the port used to access the TED Pro Home web site, default is port 80
- Set the username and password to access the TED Pro Home web site
- Set the desired refresh interval

![settings example](https://raw.githubusercontent.com/danTapps/Hubitat/master/Drivers/TED%20Pro%20Home/images/ted_settings.png)
