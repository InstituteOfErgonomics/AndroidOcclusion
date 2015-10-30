package de.tum.mw.lfe.occlusion;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;

import fi.iki.elonen.NanoHTTPD;

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

public class OccService extends Service implements Camera.PreviewCallback {
    private static final String TAG = "LFEocclusion.Service";
    public Preferences mPrefs;
    private static final int WD_ID = 7021;//unique status bar notification ID magic number
    private static final int LAST_RESULT_ID = 7020;//unique status bar notification ID magic number


    private View mView;
    private boolean mStatus = OPEN;
    public static final boolean OPEN = true;
    public static final boolean CLOSED = false;

    private static boolean mServiceRunning = false;
    private PowerManager.WakeLock mWakeLock = null;
    private CameraPreview mCamView;//view for cam preview
    private SurfaceHolder mHolder;//
    private Camera mCamera;
    private static final int TIME_BETWEEN_FRAMES_SIZE = 10;
    private long[] timeBetweenFrames = new long[TIME_BETWEEN_FRAMES_SIZE];
    private long mLastPreviewFrameT = SystemClock.elapsedRealtime();

    private boolean mIsExperimentRunning = false;


    //logging
    private File mFile = null;
    public static final String CSV_DELIMITER = ";"; //delimiter within csv
    public static final String CSV_LINE_END = "\r\n"; //line end in csv
    public static final String FOLDER = "Occlusion"; //folder
    public static final String FOLDER_DATE_STR = "yyyy-MM-dd";//logging folder format
    public static final String FILE_EXT = ".txt";
    public static final String HEADER = "timestamp;action;mode;closeEnabled;open;gray;displayRefreshRate;avgFrameRate;previewSize;rotation;marker;";

    //action: toggle, prepared, start, stop, open, close

    //instant stats; we calculate and display the results of last experiment directly
    long mTsot;
    long mLastScreenOpenT = NOT_STARTED;
    long mStartOfExperimentT = NOT_STARTED;
    long mStopOfExperimentT;
    public static final long NOT_STARTED = -1;
    //tcp
    private ServerRunnable mServerRunnable = null;
    private Thread mServerThread = null;
    private List<byte[]> mToSend = new ArrayList<byte[]>();
    public static final int TELNET_PORT = 7060; // open this port
    public static final byte[] MARKER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    public static final char START_SIGN = '#'; // send this sign on port to start
    public static final char TOGGLE_SIGN = 't'; // send this sign on port to toggle open/close
    public static final char STOP_SIGN = 'e'; // send this sign on port to get back in prepare mode
    public static final char EXIT_SIGN = '$'; // send this sign on port to stop
    private byte mMarker = '-';//marker received via network


    public static final int WEB_PORT = 7070;//provide webserver gui on this port
    public static final int REFRESH = 2;//refresh every 2s
    private WebServer mWebserver;

    //-------------------------------------------------------------------


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int remoteCommand = msg.what;

            switch (remoteCommand) {
                case STOP_SIGN:
                    experimentStop();
                    break;
                case START_SIGN:
                    experimentStart();
                    break;
                case EXIT_SIGN:
                    experimentStopAndQuit();
                    break;
                case TOGGLE_SIGN:
                    toggle();
                    break;
                default:
                    break;

            }//switch

