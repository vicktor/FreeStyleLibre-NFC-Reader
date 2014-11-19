package com.socialdiabetes.freestylelibre;

import android.app.Activity;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.graphics.Typeface;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
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
 
    private NfcAdapter NFCAdapter;
    
    private String lectura;
    
    private TextView tvResult;
 
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_abbott);
  
		tvResult = (TextView)findViewById(R.id.result);

        NFCAdapter = NfcAdapter.getDefaultAdapter(this);
        
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/Courier.ttf");
        tvResult.setTypeface(tf);
 
        if (NFCAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;
 
        }
     
        if (!NFCAdapter.isEnabled()) {
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
        setupForegroundDispatch(this, NFCAdapter);
    }
     
    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, NFCAdapter);
         
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
    	        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
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
 
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        
        try {
            filters[0].addDataType(MIME_TEXT_PLAIN);
        } catch (MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }
         
        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
    }
 
    /**
     * @param activity The corresponding {@link BaseActivity} requesting to stop the foreground dispatch.
     * @param adapter The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        adapter.disableForegroundDispatch(activity);
    }
    
    /**
     * 
     * Background task for reading the data. Do not block the UI thread while reading. 
     * 
     */
    private class NfcVReaderTask extends AsyncTask<Tag, Void, String> {
     
    	@Override
        protected void onPostExecute(String result) {
    		if (result != null) {
                Toast.makeText(getApplicationContext(), "Saved as /sdcard/"+result, Toast.LENGTH_LONG).show();
    		}
    	    Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);
        }
    	
        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            
            NfcV nfcvTag = NfcV.get(tag);
            
            try {                   
                nfcvTag.connect();
            } catch (IOException e) {
            	Abbott.this.runOnUiThread(new Runnable() {
            		public void run() {
                        Toast.makeText(getApplicationContext(), "Error opening NFC connection", Toast.LENGTH_SHORT).show();
            		}
            	});

                return null;
            }
            
            lectura = "";

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

                //Log.d("socialdiabetes", "systeminfo HEX: "+bytesToHex(systeminfo));
                
                systeminfo = Arrays.copyOfRange(systeminfo, 2, systeminfo.length - 1);
                
                byte[] memorySize = { systeminfo[6], systeminfo[5]};
                Log.d("socialdiabetes", "Memory Size: "+bytesToHex(memorySize)+" / "+ Integer.parseInt(bytesToHex(memorySize).trim(), 16 ));

                byte[] blocks = { systeminfo[8]};
                Log.d("socialdiabetes", "blocks: "+bytesToHex(blocks)+" / "+ Integer.parseInt(bytesToHex(blocks).trim(), 16 ));
                
                int totalBlocks = Integer.parseInt(bytesToHex(blocks).trim(), 16);

                for(int i=0; i <= totalBlocks; i++) {
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

	                byte[] userdata = nfcvTag.transceive(cmd);
	                Log.d("socialdiabetes", "userdata: "+userdata.toString()+" - "+userdata.length);
	                userdata = Arrays.copyOfRange(userdata, 1, userdata.length);	
	                
	                Log.d("socialdiabetes", "userdata HEX: "+bytesToHex(userdata));
	                lectura = lectura + bytesToHex(userdata)+"\r\n";
	                
                }
                addText(lectura);
            } catch (IOException e) {
            	Abbott.this.runOnUiThread(new Runnable() {
            		public void run() {
            			Toast.makeText(getApplicationContext(), "Error reading NFC!", Toast.LENGTH_SHORT).show();
            		}
            	});
                
                return null;
            }

            try {
                nfcvTag.close();
            } catch (IOException e) {
            	Abbott.this.runOnUiThread(new Runnable() {
            		public void run() {
                        Toast.makeText(getApplicationContext(), "Error closing NFC connection!", Toast.LENGTH_SHORT).show();
            		}
            	});

                return null;
            }
            Log.d("socialdiabetes", "---------------------------------------------------------------");
            
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
            
            return myFile.getName();
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
    

}