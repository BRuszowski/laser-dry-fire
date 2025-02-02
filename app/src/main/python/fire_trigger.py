import time
import gpiozero
from gpiozero.pins.pigpio import PiGPIOFactory

output_pin = 17
TRIGGER_TIME = .3
pin_out = None

'''
Initialize GPIO pins
'''
def set_pin_out(input_ip):
    global pin_out
    factory = gpiozero.pins.pigpio.PiGPIOFactory(host=input_ip)
    pin_out = gpiozero.OutputDevice(output_pin, pin_factory=factory)

'''
Activate pin for specified time
'''
def fire_trigger():
    global pin_out
    pin_out.on()
    time.sleep(TRIGGER_TIME)
    pin_out.off()

