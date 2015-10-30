package de.tum.mw.lfe.occlusion;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.Html;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

//------------------------------------------------------
//Revision History 'Occlusion App'
//------------------------------------------------------
//Version	Date			Author				Mod
//1			Mar, 2015	Michael Krause		initial
//1.1	    Oct, 2015	Michael Krause		added web server
//
//------------------------------------------------------

/*
Copyright (C) 2014  Michael Krause (krause@tum.de), Institute of Ergonomics, Technische Universität München

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

public class MainActivity extends Activity {
	
	private static final String TAG = "LFEocclusion.Activity";
	private Preferences mPrefs; 

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		stopService();
		
		mPrefs = new Preferences(this);
		refreshGui();
		init();
		
	}
	

	public void startOccExperimentManual(View v){
		//start service in manual mode
	    Intent serviceIntent = new Intent(); 
	    serviceIntent.setAction("de.tum.mw.lfe.occlusion.START_EXPERIMENT");
	    startService(serviceIntent);
	    
	    finish();
	}
	
	public void startOccService(){
	    Intent serviceIntent = new Intent(); 
	    serviceIntent.setAction("de.tum.mw.lfe.occlusion.START_SERVICE");
	    startService(serviceIntent);
	    
	    //finish();
	}
	
    
	
	private void stopService(){
        if(OccService.isServiceRunning()){
        	Intent i=new Intent(MainActivity.this, OccService.class);
        	stopService(i);
        }
      }	
	
	public void init(){
		 
		//camera rotation
		RadioGroup rotation = (RadioGroup)findViewById(R.id.rotationRadioGroup);
		rotation.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
            	valueChanged();
            }
        });
		//mode listener
		RadioGroup mode = (RadioGroup)findViewById(R.id.modeRadioGroup);
		mode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
            	valueChanged();
            }
        });

		//openms
		EditText openmsText = (EditText)findViewById(R.id.openmsText);
		openmsText.setOnFocusChangeListener(new EditText.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View arg0, boolean arg1) {
				valueChanged();	
			}
        });
		
		//closems
		EditText closemsText = (EditText)findViewById(R.id.closemsText);
		closemsText.setOnFocusChangeListener(new EditText.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View arg0, boolean arg1) {
				valueChanged();	
			}
        });		
		
		//enabledClose
		CheckBox enabledClose = (CheckBox)findViewById(R.id.tttCheckBox);
		enabledClose.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				valueChanged();			
			}
        });
		//enableWakelock
		CheckBox enableWakelock = (CheckBox)findViewById(R.id.wakelockCheckBox);
		enableWakelock.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				valueChanged();			
			}
        });		
		//seekBar grey
		SeekBar gray = (SeekBar)findViewById(R.id.seekBar1);
		gray.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				valueChanged();
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				valueChanged();
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				valueChanged();
				
			}
		});
		
		
		refreshGui();
	}
		
	public void refreshGui(){
		//rotation
		RadioButton rotationRadio;
		switch(mPrefs.getRotation()){
        case 0: 
    		rotationRadio = (RadioButton)findViewById(R.id.RadioButtonRotation0);
    		rotationRadio.setChecked(true);
            break;

        case 90: 
    		rotationRadio = (RadioButton)findViewById(R.id.RadioButtonRotation90);
    		rotationRadio.setChecked(true);
            break;

        case 180: 
    		rotationRadio = (RadioButton)findViewById(R.id.RadioButtonRotation180);
    		rotationRadio.setChecked(true);
            break;

        case 270: 
    		rotationRadio = (RadioButton)findViewById(R.id.RadioButtonRotation270);
    		rotationRadio.setChecked(true);
            break;

        default: 
    		rotationRadio = (RadioButton)findViewById(R.id.RadioButtonRotation0);
    		rotationRadio.setChecked(true);
            break;
		}
		
		//mode

		if (mPrefs.getMode() == Preferences.MODE_SCREEN){
			RadioButton screenRadio = (RadioButton)findViewById(R.id.screenRadio);
			screenRadio.setChecked(true);
            RadioGroup rotRG = (RadioGroup)findViewById(R.id.rotationRadioGroup);
            rotRG.setVisibility(View.GONE);
		}else{
			RadioButton cameraRadio = (RadioButton)findViewById(R.id.cameraRadio);
			cameraRadio.setChecked(true);
            RadioGroup rotRG = (RadioGroup)findViewById(R.id.rotationRadioGroup);
            rotRG.setVisibility(View.VISIBLE);
		}

		//openms
		EditText openmsText = (EditText)findViewById(R.id.openmsText);
		openmsText.setText(Integer.toString(mPrefs.getOpen()));
		//closems
		EditText closemsText = (EditText)findViewById(R.id.closemsText);
		closemsText.setText(Integer.toString(mPrefs.getClose()));
		
		//enabledClose
		CheckBox enabledClose = (CheckBox)findViewById(R.id.tttCheckBox);
		enabledClose.setChecked(mPrefs.getEnabledClose());
		
		closemsText.setEnabled(enabledClose.isChecked());
	
		//enableWakelock
		CheckBox enableWakelock = (CheckBox)findViewById(R.id.wakelockCheckBox);
		enableWakelock.setChecked(mPrefs.getEnableWakelock());		
		
		//grey
		SeekBar greyBar = (SeekBar)findViewById(R.id.seekBar1);
		greyBar.setProgress(mPrefs.getGray());
		
		TextView grey = (TextView)findViewById(R.id.grayColorView);
		grey.setBackgroundColor(mPrefs.getGrayColor());
		

		//ip
		TextView ip = (TextView)findViewById(R.id.ipTextView);
		ip.setText("Telnet IP:"+getIpAddress()+" PORT:"+Integer.toString(OccService.TELNET_PORT) + "\n" +
                   "Webserver IP:"+getIpAddress()+" PORT:"+Integer.toString(OccService.WEB_PORT));
		
	}

	public void savePrefs(){
		
		//rotation
		RadioGroup rotation = (RadioGroup)findViewById(R.id.rotationRadioGroup);
		switch(rotation.getCheckedRadioButtonId()){
        case R.id.RadioButtonRotation0: 
        	mPrefs.setRotation(0);
            break;

        case R.id.RadioButtonRotation90: 
        	mPrefs.setRotation(90);
            break;

        case R.id.RadioButtonRotation180: 
        	mPrefs.setRotation(180);
            break;

        case R.id.RadioButtonRotation270: 
        	mPrefs.setRotation(270);
            break;


        default: 
        	mPrefs.setRotation(0);
            break;
		}
		
		//mode
		RadioGroup mode = (RadioGroup)findViewById(R.id.modeRadioGroup);
		if (mode.getCheckedRadioButtonId() == R.id.screenRadio){
			mPrefs.setMode(Preferences.MODE_SCREEN);
		}else{
			mPrefs.setMode(Preferences.MODE_CAMERA);			
		}
		//open ms
		EditText openmsText = (EditText)findViewById(R.id.openmsText);
		try{mPrefs.setOpen(Integer.parseInt(openmsText.getText().toString()));}catch(Exception e){}
		//close ms
		EditText closemsText = (EditText)findViewById(R.id.closemsText);
		try{mPrefs.setClose(Integer.parseInt(closemsText.getText().toString()));}catch(Exception e){}
		
		//enabledClose
		CheckBox enabledClose = (CheckBox)findViewById(R.id.tttCheckBox);
		mPrefs.setEnabledClose(enabledClose.isChecked());

		//enableWakelock
		CheckBox enableWakelock = (CheckBox)findViewById(R.id.wakelockCheckBox);
		mPrefs.setEnableWakelock(enableWakelock.isChecked());		
		
		//grey 
		SeekBar gray = (SeekBar)findViewById(R.id.seekBar1);
		mPrefs.setGray(gray.getProgress());
		 
	}
	
	void valueChanged(){
		savePrefs();
		refreshGui();
	}



    @Override
    public void onResume(){
        super.onResume();

        startOccService();//this stops running experiment and prepares a new one
    }

    @Override
    public void onPause(){
        super.onPause();
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		//getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	
	public void guiShowAbout(View v){
		
	      String tempStr = "This is an open source GPL implementation of an occlusion app.";
	      tempStr += "<br/><br/> (c) Michael Krause <a href=\"mailto:krause@tum.de\">krause@tum.de</a> <br/>2014 Institute of Ergonomics, TUM";
        tempStr += "<br/><br/>More information on <br/><a href=\"http://www.lfe.mw.tum.de/android-occlusion\">http://www.lfe.mw.tum.de/android-occlusion</a>";
        tempStr += "<br/><br/>Data are logged to folder:"+ OccService.FOLDER;
        tempStr += "<br/><br/> Experiment is stopped when volume is changed (e.g., via volume keys). Occlusion service is quit when device screen goes off (e.g., with power button); to stop timing this (power button) is less accurate than volume keys";
        tempStr += "<br/><br/>You can use a web browser to connect to IP:"+getIpAddress()+ " PORT:"+Integer.toString(OccService.WEB_PORT) + "";
        tempStr += "<br/><br/>or you can telnet into IP:"+getIpAddress()+ " PORT:"+Integer.toString(OccService.TELNET_PORT) + " and use text commands:";
       /*
        tempStr += "<br/>'"+OccService.START_SIGN+"' to start an experiment";
        tempStr += "<br/>'"+OccService.STOP_SIGN+"' to stop an experiment";
        tempStr += "<br/>'"+OccService.EXIT_SIGN+"' to quit occlusion service";
        tempStr += "<br/>'"+OccService.TOGGLE_SIGN+"' to toggle open/close";
        tempStr += "<br/> you can use the numeric keys (1,2,3,...), to mark experimental conditions (marker)";
        */
        tempStr += "<br/><br/>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.";


	      final SpannableString s = new SpannableString(Html.fromHtml(tempStr));
	      Linkify.addLinks(s, Linkify.EMAIL_ADDRESSES|Linkify.WEB_URLS);
	      
	      AlertDialog alert = new AlertDialog.Builder(this)
	          .setMessage( s )
		      .setTitle("Occlusion App "+getVersionString())
		      .setPositiveButton(android.R.string.ok,
		         new DialogInterface.OnClickListener() {
		         public void onClick(DialogInterface dialog, int whichButton){}
		         })
		      .show();
		   
		   ((TextView)alert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance()); 
	
	}
	
	public static String getIpAddress(){//helper
	    try {
	        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
	            NetworkInterface intf = en.nextElement();
	            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
	                InetAddress inetAddress = enumIpAddr.nextElement();
	                if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
	                    return inetAddress.getHostAddress().toString();
	                }
	            }
	        }
	    } catch (Exception e) {
			Log.e(TAG, "getIpAddress() failed: " + e.getMessage());
	    }
	    return "---";
	}

    private String getVersionString(){
        String retString = "";
        String appVersionName = "";
        int appVersionCode = 0;
        try{
            appVersionName = getPackageManager().getPackageInfo(getPackageName(), 0 ).versionName;
            appVersionCode= getPackageManager().getPackageInfo(getPackageName(), 0 ).versionCode;
        }catch (Exception e) {
            Log.e(TAG, "getVersionString failed: "+e.getMessage());
        }

        retString = "V"+appVersionName+"."+appVersionCode;

        return retString;
    }

}