            for (int i = 0; i < MARKER.length; i++) {
                if (remoteCommand == MARKER[i]) {
                    mMarker = MARKER[i];
                    sendMessageToClient("* marker [i]/decimal/ASCII: [" + Integer.toString(i) + "]/" + Integer.toString(mMarker) + "/" + String.valueOf(Character.toChars(mMarker)));
                    break;
                    //Message msg2 = mHandler.obtainMessage();
                    //msg2.what = UPDATE_MARKER_TEXT;
                    //mHandler.sendMessage(msg2);

                }
            } //for
        }
    };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //handleCommand(intent);

        if (intent != null) {
            //--------------------------------------------------------------------------------
            if (intent.getAction().equals("de.tum.mw.lfe.occlusion.START_EXPERIMENT")) {
                //Log.i(TAG, "START_EXPERIMENT intent received");
                experimentStart();

            }
            //--------------------------------------------------------------------------------
            if (intent.getAction().equals("de.tum.mw.lfe.occlusion.STOP_EXPERIMENT")) {
                //Log.i(TAG, "STOP_EXPERIMENT intent received");
                experimentStop();

            }
            //--------------------------------------------------------------------------------
            if (intent.getAction().equals("de.tum.mw.lfe.occlusion.START_SERVICE")) {
                //Log.i(TAG, "START_SERVICE intent received");
                //experiment is later started via TCP commands over network or manually

                if (mIsExperimentRunning) {
                    experimentStop();
                }

            }
            //--------------------------------------------------------------------------------

        }//!=null

        return START_STICKY;     // We want this service to continue running until it is explicitly  stopped, so return sticky.
    }


    //-------------------------------------------------------------------

    class ServerRunnable implements Runnable {
        private CommunicationThread commThread;
        private Thread thread;
        private ServerSocket mServerSocket;
        private Socket mClientSocket;

        public ServerRunnable() {
            try {
                if (mServerSocket == null) mServerSocket = new ServerSocket(TELNET_PORT);
            } catch (Exception e) {
                Log.e(TAG, "ServerThread failed on open port: " + e.getMessage());
            }
        }

        public void run() {

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    mClientSocket = mServerSocket.accept();
                    commThread = new CommunicationThread(mClientSocket);
                    thread = new Thread(commThread);
                    thread.start();
                    Thread.sleep(100);
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {//only log error if this was not an intentional interrupt
                        Log.e(TAG, "ServerThread failed on accept connection: " + e.getMessage());
                    }
                }
            }//while

            closeSockets();

        }//run

        //helpers
        public void closeSockets() {
            try {
                if (mServerSocket != null) mServerSocket.close();
                if (mClientSocket != null) mClientSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "ServerThread failed to close sockets: " + e.getMessage());
            }
        }

    }

    //-------------------------------------------------------------------
    private class WebServer extends NanoHTTPD {

        public WebServer() {
            super(WEB_PORT);
        }

        @Override
        public Response serve(String uri, Method method,
                              Map<String, String> header, Map<String, String> parameters,
                              Map<String, String> files) {

            Log.i(TAG, "WebServer():" + uri);

            String serverURL = "http://" + MainActivity.getIpAddress() + ":" + Integer.toString(WEB_PORT);

            if (uri.endsWith("listing")) {
                File folder = new File(getLoggingFolder());
                File[] dir1files = folder.listFiles();
                StringBuilder page = new StringBuilder(10000);//~10KByte
                page.append("<html><head><title>Occlusion File Listing</title></head><body><a href=\"" + serverURL + "\">&lt;&lt;&lt;Control panel</a><hr/>");
                page.append("<br/>");
                page.append(getLoggingFolder());
                page.append("<br/>");
                page.append("<ul>");
                for (File file1 : dir1files) {
                    if (file1.isDirectory()) {
                        page.append("<li><b>");
                        page.append(file1.getName());
                        page.append("</b></li>");
                        page.append("<ul>");
                        File[] dir2files = file1.listFiles();
                        for (File file2 : dir2files) {
                            page.append("<li>");
                            String fname = "";
                            try {
                                fname = URLEncoder.encode(file2.getName(), "UTF-8");
                            } catch (Exception e) {
                                Log.e(TAG, "URLEncoder failed: " + e.getMessage());
                                fname = "";
                            }
                            page.append("<a href=\"" + serverURL + "/download?filename=" + fname + "\">" + fname + "</a>");
                            page.append("</li>");
                        }
                        page.append("</ul>");
                    } else {
                        page.append("<li>");
                        String fname = "";
                        try {
                            fname = URLEncoder.encode(file1.getName(), "UTF-8");
                        } catch (Exception e) {
                            Log.e(TAG, "URLEncoder failed: " + e.getMessage());
                            fname = "";
                        }
                        page.append("<a href=\"" + serverURL + "/download?filename=\"" + fname + "\">" + fname + "</a>");
                        page.append("</li>");
                    }

                }
                page.append("</ul>");
                page.append("</body></html>");
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_HTML, page.toString());
            }



            if ((uri.contains("download"))) {
                String filename = URLDecoder.decode(parameters.get("filename"));

                File folder = new File(getLoggingFolder());
                File[] dir1files = folder.listFiles();
                for (File file1 : dir1files) {
                    if (file1.isDirectory()) {
                        File[] dir2files = file1.listFiles();
                        for (File file2 : dir2files) {
                            if (file2.getName().equals(filename)) {
                                try{
                                    FileInputStream fis = new FileInputStream(file2);
                                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, fis, file2.length());
                                } catch (Exception e) {
                                    Log.e(TAG, "send file failed: " + e.getMessage());
                                }
                            }
                        }
                    } else {
                        if (file1.getName().equals(filename)) {
                         //this should not happen; data files are always stored inside subfolders
                        }
                    }
                }

            }


            if (uri.contains("control")) {
                String command = parameters.get("command");
                if (command.equals("start")) {
                    Message msg = mHandler.obtainMessage();
                    msg.what = START_SIGN;
                    mHandler.sendMessage(msg);
                }
                if (command.equals("stop")) {
                    Message msg = mHandler.obtainMessage();
                    msg.what = STOP_SIGN;
                    mHandler.sendMessage(msg);
                }
                if (command.equals("toggle")) {
                    Message msg = mHandler.obtainMessage();
                    msg.what = TOGGLE_SIGN;
                    mHandler.sendMessage(msg);
                }
                if (command.equals("marker")) {
                    Message msg = mHandler.obtainMessage();
                    msg.what = 48+Integer.parseInt(parameters.get("marker"));//48+x convert 1-9 to ascii code
                    mHandler.sendMessage(msg);
                }
            }


            //default


            String status0;
            if (mStatus == OPEN) {
                status0 = "<br/>Shutter is: open";
            } else {
                status0 = "<br/>Shutter is: closed";
            }

            String status1 = "";
            if (mIsExperimentRunning) {
                status1 = "<br/>experiment running<br/>current TTT:" + getFormated(SystemClock.elapsedRealtime() - mStartOfExperimentT) + "s <br/>current TSOT:" + getFormated(mTsot) + "s";
            } else {
                status1 = "<br/>no experiment running at the moment";
            }

            String status2 = "";
            if (mPrefs != null) {
                status2 = "<br/>Last result TTT:" + getFormated(mPrefs.getLastTtt()) + "s TSOT:" + getFormated(mPrefs.getLastTsot()) + "s";
            }

            String marker = "<br/>experimental marker:"+(char)mMarker;


            String page = "<html><head><title>Occlusion Control</title><meta http-equiv=\"refresh\" content=\""+ Integer.toString(REFRESH)+"; URL=" + serverURL + "\"/></head><body>" +
                    "<a href=\"" + serverURL + "/listing\">file listing</a><hr/><ul>" +
                    "<li><a href=\"" + serverURL + "/control?command=start\">start</a></li>" +
                    "<li><a href=\"" + serverURL + "/control?command=stop\">stop</a></li>" +
                    "<li><a href=\"" + serverURL + "/control?command=toggle\">toggle</a></li>" +
                    "<li>marker: " +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=0\"> [ 0 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=1\"> [ 1 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=2\"> [ 2 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=3\"> [ 3 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=4\"> [ 4 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=5\"> [ 5 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=6\"> [ 6 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=7\"> [ 7 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=8\"> [ 8 ] </a>" +
                    "<a href=\"" + serverURL + "/control?command=marker&marker=9\"> [ 9 ] </a>" +
                    "</li>" +
                    "</ul><br/>[updates every "+Integer.toString(REFRESH)+"s]" + status1 + status2 + status0 + marker +"</body></html>";

            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_HTML, page);

        }
    }
