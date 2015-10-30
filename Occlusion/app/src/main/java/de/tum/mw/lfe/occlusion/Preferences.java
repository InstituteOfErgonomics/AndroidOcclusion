package de.tum.mw.lfe.occlusion;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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

public class Preferences {
	private static final String TAG = "LFEocclusion.Preferences";
	private SharedPreferences sharedPrefs;
	
	public static final int MODE_SCREEN = 0;
	public static final int MODE_CAMERA = 1;
	
	Preferences(Context ctx){
		sharedPrefs=ctx.getSharedPreferences("OCCLUSION_PREFS", Context.MODE_PRIVATE);
	}   

	public void setOpen(int open){
		sharedPrefs.edit().putInt("open", open).commit();
	}
	public int getOpen(){
		return sharedPrefs.getInt("open", 1500);
	}	
	
	
	public void setClose(int close){
		sharedPrefs.edit().putInt("close", close).commit();
	}
	public int getClose(){
		return sharedPrefs.getInt("close", 1500);
	}	
	
	public void setMode(int mode){
		sharedPrefs.edit().putInt("mode", mode).commit();
	}
	public int getMode(){
		return sharedPrefs.getInt("mode", MODE_SCREEN);
	}
	
	
	public void setRotation(int rotation){
		sharedPrefs.edit().putInt("rotation", rotation).commit();
	}
	public int getRotation(){
		return sharedPrefs.getInt("rotation", 90);
	}

    public void setLastTsot(long tsot){sharedPrefs.edit().putLong("tsot", tsot).commit();}
    public long getLastTsot(){
        return sharedPrefs.getLong("tsot", -1);
    }

    public void setLastTtt(long ttt){
        sharedPrefs.edit().putLong("ttt", ttt).commit();
    }
    public long getLastTtt(){
        return sharedPrefs.getLong("ttt", -1);
    }


    public void setEnabledClose(boolean enabledClose){
		sharedPrefs.edit().putBoolean("enabledClose", enabledClose).commit();
	}
	public boolean getEnabledClose(){
		return sharedPrefs.getBoolean("enabledClose", true);
	}		
	
	public void setGray(int grey){
		sharedPrefs.edit().putInt("gray", grey).commit();
	}
	public int getGray(){
		return sharedPrefs.getInt("gray", 0);
	}
	public int getGrayColor(){
		//convert gray (0-255) to a color value 0xaarrggbb
		int gray = sharedPrefs.getInt("gray", 0);
		int tempColor = 0xff000000;//opacity full
		tempColor += gray*256*256;//r
		tempColor += gray*256;//g
		tempColor += gray;//b
		return tempColor;
	}	
	
	
	public void setEnableWakelock(boolean enableWakelock){
		sharedPrefs.edit().putBoolean("enableWakelock", enableWakelock).commit();
	}
	public boolean getEnableWakelock(){
		return sharedPrefs.getBoolean("enableWakelock", true);
	}


}
