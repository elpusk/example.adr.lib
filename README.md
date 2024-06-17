# example.adr.lib
the Example of Android liblpu237 aar library.(use liblpu237 v1.4) <br>
this source code describes how to use liblpu237 aar library.<br>


# How to use liblpu237 aar file.
+ make 'libs' folder and copy library file to 'libs' folder.(In example,liblpu237-release.aar )
+ add dependency to build.gradle.kts of your app.
```
dependencies {
    ....
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
```
+ Coding your app.
+ build.

# Using secenario
## To change system parameters.
### Basic step
+ library on
+ get device list
+ get target device permission.
+ open device
+ get system parameters from device.
+ changes system parameters
+ set system parameters to device.
+ apply the changed system parameters.
+ close device.
+ library off.

### Code example
+ _startMainTaskGetSet() method of  [MainActivity.java](/app/src/main/java/kr/co/elpusk/example/lib/MainActivity.java)
+ this method executes on independent thread from UI thread.
+ this method represents the above Basic step.

### more details
+ [here](/doc/getset.md)

## To read magetic card data.
### Basic step
+ library on.
+ get device list
+ get target device permission.
+ open device
+ get the minium system parameters from device.
+ if the current interface isn't 'usb hid vendor' interface, change interface to 'usb hid vendor'.
+ Waits a reading MSR with MSR type-callback object.
+ if callback is done, get data from callack parameters.
+ if needs more reading, rewaits a reading MSR with MSR type-callback object.
+ if No needs more reading, close device and library off.

### Code example
+ _startMainTaskReadingMsr() method of  [MainActivity.java](/app/src/main/java/kr/co/elpusk/example/lib/MainActivity.java)
+ this method executes on independent thread from UI thread.
+ this method represents the above Basic step.

### more details
+ [here](/doc/readmsr.md)


## To read i-button.
### Basic step
+ library on
+ get device list
+ get target device permission.
+ open device
+ get the minium system parameters from device.
+ if the current interface isn't 'usb hid vendor' interface, change interface to 'usb hid vendor'.
+ Waits a reading i-button with i-button type-callback object.
+ if callback is done, get data from callack parameters.
+ if needs more reading, rewaits a reading i-button with i-button type-callback object.
+ if No needs more reading, close device and library off.

### Code example
+ _startMainTaskReadingiButton() method of  [MainActivity.java](/app/src/main/java/kr/co/elpusk/example/lib/MainActivity.java)
+ this method executes on independent thread from UI thread.
+ this method represents the above Basic step.

### more details
+ [here](/doc/readibutton.md)

## To read MSR and i-button.
### Basic step
+ library on
+ get device list
+ get target device permission.
+ open device
+ get the minium system parameters from device.
+ if the current interface isn't 'usb hid vendor' interface, change interface to 'usb hid vendor'.
+ Waits a reading MSR or i-button with MSR and i-button type-callback object.
+ if callback is done, get data from callack parameters.
+ if needs more reading, rewaits a reading MSR or i-button with MSR and i-button type-callback object.
+ if No needs more reading, close device and library off.

### Code example
+ _startMainTaskReadingAll() method of  [MainActivity.java](/app/src/main/java/kr/co/elpusk/example/lib/MainActivity.java)
+ this method executes on independent thread from UI thread.
+ this method represents the above Basic step.

### more details
+ [here](/doc/readmsributton.md)
