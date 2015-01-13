package com.socialdiabetes.freestylelibre;


        import java.io.File;
        import java.io.FileOutputStream;
        import java.io.IOException;
        import java.io.OutputStreamWriter;
        import java.text.SimpleDateFormat;
        import java.util.Arrays;
        import java.util.Date;

        import android.app.Activity;
        import android.app.AlertDialog;
        import android.app.PendingIntent;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.content.IntentFilter.MalformedMimeTypeException;
        import android.media.MediaPlayer;
        import android.media.MediaPlayer.OnCompletionListener;
        import android.nfc.NfcAdapter;
        import android.nfc.Tag;
        import android.nfc.tech.NfcV;
        import android.os.AsyncTask;
        import android.os.Bundle;
        import android.os.Vibrator;
        import android.util.Log;
        import android.widget.TextView;
        import android.widget.Toast;

/**
 *
 * Activity for reading data from FreeStyleLibre Tag
 *
 */
public class Abbott extends Activity {

    public static final String MIME_TEXT_PLAIN = "text/plain";

    private NfcAdapter mNfcAdapter;

    private String lectura, buffer;
    private float currentGlucose = 0f;
    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_abbott);

        tvResult = (TextView)findViewById(R.id.result);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if (mNfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;

        }

        if (!mNfcAdapter.isEnabled()) {
            Toast.makeText(this, "NFC is disabled.", Toast.LENGTH_LONG).show();
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();

        /**
         * It's important, that the activity is in the foreground (resumed). Otherwise
         * an IllegalStateException is thrown.
         */
        setupForegroundDispatch(this, mNfcAdapter);
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, mNfcAdapter);

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        /**
         * This method gets called, when a new Intent gets associated with the current activity instance.
         * Instead of creating a new activity, onNewIntent will be called. For more information have a look
         * at the documentation.
         *
         * In our case this method gets called, when the user attaches a Tag to the device.
         */
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            Log.d("socialdiabetes", "NfcAdapter.ACTION_TECH_DISCOVERED");
            // In case we would still use the Tech Discovered Intent
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            String[] techList = tag.getTechList();
            String searchedTech = NfcV.class.getName();
            new NfcVReaderTask().execute(tag);

        }
    }

    /**
     * @param activity The corresponding {@link Activity} requesting the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }

    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     *
     * Background task for reading the data. Do not block the UI thread while reading.
     *
     */
    private class NfcVReaderTask extends AsyncTask<Tag, Void, String> {

        @Override
        protected void onPostExecute(String result) {
            Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);
            //Abbott.this.finish();
        }

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];

            NfcV nfcvTag = NfcV.get(tag);
            Log.d("socialdiabetes", "Enter NdefReaderTask: " + nfcvTag.toString());

            Log.d("socialdiabetes", "Tag ID: "+tag.getId());


            try {
                nfcvTag.connect();
            } catch (IOException e) {
                Abbott.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error opening NFC connection!", Toast.LENGTH_SHORT).show();
                    }
                });

                return null;
            }

            lectura = "";

            byte[][] bloques = new byte[40][8];
            byte[] allBlocks = new byte[40*8];


            Log.d("socialdiabetes", "---------------------------------------------------------------");
            //Log.d("socialdiabetes", "nfcvTag ID: "+nfcvTag.getDsfId());

            //Log.d("socialdiabetes", "getMaxTransceiveLength: "+nfcvTag.getMaxTransceiveLength());
            try {

                // Get system information (0x2B)
                byte[] cmd = new byte[] {
                        (byte)0x00, // Flags
                        (byte)0x2B // Command: Get system information
                };
                byte[] systeminfo = nfcvTag.transceive(cmd);

                //Log.d("socialdiabetes", "systeminfo: "+systeminfo.toString()+" - "+systeminfo.length);
                //Log.d("socialdiabetes", "systeminfo HEX: "+bytesToHex(systeminfo));

                systeminfo = Arrays.copyOfRange(systeminfo, 2, systeminfo.length - 1);

                byte[] memorySize = { systeminfo[6], systeminfo[5]};
                Log.d("socialdiabetes", "Memory Size: "+bytesToHex(memorySize)+" / "+ Integer.parseInt(bytesToHex(memorySize).trim(), 16 ));

                byte[] blocks = { systeminfo[8]};
                Log.d("socialdiabetes", "blocks: "+bytesToHex(blocks)+" / "+ Integer.parseInt(bytesToHex(blocks).trim(), 16 ));

                int totalBlocks = Integer.parseInt(bytesToHex(blocks).trim(), 16);

                for(int i=3; i <= 40; i++) { // Leer solo los bloques que nos interesan
                	/*
	                cmd = new byte[] {
	                    (byte)0x00, // Flags
	                    (byte)0x23, // Command: Read multiple blocks
	                    (byte)i, // First block (offset)
	                    (byte)0x01  // Number of blocks
	                };
	                */
                    // Read single block
                    cmd = new byte[] {
                            (byte)0x00, // Flags
                            (byte)0x20, // Command: Read multiple blocks
                            (byte)i // block (offset)
                    };

                    byte[] oneBlock = nfcvTag.transceive(cmd);
                    Log.d("socialdiabetes", "userdata: "+oneBlock.toString()+" - "+oneBlock.length);
                    oneBlock = Arrays.copyOfRange(oneBlock, 1, oneBlock.length);
                    bloques[i-3] = Arrays.copyOf(oneBlock, 8);


                    Log.d("socialdiabetes", "userdata HEX: "+bytesToHex(oneBlock));

                    lectura = lectura + bytesToHex(oneBlock)+"\r\n";
                }

                String s = "";
                for(int i=0;i<40;i++) {
                    Log.d("socialdiabetes", bytesToHex(bloques[i]));
                    s = s + bytesToHex(bloques[i]);
                }

                Log.d("socialdiabetes", "S: "+s);

                Log.d("socialdiabetes", "Next read: "+s.substring(4,6));
                int current = Integer.parseInt(s.substring(4, 6), 16);
                Log.d("socialdiabetes", "Next read: "+current);
                Log.d("socialdiabetes", "Next historic read "+s.substring(6,8));

                String[] bloque1 = new String[16];
                String[] bloque2 = new String[32];
                Log.d("socialdiabetes", "--------------------------------------------------");
                int ii=0;
                for (int i=8; i< 8+15*12; i+=12)
                {
                    Log.d("socialdiabetes", s.substring(i,i+12));
                    bloque1[ii] = s.substring(i,i+12);

                    final String g = s.substring(i+2,i+4)+s.substring(i,i+2);

                    if (current == ii) {
                        currentGlucose = glucoseReading(Integer.parseInt(g,16));
                    }
                    ii++;


                }
                lectura = lectura + "Current approximate glucose "+currentGlucose;
                Log.d("socialdiabetes", "Current approximate glucose "+currentGlucose);

                Log.d("socialdiabetes", "--------------------------------------------------");
                ii=0;
                for (int i=188; i< 188+31*12; i+=12)
                {
                    Log.d("socialdiabetes", s.substring(i,i+12));
                    bloque2[ii] = s.substring(i,i+12);
                    ii++;
                }
                Log.d("socialdiabetes", "--------------------------------------------------");

            } catch (IOException e) {
                Abbott.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error reading NFC!", Toast.LENGTH_SHORT).show();
                    }
                });

                return null;
            }

            addText(lectura);

            try {
                nfcvTag.close();
            } catch (IOException e) {
                /*
                Abbott.this.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error closing NFC connection!", Toast.LENGTH_SHORT).show();
                    }
                });

                return null;
                */
            }


            MediaPlayer mp;
            mp = MediaPlayer.create(Abbott.this, R.raw.notification);
            mp.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // TODO Auto-generated method stub
                    mp.reset();
                    mp.release();
                    mp=null;
                }
            });
            mp.start();

            Date date = new Date() ;
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss") ;
            File myFile = new File("/sdcard/fsl_"+dateFormat.format(date) + ".log");
            try {
                myFile.createNewFile();
                FileOutputStream fOut = new FileOutputStream(myFile);
                OutputStreamWriter myOutWriter =new OutputStreamWriter(fOut);
                myOutWriter.append(lectura);
                myOutWriter.close();
                fOut.close();
            }
            catch (Exception e)
            {
            }



            return null;
        }


    }

    private void addText(final String s)
    {
        Abbott.this.runOnUiThread(new Runnable() {
            public void run() {
                tvResult.setText(s);
            }
        });

    }

    private void GetTime(Long minutes){
        Long t3 = minutes;
        Long t4 = t3/1440;
        Long t5 = t3-(t4*1440);
        Long t6 = (t5/60);
        Long t7 = t5-(t6*60);
    }

    private float glucoseReading(int val) {
        // ((0x4531 & 0xFFF) / 6) - 37;
        int bitmask = 0x0FFF;
        return Float.valueOf( Float.valueOf((val & bitmask) / 6) - 37);
    }


}


