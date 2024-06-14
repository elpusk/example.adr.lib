package kr.co.elpusk.example.lib;

import java.nio.charset.StandardCharsets;

/**
 * this class parse the response of plaintext
 */
public class ParserPlaintextRsp {

    private int[] m_nIsoErrorCode = new int[]{0,0,0};
    private String[] m_sIsoData = new String[]{"","",""};
    private boolean m_bParsingOK = false;
    /**
     * Constructor of parser.
     * @param rx - the response of plaintext
     */
    public ParserPlaintextRsp(byte[] rx){

        do{
            if(rx== null){
                continue;
            }
            if(rx.length < 3){
                continue;
            }
            if( Util.array_has_triple_e6_in_header(rx) ){
                continue;//mat be encrypted response
            }
            int[] nIso = new int[]{rx[0],rx[1],rx[2]};

            byte[] a1 = new byte[0];
            byte[] a2 = new byte[0];
            byte[] a3 = new byte[0];
            String s1 = "", s2="", s3="";

            int nOffset = 3;
            if( nIso[0] >0 ) {
                a1 = new byte[nIso[0]];
                System.arraycopy(rx,nOffset,a1,0,a1.length);
                for (int i = 0; i < a1.length; i++) {
                    a1[i] += 0x20;
                }

                s1 = new String(a1, StandardCharsets.US_ASCII);
                nOffset += a1.length;
                m_sIsoData[0] = s1;
            }
            else{
                m_nIsoErrorCode[0] = nIso[0];
                if(nIso[0] ==0 ){
                    m_sIsoData[0] = "";
                }
            }
            if( nIso[1] >0 ) {
                a2 = new byte[nIso[1]];
                System.arraycopy(rx,nOffset,a2,0,a2.length);
                for (int i = 0; i < a2.length; i++) {
                    a2[i] += 0x30;
                }

                s2 = new String(a2, StandardCharsets.US_ASCII);
                nOffset += a2.length;
                m_sIsoData[1] = s2;
            }
            else{
                m_nIsoErrorCode[1] = nIso[1];
                if(nIso[1] ==0 ){
                    m_sIsoData[1] = "";
                }
            }

            if( nIso[2] >0 ) {
                a3 = new byte[nIso[2]];
                System.arraycopy(rx,nOffset,a3,0,a3.length);
                for (int i = 0; i < a3.length; i++) {
                    a3[i] += 0x30;
                }

                s3 = new String(a3, StandardCharsets.US_ASCII);
                m_sIsoData[2] = s3;
            }
            else{
                m_nIsoErrorCode[2] = nIso[2];
                if(nIso[2] ==0 ){
                    m_sIsoData[2] = "";
                }
            }
            m_bParsingOK = true;
        }while(false);
    }

    /**
     * get parsing result
     * @return true -> success, false -> fail
     */
    public boolean IsSuccessParsing(){
        return m_bParsingOK;
    }


    /**
     * check the each tack error status.
     * @param nIso - track number (0~2) 0 is ISO1 track. and so on.......
     * @return true -> error or invalid parameter, false -> none error.
     */
    public boolean IsError(int nIso){
        boolean bResult = true;

        do{
            if(nIso<0 || nIso >2){
                continue;
            }
            if(m_nIsoErrorCode[nIso]<0){
                continue;
            }
            bResult = false;
        }while(false);
        return bResult;
    }

    /**
     * get iso track data by ASCII string
     * @param nIso - track number (0~2) 0 is ISO1 track. and so on.......
     * @return string of ISO data
     */
    public String GetIsoAscii(int nIso){
        String s = "";

        do{
            if(nIso<0 || nIso >2){
                continue;
            }
            s = m_sIsoData[nIso];
        }while(false);
        return s;
    }

    private ParserPlaintextRsp(){

    }
}