//-------------------------------------------------------------------

	class CommunicationThread implements Runnable {//telnet

		private Socket clientSocket;
		private BufferedReader input;
		//private BufferedWriter output;
		private OutputStream output;


		public CommunicationThread(Socket clientSocket) {
			this.clientSocket = clientSocket;

			try {
				this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
				//this.output = new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));
				this.output = this.clientSocket.getOutputStream();
			} catch (Exception e) {
            	Log.e(TAG,"CommunicationThread failed on create streams: "+ e.getMessage());
			}
		}

        public void run() {

		    //Message msg = mHandler.obtainMessage();
		    //msg.what = CONNECTED;
		    //mHandler.sendMessage(msg);

            sendMessageToClient("-----------------------------------------------------------------------");
            sendMessageToClient(" Occlusion ");
            sendMessageToClient("-----------------------------------------------------------------------");
            sendMessageToClient(" '"+OccService.START_SIGN+"' to start an experiment");
            sendMessageToClient(" '"+OccService.STOP_SIGN+"' to stop an experiment");
            sendMessageToClient(" '"+OccService.EXIT_SIGN+"' to quit occlusion service");
            sendMessageToClient(" '"+OccService.TOGGLE_SIGN+"' to toggle open/close when no experiment");
            sendMessageToClient(" you can use the numeric keys (1,2,3,...), ");
            sendMessageToClient(" to mark experimental conditions (marker)");
            sendMessageToClient(" to start occlusion service again. start occlusion or ");
            sendMessageToClient(" turn from landscape to portrait when occlusion app is running");
            sendMessageToClient("-----------------------------------------------------------------------");


			int read;
			while ((!Thread.currentThread().isInterrupted()) && (!clientSocket.isClosed())) {

				SystemClock.sleep(1);

				try {
					if(input.ready()){
						read = input.read();
					}else{
						read =-1;
					}
					if (read != -1){
                        Message msg = mHandler.obtainMessage();
                        msg.what = read;
                        mHandler.sendMessage(msg);

                        //echo back:
                        //sendMessageToClient(String.valueOf(Character.toChars(read)));

                    }//if



					//output

					synchronized(mToSend){//sync
						if(mToSend.size() > 0){
							output.write(mToSend.get(0),0,mToSend.get(0).length);//send first in queue
							output.flush();
							mToSend.remove(0);//remove first from queue

						}
					}//sync


				} catch (Exception e) {
	            	Log.e(TAG,"CommunicationThread failed while input/output: "+ e.getMessage());
	            	Thread.currentThread().interrupt();
				}

			}//while
			try{
				input.close();
				output.close();
			} catch (Exception e) {
            	Log.e(TAG,"CommunicationThread failed on closing streams: "+ e.getMessage());
			}
		    //Message msg2 = mGuiHandler.obtainMessage();
		    //msg2.what = NOT_CONNECTED;
		    //mGuiHandler.sendMessage(msg2);

		}//run

	}
