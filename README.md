# Laser dry-fire app
## Summary
This app was written as a part of laser dry-fire system I created. 
The laser comes from a boresighter that was modified to allow a Raspberry Pi to programmatically control it.
The detection system uses two Android phones. One is mounted near the target to detect the laser’s position and send the position to the other phone. The other phone reports the position.
The reporter also listens for the trigger sound. Upon detection, it activates the boresighter using the Pi’s remote GPIO support.

## Dependencies
+ Android OpenCV Version 4.11.0
++ Link: https://opencv.org/android/
+ GPIOZero for remote control of Pi GPIO pins
++ Link: https://gpiozero.readthedocs.io/en/latest/
+ Chaquopy to run Python library from app
++ Link: https://chaquo.com/chaquopy/
