import xbee
import time
import binascii
x = xbee.XBee() #Create an XBee object
while True:
	voltage = x.atcmd('%V')
	print("Voltage at " + str(voltage))
	tx_req = ("7E"+"00"+"05"+"2D"+"01"+str(voltage))
	try:
		xbee.transmit(xbee.ADDR_COORDINATOR, binascii.unhexlify(tx_req))
	except:
		print("err")
	print("going to sleep now")
	time.sleep_ms(60000)
	if x.wake_reason() is xbee.PIN_WAKE:
		print("woke early on DTR toggle")