//-------------------------------------------------------------------

	private void clearPacketsToSendQueue(){

		synchronized(mToSend){//sync against append and send
		   mToSend.clear();
		}//sync


	}

    private void sendMessageToClient(String msg) {
        try {
            msg += "\r\n";
            byte b[] = msg.getBytes("US-ASCII");
            appendBytesToSend(b);
        } catch (Exception e) {
        }
    }

	private void appendBytesToSend(byte[] message){
				
		synchronized(mToSend){//sync against send and clear
						
			mToSend.add(message);//queque this status byte
			
		}//sync	
	}

    private void startWebServer(){
        mWebserver = new WebServer();
        try {
            mWebserver.start();
        } catch(Exception e) {
            Log.e(TAG, "Error while web starting:"+e.getMessage());
        }
        Log.i(TAG, "Web server started");
    }

    private void stopWebServer(){
        if (mWebserver != null) mWebserver.stop();
    }


    private void startTelnetServer(){
		if (mServerRunnable == null){
			mServerRunnable	= new ServerRunnable();
		}
		if (mServerThread == null){
		    mServerThread = new Thread(mServerRunnable);
		    mServerThread.start();
		}
	}
	
	private void stopTelnetServer(){
        try {
        	mServerThread.interrupt();
        	mServerRunnable.closeSockets();
        } catch (Exception e) {
			Log.e(TAG, "mServerThread.interrupt() failed: " + e.getMessage());
        }
	}	

    private void prepare(boolean openNewFile){//prepare everything so we can start

        if ((openNewFile) || (mFile == null)) {
            mFile = prepareLogging();//prepare new logging file
        }

        if (mPrefs.getEnableWakelock()) getWakeLock();

        if (mPrefs.getMode() == mPrefs.MODE_CAMERA) initCam();//init cam only in MODE_CAMERA

        if (mView == null) {
            mView = new LinearLayout(this);
            mView.setBackgroundColor(mPrefs.getGrayColor());
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.FILL_PARENT,
                    WindowManager.LayoutParams.FILL_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.addView(mView, params);
            mView.setBackgroundColor(mPrefs.getGrayColor() - 0xff000000);//remove opacity => transparent
            mView.bringToFront();
            mStatus = OPEN;
        }
    }

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private void experimentStart(){

        if(!mIsExperimentRunning) {

            clearPacketsToSendQueue();//clear message buffer
            sendMessageToClient("--------------------------------");
            sendMessageToClient("new experiment");

            note("experiment running");

            prepare(true);//prepare & open new logging file

            logging("* start");

            if (mView != null) {
                mView.post(screenOpenRunnable);
                if (mPrefs.getEnabledClose()) {
                    mView.postDelayed(screenCloseDelayedOpen, mPrefs.getClose());//schedule first close
                }
            }

            mStartOfExperimentT = SystemClock.elapsedRealtime();
            mTsot = 0;

            mIsExperimentRunning = true;
        }
	}


    private void resetTimes(){
        mTsot = 0;
        mLastScreenOpenT = NOT_STARTED;
        mStartOfExperimentT = NOT_STARTED;
    }

	private void stop(){
        mStopOfExperimentT = SystemClock.elapsedRealtime();
        mIsExperimentRunning = false;

        if (mStatus == OPEN){
            long nowT = SystemClock.elapsedRealtime();
            mTsot += nowT- mLastScreenOpenT;
            if (mLastScreenOpenT == NOT_STARTED){mTsot = 0;}
        }

        //save for instant stats last result to preferences
        mPrefs.setLastTsot(mTsot);
        Long ttt;
        if (mStartOfExperimentT == NOT_STARTED){
            ttt = 0l;
        }else{
            ttt = mStopOfExperimentT - mStartOfExperimentT;
        }
        mStartOfExperimentT = NOT_STARTED;//reset start
        mPrefs.setLastTtt(ttt);


        noteResult(mTsot);//show last result in notification bar

        sendMessageToClient("*TSOT: "+getFormated(mTsot)+"s");

        openShutter();

        resetTimes();

        //releaseCam();

        removeCamView();

    }

	public void experimentStop(){
        if (mIsExperimentRunning) {

            logging("stop");

            stop();

            note("experiment stopped");
        }
    }

    public void experimentStopAndQuit() {

        experimentStop();

        //Intent i=new Intent(this, OccService.class);
        //stopService(i);
        this.stopSelf();

    }

    BroadcastReceiver mReceiver = new BroadcastReceiver() {//e.g. some one has pressed the off button
        @Override
        public void onReceive(Context context, Intent intent) {
	        if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF)){
	            //here we can use the power off button, which cause screen off
	            //problem: after press on power button it needs about half or up to one second before screen is off => volume keys are faster
                experimentStopAndQuit();
	        }
            if(intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")){
                //here we can use the volume keys, which cause VOLUME_CHANGED_ACTION
                //problem: if a task involves adjustment of volume (even with a slider within the app) the occlusion is stopped
                experimentStop();
            }
        }
    };


	@Override
	public void onCreate() {

        mPrefs = new Preferences(this);

		startTelnetServer();
        startWebServer();

		mServiceRunning = true;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(mReceiver, filter);

        note("is running");

	}

    private void note(String msg){
        //foreground
        int icon = R.drawable.ic_launcher;
        CharSequence tickerText = "Occlusion service is running";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        Context context = getApplicationContext();
        CharSequence contentTitle = "Occlusion Service";
        CharSequence contentText = msg;
        //Intent notificationIntent = new Intent();
        Intent notificationIntent = new Intent(context, de.tum.mw.lfe.occlusion.MainActivity.class); //start activity when user selects notification
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        startForeground(WD_ID, notification);
    }


    public static Boolean isServiceRunning(){
		return mServiceRunning;
	}

    private void removeCamView(){
        try {
            if (mCamView != null) {
                //mCamView.surfaceDestroyed(mCamView.getSurfaceHolder());
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.removeView(mCamView);
            }
        }catch(Exception e){
            Log.e(TAG,"removeCamView() failed:"+ e.getMessage());
        }

    }


        @Override
	public void onDestroy() {

        if (mIsExperimentRunning){
            experimentStop();
        }

        openShutter();


        releaseCam();

        removeCamView();

		if (mView != null){
			mView.removeCallbacks(screenOpenRunnable);
			mView.removeCallbacks(screenCloseRunnable);
			mView.removeCallbacks(screenCloseDelayedOpen);
			mView.removeCallbacks(screenOpenDelayedClose);
		}
		/*
		if(mView!=null){
			WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
			wm.removeView(mView);
		}
		*/
		
		
        if(mWakeLock != null){
        	mWakeLock.release();
        	mWakeLock = null;
        }
        		
		
		mServiceRunning = false;
		unregisterReceiver(mReceiver);
		
		stopTelnetServer();
        stopWebServer();
		
		//android.os.Process.killProcess(android.os.Process.myPid());

	}	
	
	
	
	private void initCam(){
		Log.d(TAG,"initCam()");
        releaseCam();
		
		try{
			//int mCamId = 0;
			//mCamera = Camera.open(mCamId);
			mCamera = Camera.open();//without argument => first rear facing
			mCamera.lock();
			mCamView = new CameraPreview(this, this, mCamera);	
	
		}catch(Exception e){
	    	Log.e(TAG,"initCam() failed:"+ e.getMessage());
		}	
	}	
	
    public void onPreviewFrame(byte[] data, Camera camera) {//Camera.PreviewCallback
    	//shift array
    	for(int i=0; i<TIME_BETWEEN_FRAMES_SIZE-1; i++){
    		timeBetweenFrames[i] = timeBetweenFrames[i+1];
    	}
    	long nowT = SystemClock.elapsedRealtime();
    	timeBetweenFrames[TIME_BETWEEN_FRAMES_SIZE-1] =  nowT - mLastPreviewFrameT;
		mLastPreviewFrameT = nowT;
    	mCamera.addCallbackBuffer(data);
    }
    
    
    public long getAveragFramerate(){//frames per second multiplied by 1000
    	int fps=0;
    	long sum = 0;
    	for(int i=0; i<TIME_BETWEEN_FRAMES_SIZE; i++){
    		sum += timeBetweenFrames[i];
    	}

    	//note: time is in millisec
    	long temp = 0;
    	try{
    		temp = (1000000/ (sum / TIME_BETWEEN_FRAMES_SIZE));
    	} catch(Exception e){
    		temp = -1;
    	}
    	return temp;
    }

	public void releaseCam(){
		try{		
			if  (mCamera != null){
				mCamera.reconnect();
				mCamera.stopPreview();
				mCamera.release();
			}
		}catch(Exception e){
	    	Log.e(TAG,"releaseCam() failed:"+ e.getMessage());
		}				

	}
	
	
	public void toggle(){//switch from immediately from open to close; called via 't'oggle command via TCP-network
        prepare(false);//prepare & dont open a new logging file

		if(!mIsExperimentRunning){//only if experiment is not running
			if(mView!=null){
				if (mStatus == OPEN){
					mView.post(screenCloseRunnable);
				}else{
					mView.post(screenOpenRunnable);
				}
				logging("toggle");
			}		
		}
		
	}
	
	private void openShutter(){
		if (mView != null){
			mView.setBackgroundColor(mPrefs.getGrayColor()-0xff000000);//remove opacity => transparent
			mStatus = OPEN;
		}
        mLastScreenOpenT = SystemClock.elapsedRealtime();

        if (mIsExperimentRunning){//send msg handling
            long sinceStart = mLastScreenOpenT -mStartOfExperimentT;
            sendMessageToClient("* open  " + Long.toString(sinceStart));
        }else{
            sendMessageToClient("* open");
        }
	}
	
	private void closeShutter(){
		if (mView != null){
			mView.setBackgroundColor(mPrefs.getGrayColor());
			mStatus = CLOSED;
		}

        if (mIsExperimentRunning){
            //TSOT handling
            long nowT = SystemClock.elapsedRealtime();
            mTsot += nowT - mLastScreenOpenT;

            //send msg handling
            long sinceStart = nowT -mStartOfExperimentT;
            sendMessageToClient("* close " + Long.toString(sinceStart));
        }else{
            sendMessageToClient("* close");
        }
    }

    private Runnable screenOpenRunnable = new Runnable() {//used by toggle via network
		@Override
		public void run() {
			openShutter();
		} 			    	
    };	
	
    private Runnable screenCloseRunnable = new Runnable() {//used by toggle via network
		@Override
		public void run() {
			closeShutter();
		} 			    	
    };	
	
    private Runnable screenOpenDelayedClose = new Runnable() {
		@Override
		public void run() {
			if (mIsExperimentRunning){
				openShutter();
				logging("open");		
				mView.postDelayed(screenCloseDelayedOpen, mPrefs.getClose());//schedule the next close
			}	
		} 			    	
    };	
	
    private Runnable screenCloseDelayedOpen = new Runnable() {
		@Override
		public void run() {
			if (mIsExperimentRunning){
				closeShutter();
				logging("close");
				mView.postDelayed(screenOpenDelayedClose, mPrefs.getOpen());//schedule the next open
			}	
		} 			    	
    };	

    
    private String getLoggingFolder(){//helper
        return Environment.getExternalStorageDirectory () + File.separator + FOLDER + File.separator;
    }
	
    protected void getWakeLock(){
	    try{
			PowerManager powerManger = (PowerManager) getSystemService(Context.POWER_SERVICE);
	        mWakeLock = powerManger.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.FULL_WAKE_LOCK, "de.tum.ergonomie.occlusion");
	        mWakeLock.acquire();
	        //Log.i(TAG,"getWakelock()");
		}catch(Exception e){
        	Log.e(TAG,"get wakelock failed:"+ e.getMessage());
		}	
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
	
	private void toasting(final String msg, final int duration){
		Context context = getApplicationContext();
		CharSequence text = msg;
		Toast toast = Toast.makeText(context, text, duration);
        toast.setDuration(duration);
		toast.show();		
	}
	
	private File  prepareLogging(){
		File file = null;
		File folder = null;
		SimpleDateFormat  dateFormat = new SimpleDateFormat(FOLDER_DATE_STR);
		String folderTimeStr =  dateFormat.format(new Date());
		String timestamp = Long.toString(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
	   try{
		   //try to prepare external logging
		   String folderStr = getLoggingFolder() + folderTimeStr;
		   file = new File(folderStr, timestamp + FILE_EXT);
		   folder = new File(folderStr);
		   folder.mkdirs();//create missing dirs
		   file.createNewFile();
		   if (!file.canWrite()) throw new Exception();
		   
			String header = HEADER +  getVersionString() + "\r\n";
		    byte[] headerBytes = header.getBytes("US-ASCII");
			writeToFile(headerBytes,file);
	
	   }catch(Exception e){
		   try{
	    	   error("maybe no SD card inserted");//toast
			   this.stopSelf();//we quit. we will not continue without file logging

			   //we do not log to internal memory, its not so easy to get the files back, external is easier via usb mass storage
			   /*
			   //try to prepare internal logging
				File intfolder = getApplicationContext().getDir("data", Context.MODE_WORLD_WRITEABLE);
				String folderStr = intfolder.getAbsolutePath() + File.separator + folderTimeStr;
				toasting("logging internal to: " +folderStr, Toast.LENGTH_LONG);
				file = new File(folderStr, timestamp + FILE_EXT);
			    folder = new File(folderStr);
			    folder.mkdirs();//create missing dirs
				file.createNewFile();
				if (!file.canWrite()) throw new Exception();
				*/
		   }catch(Exception e2){
			   file= null;
	    	   error("exception during prepareLogging(): " + e2.getMessage());//toast
	    	   this.stopSelf();//we quit. we will not continue without file logging
		   } 
		   
		  		   
		   
	   }
	   return file;
	}


    private String getFormated(long t){
      return String.format("%.2f", (double)t/1000);
    }

    private void noteResult(long tsot){
        int icon = R.drawable.ic_launcher;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String result = "*TSOT: "+getFormated(mTsot)+"s";
        Notification notification = new Notification(icon, result, System.currentTimeMillis());
        Context context = getApplicationContext();
        Intent notificationIntent = new Intent(context, de.tum.mw.lfe.occlusion.MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(context, "Last occlusion result", result, contentIntent);
        notificationManager.notify(LAST_RESULT_ID, notification);
    }

        private void logging(String action){
		 long nowT = SystemClock.elapsedRealtime();
	 
		 // HEADER = timestamp;action;mode;closeEnabled;open;gray;avgFrameRate;previewSize;rotation;marker;
		 StringBuilder log = new StringBuilder(2048);
		 log.append(nowT);//timestamp
		 log.append(CSV_DELIMITER);
		 log.append(action);
		 log.append(CSV_DELIMITER);
		 log.append(mPrefs.getMode());
		 log.append(CSV_DELIMITER);
		 log.append(mPrefs.getEnabledClose());
		 log.append(CSV_DELIMITER);
		 log.append(mStatus);
		 log.append(CSV_DELIMITER);
		 log.append(mPrefs.getGray());
		 log.append(CSV_DELIMITER);
		 Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		 log.append(display.getRefreshRate());			 
		 log.append(CSV_DELIMITER);
		 if(mPrefs.getMode() == mPrefs.MODE_CAMERA){
			 log.append(getAveragFramerate()/1000);
			 log.append(CSV_DELIMITER);
			 if(mCamera != null){
				 log.append(mCamera.getParameters().getPreviewSize().width);
				 log.append("x");
				 log.append(mCamera.getParameters().getPreviewSize().height);
				 log.append(CSV_DELIMITER);
			 }else{
				 log.append("null");				 
				 log.append(CSV_DELIMITER);				 
			 }
			 log.append(mPrefs.getRotation());
			 log.append(CSV_DELIMITER);			 

		 }else{
			 log.append("-");
			 log.append(CSV_DELIMITER);
			 log.append("-");
			 log.append(CSV_DELIMITER);			 
			 log.append("-");
			 log.append(CSV_DELIMITER);			 
		 }

		 log.append((char)mMarker);
		 log.append(CSV_LINE_END);
		 		 
		 
		   try{
			   String tempStr = log.toString();
			    byte[] bytes = tempStr.getBytes("US-ASCII");
				writeToFile(bytes,mFile);
		   }catch(Exception e){
			   error("error writing log data: "+e.getMessage());//toast
			   this.stopSelf();//we quit. we will not continue without file logging
		   }		
	}	

	private void writeToFile(byte[] data, File file){
   		       		
   		if (data == null){//error
       		error("writeFile() data==null?!");
       		this.stopSelf();//we quit. we will not continue without file logging
   		}
   		
		FileOutputStream dest = null; 
							
		try {
			dest = new FileOutputStream(file, true);
			dest.write(data);
		}catch(Exception e){
			error("writeFile() failed. msg: " + e.getMessage());
			this.stopSelf();//we quit. we will not continue without file logging
			
		}finally {
			try{
				dest.flush();
				dest.close();
			}catch(Exception e){}
		}
		
		return;
   }
	
	private void error(final String msg){//toast and log some errors
		toasting(msg, Toast.LENGTH_LONG);
		Log.e(TAG,msg);
	}	    
    
	
}