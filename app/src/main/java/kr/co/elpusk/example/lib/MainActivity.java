package kr.co.elpusk.example.lib;

import android.app.AlertDialog;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import kr.co.elpusk.android.liblpu237.ApiFactory;
import kr.co.elpusk.android.liblpu237.ApiInterface;
import kr.co.elpusk.android.liblpu237.ApiLpu237;
import kr.co.elpusk.android.liblpu237.Lpu237DoneCallbackLambda;
import kr.co.elpusk.android.liblpu237.Lpu237GetSetCallbackLambda;
import kr.co.elpusk.android.liblpu237.UsbDevHandle;
import kr.co.elpusk.android.liblpu237.lpu237.Lpu237Callback;
import kr.co.elpusk.android.liblpu237.lpu237.Lpu237Const;
import kr.co.elpusk.android.liblpu237.lpu237.Lpu237Tags;
import kr.co.elpusk.android.liblpu237.util.Tools;

public class MainActivity extends AppCompatActivity {
    private class CbMsrRead extends Lpu237Callback{
        public CbMsrRead(Lpu237Callback.TypeRx t){
            super(t);
        }
        @Override
        public void Run(Lpu237Callback.Result read_result,byte[] rx){
            //Don't use Main thread GUI Component here!
            //If use it, occur a dead-lock.!
            if(Result.RESULT_SUCCESS == read_result){
                ParserPlaintextRsp rsp = new ParserPlaintextRsp(rx);
                if(rsp.IsSuccessParsing()){
                    String sInfo = "PlainText\n";
                    for( int i=0; i<3; i++ ) {
                        sInfo = sInfo + "iso"+(i+1)+":" + rsp.GetIsoAscii(i).length() + ":" + rsp.GetIsoAscii(i) + "\n";
                    }

                    Util.sendBroadercast(getApplication(), Const.STDOUT, Const.STR_KEY_STDOUT, sInfo);
                }
                else{//encrypted data.
                    //remove triple 0xe6
                    byte[] r = new byte[rx.length-3];
                    System.arraycopy(rx,3,r,0,r.length);
                    String sHex = Tools.get_hex_string_from_binary(r);
                    Util.sendBroadercast(getApplication(), Const.STDOUT, Const.STR_KEY_STDOUT, sHex);
                }
            }
            else if(Result.RESULT_ERROR == read_result){
                Util.sendBroadercast(getApplication(), Const.STDOUT, Const.STR_KEY_STDOUT, "error : read");
            }
            else{
                Util.sendBroadercast(getApplication(), Const.STDOUT, Const.STR_KEY_STDOUT, "cancel : read");
            }

            try {
                blockingQ.put("");
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    Lpu237Callback m_cb_read_msr = new CbMsrRead(Lpu237Callback.TypeRx.RX_MSR);

    private class CbiButtonRead extends Lpu237Callback{
        public CbiButtonRead(Lpu237Callback.TypeRx t){
            super(t);
        }
        @Override
        public void Run(Lpu237Callback.Result read_result,byte[] rx){
            //Don't use Main thread GUI Component here!
            //If use it, occur a dead-lock.!
            if(Result.RESULT_SUCCESS == read_result){
                String sHex = "";
                if(rx != null) {
                    sHex = Util.byteArrayToHex(rx);
                }
                Util.sendBroadercast(getApplication(), Const.STDOUT, Const.STR_KEY_STDOUT, sHex+"\n");
            }
            else if(Result.RESULT_ERROR == read_result){
                Util.sendBroadercast(getApplication(), Const.STDOUT, Const.STR_KEY_STDOUT, "error : read\n");
            }
            else{
                Util.sendBroadercast(getApplication(), Const.STDOUT, Const.STR_KEY_STDOUT, "cancel : read\n");
            }

            try {
                blockingQ.put("");
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    Lpu237Callback m_cb_read_ibutton = new CbiButtonRead(Lpu237Callback.TypeRx.RX_IBUTTON);

    private ExecutorService m_executorService;
    private Handler m_mainHandler;

    private UsbManager usbManager = null;
    private EditText consoleInput;
    private static final String PROMPT = "cmd> ";
    private Button m_btGetSet, m_btReadMsr, m_btReadiButton, m_btReadAll, m_btClear;

    BlockingQueue<Object> blockingQ = new LinkedBlockingQueue<>();
    Queue<String> queue = new LinkedList<>();

    private BroadcastReceiver m_Receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(Const.LPU237_PERMISSION)) {
                // 작업 수행
                try {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);

                    if (granted) {
                        _putToStd("OK : Permission");
                        blockingQ.put("yes");
                    } else {
                        _putToStd("FAIL : Permission");
                        blockingQ.put("no");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            else if(action != null && action.equals(Const.STDOUT)){
                String s = intent.getExtras().getString(Const.STR_KEY_STDOUT,"-_-");
                _putToStd(s);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getApplication().getSystemService(getApplication().USB_SERVICE);

        m_btGetSet = findViewById(R.id.id_bt_get_set);
        m_btGetSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _startMainTaskGetSet(getApplication());
            }
        });

        m_btReadMsr = findViewById(R.id.id_bt_read_msr);
        m_btReadMsr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _startMainTaskReadingMsr(getApplication());
            }
        });

        m_btReadiButton = findViewById(R.id.id_bt_read_ibutton);
        m_btReadiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _startMainTaskReadingiButton(getApplication());
            }
        });

        m_btReadAll = findViewById(R.id.id_bt_read_msr_ibutton);
        m_btReadAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _startMainTaskReadingAll(getApplication());
            }
        });

        m_btClear = findViewById(R.id.id_bt_clear);
        m_btClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                consoleInput.setText("");
            }
        });


        consoleInput = findViewById(R.id.id_main);
        consoleInput.setText(PROMPT);
        consoleInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                String sOrg = consoleInput.getText().toString();
                String input = consoleInput.getText().toString().substring(PROMPT.length()).trim();
                queue.add(input);

                sOrg = sOrg + '\n';
                sOrg = sOrg + PROMPT;
                consoleInput.setText(sOrg);
                setCursorToEnd();
                return true;
            }
            return false;
        });

        //
        String[] sAct = new String[]{Const.LPU237_PERMISSION,Const.STDOUT};
        Util.registerIntentFilter(this,m_Receiver,sAct);
        //
        m_executorService = Executors.newSingleThreadExecutor();
        m_mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        m_executorService.shutdown();
        unregisterReceiver(m_Receiver);
    }

    private void _startMainTaskGetSet(Application input) {
        m_executorService.execute(new Runnable() {
            @Override
            public void run() {
                final String result = _mainTaskGetSet(input);

                m_mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        _putToStd(result);
                    }
                });
            }
        });
    }
    private void _startMainTaskReadingMsr(Application input) {
        m_executorService.execute(new Runnable() {
            @Override
            public void run() {
                final String result = _mainTaskReadingMsr(input);

                m_mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        _putToStd(result);
                    }
                });
            }
        });
    }
    private void _startMainTaskReadingiButton(Application input) {
        m_executorService.execute(new Runnable() {
            @Override
            public void run() {
                final String result = _mainTaskReadingiButton(input);

                m_mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        _putToStd(result);
                    }
                });
            }
        });
    }
    private void _startMainTaskReadingAll(Application input) {
        m_executorService.execute(new Runnable() {
            @Override
            public void run() {
                final String result = _mainTaskReadingAll(input);

                m_mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        _putToStd(result);
                    }
                });
            }
        });
    }

    @NonNull
    private String _mainTaskGetSet(Application in){
        String sResult = "";
        boolean bResult = false;
        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, "Starting test\n");
        ApiInterface api = null;
        Object obj = null;

        boolean b_need_off = false;
        boolean b_need_close = false;
        UsbDevHandle hDev = null;

        int nNewInf = 0;
        int nNewBuzzer = 0;
        int[] nNewIsoStatus = new int[]{0,0,0};
        Lpu237Tags[] NewIsoTagPrivatePrefix = new Lpu237Tags[]{new Lpu237Tags(),new Lpu237Tags(),new Lpu237Tags()};
        Lpu237Tags[] NewIsoTagPrivatePostfix = new Lpu237Tags[]{new Lpu237Tags(),new Lpu237Tags(),new Lpu237Tags()};
        int nNewiButtonMode = 0;
        Lpu237Tags NewiButtonTagPrefix = new Lpu237Tags();
        Lpu237Tags NewiButtonTagPostfix = new Lpu237Tags();
        Lpu237Tags NewiButtonTagRemove = new Lpu237Tags(Lpu237Tags.NUMBER_IBUTTON_REMOVE_TAG);
        Lpu237Tags NewiButtonRTagPrefix = new Lpu237Tags();
        Lpu237Tags NewiButtonRTagPostfix = new Lpu237Tags();
        int nNewStart = 0;
        int nNewEnd = 0;

        do{
            try {
                api = ApiFactory.getInstance(ApiFactory.VERSION.LAST);
                if( api == null){
                    sResult = "ER : create lpu237 api instance";
                    continue;
                }

                ////////////////////////////////////////////////
                // initialize api
                if( !api.On(in) ){
                    sResult = "ER : initialize lpu237 api instance";
                    continue;
                }
                b_need_off = true;

                ////////////////////////////////////////////////
                // get api version
                String sApiVersion = api.GetVersion();
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * API Version : "+sApiVersion+"\n");

                ////////////////////////////////////////////////
                // get lpu237 device list
                ArrayList<UsbDevHandle> listDevPath= api.GetList();
                if( listDevPath.isEmpty()){
                    sResult = "ER : none lpu237 device";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * detected device : "+listDevPath.size()+"\n");
                for( int i=0;i<listDevPath.size(); i++){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** "+listDevPath.get(i).get_path()+"\n");
                }//end for

                ////////////////////////////////////////////////
                ////////////////////////////////////////////////
                //Very important code. before opening device, you must grant the permission
                if(!usbManager.hasPermission(listDevPath.get(0).get_usbdevice())) {
                    Util.sendRequstPermission(in, Const.LPU237_PERMISSION, listDevPath.get(0));
                    obj = blockingQ.take();
                    String sResultPermission = (String) obj;
                    if (sResultPermission.compareTo("yes") != 0) {
                        sResult = "ER : need device permission";
                        continue;
                    }
                }

                ////////////////////////////////////////////////
                //open the first device for test
                hDev = api.Open(listDevPath.get(0).get_path());
                if(hDev.is_empty()){
                    sResult = "ER : Open device";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * opened device : "+hDev.get_path()+"\n");
                b_need_close = true;

                ////////////////////////////////////////////////
                //get system parameter from device.
                if( !api.ToolsMsrStartGetSetting(hDev,new Lpu237GetSetCallbackLambda(true, (result, sDescription, nCurZeroBaseStep, nTotalStep) -> {
                            try {
                                if (result == Lpu237Callback.Result.RESULT_SUCCESS) {
                                    if ((nCurZeroBaseStep+1) == nTotalStep) {//complete
                                        blockingQ.put("success");
                                    } else {
                                        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * loading : " + (nCurZeroBaseStep + 1) + "/" + nTotalStep + " : " + sDescription+"\n");
                                    }
                                } else if (result == Lpu237Callback.Result.RESULT_ERROR) {
                                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * error : " + (nCurZeroBaseStep + 1) + "/" + nTotalStep + " : " + sDescription+"\n");
                                    blockingQ.put("error");
                                } else {//may be cancel
                                    blockingQ.put("cancel");
                                }
                            }
                            catch (Exception e){
                                e.printStackTrace();
                            }
                            return true; // 또는 적절한 반환값
                        })
                ) ){
                    sResult = "ER : Get system parameter : starting";
                    continue;
                }
                obj = blockingQ.take();
                String sResultGetting = (String)obj;
                if(sResultGetting.compareTo("cancel")==0) {
                    sResult = "ER : getting is canceled.";
                    continue;
                }
                if(sResultGetting.compareTo("error")==0) {
                    sResult = "ER : getting system parameter.";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * get system parameter : done.\n");

                ////////////////////////////////////////////////
                // display each system parameter.
                String sVal = "";
                sVal = api.GetId(hDev);
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * device UID : "+sVal+"\n");

                boolean bValue = false;

                bValue = api.ToolsMsrIsSupportMsr(hDev);
                if(bValue){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * device is supporting msr.\n");
                }
                else{
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * device isn't supporting msr.\n");
                }

                bValue = api.ToolsMsrIsSupportIbutton(hDev);
                if(bValue){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * device is supporting ibutton.\n");
                }
                else{
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * device isn't supporting ibutton.\n");
                }

                int[] inf = api.ToolsMsrGetActiveAndValiedInterface(hDev);
                if(inf == null){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                if(inf.length<2){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * supporting interface : \n");
                for( int i=0; i<inf.length-1; i++ ){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** "+ inf[i+1] + "\n");
                }//end for
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** active interface : "+ inf[0] + "\n");
                if(inf[0] == 0){//for changing test.
                    nNewInf = 1;
                }
                else{
                    nNewInf = 0;
                }

                int nValue = 0;
                nValue = api.ToolsMsrGetBuzzer(hDev);
                if(nValue < 0){
                    sResult = "ER : ToolsMsrGetBuzzer.";
                    continue;
                }
                if(nValue == 0){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * buzzer : off.\n");
                    nNewBuzzer = 1;//for changing test.
                }
                else{
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * buzzer : on."+nNewBuzzer+"\n");
                    nNewBuzzer = 0;//for changing test.
                }

                nValue = api.ToolsMsrGetLanguage(hDev);
                if(nValue < 0){
                    sResult = "ER : ToolsMsrGetLanguage.";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * language : "+nValue+"\n");

                Lpu237Tags tag = null;
                bResult = true;
                for(int i=0; i< Lpu237Const.NUMBER_ISO_TRACK; i++){
                    nValue = api.ToolsMsrGetTrackStatus(hDev,i);
                    if(nValue < 0){
                        sResult = "ER : ToolsMsrGetTrackStatus.";
                        bResult = false;
                        break;
                    }
                    if(nValue==0 ){
                        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * disable : iso"+(i+1)+"\n");
                        nNewIsoStatus[i] = 1;//for changing test.
                    }
                    else{
                        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * enable : iso"+(i+1)+"\n");
                        nNewIsoStatus[i] = 0;//for changing test.
                    }

                    tag = api.ToolsMsrGetPrivateTag(hDev,i,true);
                    if(tag == null){
                        sResult = "ER : ToolsMsrGetPrivateTag.prfix";
                        bResult = false;
                        break;
                    }
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * prefix : iso"+(i+1)+" : "+tag+"\n");
                    if(tag.is_empty()) {//for changing test.
                        for( int j=0; j<Lpu237Tags.NUMBER_TAG-1; j++) {
                            NewIsoTagPrivatePrefix[i].push_back((byte) 0xff, (byte) ('0'+j));//'0'~'5' by ASCII code
                        }
                        NewIsoTagPrivatePrefix[i].push_back((byte) 0xff, (byte) 0x0d);//CR by ASCII code
                    }
                    else{
                        NewIsoTagPrivatePrefix[i].clear();
                    }

                    tag = api.ToolsMsrGetPrivateTag(hDev,i,false);
                    if(tag == null){
                        sResult = "ER : ToolsMsrGetPrivateTag.postfix";
                        bResult = false;
                        break;
                    }
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * postfix : iso"+(i+1)+" : "+tag+"\n");
                    if(tag.is_empty()) {//for changing test.
                        for( int j=0; j<Lpu237Tags.NUMBER_TAG-1; j++) {
                            NewIsoTagPrivatePostfix[i].push_back((byte) 0xff, (byte) ('0'+j));//'0'~'5' by ASCII code
                        }
                        NewIsoTagPrivatePostfix[i].push_back((byte) 0xff, (byte) 0x0d);//CR by ASCII code
                    }
                    else{
                        NewIsoTagPrivatePostfix[i].clear();
                    }
                }//end for

                if(!bResult){
                    continue;
                }

                nValue = api.ToolsMsrGetiButtonMode(hDev);
                if(nValue < 0){
                    sResult = "ER : ToolsMsrGetiButtonMode.";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * ibutton mode : "+nValue+"\n");
                if(nValue == 0){//for changing test.
                    nNewiButtonMode = 4;
                }
                else{
                    nNewiButtonMode = 0;
                }

                tag = api.ToolsMsrGetiButtonTag(hDev,true);
                if(tag == null){
                    sResult = "ER : ToolsMsrGetiButtonTag.prfix";
                    bResult = false;
                    break;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * prefix : ibutton : "+tag+"\n");
                if(tag.is_empty()) {//for changing test.
                    for( int j=0; j<Lpu237Tags.NUMBER_TAG-1; j++) {
                        NewiButtonTagPrefix.push_back((byte) 0xff, (byte) ('0'+j));//'0'~'5' by ASCII code
                    }
                    NewiButtonTagPrefix.push_back((byte) 0xff, (byte) 0x0d);//CR by ASCII code
                }
                else{
                    NewiButtonTagPrefix.clear();
                }

                tag = api.ToolsMsrGetiButtonTag(hDev,false);
                if(tag == null){
                    sResult = "ER : ToolsMsrGetiButtonTag.postfix";
                    bResult = false;
                    break;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * postfix : ibutton : "+tag+"\n");
                if(tag.is_empty()) {//for changing test.
                    for( int j=0; j<Lpu237Tags.NUMBER_TAG-1; j++) {
                        NewiButtonTagPostfix.push_back((byte) 0xff, (byte) ('0'+j));//'0'~'5' by ASCII code
                    }
                    NewiButtonTagPostfix.push_back((byte) 0xff, (byte) 0x0d);//CR by ASCII code
                }
                else{
                    NewiButtonTagPostfix.clear();
                }

                tag = api.ToolsMsrGetiButtonRemoveIndicationTag(hDev);
                if(tag == null){
                    sResult = "ER : ToolsMsrGetiButtonRemoveIndicationTag";
                    bResult = false;
                    break;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * remove : ibutton : "+tag+"\n");
                if(tag.is_empty()) {//for changing test.
                    for( int j=0; j<Lpu237Tags.NUMBER_IBUTTON_REMOVE_TAG-1; j++) {
                        NewiButtonTagRemove.push_back((byte) 0xff, (byte) ('0'+j));//'0'~'B' by ASCII code
                    }
                    NewiButtonTagRemove.push_back((byte) 0xff, (byte) 0x0d);//CR by ASCII code
                }
                else{
                    NewiButtonTagRemove.clear();
                }

                ////
                tag = api.ToolsMsrGetiButtonRTag(hDev,true);
                if(tag == null){
                    sResult = "ER : ToolsMsrGetiButtonRTag.prfix";
                    bResult = false;
                    break;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * remove prefix : ibutton : "+tag+"\n");
                if(tag.is_empty()) {//for changing test.
                    for( int j=0; j<Lpu237Tags.NUMBER_TAG-1; j++) {
                        NewiButtonRTagPrefix.push_back((byte) 0xff, (byte) ('0'+j));//'0'~'5' by ASCII code
                    }
                    NewiButtonRTagPrefix.push_back((byte) 0xff, (byte) 0x0d);//CR by ASCII code
                }
                else{
                    NewiButtonRTagPrefix.clear();
                }

                tag = api.ToolsMsrGetiButtonRTag(hDev,false);
                if(tag == null){
                    sResult = "ER : ToolsMsrGetiButtonRTag.postfix";
                    bResult = false;
                    break;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * remove postfix : ibutton : "+tag+"\n");
                if(tag.is_empty()) {//for changing test.
                    for( int j=0; j<Lpu237Tags.NUMBER_TAG-1; j++) {
                        NewiButtonRTagPostfix.push_back((byte) 0xff, (byte) ('0'+j));//'0'~'5' by ASCII code
                    }
                    NewiButtonRTagPostfix.push_back((byte) 0xff, (byte) 0x0d);//CR by ASCII code
                }
                else{
                    NewiButtonRTagPostfix.clear();
                }

                ////
                int nStart = api.ToolsMsrGetiButtonStartZeroBaseOffsetOfRange(hDev);
                if(nStart < 0){
                    sResult = "ER : ToolsMsrGetiButtonStartZeroBaseOffsetOfRange.";
                    continue;
                }
                int nEnd = api.ToolsMsrGetiButtonEndZeroBaseOffsetOfRange(hDev);
                if(nEnd < 0){
                    sResult = "ER : ToolsMsrGetiButtonEndZeroBaseOffsetOfRange.";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * ibutton range : "+nStart+"~"+nEnd+"\n");
                if( nStart == 7 && nEnd == 7){//for changing test.
                    nNewStart = nNewEnd = 0;//set default all range
                }
                else{
                    nNewStart = nNewEnd = 7;
                }

                ////////////////////////////////////////////////
                // the changed system parameter save to api memory.
                bResult = api.ToolsMsrSetInterface(hDev,nNewInf);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetInterface.";
                    continue;
                }

                if(nNewBuzzer == 0) {
                    bResult = api.ToolsMsrSetBuzzer(hDev, false);
                }
                else{
                    bResult = api.ToolsMsrSetBuzzer(hDev, true);
                }
                if(!bResult){
                    sResult = "ER : ToolsMsrSetBuzzer.";
                    continue;
                }

                for(int i=0; i<Lpu237Const.NUMBER_ISO_TRACK; i++) {
                    if( nNewIsoStatus[i] == 0 ) {
                        bResult = api.ToolsMsrSetTrackStatus(hDev, i, false);
                    }
                    else{
                        bResult = api.ToolsMsrSetTrackStatus(hDev, i, true);
                    }
                    if(!bResult) {
                        sResult = "ER : ToolsMsrSetTrackStatus.";
                        break;
                    }
                    bResult = api.ToolsMsrSetPrivateTag(hDev,i,true,NewIsoTagPrivatePrefix[i]);
                    if(!bResult) {
                        sResult = "ER : ToolsMsrSetPrivateTag : prefix";
                        break;
                    }

                    bResult = api.ToolsMsrSetPrivateTag(hDev,i,false,NewIsoTagPrivatePostfix[i]);
                    if(!bResult) {
                        sResult = "ER : ToolsMsrSetPrivateTag. : postfix";
                        break;
                    }
                }//end for
                if(!bResult){
                    continue;
                }

                bResult = api.ToolsMsrSetiButtonMode(hDev,nNewiButtonMode);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetiButtonMode.";
                    continue;
                }

                bResult = api.ToolsMsrSetiButtonTag(hDev,true,NewiButtonTagPrefix);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetiButtonTag : prefix";
                    continue;
                }

                bResult = api.ToolsMsrSetiButtonTag(hDev,false,NewiButtonTagPostfix);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetiButtonTag : postfix";
                    continue;
                }

                bResult = api.ToolsMsrSetiButtonRemoveIndicationTag(hDev,NewiButtonTagRemove);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetiButtonRemoveIndicationTag.";
                    continue;
                }

                bResult = api.ToolsMsrSetiButtonRTag(hDev,true,NewiButtonRTagPrefix);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetiButtonRTag : prefix";
                    continue;
                }

                bResult = api.ToolsMsrSetiButtonRTag(hDev,false,NewiButtonRTagPostfix);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetiButtonRTag : postfix";
                    continue;
                }

                bResult = api.ToolsMsrSetiButtonZeroBaseRange(hDev,nNewStart,nNewEnd);
                if(!bResult){
                    sResult = "ER : ToolsMsrSetiButtonZeroBaseRange.";
                    continue;
                }

                ////////////////////////////////////////////////
                // the changed system parameter of api memory transfer to device.
                if( !api.ToolsMsrStartSetSetting(hDev,new Lpu237GetSetCallbackLambda(false, (result, sDescription, nCurZeroBaseStep, nTotalStep) -> {
                            try {
                                if (result == Lpu237Callback.Result.RESULT_SUCCESS) {
                                    if ((nCurZeroBaseStep+1) == nTotalStep) {//complete
                                        blockingQ.put("success");
                                    } else {
                                        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * loading : " + (nCurZeroBaseStep + 1) + "/" + nTotalStep + " : " + sDescription+"\n");
                                    }
                                } else if (result == Lpu237Callback.Result.RESULT_ERROR) {
                                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * error : " + (nCurZeroBaseStep + 1) + "/" + nTotalStep + " : " + sDescription+"\n");
                                    blockingQ.put("error");
                                } else {//may be cancel
                                    blockingQ.put("cancel");
                                }
                            }
                            catch (Exception e){
                                e.printStackTrace();
                            }
                            return true; // 또는 적절한 반환값
                        })
                ) ){
                    sResult = "ER : Set system parameter : starting";
                    continue;
                }
                obj = blockingQ.take();
                String sResultSetting = (String)obj;
                if(sResultSetting.compareTo("cancel")==0) {
                    sResult = "ER : setting is canceled.";
                    continue;
                }
                if(sResultSetting.compareTo("error")==0) {
                    sResult = "ER : setting system parameter.";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * set system parameter : done.\n");


                ////////////////////////////////////////////////
                // the transferred system parameter of device save to flash memory of device.
                bResult = api.ToolsMsrStartApplySetting(hDev,
                        new Lpu237DoneCallbackLambda((result, sDescription) -> {
                            try {
                                if (Lpu237Callback.Result.RESULT_SUCCESS == result) {
                                    blockingQ.put("success");
                                } else if (result == Lpu237Callback.Result.RESULT_ERROR) {
                                    blockingQ.put("error");
                                } else {
                                    blockingQ.put("cancel");
                                }
                            }
                            catch (Exception e){
                                e.printStackTrace();
                            }
                        })
                );
                if(!bResult){
                    sResult = "ER : apply : starting";
                    continue;
                }
                obj = blockingQ.take();
                String sResultApply = (String)obj;
                if(sResultApply.compareTo("cancel")==0) {
                    sResult = "ER : apply is canceled.";
                    continue;
                }
                if(sResultApply.compareTo("error")==0) {
                    sResult = "ER : apply";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * Apply : done.\n");

                ////////////////////////////////////////////////
                // the special function
                int nRInf = 0;
                if(nNewInf == 0){//for changing test.
                    nRInf = 1;
                }
                else{
                    nRInf = 0;
                }
                bResult = api.ToolsMsrSetInterfaceToDeviceAndApply(hDev,nRInf,
                        new Lpu237DoneCallbackLambda((result, sDescription) -> {
                            try {
                                if (Lpu237Callback.Result.RESULT_SUCCESS == result) {
                                    blockingQ.put("success");
                                } else if (result == Lpu237Callback.Result.RESULT_ERROR) {
                                    blockingQ.put("error");
                                } else {
                                    blockingQ.put("cancel");
                                }
                            }
                            catch (Exception e){
                                e.printStackTrace();
                            }
                        })
                );
                if(!bResult){
                    sResult = "ER : ToolsMsrSetInterfaceToDeviceAndApply : starting";
                    continue;
                }
                obj = blockingQ.take();
                String sResultInfApply = (String)obj;
                if(sResultInfApply.compareTo("cancel")==0) {
                    sResult = "ER : ToolsMsrSetInterfaceToDeviceAndApply is canceled.";
                    continue;
                }
                if(sResultInfApply.compareTo("error")==0) {
                    sResult = "ER : ToolsMsrSetInterfaceToDeviceAndApply";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * ToolsMsrSetInterfaceToDeviceAndApply : done.\n");
                sResult = "OK : all test";

            } catch (Exception e) {
                e.printStackTrace();
            }
        }while(false);

        if(b_need_close){
            api.Close(hDev);
        }
        if(b_need_off){
            api.Off();
        }

        return sResult;
    }

    @NonNull
    private String _mainTaskReadingMsr(Application in){
        String sResult = "";
        boolean bResult = false;
        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, "Starting test\n");
        ApiInterface api = null;
        Object obj = null;

        boolean b_need_off = false;
        boolean b_need_close = false;
        UsbDevHandle hDev = null;

        int nNewInf = 0;
        int nNewBuzzer = 0;
        int[] nNewIsoStatus = new int[]{0,0,0};
        Lpu237Tags[] NewIsoTagPrivatePrefix = new Lpu237Tags[]{new Lpu237Tags(),new Lpu237Tags(),new Lpu237Tags()};
        Lpu237Tags[] NewIsoTagPrivatePostfix = new Lpu237Tags[]{new Lpu237Tags(),new Lpu237Tags(),new Lpu237Tags()};
        int nNewiButtonMode = 0;
        Lpu237Tags NewiButtonTagPrefix = new Lpu237Tags();
        Lpu237Tags NewiButtonTagPostfix = new Lpu237Tags();
        Lpu237Tags NewiButtonTagRemove = new Lpu237Tags(Lpu237Tags.NUMBER_IBUTTON_REMOVE_TAG);
        Lpu237Tags NewiButtonRTagPrefix = new Lpu237Tags();
        Lpu237Tags NewiButtonRTagPostfix = new Lpu237Tags();
        int nNewStart = 0;
        int nNewEnd = 0;

        do{
            try {
                api = ApiFactory.getInstance(ApiFactory.VERSION.LAST);
                if( api == null){
                    sResult = "ER : create lpu237 api instance";
                    continue;
                }

                ////////////////////////////////////////////////
                // initialize api
                if( !api.On(in) ){
                    sResult = "ER : initialize lpu237 api instance";
                    continue;
                }
                b_need_off = true;

                ////////////////////////////////////////////////
                // get api version
                String sApiVersion = api.GetVersion();
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * API Version : "+sApiVersion+"\n");

                ////////////////////////////////////////////////
                // get lpu237 device list
                ArrayList<UsbDevHandle> listDevPath= api.GetList();
                if( listDevPath.isEmpty()){
                    sResult = "ER : none lpu237 device";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * detected device : "+listDevPath.size()+"\n");
                for( int i=0;i<listDevPath.size(); i++){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** "+listDevPath.get(i).get_path()+"\n");
                }//end for

                ////////////////////////////////////////////////
                ////////////////////////////////////////////////
                //Very important code. before opening device, you must grant the permission
                if(!usbManager.hasPermission(listDevPath.get(0).get_usbdevice())) {
                    Util.sendRequstPermission(in, Const.LPU237_PERMISSION, listDevPath.get(0));
                    obj = blockingQ.take();
                    String sResultPermission = (String) obj;
                    if (sResultPermission.compareTo("yes") != 0) {
                        sResult = "ER : need device permission";
                        continue;
                    }
                }

                ////////////////////////////////////////////////
                //open the first device for test
                hDev = api.Open(listDevPath.get(0).get_path());
                if(hDev.is_empty()){
                    sResult = "ER : Open device";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * opened device : "+hDev.get_path()+"\n");
                b_need_close = true;

                ////////////////////////////////////////////////
                //setup for reading
                //you must change the current interface to usb vendor hid.
                int[] inf = api.ToolsMsrGetActiveAndValiedInterface(hDev);
                if(inf == null){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                if(inf.length<2){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                if(inf[0] == 1 ) {
                    bResult = api.SetupForRead(hDev, false);//No need change interface.
                }
                else{
                    bResult = api.SetupForRead(hDev, true);
                }
                if(!bResult){
                    sResult = "ER : SetupForRead.";
                    continue;
                }

                bResult = api.EnableMsr(hDev,true);
                if(!bResult){
                    sResult = "ER : EnableMsr : enable";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * 10 times Reading test.\n");
                for( int i=0; i<10; i++ ) {
                    bResult = api.WaitMsrOriButtonWithCallback(hDev, m_cb_read_msr);
                    if (!bResult) {
                        sResult = "ER : WaitMsrOriButtonWithCallback.";
                        break;
                    }
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** " +(i+1) +" times Reading : \n");
                    obj = blockingQ.take();
                    if(!((String)obj).isEmpty()){
                        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, (String)obj);
                    }
                }//end for

                bResult = api.EnableMsr(hDev,false);
                if(!bResult){
                    sResult = "ER : EnableiButton : disable.";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * TEST complete.\n");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }while(false);

        if(b_need_close){
            api.Close(hDev);
        }
        if(b_need_off){
            api.Off();
        }

        return sResult;
    }

    private String _mainTaskReadingiButton(Application in){
        String sResult = "";
        boolean bResult = false;
        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, "Starting test\n");
        ApiInterface api = null;
        Object obj = null;

        boolean b_need_off = false;
        boolean b_need_close = false;
        UsbDevHandle hDev = null;

        do{
            try {
                api = ApiFactory.getInstance(ApiFactory.VERSION.LAST);
                if( api == null){
                    sResult = "ER : create lpu237 api instance";
                    continue;
                }

                ////////////////////////////////////////////////
                // initialize api
                if( !api.On(in) ){
                    sResult = "ER : initialize lpu237 api instance";
                    continue;
                }
                b_need_off = true;

                ////////////////////////////////////////////////
                // get api version
                String sApiVersion = api.GetVersion();
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * API Version : "+sApiVersion+"\n");

                ////////////////////////////////////////////////
                // get lpu237 device list
                ArrayList<UsbDevHandle> listDevPath= api.GetList();
                if( listDevPath.isEmpty()){
                    sResult = "ER : none lpu237 device";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * detected device : "+listDevPath.size()+"\n");
                for( int i=0;i<listDevPath.size(); i++){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** "+listDevPath.get(i).get_path()+"\n");
                }//end for

                ////////////////////////////////////////////////
                ////////////////////////////////////////////////
                //Very important code. before opening device, you must grant the permission
                if(!usbManager.hasPermission(listDevPath.get(0).get_usbdevice())) {
                    Util.sendRequstPermission(in, Const.LPU237_PERMISSION, listDevPath.get(0));
                    obj = blockingQ.take();
                    String sResultPermission = (String) obj;
                    if (sResultPermission.compareTo("yes") != 0) {
                        sResult = "ER : need device permission";
                        continue;
                    }
                }

                ////////////////////////////////////////////////
                //open the first device for test
                hDev = api.Open(listDevPath.get(0).get_path());
                if(hDev.is_empty()){
                    sResult = "ER : Open device";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * opened device : "+hDev.get_path()+"\n");
                b_need_close = true;

                ////////////////////////////////////////////////
                //setup for reading
                //you must change the current interface to usb vendor hid.
                int[] inf = api.ToolsMsrGetActiveAndValiedInterface(hDev);
                if(inf == null){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                if(inf.length<2){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                if(inf[0] == 1 ) {
                    bResult = api.SetupForRead(hDev, false);//No need change interface.
                }
                else{
                    bResult = api.SetupForRead(hDev, true);
                }
                if(!bResult){
                    sResult = "ER : SetupForRead.";
                    continue;
                }

                bResult = api.EnableiButton(hDev,true);
                if(!bResult){
                    sResult = "ER : EnableMsr : enable";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * 10 times Reading test.\n");
                for( int i=0; i<10; i++ ) {
                    bResult = api.WaitMsrOriButtonWithCallback(hDev, m_cb_read_ibutton);
                    if (!bResult) {
                        sResult = "ER : WaitMsrOriButtonWithCallback with ibutton.";
                        break;
                    }
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** " +(i+1) +" times Reading : \n");
                    obj = blockingQ.take();
                    if(!((String)obj).isEmpty()){
                        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, (String)obj);
                    }
                }//end for

                bResult = api.EnableiButton(hDev,false);
                if(!bResult){
                    sResult = "ER : EnableiButton : disable.";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * TEST complete.\n");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }while(false);

        if(b_need_close){
            api.Close(hDev);
        }
        if(b_need_off){
            api.Off();
        }

        return sResult;
    }

    private String _mainTaskReadingAll(Application in){
        String sResult = "";
        boolean bResult = false;
        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, "Starting test\n");
        ApiInterface api = null;
        Object obj = null;

        boolean b_need_off = false;
        boolean b_need_close = false;
        UsbDevHandle hDev = null;

        do{
            try {
                api = ApiFactory.getInstance(ApiFactory.VERSION.LAST);
                if( api == null){
                    sResult = "ER : create lpu237 api instance";
                    continue;
                }

                ////////////////////////////////////////////////
                // initialize api
                if( !api.On(in) ){
                    sResult = "ER : initialize lpu237 api instance";
                    continue;
                }
                b_need_off = true;

                ////////////////////////////////////////////////
                // get api version
                String sApiVersion = api.GetVersion();
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * API Version : "+sApiVersion+"\n");

                ////////////////////////////////////////////////
                // get lpu237 device list
                ArrayList<UsbDevHandle> listDevPath= api.GetList();
                if( listDevPath.isEmpty()){
                    sResult = "ER : none lpu237 device";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * detected device : "+listDevPath.size()+"\n");
                for( int i=0;i<listDevPath.size(); i++){
                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** "+listDevPath.get(i).get_path()+"\n");
                }//end for

                ////////////////////////////////////////////////
                ////////////////////////////////////////////////
                //Very important code. before opening device, you must grant the permission
                if(!usbManager.hasPermission(listDevPath.get(0).get_usbdevice())) {
                    Util.sendRequstPermission(in, Const.LPU237_PERMISSION, listDevPath.get(0));
                    obj = blockingQ.take();
                    String sResultPermission = (String) obj;
                    if (sResultPermission.compareTo("yes") != 0) {
                        sResult = "ER : need device permission";
                        continue;
                    }
                }

                ////////////////////////////////////////////////
                //open the first device for test
                hDev = api.Open(listDevPath.get(0).get_path());
                if(hDev.is_empty()){
                    sResult = "ER : Open device";
                    continue;
                }
                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * opened device : "+hDev.get_path()+"\n");
                b_need_close = true;

                ////////////////////////////////////////////////
                //setup for reading
                //you must change the current interface to usb vendor hid.
                int[] inf = api.ToolsMsrGetActiveAndValiedInterface(hDev);
                if(inf == null){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                if(inf.length<2){
                    sResult = "ER : ToolsMsrGetActiveAndValiedInterface : null.";
                    continue;
                }
                if(inf[0] == 1 ) {
                    bResult = api.SetupForRead(hDev, false);//No need change interface.
                }
                else{
                    bResult = api.SetupForRead(hDev, true);
                }
                if(!bResult){
                    sResult = "ER : SetupForRead.";
                    continue;
                }

                bResult = api.EnableiButton(hDev,true);
                if(!bResult){
                    sResult = "ER : EnableiButton : enable";
                    continue;
                }
                bResult = api.EnableMsr(hDev,true);
                if(!bResult){
                    sResult = "ER : EnableMsr : enable";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * 10 times Reading test.\n");
                for( int i=0; i<10; i++ ) {
                    bResult = api.WaitMsrOriButtonWithCallback(hDev, m_cb_read_msr);
                    if (!bResult) {
                        sResult = "ER : WaitMsrOriButtonWithCallback with MSR.";
                        break;
                    }
                    bResult = api.WaitMsrOriButtonWithCallback(hDev, m_cb_read_ibutton);
                    if (!bResult) {
                        sResult = "ER : WaitMsrOriButtonWithCallback with ibutton.";
                        break;
                    }

                    Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " ** " +(i+1) +" times Reading : \n");
                    obj = blockingQ.take();
                    if(!((String)obj).isEmpty()){
                        Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, (String)obj);
                    }
                }//end for

                bResult = api.EnableMsr(hDev,false);
                if(!bResult){
                    sResult = "ER : EnableMsr : disable.";
                    continue;
                }
                bResult = api.EnableiButton(hDev,false);
                if(!bResult){
                    sResult = "ER : EnableiButton : disable.";
                    continue;
                }

                Util.sendBroadercast(in, Const.STDOUT, Const.STR_KEY_STDOUT, " * TEST complete.\n");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }while(false);

        if(b_need_close){
            api.Close(hDev);
        }
        if(b_need_off){
            api.Off();
        }

        return sResult;
    }
    /**
     * this function cannot be called directly another thread.
     * Instance of this, Use sendBroadercast(this, Const.STDOUT, Const.STR_KEY_STDOUT, your string data)
     * @param src
     */
    private void _putToStd(String src){
        //TODO. more
        String sOrg = consoleInput.getText().toString();
        consoleInput.setText(sOrg+src);
        consoleInput.setSelection(consoleInput.getText().length());
    }
    private void setCursorToEnd() {
        consoleInput.post(() -> consoleInput.setSelection(consoleInput.getText().length()));
    }
    private void showDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Input Received")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
