#
# XBee Presence
#
#  Copyright 2019 Daniel Terryn
#
#  Licensed Virtual Image Switch the Apache License, Version 2.0 (the "License"); you may not use this file except
#  in compliance with the License. You may obtain a copy of the License at:
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
#  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
#  for the specific language governing permissions and limitations under the License.
#
#  Change History:
#
#    Date        Who            What
#    ----        ---            ----
#    2019-05-21  Daniel Terryn  Original Creation
# 
#
import xbee
import time
import binascii
import sys
x = xbee.XBee() #Create an XBee object
while True:
	voltage = x.atcmd('%V')
	print("Voltage at " + str(voltage))
	tx_req = ("7E"+"00"+"05"+"2D"+"01"+str(voltage))
	try:
		xbee.transmit(xbee.ADDR_COORDINATOR, binascii.unhexlify(tx_req))
	except:
		print("Error occured to send package, probably not connected to ZigBee network")
	print("going to sleep now")
	time.sleep_ms(60000)
	if x.wake_reason() is xbee.PIN_WAKE:
		print("woke early on DTR toggle")
		sys.exit()
