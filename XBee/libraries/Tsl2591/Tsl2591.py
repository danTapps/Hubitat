#
# Tsl2591 library to work with Tsl2591 illuminance sensors via I2C
#  such as: https://www.adafruit.com/product/439
#
#  Based on maxlklaxl work at: https://github.com/maxlklaxl/python-tsl2591
#
#  Copyright 2019 Daniel Terryn
#
#  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
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
#    2019-06-21  Daniel Terryn  Original Creation
#
# Example:
#  from machine import I2C
#  myI2C = I2C(1)
#  tsl = Tsl2591(myI2C)  # initialize
#  full, ir = tsl.get_full_luminosity()  # read raw values (full spectrum and ir spectrum)
#  lux = tsl.calculate_lux(full, ir)  # convert raw values to lux
#  print (lux, full, ir)
#

import time

VISIBLE = 2  # channel 0 - channel 1
INFRARED = 1  # channel 1
FULLSPECTRUM = 0  # channel 0

ADDR = 0x29
READBIT = 0x01
COMMAND_BIT = 0xA0  # bits 7 and 5 for 'command normal'
CLEAR_BIT = 0x40  # Clears any pending interrupt (write 1 to clear)
WORD_BIT = 0x20  # 1 = read/write word (rather than byte)
BLOCK_BIT = 0x10  # 1 = using block read/write
ENABLE_POWERON = 0x01
ENABLE_POWEROFF = 0x00
ENABLE_AEN = 0x02
ENABLE_AIEN = 0x10
CONTROL_RESET = 0x80
LUX_DF = 408.0
LUX_COEFB = 1.64  # CH0 coefficient
LUX_COEFC = 0.59  # CH1 coefficient A
LUX_COEFD = 0.86  # CH2 coefficient B

REGISTER_ENABLE = 0x00
REGISTER_CONTROL = 0x01
REGISTER_THRESHHOLDL_LOW = 0x02
REGISTER_THRESHHOLDL_HIGH = 0x03
REGISTER_THRESHHOLDH_LOW = 0x04
REGISTER_THRESHHOLDH_HIGH = 0x05
REGISTER_INTERRUPT = 0x06
REGISTER_CRC = 0x08
REGISTER_ID = 0x0A
REGISTER_CHAN0_LOW = 0x14
REGISTER_CHAN0_HIGH = 0x15
REGISTER_CHAN1_LOW = 0x16
REGISTER_CHAN1_HIGH = 0x17
INTEGRATIONTIME_100MS = 0x00
INTEGRATIONTIME_200MS = 0x01
INTEGRATIONTIME_300MS = 0x02
INTEGRATIONTIME_400MS = 0x03
INTEGRATIONTIME_500MS = 0x04
INTEGRATIONTIME_600MS = 0x05

GAIN_LOW = 0x00  # low gain (1x)
GAIN_MED = 0x10  # medium gain (25x)
GAIN_HIGH = 0x20  # medium gain (428x)
GAIN_MAX = 0x30  # max gain (9876x)


class Tsl2591(object):
    def __init__(
                 self,
                 i2c_bus,
                 sensor_address=0x29,
                 integration=INTEGRATIONTIME_100MS,
                 gain=GAIN_LOW
                 ):
        self._i2c = i2c_bus
        self.sendor_address = sensor_address
        self.integration_time = integration
        self.gain = gain
        self.set_timing(self.integration_time)
        self.set_gain(self.gain)
        self.disable()  # to be sure

    def _write(self, addr, data0, data1):
        raw_data = []
        raw_data.append(data0)
        raw_data.append(data1)
        
        # print(raw_data)
        try:
            self._i2c.writeto(addr, bytes(raw_data))
            # self._bus.write_i2c_block_data(addr, data0, data1)
        except Exception as e:
            print( "Error writing I2C", e )

    def _read(self, addr, cmd):
        try:
            self._i2c.writeto(41, bytes([cmd]))
            value = self._i2c.readfrom(addr, 2)
        except Exception as e:
            print( "Error writing I2C", e )
            value = 0x0
        return int.from_bytes(value, 'big')

    def set_timing(self, integration):
        self.enable()
        self.integration_time = integration
        self._write(
                    self.sendor_address,
                    COMMAND_BIT | REGISTER_CONTROL,
                    self.integration_time | self.gain
                    )
        self.disable()

    def get_timing(self):
        return self.integration_time

    def set_gain(self, gain):
        self.enable()
        self.gain = gain
        self._write(
                    self.sendor_address,
                    COMMAND_BIT | REGISTER_CONTROL,
                    self.integration_time | self.gain
                    )
        self.disable()

    def get_gain(self):
        return self.gain

    def calculate_lux(self, full, ir):
        # Check for overflow conditions first
        if (full == 0xFFFF) | (ir == 0xFFFF):
            return 0
            
        case_integ = {
            INTEGRATIONTIME_100MS: 100.,
            INTEGRATIONTIME_200MS: 200.,
            INTEGRATIONTIME_300MS: 300.,
            INTEGRATIONTIME_400MS: 400.,
            INTEGRATIONTIME_500MS: 500.,
            INTEGRATIONTIME_600MS: 600.,
            }
        if self.integration_time in case_integ.keys():
            atime = case_integ[self.integration_time]
        else:
            atime = 100.

        case_gain = {
            GAIN_LOW: 1.,
            GAIN_MED: 25.,
            GAIN_HIGH: 428.,
            GAIN_MAX: 9876.,
            }

        if self.gain in case_gain.keys():
            again = case_gain[self.gain]
        else:
            again = 1.

        # cpl = (ATIME * AGAIN) / DF
        cpl = (atime * again) / LUX_DF
        lux1 = (full - (LUX_COEFB * ir)) / cpl

        lux2 = ((LUX_COEFC * full) - (LUX_COEFD * ir)) / cpl

        # The highest value is the approximate lux equivalent
        return max([lux1, lux2])

    def enable(self):
        self._write(
                    self.sendor_address,
                    COMMAND_BIT | REGISTER_ENABLE,
                    ENABLE_POWERON | ENABLE_AEN | ENABLE_AIEN
                    )  # Enable

    def disable(self):
        self._write(
                    self.sendor_address,
                    COMMAND_BIT | REGISTER_ENABLE,
                    ENABLE_POWEROFF
                    )


    def get_full_luminosity(self):
        self.enable()
        time.sleep(0.120*self.integration_time+1)  # not sure if we need it "// Wait x ms for ADC to complete"
        full = self._read(
                    self.sendor_address, COMMAND_BIT | REGISTER_CHAN0_LOW
                    )
        ir = self._read(
                    self.sendor_address, COMMAND_BIT | REGISTER_CHAN1_LOW
                    )                    
        self.disable()
        return full, ir

    def get_luminosity(self, channel):
        full, ir = self.get_full_luminosity()
        if channel == FULLSPECTRUM:
            # Reads two byte value from channel 0 (visible + infrared)
            return full
        elif channel == INFRARED:
            # Reads two byte value from channel 1 (infrared)
            return ir
        elif channel == VISIBLE:
            # Reads all and subtracts out ir to give just the visible!
            return full - ir
        else: # unknown channel!
            return 0
