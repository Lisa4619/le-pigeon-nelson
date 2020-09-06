package com.jmtrivial.lepigeonnelson.broadcastplayer;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class UIHandler extends Handler {
    public static final int END_OF_BROADCAST = 0;
    public static final int SERVER_ERROR = 1;

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
    }

    public void setListener(BroadcastPlayer.BroadcastPlayerListener listener) {
        this.listener = listener;
    }
}
