# How To change system parameters
# Below mandatory code represents example without error handling.
```java
String _mainTaskGetSet(Application in){

    ApiInterface api = ApiFactory.getInstance(ApiFactory.VERSION.LAST);
    api.On(in);//initialize API

    //get deivce list
    ArrayList<UsbDevHandle> listDevPath= api.GetList();

    //... Before opening device, Waits here until grant is received from user.

    //open device
    UsbDevHandle hDev = api.Open(listDevPath.get(0).get_path());

    //get system parameter from device.
    boolean bResult = api.ToolsMsrStartGetSetting(hDev,CallbackFunctionWhenpParameterIsReceived);
    //
    //waits here until CallbackFunctionWhenpParameterIsReceived is called with error or cancel.
    // OR The repeated CallbackFunctionWhenpParameterIsReceived calls are done with all success.

    // get all system parameters.......
    int[] inf = api.ToolsMsrGetActiveAndValiedInterface(hDev);
    int nValue = api.ToolsMsrGetBuzzer(hDev);
    //...
    //call api.ToolsMsrGet...() methods
    //...
    int nEnd = api.ToolsMsrGetiButtonEndZeroBaseOffsetOfRange(hDev);

    // here change system parameters
    // ...

    // set the changed system parameters
    bResult = api.ToolsMsrSetInterface(hDev,nNewInf);
    //...
    //call api.ToolsMsrSet...() methods 
    //...
    bResult = api.ToolsMsrSetiButtonZeroBaseRange(hDev,nNewStart,nNewEnd);

    // the changed system parameter of api memory transfer to device.
    bResult = api.ToolsMsrStartSetSetting(hDev,CallbackFunctionWhenpParameterIsTransfered);
    //
    //waits here until CallbackFunctionWhenpParameterIsTransfered is called with error or cancel.
    // OR The repeated CallbackFunctionWhenpParameterIsTransfered calls are done with all success.


    bResult = api.ToolsMsrStartApplySetting(hDev,CallbackFunctionWhenApplyDone);
    //
    //waits here until CallbackFunctionWhenApplyDone is called.

    api.Close(hDev);
    api.Off();
    return "the end of getset scenario";
}

```