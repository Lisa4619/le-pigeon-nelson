package com.jmtrivial.lepigeonnelson.broadcastplayer;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class UIHandler extends Handler {
    public static final int END_OF_BROADCAST = 0;
    public static final int SERVER_ERROR = 1;
    public static final int SERVER_CONTENT_ERROR = 2;
    public static final int NO_GPS = 3;
    public static final int NEW_SERVER_DESCRIPTION = 4;
    public static final int UPDATE_LIST = 5;

    private BroadcastPlayer.BroadcastPlayerListener listener;



    public UIHandler() {
        listener = null;
    }

    public void handleMessage(Message msg) {
        final int what = msg.what;
        if (what == END_OF_BROADCAST) {
            if (listener != null) {
                Log.d("BroadcastPlayer", "End of broadcast sent to UI");
                listener.onEndOfBroadcast();
            }
        }
        else if (what == SERVER_ERROR) {
            Log.d("BroadcastPlayer", "Error while reading server");
            listener.onServerError();
        }
        else if (what == SERVER_CONTENT_ERROR) {
            Log.d("BroadcastPlayer", "Error while reading server (content error)");
            listener.onServerContentError();
        }
        else if (what == NO_GPS) {
            Log.d("BroadcastPlayer", "Cannot get GPS coordinate");
            listener.onServerGPSError();
        }
        else if (what == NEW_SERVER_DESCRIPTION) {
            Log.d("BroadcastPlayer", "New server description received");
            ServerDescription description = (ServerDescription) msg.obj;
            listener.onServerDescriptionUpdate(description);
        }
        else if (what == UPDATE_LIST) {
            listener.onServerListUpdated();
        }
    }

    public void setListener(BroadcastPlayer.BroadcastPlayerListener listener) {
        this.listener = listener;
    }
}
