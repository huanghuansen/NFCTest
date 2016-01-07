package bastian.nfctest;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;
    MainActivity parentView=this;
    AlertDialog dialog;

    boolean isCustomer;
    String mQueueName;

    Integer mRegal;

    TextView textView;

    TextToSpeech tts;

    Connection mCon;
    Channel mChannel;
    static final String EXCHANGE_NAME = "ex";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //App keeps screen on while open
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textView = (TextView) findViewById(R.id.textfield);
        //enable scrolling for text view
        textView.setMovementMethod(new ScrollingMovementMethod());

        tts = new TextToSpeech(this, this);

        //*** Dialogs order ***
        // 1 - Host dialog
        // 2 - Type dialog
        // 3 - Regal dialog

        // dialogs call each other

        //*** Declaration: Host dialog ***
        final AlertDialog.Builder hostInput= new AlertDialog.Builder(this);
        hostInput.setTitle("Enter IP");
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        hostInput.setView(editText);
        hostInput.setNeutralButton("OK", null);

        //*** Declaration: Regal Dialog ***
        final AlertDialog.Builder regalInput = new AlertDialog.Builder(this);
        regalInput.setTitle("Enter Regal");
        final EditText regalEdit = new EditText(this);
        //only numbers can be entered and keypad is shown
        regalEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        regalInput.setView(regalEdit);
        regalInput.setNeutralButton("OK", null);

        //*** Declaration: Type Dialog ***
        final AlertDialog.Builder typeChooser = new AlertDialog.Builder(this);
        isCustomer = true;
        typeChooser.setSingleChoiceItems(new String[]{"Customer", "Item"}, 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0)
                    isCustomer = true;
                else
                    isCustomer = false;
            }
        });
        typeChooser.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // *** Call: Regal Dialog ***
                final AlertDialog regalDialog = regalInput.show();
                regalDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            JSONObject o = new JSONObject();
                            o.put("msg_type", "nfc_req_regal");
                            o.put("ans_to", mQueueName);

                            JSONObject msg = new JSONObject();
                            msg.put("imei", ((TelephonyManager)(getSystemService(Context.TELEPHONY_SERVICE))).getDeviceId());
                            msg.put("regal", regalEdit.getText().toString());
                            msg.put("isCustomer", isCustomer);
                            o.put("msg", msg);

                            mChannel.basicPublish("ex", "db", null, o.toString().getBytes());

                            while(mRegal == null)
                            {
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            if(mRegal == -1)
                            {
                                regalDialog.setTitle("Error! Enter Regal again");
                            } else
                            {
                                regalDialog.dismiss();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });
            }
        });

        //*** Call: Host Dialog ***
        final AlertDialog hostdialog = hostInput.show();
        hostdialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connectServer(editText.getText().toString())) {
                    hostdialog.dismiss();
                    //*** Call: Type Dialog ***
                    typeChooser.show();
                } else {
                    hostdialog.setTitle("Error! Enter IP again");
                }
            }
        });

        CardReader reader = new CardReader(this);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    public void showMessage(final String msg)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog = new AlertDialog.Builder(parentView).create();
                dialog.setTitle(msg);
                dialog.show();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Display text in the textfield
     * @param s - text to print
     */
    public void appendText(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.append("\n" + s);
            }
        });
    }

    /**
     * Establish connection to rabbitmq server
     * @param ip - host
     * @return - boolean whether connection was established or not
     */
    public Boolean connectServer(final String ip) {
        final Boolean[] returnValue = new Boolean[1];

        new Thread(new Runnable() {
            @Override
            public void run() {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(ip);
                try {
                    mCon = factory.newConnection();
                    mChannel = mCon.createChannel();
                    mQueueName = mChannel.queueDeclare().getQueue();
                    mChannel.queueBind(mQueueName, EXCHANGE_NAME, mQueueName);
                    mChannel.basicConsume(mQueueName, true, new DefaultConsumer(mChannel)
                    {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope,
                                                   AMQP.BasicProperties properties, byte[] body) throws IOException {
                            String msg = new String(body, "UTF-8");
                            appendText(msg);
                            try {
                                final JSONObject o = new JSONObject(msg);
                                switch(o.getString("msg_type"))
                                {
                                    //answer if regal is valid
                                    case "nfc_ans_regal":
                                        if(o.getJSONObject("msg").getBoolean("result"))
                                        {
                                            mRegal = o.getJSONObject("msg").getInt("regal");
                                        } else
                                        {
                                            mRegal = -1;
                                        }
                                        break;
                                    //display a message (e.g. "Kundenkarte auflegen")
                                    case "nfc_disp_msg":

                                        //speaker.setLanguage(Locale.GERMANY);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            tts.speak(o.getJSONObject("msg").getString("msg"), TextToSpeech.QUEUE_FLUSH, null, null);
                                        }
                                        //parentView.showMessage(o.getJSONObject("msg").getString("msg"));
                                        break;
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    appendText("Connection & Channel established: "+ip);
                    returnValue[0] = new Boolean(true);
                    return;
                } catch (IOException e) {
                    appendText("Error: IOException");
                } catch (TimeoutException e) {
                    appendText("Error: TimeoutException");
                }
                returnValue[0] = new Boolean(false);
                return;
            }
        }).start();

        while(returnValue[0] == null)
        {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return returnValue[0];
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                null,
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://bastian.nfctest/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();
        tts.stop();
        tts.shutdown();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                null,
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://bastian.nfctest/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    @Override
    public void onInit(int status) {
        if(status==TextToSpeech.SUCCESS)
        {
            tts.setLanguage(Locale.GERMAN);
            this.appendText("TTS Init Success");
        } else
        {
            this.appendText("TTS Init Failed");
        }
    }
}
