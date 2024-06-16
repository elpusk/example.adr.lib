# How To read i-button data.
# Below mandatory code represents example without error handling.
```java
String _mainTaskReadingiButton(Application in){

    //
    //the current interface must be usb vendor hid.
    //Before running this method , you change interface to usb vendor hid by lpu237 mapper app.

    ApiInterface api = ApiFactory.getInstance(ApiFactory.VERSION.LAST);
    api.On(in);//initialize API

    //get deivce list
    ArrayList<UsbDevHandle> listDevPath= api.GetList();

    //... Before opening device, Waits here until grant is received from user.

    //open device
    UsbDevHandle hDev = api.Open(listDevPath.get(0).get_path());

    // setup for reading
    boleoan bResult = api.SetupForRead(hDev, false);//No need change interface.

    bResult = api.EnableiButton(hDev,true);//enable callback
    //starts waits i-button data from device
    // m_cb_read_ibutton is Lpu237Callback.TypeRx.RX_IBUTTON
    bResult = api.WaitMsrOriButtonWithCallback(hDev, m_cb_read_ibutton);

    ///waits here until CallbackFunctionWhenApplyDone is called.
    // this waiting can be cancelled by calling api.CancelWait(hDev) in the other thread.
    // At the end of waiting, you can recall api.WaitMsrOriButtonWithCallback() for reading once more.
    
    bResult = api.EnableiButton(hDev,false);//disable callback

    api.Close(hDev);
    api.Off();
    return "the end of getset scenario";
}

```