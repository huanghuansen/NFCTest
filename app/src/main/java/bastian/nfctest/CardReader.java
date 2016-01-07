package bastian.nfctest;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.telephony.TelephonyManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Created by Bastian on 11/25/2015.
 */
public class CardReader implements NfcAdapter.ReaderCallback {

    public static int READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

    private MainActivity parentView;

    public CardReader(MainActivity a) {
        NfcAdapter.getDefaultAdapter(a).enableReaderMode(a, this, READER_FLAGS, null);
        parentView = a;
        parentView.appendText("Card Reader Init");
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        NfcA nfc = NfcA.get(tag);
        if(nfc != null)
        {
            try {
                nfc.connect();
                parentView.appendText("Tag Connected: " + bytesToHexString(tag.getId()));

                JSONObject o = new JSONObject();
                if(parentView.isCustomer) {
                    o.put("msg_type", "nfc_customer_con");
                    if(parentView.dialog!=null) {
                        parentView.dialog.dismiss();
                        parentView.dialog=null;
                    }
                }
                else
                    o.put("msg_type", "nfc_item_con");

                JSONObject msg = new JSONObject();
                msg.put("id", new BigInteger(tag.getId()).longValue());
                msg.put("imei", ((TelephonyManager) (parentView.getSystemService(Context.TELEPHONY_SERVICE))).getDeviceId());
                o.put("msg", msg);
                //send message to ERP system that tag is connected with tag information
                parentView.mChannel.basicPublish(parentView.EXCHANGE_NAME, "erp", null, o.toString().getBytes());
                parentView.appendText("published");
                //check every 200 ms if tag is still connected
                while(nfc.isConnected())
                {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                parentView.appendText("Tag disconnected");
                if(parentView.isCustomer)
                    o.put("msg_type", "nfc_customer_discon");
                else
                    o.put("msg_type", "nfc_item_discon");
                //send message that tag is disconnected
                parentView.mChannel.basicPublish(parentView.EXCHANGE_NAME, "erp", null, o.toString().getBytes());
            } catch (IOException e) {
                parentView.appendText("Tag failed");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("0x");
        if (src == null || src.length <= 0) {
            return null;
        }

        char[] buffer = new char[2];
        for (int i = 0; i < src.length; i++) {
            buffer[0] = Character.forDigit((src[i] >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(src[i] & 0x0F, 16);
            System.out.println(buffer);
            stringBuilder.append(buffer);
        }

        return stringBuilder.toString();
    }
}
