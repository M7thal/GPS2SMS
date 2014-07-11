package ru.perm.trubnikov.gps2sms;

import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	// Menu
	public static final int IDM_SETTINGS = 101;
	
	// Dialogs
    private static final int SEND_SMS_DIALOG_ID = 0;
    private final static int PHONE_DIALOG_ID = 1;
	ProgressDialog mSMSProgressDialog;

	// My GPS states
	public static final int GPS_PROVIDER_DISABLED = 1;
	public static final int GPS_GETTING_COORDINATES = 2;
	public static final int GPS_GOT_COORDINATES = 3;
	public static final int GPS_PROVIDER_UNAVIALABLE = 4;
	public static final int GPS_PROVIDER_OUT_OF_SERVICE = 5;
	public static final int GPS_PAUSE_SCANNING = 6;
	
	// Location manager
	private LocationManager manager;
	
	// SMS thread
    ThreadSendSMS mThreadSendSMS;
	
	// Views
	TextView GPSstate;
	Button sendBtn;
	Button enableGPSBtn ;
	Button btnSelContact;
	
	// Globals
	private String coordsToSend;
	
    // Database
    DBHelper dbHelper;
	
    
	// Small util to show text messages by resource id
	protected void ShowToast(int txt, int lng) {
		Toast toast = Toast.makeText(MainActivity.this, txt, lng);
	    toast.setGravity(Gravity.TOP, 0, 0);
	    toast.show();
	}
	
	// Small util to show text messages
	protected void ShowToastT(String txt, int lng) {
		Toast toast = Toast.makeText(MainActivity.this, txt, lng);
	    toast.setGravity(Gravity.TOP, 0, 0);
	    toast.show();
	}
    
	/*
	protected void HideKeyboard() {
		InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        //	check if no view has focus:
        View v=this.getCurrentFocus();
        if(v!=null)
        	inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
	}

	protected void ShowKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(smsEdit, InputMethodManager.SHOW_IMPLICIT);
	}
	 */
	
    // Define the Handler that receives messages from the thread and update the progress
 	// SMS send thread. Result handling
     final Handler handler = new Handler() {
         public void handleMessage(Message msg) {

        	 String res_send = msg.getData().getString("res_send");
             //String res_deliver = msg.getData().getString("res_deliver");

        	 dismissDialog(SEND_SMS_DIALOG_ID);
        	 
        	 if (res_send.equalsIgnoreCase(getString(R.string.info_sms_sent))) {
        		//HideKeyboard();
        		Intent intent = new Intent(MainActivity.this, AnotherMsgActivity.class);
     	     	startActivity(intent);
        	 } else {
            	 MainActivity.this.ShowToastT(res_send, Toast.LENGTH_SHORT);
        	 }
        	 
         }
     };  


	// Location events (we use GPS only)
	private LocationListener locListener = new LocationListener() {
		
		public void onLocationChanged(Location argLocation) {
			printLocation(argLocation, GPS_GOT_COORDINATES);
		}
	
		@Override
		public void onProviderDisabled(String arg0) {
			printLocation(null, GPS_PROVIDER_DISABLED);
		}
	
		@Override
		public void onProviderEnabled(String arg0) {
		}
	
		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		}
	};
	
	private void Pause_GPS_Scanning() {
		manager.removeUpdates(locListener);
	} 
	
	private void Resume_GPS_Scanning() {
		manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locListener);
		sendBtn.setEnabled(false);
		if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			printLocation(null, GPS_GETTING_COORDINATES);
		}
	} 
	
	private void printLocation(Location loc, int state) {
		
		String accuracy;
		
		switch (state) {
		case GPS_PROVIDER_DISABLED :
			GPSstate.setText(R.string.gps_state_disabled);
			GPSstate.setTextColor(Color.RED);
			enableGPSBtn.setVisibility(View.VISIBLE);
			break;
		case GPS_GETTING_COORDINATES :
			GPSstate.setText(R.string.gps_state_in_progress);
			GPSstate.setTextColor(Color.YELLOW);
			enableGPSBtn.setVisibility(View.INVISIBLE);
			break;
		case GPS_PAUSE_SCANNING :
			GPSstate.setText("");
			enableGPSBtn.setVisibility(View.INVISIBLE);
			break;	
		case GPS_GOT_COORDINATES :
			if (loc != null) {

				coordsToSend = String.format(Locale.US , "%2.7f", loc.getLatitude()) + " " + String.format(Locale.US ,"%3.7f", loc.getLongitude());

				// Accuracy
				if (loc.getAccuracy() < 0.0001) {accuracy = "?"; }
					else if (loc.getAccuracy() > 99) {accuracy = "> 99";}
						else {accuracy = String.format(Locale.US, "%2.0f", loc.getAccuracy());};
				
				GPSstate.setText(getString(R.string.info_print1) + " " + accuracy + " " + getString(R.string.info_print2)
						+ "\t\n" + getString(R.string.info_latitude) + " " + String.format(Locale.US , "%2.7f", loc.getLatitude()) 
						+ "\t\n" + getString(R.string.info_longitude) + " " + String.format(Locale.US ,"%3.7f", loc.getLongitude()));
				GPSstate.setTextColor(Color.GREEN);
				sendBtn.setEnabled(true);
				enableGPSBtn.setVisibility(View.INVISIBLE);
				
			}
			else {
				GPSstate.setText(R.string.gps_state_unavialable);
				GPSstate.setTextColor(Color.RED);
				enableGPSBtn.setVisibility(View.VISIBLE);
			}
			break;
		}
	
	}
		
	// Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		//return true;
	
		menu.add(Menu.NONE, IDM_SETTINGS, Menu.NONE, R.string.menu_item_settings);
		return(super.onCreateOptionsMenu(menu));
	}
		
		
	// Dialogs
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        
        case SEND_SMS_DIALOG_ID:
        	  mSMSProgressDialog = new ProgressDialog(MainActivity.this);
        	  //mCatProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        	  mSMSProgressDialog.setCanceledOnTouchOutside(false);
        	  mSMSProgressDialog.setCancelable(false);
        	  mSMSProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        	  mSMSProgressDialog.setMessage(getString(R.string.info_please_wait));
        	  return mSMSProgressDialog;
        	  
        case PHONE_DIALOG_ID:
            LayoutInflater inflater = getLayoutInflater();
            View layout = inflater.inflate(R.layout.phone_dialog, (ViewGroup)findViewById(R.id.phone_dialog_layout));
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(layout);
            
            // Stored msg
            final EditText keyDlgEdit = (EditText) layout.findViewById(R.id.msg_edit_text);
    		dbHelper = new DBHelper(this);
         	keyDlgEdit.setText(dbHelper.getSmsMsg());
    		dbHelper.close();
    		
            builder.setMessage(getString(R.string.info_sms_txt));
            
            builder.setPositiveButton(getString(R.string.save_btn_txt), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {

                	// update 
                	dbHelper = new DBHelper(MainActivity.this);
	        		SQLiteDatabase db = dbHelper.getWritableDatabase();
	        		ContentValues cv = new ContentValues();
	                cv.put("msg", keyDlgEdit.getText().toString());
	                db.update("msg", cv, "_id = ?", new String[] { "1" });
	                dbHelper.close();
	                keyDlgEdit.selectAll(); // ����� ��� ��������� �������� ����� ��� �������
                }
            });
            
            builder.setNegativeButton(getString(R.string.cancel_btn_txt), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                	keyDlgEdit.selectAll(); // ����� ��� ��������� �������� ����� ��� �������
                    dialog.cancel();
                    }
            });
            
            builder.setCancelable(false);

            AlertDialog dialog = builder.create();
            // show keyboard automatically
            keyDlgEdit.selectAll();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            return dialog;

        }
        return null;
    }
		
    // Menu
 	@Override
 	public boolean onOptionsItemSelected(MenuItem item) {
        
        switch (item.getItemId()) {
            case IDM_SETTINGS:
            	showDialog(PHONE_DIALOG_ID);
                break;    
            default:
                return false;
        }
        
        return true;
    }
    
    
	@Override
	protected void onResume() {
		super.onResume();
		Resume_GPS_Scanning();
	}
		
	
	@Override
	protected void onPause() {
		super.onPause();
		Pause_GPS_Scanning();
	}
	
	
	public void showSelectedNumber(String number, String name) {
		if (number.equalsIgnoreCase("") && name.equalsIgnoreCase("")) {
			btnSelContact.setText(getString(R.string.select_contact_btn_txt));
		} else {
			btnSelContact.setText(name + " (" + number + ")");
		}
	}
	
	
	@Override
	public void onActivityResult(int reqCode, int resultCode, Intent data) {
	  super.onActivityResult(reqCode, resultCode, data);

	  switch (reqCode) {
	    case (1001) :
	    	String number = "";
	        String name = "";
	        //int type = 0;
	        if (data != null) {
	            Uri uri = data.getData();

	            if (uri != null) {
	                Cursor c = null;
	                try {
	                	
	                    c = getContentResolver().query(uri, new String[]{ 
	                                ContactsContract.CommonDataKinds.Phone.NUMBER,  
	                                ContactsContract.CommonDataKinds.Phone.TYPE,
	                                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME },
	                            null, null, null);

	                    if (c != null && c.moveToFirst()) {
	                        number = c.getString(0);
	                        number = number.replaceAll("(-| )", "");
	                        //type = c.getInt(1);
	                        name = c.getString(2);
	                        showSelectedNumber(number, name);

	                    	// update 
	                    	dbHelper = new DBHelper(MainActivity.this);
	    	        		SQLiteDatabase db = dbHelper.getWritableDatabase();
	    	        		ContentValues cv = new ContentValues();
	    	                cv.put("contact", name);
	    	                db.update("contact", cv, "_id = ?", new String[] { "1" });
	    	                cv.clear();
	    	                cv.put("phone", number);
	    	                db.update("phone", cv, "_id = ?", new String[] { "1" });
	    	                dbHelper.close();
	                        
	                    }
	                } finally {
	                    if (c != null) { c.close(); }
	                }
	            }
	        }
	    	
	    
	      break;
	  }
	}
	

	// ------------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Select contact
 		btnSelContact = (Button)findViewById(R.id.button2);
 		btnSelContact.setOnClickListener(new OnClickListener() {

 	        	@Override
 	            public void onClick(View v) {
 	        		Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
 	        		intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
 	        		startActivityForResult(intent, 1001);
 	            }
 	        });
        
        // Stored phone number & name -> to button
		dbHelper = new DBHelper(this);
     	showSelectedNumber(dbHelper.getPhone(), dbHelper.getName());
		dbHelper.close();
        
        // GPS init
        manager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);		
      
        //Prepare SMS Listeners, prepare Send button 
        sendBtn = (Button)findViewById(R.id.button1);
        sendBtn.setEnabled(false);
        
        sendBtn.setOnClickListener(new OnClickListener() {

        	@Override
            public void onClick(View v) {
        		
        		dbHelper = new DBHelper(MainActivity.this);
        		String smsMsg = dbHelper.getSmsMsg() + " " + coordsToSend;
        		String phNum = dbHelper.getPhone();
        		dbHelper.close();

        		if (phNum.equalsIgnoreCase("")) {
        			MainActivity.this.ShowToast(R.string.error_contact_is_not_selected, Toast.LENGTH_LONG);
        		} else {
                	showDialog(SEND_SMS_DIALOG_ID);

					// ��������� ����� ����� ��� �������� SMS
					mThreadSendSMS = new ThreadSendSMS(handler, getApplicationContext());
					mThreadSendSMS.setMsg(smsMsg);
					mThreadSendSMS.setPhone(phNum);
					mThreadSendSMS.setState(ThreadSendSMS.STATE_RUNNING);
					mThreadSendSMS.start();
        		}
                
            }
        	
        });
     
        // Enable GPS button
        enableGPSBtn = (Button)findViewById(R.id.button3);
        enableGPSBtn.setVisibility(View.INVISIBLE);
        enableGPSBtn.setOnClickListener(new OnClickListener() {

        	@Override
            public void onClick(View v) {
               	if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
           			startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
           		}
            }
        	
        });
        
    	// GPS-state TextView init
        GPSstate = (TextView)findViewById(R.id.textView1);
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        	GPSstate.setTextColor(Color.YELLOW);
        } else {
        	GPSstate.setTextColor(Color.RED);
        }

        
    }
    
	// ------------------------------------------------------------------------------------------
    
}
