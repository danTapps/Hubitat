#
# SerLCD library to work with Spark Funs Serial LCDs via I2C
#  such as: SparkFun 16x2 SerLCD - RGB on Black 3.3V https://www.sparkfun.com/products/14073
#
#  Based on shigeru-kawaguchi work at: https://github.com/shigeru-kawaguchi/Python-SparkFun-SerLCD
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
#    2019-20-06  Daniel Terryn  Original Creation
#
#
#  Example usage:
#   from machine import I2C
#   import SerLCD
#   myI2C = I2C(1)
#   myLCD = SerLCD.SerLCD(myI2C)
#   myLCD.setBacklight(64, 64, 64)
#   myLCD.noCursor()
#   myLCD.clear()
#   myLCD.write("Hello World!")
#   myLCD.setCursor(0, 1)
#   myLCD.write("2nd Line comment")

import time

class SerLCD:
    """SparkFun SerLCD"""

    #I2C Slave Address
    I2C_ADDRESS = 0x72
    #Display
    MAX_ROWS = 4
    MAX_COLUMNS = 20
    #Command Char
    SPECIAL_COMMAND = 254
    SETTING_COMMAND = 0x7C
    #Commands
    CLEAR_COMMAND = 0x2D
    CONTRAST_COMMAND = 0x18
    ADDRESS_COMMAND = 0x19
    SET_RGB_COMMAND = 0x2B
    ENABLE_SYSTEM_MESSAGE_DISPLAY = 0x2E
    DISABLE_SYSTEM_MESSAGE_DISPLAY = 0x2F
    ENABLE_SPLASH_DISPLAY = 0x30
    DISABLE_SPLASH_DISPLAY = 0x31
    SAVE_CURRENT_DISPLAY_AS_SPLASH = 0x0A
    #Special Commands
    LCD_RETURNHOME = 0x02
    LCD_ENTRYMODESET = 0x04
    LCD_DISPLAYCONTROL = 0x08
    LCD_CURSORSHIFT = 0x10
    LCD_SETDDRAMADDR = 0x80
    #Flags for display entry mode
    LCD_ENTRYRIGHT = 0x00
    LCD_ENTRYLEFT = 0x02
    LCD_ENTRYSHIFTINCREMENT = 0x01
    LCD_ENTRYSHIFTDECREMENT = 0x00
    #Flags for display on/off control
    LCD_DISPLAYON = 0x04
    LCD_DISPLAYOFF = 0x00
    LCD_CURSORON = 0x02
    LCD_CURSOROFF = 0x00
    LCD_BLINKON = 0x01
    LCD_BLINKOFF = 0x00
    #Flags for display/cursor shift
    LCD_DISPLAYMOVE = 0x08
    LCD_CURSORMOVE = 0x00
    LCD_MOVERIGHT = 0x04
    LCD_MOVELEFT = 0x00

    #I2C Config
    _i2cAddr = I2C_ADDRESS
    _displayControl = LCD_DISPLAYON | LCD_CURSOROFF | LCD_BLINKOFF
    _displayMode = LCD_ENTRYLEFT | LCD_ENTRYSHIFTDECREMENT

    def _write(self, addr, data0, data1):
        raw_data = []
        raw_data.append(data0)
        
        for c in data1:
            raw_data.append(c)
        # print(raw_data)
        self._i2c.writeto(addr, bytes(raw_data))
        # self._bus.write_i2c_block_data(addr, data0, data1)

    def __init__(self, inI2C, i2cAddr=I2C_ADDRESS):
        self._i2cAddr = i2cAddr
        self._i2c = inI2C

        self.specialCommand([self.LCD_DISPLAYCONTROL | self._displayControl])
        self.specialCommand([self.LCD_ENTRYMODESET | self._displayMode])
        self.clear()

    def command(self, byteCmd):
        self._write(self._i2cAddr, self.SETTING_COMMAND, byteCmd)
        # self._bus.write_i2c_block_data(self._i2cAddr, self.SETTING_COMMAND, byteCmd)
        time.sleep_ms(10)

    def specialCommand(self, byteCmd):
        self._write(self._i2cAddr, self.SPECIAL_COMMAND, byteCmd)
        time.sleep_ms(50)

    def clear(self):
        self.command([self.CLEAR_COMMAND])
        time.sleep_ms(10)

    def home(self):
        self.specialCommand([self.LCD_RETURNHOME])

    def setCursor(self, col, row):
        row_offsets = [0x00, 0x40, 0x14, 0x54]
        row = max([0, row])
        row = min([row, self.MAX_ROWS - 1])
        self._write(self._i2cAddr, self.SPECIAL_COMMAND, [self.LCD_SETDDRAMADDR | (col + row_offsets[row])])
        time.sleep_ms(50)

    #Custom character
    def createChar(self, location, char_map):
        location = location & 0x7
        char_map.insert(0, 27 + location)
        char_map.insert(0, self.SETTING_COMMAND)
        self._write(self._i2cAddr, 0, char_map)
        time.sleep_ms(50)

    def writeChar(self, location):
        location = location & 0x7
        self._write(self._i2cAddr, self.SETTING_COMMAND, [35 + location])
        time.sleep_ms(10)

    #Write string
    def write(self, text):
        data = []
        for char in text:
            hv = ord(char)
            data.append(hv)
        data0 = data[0]
        del data[0]
        self._write(self._i2cAddr, data0, data)
        time.sleep_ms(10)

    #Display control
    def noDisplay(self):
        self._displayControl = self._displayControl & ~self.LCD_DISPLAYON
        self.specialCommand([self.LCD_DISPLAYCONTROL | self._displayControl])

    def display(self):
        self._displayControl = self._displayControl | self.LCD_DISPLAYCONTROL
        self.specialCommand([self.LCD_DISPLAYCONTROL | self._displayControl])

    def noCursor(self):
        self._displayControl = self._displayControl & ~self.LCD_CURSORON
        self.specialCommand([self.LCD_DISPLAYCONTROL | self._displayControl])

    def cursor(self):
        self._displayControl = self._displayControl | self.LCD_CURSORON
        self.specialCommand([self.LCD_DISPLAYCONTROL | self._displayControl])

    def noBlink(self):
        self._displayControl = self._displayControl & ~self.LCD_BLINKON
        self.specialCommand([self.LCD_DISPLAYCONTROL | self._displayControl])

    def blink(self):
        self._displayControl = self._displayControl | self.LCD_BLINKON
        self.specialCommand([self.LCD_DISPLAYCONTROL | self._displayControl])

    def scrollDisplayLeft(self):
        self.specialCommand([self.LCD_CURSORSHIFT | self.LCD_DISPLAYMOVE | self.LCD_MOVELEFT])

    def scrollDisplayRight(self):
        self.specialCommand([self.LCD_CURSORSHIFT | self.LCD_DISPLAYMOVE | self.LCD_MOVERIGHT])

    def moveCursorLeft(self):
        self.specialCommand([self.LCD_CURSORSHIFT | self.LCD_CURSORMOVE | self.LCD_MOVELEFT])

    def moveCursorRight(self):
        self.specialCommand([self.LCD_CURSORSHIFT | self.LCD_CURSORMOVE | self.LCD_MOVERIGHT])

    def setBacklight(self, r, g, b):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.SET_RGB_COMMAND, r, g, b])
        time.sleep_ms(10)

    def enableSystemMessages(self):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.ENABLE_SYSTEM_MESSAGE_DISPLAY])
        time.sleep_ms(10)

    def disableSystemMessages(self):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.DISABLE_SYSTEM_MESSAGE_DISPLAY])
        time.sleep_ms(10)

    def enableSplash(self):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.ENABLE_SPLASH_DISPLAY])
        time.sleep_ms(10)

    def disableSplash(self):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.DISABLE_SPLASH_DISPLAY])
        time.sleep_ms(10)

    def saveAsSplash(self):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.SAVE_CURRENT_DISPLAY_AS_SPLASH])
        time.sleep_ms(10)

    def leftToRight(self):
        self._displayMode = self._displayMode | self.LCD_ENTRYLEFT
        self.specialCommand([self.LCD_ENTRYMODESET | self._displayMode])

    def rightToLeft(self):
        self._displayMode = self._displayMode & ~self.LCD_ENTRYLEFT
        self.specialCommand([self.LCD_ENTRYMODESET | self._displayMode])

    def autoScroll(self):
        self._displayMode = self._displayMode | self.LCD_ENTRYSHIFTINCREMENT
        self.specialCommand([self.LCD_ENTRYMODESET | self._displayMode])

    def noAutoScroll(self):
        self._displayMode = self._displayMode & ~self.LCD_ENTRYSHIFTINCREMENT
        self.specialCommand([self.LCD_ENTRYMODESET | self._displayMode])

    def setContrast(self, newVal):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.CONTRAST_COMMAND, newVal])
        time.sleep_ms(10)

    def setAddress(self, newAddr):
        self._write(self._i2cAddr, self.SETTING_COMMAND, [self.ADDRESS_COMMAND, newAddr])
        self._i2cAddr = newAddr
        time.sleep_ms(50)

